"""Motor de síntese dos sons da Armilar.

Tudo nasce aqui, em numpy, sem gravação de fora: vozes de coro por formantes, vento cantado (ruído em banda
estreita), cristais com parciais agrupados que cintilam, chuva de brilho, estalos elétricos, servo e reverb de
convolução com cauda que escurece. Os loops são periódicos por construção: toda frequência é múltiplo de 1/T, o
ruído é circular e o reverb é convolução circular, então o fim emenda no começo sem crossfade.
"""
from __future__ import annotations

import numpy as np

SR = 48000
NFFT = 2048
HOP = 480


# ---------------------------------------------------------------- base

def n_of(sec: float) -> int:
    return int(round(sec * SR))


def tl(n: int) -> np.ndarray:
    return np.arange(n) / SR


_SEMI = {'C': -9, 'C#': -8, 'D': -7, 'D#': -6, 'E': -5, 'F': -4, 'F#': -3, 'G': -2, 'G#': -1, 'A': 0, 'A#': 1, 'B': 2}


def hz(nota: str) -> float:
    """'D3', 'F#5'... com lá 4 = 440 Hz."""
    return 440.0 * 2 ** ((_SEMI[nota[:-1]] + 12 * (int(nota[-1]) - 4)) / 12)


def cents(f: float, c: float) -> float:
    return f * 2 ** (c / 1200)


def db(x: float) -> float:
    return 10 ** (x / 20)


def snap(f: float, period: float | None) -> float:
    """Num loop de T segundos, a frequência vira múltiplo de 1/T (fecha o ciclo exato)."""
    if period is None:
        return f
    return max(1.0, round(f * period)) / period


def ar(n: int, attack: float, t60: float, start: float = 0.0, hold: float = 0.0) -> np.ndarray:
    """Envelope: subida em seno² de `attack` s, platô de `hold` s e queda exponencial de 60 dB em `t60` s."""
    t = tl(n) - start
    e = np.zeros(n)
    m = t >= 0
    tt = t[m]
    a = np.clip(tt / attack, 0, 1) if attack > 0 else np.ones_like(tt)
    a = np.sin(a * np.pi / 2) ** 2
    d = 10 ** (-3 * np.maximum(tt - attack - hold, 0) / t60)
    e[m] = a * d
    return e


def ramp(n: int, pts: list[tuple[float, float]], curve: str = 'lin') -> np.ndarray:
    """Curva por pontos (tempo s, valor). 'exp' interpola em log (para frequências)."""
    t = tl(n)
    ts = np.array([p[0] for p in pts])
    vs = np.array([p[1] for p in pts], dtype=float)
    if curve == 'exp':
        return np.exp(np.interp(t, ts, np.log(vs)))
    if curve == 'smooth':
        out = np.empty(n)
        idx = np.clip(np.searchsorted(ts, t, side='right') - 1, 0, len(ts) - 2)
        t0, t1 = ts[idx], ts[idx + 1]
        u = np.clip((t - t0) / np.maximum(t1 - t0, 1e-9), 0, 1)
        u = u * u * (3 - 2 * u)
        out[:] = vs[idx] + (vs[idx + 1] - vs[idx]) * u
        return out
    return np.interp(t, ts, vs)


def fade(x: np.ndarray, fin: float = 0.003, fout: float = 0.04) -> np.ndarray:
    x = x.copy()
    a, b = n_of(fin), n_of(fout)
    if a:
        x[:a] *= np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    if b:
        x[-b:] *= np.cos(np.linspace(0, np.pi / 2, b)) ** 2
    return x


def place(out: np.ndarray, x: np.ndarray, at: float, gain: float = 1.0) -> None:
    i = n_of(at)
    if i >= len(out):
        return
    m = min(len(x), len(out) - i)
    out[i:i + m] += gain * x[:m]


def place_circ(out: np.ndarray, x: np.ndarray, at_sample: int, gain: float = 1.0) -> None:
    n = len(out)
    idx = (at_sample + np.arange(len(x))) % n
    np.add.at(out, idx, gain * x)


# ---------------------------------------------------------------- osciladores

