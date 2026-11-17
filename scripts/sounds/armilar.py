"""Os 14 sons da Armilar, um por etapa, numa paleta magitech: a magia é tonal (coro, cristal, vento cantado e
brilho, todos em ré lídio) e a tecnologia é o latão e a eletricidade (giro das órbitas, trava, faísca e zumbido).

Regras da paleta: nenhuma varredura de oscilador puro (soa a teremim de rádio antigo); toda subida ou descida é ar
cantado ou coro. Nada de estalo em banda larga nos loops (soa a disco riscado); a eletricidade é faísca aguda.
Nada de saturação nos eventos (o tanh em agudos densos cria lama no médio-grave). Grãos com ataque de 2 a 6 ms,
sem clique. O grave fica seco: o reverb só recebe acima de 280 Hz.

Cada elemento tem a sua voz:
- o sol é um coro grave em "u" e "o" que respira devagar;
- o feixe é um rio de ar cantado em lá, com faíscas e um zumbido elétrico por baixo;
- os cristais são vidro agudo com parciais que cintilam;
- as órbitas são latão: o ar do aro que gira e a trava que fecha;
- a conjunção junta tudo num acorde de coro com chuva de brilho.
"""
from __future__ import annotations

import numpy as np

from dsp import (ar, buzz, choir, clean_low, crackle, crystal, fade, hall_ir, highpass, hz, lowpass, n_of, osc,
                 peak_norm, ramp, reverb, rms_norm, shape, sparkle, sung_wind, swish, tame, thump, tl, trim, whoosh,
                 white)

ACORDE = ['D', 'E', 'F#', 'A', 'C#']                 # ré com nona e sétima maior: o brilho de toda a família
BRILHO = [hz(f'{x}6') for x in ACORDE]               # notas dos grãos de brilho (1,2 a 2,2 kHz, sobem 1 ou 2 oitavas)

SALA = hall_ir(2.6, 0.95, rng=np.random.default_rng(101))
SALA_CURTA = hall_ir(1.1, 0.45, predelay=0.008, rng=np.random.default_rng(102))
SALA_LOOP = hall_ir(3.2, 1.3, predelay=0.02, rng=np.random.default_rng(103))


def _env(n, pts, curve='smooth'):
    return ramp(n, pts, curve)


def _latch(n, at, f=1830.0, rng=None, amp=1.0):
    """Trava de latão: modos metálicos curtos e um tique."""
    rng = rng or np.random.default_rng(11)
    out = np.zeros(n)
    m = n - n_of(at)
    seg = np.zeros(m)
    for r, a, t60 in [(1.0, 1.0, 0.16), (1.48, 0.7, 0.12), (2.44, 0.45, 0.09), (3.34, 0.3, 0.06)]:
        seg += a * ar(m, 0.0006, t60) * osc(m, f * r * rng.uniform(0.995, 1.005), rng=rng)
    k = white(n_of(0.006), rng) * ar(n_of(0.006), 0.0002, 0.004)
    seg[:len(k)] += 0.8 * k
    out[n_of(at):] = amp * seg
    return out


def _bend(n, c0, tau, end_c=0.0):
    """Curva de afinação: começa a `c0` cents e converge para `end_c` com constante `tau`."""
    t = tl(n)
    return 2 ** ((end_c + (c0 - end_c) * np.exp(-t / tau)) / 1200)


# ================================================================ loops (periódicos por construção)

def sun_loop():
    T = 8.0
    n = n_of(T)
    rng = np.random.default_rng(201)
    notas = [(hz('D2'), 1.0), (hz('A2'), 0.62), (hz('D3'), 0.36), (hz('F#3'), 0.14)]
    pad_u = sum(g * choir(n, f, 'u', vozes=4, espalho_c=7, vib_c=4, rng=rng, period=T, ar_g=0.06) for f, g in notas)
    pad_o = sum(g * choir(n, f, 'o', vozes=4, espalho_c=7, vib_c=4, rng=rng, period=T, ar_g=0.06) for f, g in notas)
    w = 0.5 + 0.5 * np.sin(2 * np.pi * tl(n) / T)           # a vogal respira de "u" para "o" a cada 8 s
    pad = (1 - w) * pad_u + w * pad_o
    halo = sung_wind(n, [(hz('D5'), 1.0), (hz('A5'), 0.7), (hz('E6'), 0.35)], q=90, rng=rng, period=T)
    brasa = sparkle(n, 0, T, lambda t: 3.0, BRILHO, rng=rng, dec=(0.05, 0.16), amp=(0.2, 0.6), oitavas=(0, 1),
                    period=T)
    x = pad / np.std(pad) + 0.07 * halo + 0.035 * brasa / max(np.std(brasa), 1e-9)
    x = reverb(x, SALA_LOOP, 0.35, period=T)
    x = clean_low(x, 32, circ=True)
    return rms_norm(x, -31.0)


