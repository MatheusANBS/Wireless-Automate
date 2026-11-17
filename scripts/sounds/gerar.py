"""Gera os sons da Armilar: WAV 48 kHz mono, .ogg no mod, prévias em mp3, medidas e folha de espectros.

Uso: python -I scripts/sounds/gerar.py [--so nome1,nome2] [--previa PASTA]
Sementes fixas: rodar de novo dá os mesmos arquivos.
"""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import wave

import numpy as np

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, AQUI)

from dsp import SR  # noqa: E402
import armilar  # noqa: E402

RAIZ = os.path.normpath(os.path.join(AQUI, '..', '..'))
DESTINO = os.path.join(RAIZ, 'src', 'main', 'resources', 'assets', 'wirelessautomate', 'sounds', 'armilar')
DOC = os.path.join(RAIZ, 'docs', 'preview')

ORDEM_PREVIA = [
    ('assemble', '01-montagem'), ('sun_loop', '02-sol-continuo'), ('tune_lock', '03-sintonia-trava'),
    ('conj_charge', '04-conjuncao-carga'), ('conj_arc', '05-conjuncao-arco'),
    ('conj_burst_1', '06-conjuncao-explosao-1'), ('conj_burst_2', '07-conjuncao-explosao-2'),
    ('conj_impact_1', '08-conjuncao-impacto-1'), ('conj_impact_2', '09-conjuncao-impacto-2'),
    ('conj_impact_3', '10-conjuncao-impacto-3'), ('flow_loop', '11-feixe-continuo'),
    ('full_loop', '12-buffer-cheio-continuo'), ('recharge_grain_1', '13-recarga-grao-1'),
    ('recharge_grain_2', '14-recarga-grao-2'), ('recharge_grain_3', '15-recarga-grao-3'),
    ('recharge_done', '16-recarga-pronta'), ('crystal_crack_1', '17-cristal-racha-1'),
    ('crystal_crack_2', '18-cristal-racha-2'), ('crystal_dull', '19-cristal-apagado'),
    ('tune_lost', '20-sintonia-perdida'),
]

# teto de duração por evento (s); passando dele, o fim vira fade longo
TETO = {'assemble': 4.2, 'tune_lock': 2.2, 'tune_lost': 1.9, 'conj_burst_1': 2.6, 'conj_burst_2': 2.6,
        'conj_arc': 1.2, 'conj_impact_1': 1.6, 'conj_impact_2': 1.6, 'conj_impact_3': 1.6,
        'recharge_grain_1': 0.4, 'recharge_grain_2': 0.4, 'recharge_grain_3': 0.4, 'recharge_done': 2.6,
        'crystal_crack_1': 1.1, 'crystal_crack_2': 1.1, 'crystal_dull': 1.9}


def ffmpeg() -> str:
    exe = shutil.which('ffmpeg')
    if not exe:
        raise SystemExit('ffmpeg não encontrado no PATH')
    return exe