def osc(n: int, f, amp=1.0, phase: float | None = None, rng=None, vib=None) -> np.ndarray:
    """Senoide com frequência fixa ou curva (Hz por amostra). `vib` = (taxa Hz, profundidade em cents, fase).

    Com frequência fixa e vibrato senoidal a fase é fechada em forma analítica: num loop, se f·T e taxa·T são
    inteiros, o ciclo fecha sem erro de soma.
    """
    rng = rng or np.random.default_rng(0)
    ph0 = rng.uniform(0, 2 * np.pi) if phase is None else phase
    t = tl(n)
    if np.isscalar(f):
        ph = 2 * np.pi * f * t + ph0
        if vib is not None:
            rate, depth, vph = vib
            beta = f * (2 ** (depth / 1200) - 1) / rate
            ph = ph + beta * np.sin(2 * np.pi * rate * t + vph)
        fmax = f
    else:
        f = np.asarray(f, dtype=float)
        ph = 2 * np.pi * np.cumsum(f) / SR + ph0
        fmax = f
    y = np.sin(ph)
    lim = 0.46 * SR
    if np.isscalar(fmax):
        if fmax >= lim:
            return np.zeros(n)
    else:
        y = y * np.clip((lim - fmax) / 2000, 0, 1)
    return amp * y


# ---------------------------------------------------------------- STFT

_WIN = np.hanning(NFFT + 1)[:-1]
FREQS = np.fft.rfftfreq(NFFT, 1 / SR)


def stft(x: np.ndarray, circ: bool = False):
    n = len(x)
    if circ:
        assert n % HOP == 0, 'loop tem de ter tamanho múltiplo do salto'
        nfr = n // HOP
        idx = (np.arange(NFFT)[None, :] + HOP * np.arange(nfr)[:, None]) % n
        frames = x[idx] * _WIN
        times = (HOP * np.arange(nfr) + NFFT / 2) / SR % (n / SR)
    else:
        pad = NFFT
        xp = np.concatenate([np.zeros(pad), x, np.zeros(pad + NFFT)])
        nfr = (len(xp) - NFFT) // HOP + 1
        idx = np.arange(NFFT)[None, :] + HOP * np.arange(nfr)[:, None]
        frames = xp[idx] * _WIN
        times = (HOP * np.arange(nfr) - pad + NFFT / 2) / SR
    return np.fft.rfft(frames, axis=1), times


def istft(S: np.ndarray, n: int, circ: bool = False) -> np.ndarray:
    frames = np.fft.irfft(S, n=NFFT, axis=1) * _WIN
    nfr = len(frames)
    if circ:
        out = np.zeros(n)
        w = np.zeros(n)
        for i in range(nfr):
            idx = (i * HOP + np.arange(NFFT)) % n
            np.add.at(out, idx, frames[i])
            np.add.at(w, idx, _WIN ** 2)
        return out / np.maximum(w, 1e-9)
    pad = NFFT
    L = HOP * (nfr - 1) + NFFT
    out = np.zeros(L)
    w = np.zeros(L)
    for i in range(nfr):
        out[i * HOP:i * HOP + NFFT] += frames[i]
        w[i * HOP:i * HOP + NFFT] += _WIN ** 2
    out /= np.maximum(w, 1e-9)
    return out[pad:pad + n]


def shape(x: np.ndarray, gain, circ: bool = False) -> np.ndarray:
    """Filtro que muda no tempo: `gain(t[frames,1], f[1,bins])` devolve o ganho (amplitude) por quadro e por faixa."""
    S, times = stft(x, circ)
    G = gain(times[:, None], FREQS[None, :])
    return istft(S * G, len(x), circ)


def white(n: int, rng) -> np.ndarray:
    return rng.standard_normal(n)


# ---------------------------------------------------------------- formas de filtro (ganho em função de f)

def bandpass(f, fc, bw_oct):
    """Sino em log-frequência: largura total a -6 dB de `bw_oct` oitavas."""
    fc = np.maximum(fc, 1.0)
    d = np.log2(np.maximum(f, 1.0) / fc)
    return np.exp(-0.5 * (d / (bw_oct / 2.355)) ** 2)


def lowpass(f, fc, slope_oct_db=18.0):
    fc = np.maximum(fc, 1.0)
    d = np.log2(np.maximum(f, 1.0) / fc)
    return np.where(d <= 0, 1.0, db(-slope_oct_db * d))


def highpass(f, fc, slope_oct_db=18.0):
    fc = np.maximum(fc, 1.0)
    d = np.log2(np.maximum(f, 1.0) / fc)
    return np.where(d >= 0, 1.0, db(slope_oct_db * d))


def peak(f, fc, q):
    """Ressonância estreita (Lorentziana) com fator de qualidade q."""
    bw = np.maximum(fc, 1.0) / q
    return 1.0 / (1.0 + ((f - fc) / (bw / 2)) ** 2)