def flow_loop():
    T = 6.0
    n = n_of(T)
    rng = np.random.default_rng(202)
    t3 = lambda t: 2 ** (0.4 * np.sin(2 * np.pi * t / T))
    rio = whoosh(n, lambda t: 1700 * t3(t), bw_oct=2.4, rng=rng, period=T)
    rio2 = whoosh(n, lambda t: 2600 / t3(t), bw_oct=1.8, rng=rng, period=T,
                  ripple=[(0.8, 0.6, 12), (1.0, 1.0, 10), (1.33, 0.5, 12)])
    canto = sung_wind(n, [(hz('A4'), 1.0), (hz('E5'), 0.75), (hz('A5'), 0.6), (hz('C#6'), 0.4)], q=75, rng=rng,
                      period=T, drift=lambda t: 2 ** (0.012 * np.sin(2 * np.pi * 2 * t / T)))
    fagulha = crackle(n, lambda t: 18 * (1 + 0.6 * np.sin(2 * np.pi * 3 * t / T)), banda=(5500, 13000), rng=rng,
                      period=T, rajada=(1, 2))
    zumbido = buzz(n, hz('A2'), rng=rng, period=T, fmax=1400)
    brilho = sparkle(n, 0, T, lambda t: 9.0, BRILHO, rng=rng, dec=(0.03, 0.14), amp=(0.2, 0.8), period=T)
    x = (0.22 * rio + 0.14 * rio2 + 0.8 * canto + 0.05 * fagulha / max(np.std(fagulha), 1e-9)
         + 0.16 * zumbido / np.std(zumbido) + 0.32 * brilho / max(np.std(brilho), 1e-9))
    x = reverb(x, SALA_LOOP, 0.32, period=T)
    x = clean_low(x, 60, circ=True)
    return rms_norm(x, -32.0)


def full_loop():
    T = 8.0
    n = n_of(T)
    rng = np.random.default_rng(203)
    d2 = hz('D2')
    # dois rés graves a 0,25 Hz de distância: a pressão incha e desincha a cada 4 s, sem pulso nem corte
    sub = osc(n, round(d2 * T) / T, phase=0.0) + osc(n, (round(d2 * T) + 2) / T, phase=0.0) \
        + 0.4 * osc(n, round(hz('D1') * T) / T, phase=0.0)
    pad = choir(n, d2, 'u', vozes=3, espalho_c=5, vib_c=3, rng=rng, period=T, ar_g=0.04, fmax=2500) \
        + 0.5 * choir(n, hz('A2'), 'u', vozes=3, espalho_c=5, vib_c=3, rng=rng, period=T, ar_g=0.0, fmax=2500)
    tensao = sung_wind(n, [(hz('D6'), 1.0), (hz('G#6'), 0.5)], q=140, rng=rng, period=T)
    x = 0.5 * sub / np.std(sub) + pad / np.std(pad) + 0.05 * tensao
    x = tame(x, 0.8)
    x = reverb(x, SALA_LOOP, 0.25, period=T)
    x = clean_low(x, 28, circ=True)
    return rms_norm(x, -33.0)


# ================================================================ eventos