def grava_wav(caminho: str, x: np.ndarray, rng) -> None:
    d = (rng.uniform(-0.5, 0.5, len(x)) + rng.uniform(-0.5, 0.5, len(x))) / 32768.0  # dither TPDF
    y = np.clip(np.round((x + d) * 32767), -32768, 32767).astype('<i2')
    with wave.open(caminho, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(y.tobytes())


def le_audio(caminho: str) -> np.ndarray:
    r = subprocess.run([ffmpeg(), '-v', 'error', '-i', caminho, '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'],
                       capture_output=True, check=True)
    return np.frombuffer(r.stdout, dtype='<f4').astype(float)


def lufs(caminho: str) -> float:
    r = subprocess.run([ffmpeg(), '-hide_banner', '-nostats', '-i', caminho, '-af', 'ebur128', '-f', 'null', '-'],
                       capture_output=True, text=True)
    linhas = [l for l in r.stderr.splitlines() if l.strip().startswith('I:')]
    try:
        return float(linhas[-1].split()[1])
    except (IndexError, ValueError):
        return float('nan')


def emenda(x: np.ndarray) -> tuple[float, float]:
    """Salto na emenda do loop comparado com o salto típico (percentil 99,9) dentro do arquivo."""
    d = np.abs(np.diff(x))
    return float(abs(x[0] - x[-1])), float(np.percentile(d, 99.9))


def espectro(x: np.ndarray, largura: int = 760, altura: int = 300):
    from dsp import stft, FREQS
    S, _ = stft(x)
    mag = 20 * np.log10(np.abs(S) + 1e-9)
    mag -= mag.max()
    fmin, fmax = 40.0, 20000.0
    ys = np.exp(np.linspace(np.log(fmax), np.log(fmin), altura))
    cols = np.interp(np.linspace(0, len(mag) - 1, largura), np.arange(len(mag)), np.arange(len(mag))).astype(int)
    img = np.empty((altura, largura))
    for j, c in enumerate(cols):
        img[:, j] = np.interp(ys, FREQS, mag[c])
    v = np.clip((img + 90) / 90, 0, 1)
    # paleta escura: preto -> roxo -> laranja -> amarelo claro
    r = np.clip(1.6 * v - 0.2, 0, 1)
    g = np.clip(1.8 * v - 0.9, 0, 1)
    b = np.clip(np.sin(np.pi * v) * 0.8 + 0.1 * v, 0, 1)
    return (np.stack([r, g, b], -1) * 255).astype(np.uint8)


def folha(resultados: dict, caminho: str) -> None:
    from PIL import Image, ImageDraw, ImageFont
    nomes = [k for k, _ in ORDEM_PREVIA if k in resultados]
    colunas, w, h, pad, topo = 4, 760, 300, 14, 26
    linhas = (len(nomes) + colunas - 1) // colunas
    W = colunas * (w + pad) + pad
    H = linhas * (h + topo + pad) + pad
    img = Image.new('RGB', (W, H), (20, 20, 22))
    dr = ImageDraw.Draw(img)
    try:
        fonte = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 17)
    except OSError:
        fonte = ImageFont.load_default()
    for i, nome in enumerate(nomes):
        x0 = pad + (i % colunas) * (w + pad)
        y0 = pad + (i // colunas) * (h + topo + pad)
        x = resultados[nome]['x']
        dur = len(x) / SR
        img.paste(Image.fromarray(espectro(x, w, h)), (x0, y0 + topo))
        dr.text((x0, y0 + 2), f'{nome}  ({dur:.2f} s)', fill=(235, 235, 235), font=fonte)
        for f, rot in [(100, '100'), (1000, '1 k'), (10000, '10 k')]:
            yy = y0 + topo + int((np.log(20000) - np.log(f)) / (np.log(20000) - np.log(40)) * h)
            dr.line([(x0, yy), (x0 + w, yy)], fill=(90, 90, 90))
            dr.text((x0 + 4, yy - 20), rot, fill=(200, 200, 200), font=fonte)
    img.save(caminho)


def relatorio(res: dict, caminho: str) -> None:
    def num(v, casas=1):
        return 'n/d' if v != v or v < -69 else f'{v:.{casas}f}'.replace('.', ',')
    linhas = [
        '# Sons da Armilar: medidas',
        '',
        'Gerado por `scripts/sounds/gerar.py` (síntese própria; motor em `dsp.py`, desenhos em `armilar.py`). Os '
        'números são do `.ogg` final, decodificado: pico de amostra, LUFS integrado (`ffmpeg ebur128`; os grãos são '
        'curtos demais para o LUFS) e, nos loops, o salto da emenda (do fim para o começo) ao lado do salto típico '
        'entre duas amostras (percentil 99,9 dentro do arquivo).',
        '',
        '| arquivo | dur s | pico dBFS | LUFS | KB | loop | emenda / típico |',
        '| --- | --- | --- | --- | --- | --- | --- |',
    ]
    for nome, _ in ORDEM_PREVIA:
        if nome not in res:
            continue
        i = res[nome]
        em = f"{i['emenda'][0]:.4f} / {i['emenda'][1]:.4f}".replace('.', ',') if i['loop'] else ''
        linhas.append(f"| {nome} | {num(i['dur'], 2)} | {num(i['pico'])} | {num(i['lufs'])} | {num(i['kb'])} | "
                      f"{'sim' if i['loop'] else ''} | {em} |")
    linhas += ['', 'Folha de espectros (40 Hz a 20 kHz em log, 90 dB de faixa):', '',
               '![espectros dos sons da Armilar](armilar-sons.png)', '']
    with open(caminho, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(linhas))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--so', default='')
    ap.add_argument('--previa', default='')
    a = ap.parse_args()
    escolha = [s for s in a.so.split(',') if s] or list(armilar.SONS)
    os.makedirs(DESTINO, exist_ok=True)
    tmp = os.path.join(a.previa or os.path.join(RAIZ, 'build', 'sons'), 'wav')
    os.makedirs(tmp, exist_ok=True)
    ff = ffmpeg()
    rng = np.random.default_rng(999)
    res = {}
    for nome in escolha:
        fn, loop = armilar.SONS[nome]
        x = fn()
        if not loop and nome in TETO:
            from dsp import trim
            x = trim(x, max_s=TETO[nome])
        wav = os.path.join(tmp, nome + '.wav')
        grava_wav(wav, x, rng)
        ogg = os.path.join(DESTINO, nome + '.ogg')
        subprocess.run([ff, '-y', '-v', 'error', '-i', wav, '-fflags', '+bitexact', '-flags:a', '+bitexact', '-c:a', 'libvorbis',
                        '-q:a', '6', '-ac', '1', ogg],
                       check=True)
        y = le_audio(ogg)
        info = {'x': y, 'dur': len(y) / SR, 'pico': 20 * np.log10(np.max(np.abs(y)) + 1e-12), 'lufs': lufs(ogg),
                'kb': os.path.getsize(ogg) / 1024, 'loop': loop, 'n_wav': len(x), 'n_ogg': len(y)}
        if loop:
            info['emenda'] = emenda(y)
        res[nome] = info
        extra = ''
        if loop:
            s, tip = info['emenda']
            extra = f'  emenda {s:.4f} (típico {tip:.4f})  amostras wav {len(x)} ogg {len(y)}'
        print(f"{nome:18s} {info['dur']:5.2f} s  pico {info['pico']:6.1f} dBFS  {info['lufs']:6.1f} LUFS  "
              f"{info['kb']:5.1f} KB{extra}", flush=True)
    if a.previa:
        seq = os.path.join(tmp, 'lista.txt')
        pausa = os.path.join(tmp, 'pausa.wav')
        subprocess.run([ff, '-y', '-v', 'error', '-f', 'lavfi', '-i', f'anullsrc=r={SR}:cl=mono', '-t', '0.9',
                        '-c:a', 'pcm_s16le', pausa], check=True)
        with open(seq, 'w', encoding='utf-8') as f:
            for nome, rot in ORDEM_PREVIA:
                if nome not in res:
                    continue
                subprocess.run([ff, '-y', '-v', 'error', '-i', os.path.join(DESTINO, nome + '.ogg'), '-c:a',
                                'libmp3lame', '-b:a', '192k', os.path.join(a.previa, rot + '.mp3')], check=True)
                for _ in range(3 if res[nome]['loop'] else (3 if 'grain' in nome else 1)):
                    f.write(f"file '{os.path.join(tmp, nome + '.wav')}'\n".replace('\\', '/'))
                f.write(f"file '{pausa}'\n".replace('\\', '/'))
        subprocess.run([ff, '-y', '-v', 'error', '-f', 'concat', '-safe', '0', '-i', seq, '-c:a', 'libmp3lame',
                        '-b:a', '192k', os.path.join(a.previa, '00-todos-em-sequencia.mp3')], check=True)
        folha(res, os.path.join(DOC, 'armilar-sons.png'))
        if len(res) == len(armilar.SONS):
            relatorio(res, os.path.join(DOC, 'armilar-sons.md'))


if __name__ == '__main__':
    main()