# vogais de coro (contralto): formantes em Hz, ganho em dB e largura em Hz
VOGAIS = {
    'a': [(800, 0, 80), (1150, -4, 90), (2800, -20, 120), (3500, -36, 130), (4950, -60, 140)],
    'e': [(400, 0, 60), (1600, -24, 80), (2700, -30, 120), (3300, -35, 150), (4950, -60, 200)],
    'i': [(350, 0, 50), (1700, -20, 100), (2700, -30, 120), (3700, -36, 150), (4950, -60, 200)],
    'o': [(450, 0, 70), (800, -9, 80), (2830, -16, 100), (3500, -28, 130), (4950, -55, 135)],
    'u': [(325, 0, 50), (700, -12, 60), (2530, -30, 170), (3500, -40, 180), (4950, -64, 200)],
}


def formant_gain(f, vogal, abertura: float = 1.0):
    """Ganho de amplitude da vogal em f. `abertura` alarga as faixas (coro mais macio)."""
    if isinstance(vogal, tuple):  # mistura (vogal1, vogal2, peso de 0 a 1)
        v1, v2, w = vogal
        return (1 - w) * formant_gain(f, v1, abertura) + w * formant_gain(f, v2, abertura)
    g = 0.015
    for fc, gdb, bw in VOGAIS[vogal]:
        b = bw * abertura
        g = g + db(gdb) / (1.0 + ((f - fc) / (b / 2)) ** 2)
    return g


# ---------------------------------------------------------------- vozes