def assemble():
    n = n_of(3.6)
    rng = np.random.default_rng(301)
    x = np.zeros(n)
    # latão: as órbitas pegam giro (o ar de cada aro passando) e travam
    giro = swish(n, lambda t: np.interp(t, [0, 0.95, 3.6], [2.5, 11.0, 11.0]),
                 lambda t: np.interp(t, [0, 1.0, 3.6], [700, 1900, 1900]), rng=rng)
    x += 0.3 * giro * _env(n, [(0, 0), (0.3, 0.5), (0.9, 1.0), (1.02, 0.0), (3.6, 0)])
    for at, f in [(0.32, 1830.0), (0.62, 2050.0), (0.92, 2440.0)]:
        x += 0.18 * _latch(n, at, f, rng=rng)
    # o ar sobe e o brilho é puxado para dentro (cauda de reverb ao contrário)
    sobe = whoosh(n, lambda t: np.interp(t, [0, 1.0, 3.6], [450, 3600, 3600]), bw_oct=1.8, rng=rng)
    x += 0.35 * sobe * _env(n, [(0, 0), (0.95, 1.0), (1.05, 0.25), (1.6, 0), (3.6, 0)])
    m = n_of(1.0)
    hit = crystal(m, hz('D6'), 1.2, rng=rng) + crystal(m, hz('A6'), 1.2, rng=rng)
    inv = reverb(hit, SALA, 1.0)[:m][::-1]
    x[:m] += 0.5 * inv / np.max(np.abs(inv))
    # a flor: acorde de coro em "a", arpejo de cristal e chuva de brilho
    for nota, g in [('D3', 1.0), ('A3', 0.8), ('F#4', 0.6), ('C#5', 0.4), ('E5', 0.35)]:
        c = choir(n, hz(nota), ('o', 'a', 0.7), vozes=5, espalho_c=10, rng=rng, ar_g=0.12)
        x += 0.16 * g * c / np.std(c) * ar(n, 0.09, 2.6, start=1.0)
    for i, nota in enumerate(['D6', 'F#6', 'A6', 'D7']):
        x += 0.32 * crystal(n, hz(nota), 1.8 - 0.2 * i, at=1.0 + 0.07 * i, rng=rng)
    x += 0.5 * sparkle(n, 1.0, 2.8, lambda t: 70 * np.exp(-(t - 1.0) / 0.45), BRILHO, rng=rng)
    x += 0.22 * thump(n, 92, 46, 0.1, 0.3, at=1.0)
    x = reverb(x, SALA, 0.32)
    x = clean_low(x, 40)
    return peak_norm(trim(x), -4.0)


def tune_lock():
    n = n_of(1.9)
    rng = np.random.default_rng(302)
    x = np.zeros(n)
    giro = swish(n, lambda t: np.interp(t, [0, 0.27], [6.0, 13.0]), lambda t: np.interp(t, [0, 0.27], [1400, 2400]),
                 rng=rng)
    x += 0.28 * giro * _env(n, [(0, 0), (0.06, 1), (0.25, 1), (0.3, 0), (1.9, 0)])
    x += 0.26 * _latch(n, 0.27, 2210.0, rng=rng)
    # as vozes desafinadas convergem para o acorde puro: a sintonia "acerta"
    for nota, c0, g in [('D5', -38, 1.0), ('A5', 31, 0.8), ('F#6', -26, 0.55), ('D4', 22, 0.5)]:
        c = choir(n, hz(nota), ('i', 'a', 0.4), vozes=3, espalho_c=6, vib_c=3, rng=rng, ar_g=0.08,
                  glide=_bend(n, c0, 0.11))
        x += 0.18 * g * c / np.std(c) * ar(n, 0.06, 1.5, start=0.02)
    x += 0.35 * crystal(n, hz('A6'), 1.3, at=0.27, rng=rng)
    x += 0.35 * sparkle(n, 0.27, 1.2, lambda t: 40 * np.exp(-(t - 0.27) / 0.3), BRILHO, rng=rng)
    x = reverb(x, SALA, 0.3)
    x = clean_low(x, 60)
    return peak_norm(trim(x), -5.0)


def tune_lost():
    n = n_of(1.6)
    rng = np.random.default_rng(303)
    x = np.zeros(n)
    t = tl(n)
    u = np.clip(t / 1.0, 0, 1)
    for nota, abre, g in [('D5', -45, 1.0), ('A5', 38, 0.8), ('F#6', -30, 0.55), ('D4', 25, 0.5)]:
        glide = 2 ** ((-210 * u ** 1.4 + abre * u) / 1200)
        c = choir(n, hz(nota), ('i', 'u', 0.0), vozes=3, espalho_c=6, vib_c=4, rng=rng, ar_g=0.1, glide=glide)
        x += 0.2 * g * c / np.std(c) * ar(n, 0.015, 1.1)
    giro = swish(n, lambda t: np.interp(t, [0, 0.6], [12.0, 3.0]), lambda t: np.interp(t, [0, 0.6], [2200, 800]),
                 rng=rng)
    x += 0.26 * giro * _env(n, [(0, 0), (0.03, 1), (0.45, 0.6), (0.65, 0), (1.6, 0)])
    fz = crackle(n, lambda tt: 90 * np.exp(-tt / 0.15), banda=(4500, 11000), rng=rng, rajada=(1, 3))
    x += 0.16 * fz / max(np.max(np.abs(fz)), 1e-9)
    x = shape(x, lambda tt, ff: lowpass(ff, np.interp(tt, [0, 0.2, 1.1], [9000, 6000, 900]), 18))
    x = reverb(x, SALA, 0.25)
    x = clean_low(x, 60)
    return peak_norm(trim(x), -6.0)


def conj_charge():
    n = n_of(0.62)
    rng = np.random.default_rng(304)
    x = np.zeros(n)
    m = n_of(0.62)
    hit = crystal(m, hz('D6'), 1.5, rng=rng) + crystal(m, hz('A6'), 1.5, rng=rng) + crystal(m, hz('F#7'), 1.0, rng=rng)
    inv = reverb(hit, SALA, 1.0)[:m][::-1]
    x += 0.55 * inv / np.max(np.abs(inv))
    carga = sung_wind(n, [(1800, 1.0), (2700, 0.5), (3600, 0.3)], q=45, rng=rng,
                      drift=lambda t: np.exp(np.interp(t, [0, 0.62], [0, np.log(3.0)])))
    x += 0.22 * carga * _env(n, [(0, 0), (0.6, 1.0), (0.62, 1.0)])
    sobe = whoosh(n, lambda t: np.interp(t, [0, 0.62], [600, 6200]), bw_oct=1.6, rng=rng)
    x += 0.3 * sobe * _env(n, [(0, 0), (0.6, 1.0), (0.62, 1.0)])
    c = choir(n, hz('D4'), ('o', 'a', 0.8), vozes=5, espalho_c=12, rng=rng, ar_g=0.15,
              glide=ramp(n, [(0, 0.94), (0.62, 1.0)]))
    x += 0.2 * c / np.std(c) * _env(n, [(0, 0), (0.6, 1.0), (0.62, 1.0)])
    x = clean_low(x, 80)
    return peak_norm(fade(x, 0.004, 0.012), -6.0)


def _burst(seed, notas_coro, cristais, vogal, fc0, fc1):
    n = n_of(2.2)
    rng = np.random.default_rng(seed)
    x = np.zeros(n)
    snap_ = crackle(n, lambda t: 700.0 if t < 0.025 else 20 * np.exp(-(t - 0.025) / 0.12), banda=(4000, 13000),
                    rng=rng)
    x += 0.22 * snap_ / max(np.max(np.abs(snap_)), 1e-9)
    sopro = whoosh(n, lambda t: np.interp(t, [0, 0.6, 2.2], [fc0, fc1, fc1]), bw_oct=1.7, rng=rng)
    x += 0.32 * sopro * ar(n, 0.012, 0.75)
    for nota, g in notas_coro:
        c = choir(n, hz(nota), vogal, vozes=5, espalho_c=11, rng=rng, ar_g=0.14, brilho=1.15)
        x += 0.17 * g * c / np.std(c) * ar(n, 0.012, 1.35)
    for nota, g in cristais:
        x += 0.35 * g * crystal(n, hz(nota), 1.5, rng=rng)
    x += 0.55 * sparkle(n, 0.0, 1.4, lambda t: 140 * np.exp(-t / 0.28), BRILHO, rng=rng)
    x += 0.26 * thump(n, 84, 44, 0.08, 0.28)
    x = reverb(x, SALA, 0.3)
    x = clean_low(x, 40)
    return peak_norm(trim(x), -3.0)


def conj_burst_1():
    return _burst(305, [('D4', 1.0), ('A4', 0.8), ('F#5', 0.6), ('C#6', 0.4), ('E6', 0.35)],
                  [('D6', 1.0), ('A6', 0.7)], ('o', 'a', 0.8), 2600, 950)


def conj_burst_2():
    return _burst(306, [('D4', 1.0), ('B4', 0.75), ('F#5', 0.6), ('C#6', 0.45), ('G#6', 0.25)],
                  [('F#6', 1.0), ('B6', 0.6)], ('a', 'e', 0.35), 3300, 1150)


def conj_arc():
    n = n_of(0.95)
    rng = np.random.default_rng(307)
    x = np.zeros(n)
    cai = lambda t: np.interp(t, [0, 0.5, 0.95], [1.0, 0.4, 0.37])
    assobio = sung_wind(n, [(2300, 1.0), (3450, 0.5), (4600, 0.3)], q=38, rng=rng, drift=cai)
    x += 0.5 * assobio * ar(n, 0.018, 0.6)
    raio = crackle(n, lambda t: 220 * np.exp(-t / 0.2), banda=(3500, 12000), rng=rng)
    x += 0.32 * raio / max(np.max(np.abs(raio)), 1e-9)
    sopro = whoosh(n, lambda t: np.interp(t, [0, 0.5], [3200, 900]), bw_oct=1.2, rng=rng)
    x += 0.25 * sopro * ar(n, 0.01, 0.5)
    x += 0.2 * crystal(n, hz('A6'), 0.5, rng=rng)
    x = reverb(x, SALA_CURTA, 0.25)
    x = clean_low(x, 120)
    return peak_norm(trim(x), -6.0)