def choir(n: int, f0: float, vogal='o', vozes: int = 5, espalho_c: float = 9.0, rng=None, period=None,
          vib_c: float = 6.0, ar_g: float = 0.10, brilho: float = 1.0, fmax: float = 9000.0,
          glide=None) -> np.ndarray:
    """Coro de sintetizador: `vozes` vozes desafinadas (cents), série harmônica 1/h moldada pela vogal, vibrato
    lento e diferente por voz e um sopro de ar pela mesma vogal. `glide` é uma curva (multiplicador de f0)."""
    rng = rng or np.random.default_rng(1)
    out = np.zeros(n)
    t = tl(n)
    for v in range(vozes):
        c = (v - (vozes - 1) / 2) / max(1, (vozes - 1) / 2) * espalho_c + rng.normal(0, espalho_c * 0.15)
        fv = snap(cents(f0, c), period)
        if period is not None:
            rate = max(1, round(rng.uniform(0.18, 0.45) * period)) / period
        else:
            rate = rng.uniform(0.18, 0.45)
        vph = rng.uniform(0, 2 * np.pi)
        ph0 = rng.uniform(0, 2 * np.pi)
        beta = (2 ** (vib_c / 1200) - 1) / rate
        if glide is None:
            base = 2 * np.pi * fv * t + ph0 + fv * beta * np.sin(2 * np.pi * rate * t + vph)
            finst = fv
        else:
            finst = fv * glide * (1 + (2 ** (vib_c / 1200) - 1) * np.cos(2 * np.pi * rate * t + vph))
            base = 2 * np.pi * np.cumsum(finst) / SR + ph0
        H = int(min(fmax, 0.45 * SR) // fv)
        z = np.exp(1j * base)
        zh = np.ones(n, dtype=complex)
        for h in range(1, H + 1):
            zh *= z
            g = formant_gain(h * fv, vogal) / h ** (1.0 / brilho)
            if g < 2e-4:
                continue
            if glide is not None:
                out += g * zh.imag * np.clip((0.45 * SR - h * finst) / 2000, 0, 1)
            else:
                out += g * zh.imag
    out /= vozes
    if ar_g > 0:
        ns = white(n, rng)
        ar_ = shape(ns, lambda tt, ff: formant_gain(ff, vogal, 1.6) * highpass(ff, f0 * 1.5), circ=period is not None)
        out += ar_g * ar_ * np.std(out) / max(np.std(ar_), 1e-9)
    return out


def sung_wind(n: int, freqs, q: float = 60.0, rng=None, period=None, drift=None) -> np.ndarray:
    """Vento cantado: ruído passado por ressonâncias estreitas nas notas (instável e vivo, nunca senoide pura).
    `freqs` é lista de (f, ganho). `drift` (opcional) é função t -> multiplicador de frequência."""
    rng = rng or np.random.default_rng(2)
    ns = white(n, rng)

    def g(tt, ff):
        m = 1.0 if drift is None else drift(tt)
        acc = 0.0
        for f, a in freqs:
            acc = acc + a * peak(ff, f * m, q)
        return acc
    y = shape(ns, g, circ=period is not None)
    return y / max(np.std(y), 1e-9)


def crystal(n: int, f0: float, t60: float = 1.6, at: float = 0.0, rng=None, brilho: float = 1.0,
            cluster: int = 3, espalho_hz: float = 6.0, inarm: float = 0.35, tique: float = 0.25,
            amp: float = 1.0, bend=None) -> np.ndarray:
    """Cristal tocado: parciais quase harmônicos (1, 2, 3, 4,2...) e inarmônicos de vidro (2,76 e 5,40), cada um
    dividido em `cluster` cópias a poucos Hz (cintila), agudos morrendo antes, e um tique de ruído no ataque."""
    rng = rng or np.random.default_rng(3)
    parts = [(1.0, 1.0, 1.0), (2.0, 0.42, 0.7), (3.0, 0.16, 0.5), (4.18, 0.10, 0.35),
             (2.76, 0.30 * inarm, 0.55), (5.40, 0.20 * inarm, 0.3), (8.93, 0.10 * inarm, 0.2)]
    out = np.zeros(n)
    i0 = n_of(at)
    m = n - i0
    if m <= 0:
        return out
    seg = np.zeros(m)
    for ratio, a, tdec in parts:
        f = f0 * ratio * (1 + rng.normal(0, 0.0015))
        if f > 0.44 * SR:
            continue
        a = a * brilho ** (np.log2(ratio))
        for k in range(cluster):
            fk = f + (k - (cluster - 1) / 2) * espalho_hz * rng.uniform(0.6, 1.4) * ratio ** 0.5
            e = ar(m, rng.uniform(0.0008, 0.002), t60 * tdec * rng.uniform(0.85, 1.15))
            fcurve = fk if bend is None else fk * bend[:m]
            seg += a / cluster * e * osc(m, fcurve, rng=rng)
    if tique > 0:
        k = white(n_of(0.012), rng) * ar(n_of(0.012), 0.0005, 0.010)
        k = shape(k, lambda tt, ff: bandpass(ff, min(f0 * 6, 14000), 2.0))
        seg[:len(k)] += tique * k / max(np.max(np.abs(k)), 1e-9) * 0.5
    out[i0:] = amp * seg
    return out


def sparkle(n: int, inicio: float, fim: float, densidade, notas, rng=None, dec=(0.04, 0.22),
            amp=(0.25, 1.0), oitavas=(1, 2), period=None) -> np.ndarray:
    """Chuva de brilho: grãos curtos de cristal agudo em notas da escala. `densidade` é função t -> grãos por
    segundo. Num loop (`period`), os grãos dão a volta."""
    rng = rng or np.random.default_rng(4)
    out = np.zeros(n)
    t = inicio
    tmax = fim
    top = max(densidade(x) for x in np.linspace(inicio, fim, 128)) + 1e-6
    while t < tmax:
        t += rng.exponential(1.0 / top)
        if t >= tmax:
            break
        if rng.uniform() > densidade(t) / top:
            continue
        f = rng.choice(notas) * 2 ** rng.integers(oitavas[0], oitavas[1] + 1)
        f = f * (1 + rng.normal(0, 0.002))
        if f > 0.42 * SR:
            continue
        d = rng.uniform(*dec)
        L = n_of(min(d * 2.5, 0.8))
        e = ar(L, rng.uniform(0.002, 0.006), d)
        g = e * (osc(L, f, rng=rng) + 0.35 * osc(L, f * 2.003, rng=rng) + 0.5 * osc(L, f + rng.uniform(2, 9), rng=rng))
        a = rng.uniform(*amp) ** 1.5
        if period is None:
            place(out, g, t, a)
        else:
            place_circ(out, g, n_of(t) % n, a)
    return out


def crackle(n: int, taxa, banda=(2500, 9000), rng=None, period=None, rajada=(1, 5)) -> np.ndarray:
    """Estalo elétrico: rajadas de 1 a 5 cliques em poucos ms, amplitudes de cauda longa (Pareto), em banda.
    `taxa` é função t -> rajadas por segundo."""
    rng = rng or np.random.default_rng(5)
    imp = np.zeros(n)
    t = 0.0
    dur = n / SR
    top = max(taxa(x) for x in np.linspace(0, dur, 64)) + 1e-6
    while True:
        t += rng.exponential(1.0 / top)
        if t >= dur:
            break
        if rng.uniform() > taxa(t) / top:
            continue
        a = min(rng.pareto(2.2) + 0.2, 4.0)
        for k in range(rng.integers(rajada[0], rajada[1] + 1)):
            i = n_of(t + k * rng.uniform(0.0007, 0.004))
            if period is not None:
                i %= n
            elif i >= n:
                continue
            imp[i] += a * rng.choice([-1, 1]) * rng.uniform(0.3, 1.0)
    # cada clique vira uma faísca de ruído de ~1 ms
    k = white(n_of(0.003), rng) * ar(n_of(0.003), 0.0001, 0.0018)
    y = circ_conv(imp, k) if period is not None else np.convolve(imp, k)[:n]
    lo, hi = banda
    y = shape(y, lambda tt, ff: highpass(ff, lo, 12) * lowpass(ff, hi, 12), circ=period is not None)
    return y


def buzz(n: int, f0: float, rng=None, period=None, impar: float = 0.7, fmax: float = 3000.0) -> np.ndarray:
    """Zumbido elétrico: série harmônica com ímpares mais fortes (transformador), agudos caindo até `fmax`."""
    rng = rng or np.random.default_rng(6)
    f0 = snap(f0, period)
    out = np.zeros(n)
    for h in range(1, int(fmax // f0) + 1):
        g = (1.0 if h % 2 else 1 - impar) / h ** 0.9
        out += g * osc(n, h * f0, rng=rng)
    return out


def servo(n: int, curva_f, rng=None, banda=(250, 2200)) -> np.ndarray:
    """Servo de latão: dente de serra limitado com tom em curva, em banda, com leve aspereza de engrenagem."""
    rng = rng or np.random.default_rng(7)
    curva_f = np.asarray(curva_f, dtype=float)
    base = 2 * np.pi * np.cumsum(curva_f) / SR
    out = np.zeros(n)
    for h in range(1, 40):
        lim = np.clip((0.45 * SR - h * curva_f) / 2000, 0, 1)
        out += lim * np.sin(h * base) / h
    grit = 1 + 0.25 * shape(white(n, rng), lambda tt, ff: bandpass(ff, 90, 1.0))
    out *= grit
    lo, hi = banda
    return shape(out, lambda tt, ff: highpass(ff, lo, 18) * lowpass(ff, hi, 18))


def swish(n: int, giro, fc, bw_oct: float = 1.3, rng=None, fundo: float = 0.25) -> np.ndarray:
    """Órbita que gira: sopro em banda com o volume batendo na rotação (`giro(t)` em voltas por segundo), como um
    aro passando perto. Sem oscilador: só ar."""
    rng = rng or np.random.default_rng(12)
    t = tl(n)
    y = whoosh(n, fc, bw_oct=bw_oct, rng=rng, ripple=[(0.85, 0.6, 8), (1.0, 1.0, 6), (1.25, 0.5, 8)])
    fase = 2 * np.pi * np.cumsum(giro(t)) / SR
    am = fundo + (1 - fundo) * (0.5 + 0.5 * np.cos(fase)) ** 2
    return y * am


def thump(n: int, f_ini: float = 95, f_fim: float = 48, queda: float = 0.11, t60: float = 0.35, at: float = 0.0,
          rng=None) -> np.ndarray:
    """Baque grave macio: senoide com o tom caindo, sem estalo de clique."""
    rng = rng or np.random.default_rng(8)
    out = np.zeros(n)
    i0 = n_of(at)
    m = n - i0
    f = f_fim + (f_ini - f_fim) * np.exp(-tl(m) / queda)
    y = osc(m, f, phase=0.0) * ar(m, 0.004, t60)
    out[i0:] = np.tanh(1.6 * y) / np.tanh(1.6)
    return out


def whoosh(n: int, fc_curve, bw_oct: float = 1.6, ripple=None, rng=None, period=None) -> np.ndarray:
    """Sopro: ruído numa banda que anda (`fc_curve(t)`), com manchas tonais (formantes) que ondulam, como o vento
    de um feitiço. `ripple` = lista de (razão em relação ao centro, ganho, q)."""
    rng = rng or np.random.default_rng(9)
    ripple = ripple if ripple is not None else [(0.72, 0.8, 9), (1.0, 1.0, 7), (1.38, 0.7, 9), (1.9, 0.45, 11)]

    def g(tt, ff):
        fc = fc_curve(tt)
        acc = 0.35 * bandpass(ff, fc, bw_oct)
        for r, a, q in ripple:
            acc = acc + a * bandpass(ff, fc * r, 1.5 / q)
        return acc
    y = shape(white(n, rng), g, circ=period is not None)
    return y / max(np.std(y), 1e-9)


# ---------------------------------------------------------------- espaço

def circ_conv(x: np.ndarray, h: np.ndarray) -> np.ndarray:
    n = len(x)
    hf = np.zeros(n)
    for i in range(0, len(h), n):
        part = h[i:i + n]
        hf[:len(part)] += part
    return np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(hf), n)


def lin_conv(x: np.ndarray, h: np.ndarray) -> np.ndarray:
    L = len(x) + len(h) - 1
    N = 1 << (L - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, N) * np.fft.rfft(h, N), N)[:L]


def hall_ir(t60_grave: float = 2.4, t60_agudo: float = 0.9, predelay: float = 0.014, rng=None,
            brilho_hz: float = 9000.0) -> np.ndarray:
    """Resposta de sala sintética: ruído com queda por faixa (o agudo morre antes), reflexões iniciais esparsas,
    entrada difusa de 30 ms e sem metal."""
    rng = rng or np.random.default_rng(10)
    L = n_of(max(t60_grave, t60_agudo) * 1.05 + predelay)
    ns = white(L, rng)

    def g(tt, ff):
        lf = np.log2(np.clip(ff, 150, 12000) / 150) / np.log2(12000 / 150)
        t60 = np.exp((1 - lf) * np.log(t60_grave) + lf * np.log(t60_agudo))
        tt_ = np.maximum(tt - predelay, 0)
        build = np.clip(tt_ / 0.03, 0, 1)
        return np.where(tt < predelay, 0, build * 10 ** (-3 * tt_ / t60)) * lowpass(ff, brilho_hz, 9)
    ir = shape(ns, g)
    for _ in range(9):
        i = n_of(predelay * rng.uniform(0.4, 1.6))
        if i < L:
            ir[i] += rng.uniform(-1, 1) * 2.0
    ir[0] = 0
    return ir / np.sqrt(np.sum(ir ** 2))


def reverb(x: np.ndarray, ir: np.ndarray, mix: float = 0.3, period=None, tail: bool = True,
           hp: float = 280.0) -> np.ndarray:
    """Reverb por convolução. O envio passa por um passa-alta (`hp`): o grave fica seco e não embola a cauda."""
    envio = shape(x, lambda tt, ff: highpass(ff, hp, 18), circ=period is not None) if hp else x
    if period is not None:
        wet = circ_conv(envio, ir)
        return (1 - mix * 0.5) * x + mix * wet
    wet = lin_conv(envio, ir)
    if not tail:
        wet = wet[:len(x)]
    out = np.zeros(len(wet))
    out[:len(x)] += (1 - mix * 0.5) * x
    out += mix * wet
    return out


# ---------------------------------------------------------------- acabamento

def tame(x: np.ndarray, drive: float = 1.0) -> np.ndarray:
    """Saturação macia (corta só o excesso, dá corpo)."""
    if drive <= 0:
        return x
    p = np.max(np.abs(x)) + 1e-12
    y = np.tanh(drive * x / p) / np.tanh(drive)
    return y * p


def trim(x: np.ndarray, floor_db: float = -60.0, fout: float = 0.06, max_s: float | None = None) -> np.ndarray:
    """Corta o silêncio do fim (abaixo de `floor_db` do pico) e, se passar de `max_s`, encerra com fade longo."""
    p = np.max(np.abs(x)) + 1e-12
    k = n_of(0.01)
    c = np.cumsum(np.abs(x))
    env = (c[k:] - c[:-k]) / k
    idx = np.nonzero(env > p * db(floor_db))[0]
    end = min(len(x), idx[-1] + k + n_of(0.02)) if len(idx) else len(x)
    if max_s is not None and end > n_of(max_s):
        return fade(x[:n_of(max_s)], 0.002, max(fout, 0.25))
    return fade(x[:end], 0.002, fout)


def peak_norm(x: np.ndarray, dbfs: float) -> np.ndarray:
    return x / (np.max(np.abs(x)) + 1e-12) * db(dbfs)


def rms_norm(x: np.ndarray, dbfs: float) -> np.ndarray:
    return x / (np.sqrt(np.mean(x ** 2)) + 1e-12) * db(dbfs)


def clean_low(x: np.ndarray, fc: float = 35.0, circ: bool = False) -> np.ndarray:
    return shape(x, lambda tt, ff: highpass(ff, fc, 24), circ=circ)