def _impact(seed, f_ini, f_fim, notas, fc0):
    n = n_of(1.3)
    rng = np.random.default_rng(seed)
    x = np.zeros(n)
    x += 0.42 * thump(n, f_ini, f_fim, 0.08, 0.26)
    baf = whoosh(n, lambda t: np.interp(t, [0, 0.3], [fc0, fc0 / 3]), bw_oct=1.4, rng=rng,
                 ripple=[(0.8, 0.7, 6), (1.0, 1.0, 5), (1.5, 0.5, 7)])
    x += 0.35 * baf * ar(n, 0.008, 0.45)
    for nota, g in notas:
        x += 0.5 * g * crystal(n, hz(nota), 1.0, at=0.012, rng=rng)
    x += 0.3 * sparkle(n, 0.01, 0.5, lambda t: 70 * np.exp(-t / 0.12), BRILHO, rng=rng)
    absorve = crackle(n, lambda t: 60 * np.exp(-t / 0.14), banda=(4500, 11000), rng=rng, rajada=(1, 3))
    x += 0.12 * absorve / max(np.max(np.abs(absorve)), 1e-9)
    x = reverb(x, SALA_CURTA, 0.22)
    x = clean_low(x, 30)
    return peak_norm(trim(x), -4.0)


def conj_impact_1():
    return _impact(308, 88, 42, [('D5', 1.0), ('A5', 0.6)], 1300)


def conj_impact_2():
    return _impact(309, 98, 48, [('F#5', 1.0), ('C#6', 0.55)], 1600)


def conj_impact_3():
    return _impact(310, 78, 38, [('A4', 1.0), ('E5', 0.6)], 1050)


def _grain(seed, nota, extra):
    n = n_of(0.32)
    rng = np.random.default_rng(seed)
    x = crystal(n, hz(nota), 0.22, rng=rng, brilho=1.2, cluster=2, espalho_hz=9, tique=0.35)
    for dt, nt in extra:
        x += 0.45 * crystal(n, hz(nt), 0.12, at=dt, rng=rng, cluster=2, tique=0.0)
    x = reverb(x, SALA_CURTA, 0.2)
    x = clean_low(x, 400)
    return peak_norm(trim(x, -66, 0.03), -9.0)


def recharge_grain_1():
    return _grain(311, 'A6', [(0.025, 'E7')])


def recharge_grain_2():
    return _grain(312, 'C#7', [(0.02, 'F#7'), (0.045, 'A7')])


def recharge_grain_3():
    return _grain(313, 'E7', [(0.03, 'B7')])


def recharge_done():
    n = n_of(2.1)
    rng = np.random.default_rng(314)
    x = np.zeros(n)
    sobe = sung_wind(n, [(1200, 1.0), (1800, 0.5)], q=40, rng=rng,
                     drift=lambda t: np.exp(np.interp(t, [0, 0.3], [0, np.log(2.5)])))
    x += 0.12 * sobe * _env(n, [(0, 0), (0.2, 1.0), (0.32, 0), (2.1, 0)])
    for i, nota in enumerate(['A5', 'C#6', 'E6', 'A6']):
        x += 0.3 * crystal(n, hz(nota), 1.0 + 0.2 * i, at=0.07 * i, rng=rng)
    x += 0.38 * crystal(n, hz('E7'), 1.6, at=0.29, rng=rng, cluster=4, espalho_hz=11)
    for nota, g in [('A5', 1.0), ('E6', 0.7)]:
        c = choir(n, hz(nota), 'i', vozes=4, espalho_c=8, rng=rng, ar_g=0.1)
        x += 0.1 * g * c / np.std(c) * ar(n, 0.12, 1.3, start=0.2)
    x += 0.4 * sparkle(n, 0.22, 1.6, lambda t: 55 * np.exp(-(t - 0.22) / 0.4), BRILHO, rng=rng)
    x = reverb(x, SALA, 0.33)
    x = clean_low(x, 150)
    return peak_norm(trim(x), -5.0)


def _crack(seed, dissonante, cai_de):
    n = n_of(0.85)
    rng = np.random.default_rng(seed)
    x = np.zeros(n)
    m = n_of(0.02)
    estalo = white(m, rng) * ar(m, 0.0002, 0.016)
    estalo = shape(np.concatenate([estalo, np.zeros(n - m)]), lambda tt, ff: highpass(ff, 2200, 24) * lowpass(ff, 15000))
    x += 0.8 * estalo / np.max(np.abs(estalo))
    x += 0.12 * osc(n, 330.0, phase=0.0) * ar(n, 0.001, 0.05)
    for i in range(rng.integers(6, 9)):
        f = rng.uniform(4800, 10500)
        x += 0.2 * crystal(n, f, rng.uniform(0.08, 0.32), at=rng.uniform(0.0, 0.26) ** 1.5, rng=rng, inarm=1.0,
                           cluster=1, tique=0.2)
    caida = ramp(n, [(0, 1.0), (0.6, 0.93), (0.85, 0.93)])
    for nota in dissonante:
        x += 0.2 * crystal(n, hz(nota), 0.7, at=0.005, rng=rng, bend=caida)
    vaza = crackle(n, lambda t: 130 * np.exp(-t / 0.18), banda=(4500, 13000), rng=rng, rajada=(1, 3))
    x += 0.22 * vaza / max(np.max(np.abs(vaza)), 1e-9)
    chiado = whoosh(n, lambda t: np.interp(t, [0, 0.5], [cai_de, cai_de / 2.5]), bw_oct=2.0, rng=rng)
    x += 0.12 * chiado * ar(n, 0.004, 0.4)
    x = reverb(x, SALA_CURTA, 0.2)
    x = clean_low(x, 150)
    return peak_norm(trim(x), -5.0)


def crystal_crack_1():
    return _crack(315, ['G#5', 'D6'], 7000)


def crystal_crack_2():
    return _crack(316, ['A5', 'D#6'], 8500)


def crystal_dull():
    n = n_of(1.5)
    rng = np.random.default_rng(317)
    x = np.zeros(n)
    caida = 2 ** (-110 * np.clip(tl(n) / 1.0, 0, 1) ** 1.3 / 1200)
    x += 0.6 * crystal(n, hz('A5'), 1.7, rng=rng, cluster=3, bend=caida)
    x += 0.3 * crystal(n, hz('E6'), 1.2, rng=rng, cluster=2, bend=caida)
    apaga = sung_wind(n, [(2400, 1.0), (3600, 0.4)], q=40, rng=rng,
                      drift=lambda t: np.exp(np.interp(t, [0, 0.9], [0, np.log(0.22)])))
    x += 0.14 * apaga * _env(n, [(0, 0), (0.05, 1.0), (0.8, 0.3), (1.0, 0), (1.5, 0)])
    c = choir(n, hz('D4'), 'u', vozes=3, espalho_c=8, rng=rng, ar_g=0.1, glide=caida)
    x += 0.12 * c / np.std(c) * ar(n, 0.04, 0.9)
    x = shape(x, lambda tt, ff: lowpass(ff, np.interp(tt, [0, 0.12, 0.8], [9000, 6500, 650]), 15))
    x = reverb(x, SALA, 0.25)
    x = clean_low(x, 80)
    return peak_norm(trim(x), -7.0)


# nome do arquivo -> (função, é loop)
SONS = {
    'assemble': (assemble, False),
    'sun_loop': (sun_loop, True),
    'tune_lock': (tune_lock, False),
    'tune_lost': (tune_lost, False),
    'conj_charge': (conj_charge, False),
    'conj_burst_1': (conj_burst_1, False),
    'conj_burst_2': (conj_burst_2, False),
    'conj_arc': (conj_arc, False),
    'conj_impact_1': (conj_impact_1, False),
    'conj_impact_2': (conj_impact_2, False),
    'conj_impact_3': (conj_impact_3, False),
    'flow_loop': (flow_loop, True),
    'full_loop': (full_loop, True),
    'recharge_grain_1': (recharge_grain_1, False),
    'recharge_grain_2': (recharge_grain_2, False),
    'recharge_grain_3': (recharge_grain_3, False),
    'recharge_done': (recharge_done, False),
    'crystal_crack_1': (crystal_crack_1, False),
    'crystal_crack_2': (crystal_crack_2, False),
    'crystal_dull': (crystal_dull, False),
}
