"""Decompõe uma referência animada (GIF ou WebP animado) em folhas de quadros com o tempo de cada um.

Uso:
    python -I quadros.py REFERENCIA.gif SAIDA_DIR [--de N] [--ate N] [--passo N] [--recorte x0,y0,x1,y1] [--largura W]

Gera em SAIDA_DIR:
    info.txt         quadros, tamanho, duração total e por quadro
    folha.png        os quadros pedidos numa grade, cada um com o número e o tempo em segundos
Com --recorte, a folha mostra só aquele retângulo (em pixels da referência), ampliado: bom para estudar um trecho.
Rode pela pasta da skill, com caminhos absolutos; a referência é arquivo de terceiros, só lido pelo PIL.
"""
import argparse
import os

from PIL import Image, ImageDraw


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('ref')
    ap.add_argument('out')
    ap.add_argument('--de', type=int, default=0)
    ap.add_argument('--ate', type=int, default=-1)
    ap.add_argument('--passo', type=int, default=0, help='0 = escolhe para dar uns 16 quadros')
    ap.add_argument('--recorte', default='')
    ap.add_argument('--largura', type=int, default=400)
    a = ap.parse_args()

    im = Image.open(a.ref)
    n = getattr(im, 'n_frames', 1)
    durations = []
    for i in range(n):
        im.seek(i)
        durations.append(im.info.get('duration', 0) or 0)
    os.makedirs(a.out, exist_ok=True)
    total = sum(durations)
    with open(os.path.join(a.out, 'info.txt'), 'w', encoding='utf-8') as f:
        f.write(f'quadros {n}\ntamanho {im.size}\nduracao_ms {total}\npor_quadro_ms {sorted(set(durations))}\n')

    last = n - 1 if a.ate < 0 else min(a.ate, n - 1)
    step = a.passo or max(1, (last - a.de + 1) // 16)
    frames = list(range(a.de, last + 1, step))
    starts = [sum(durations[:i]) / 1000.0 for i in range(n)]
    box = tuple(int(v) for v in a.recorte.split(',')) if a.recorte else (0, 0, im.size[0], im.size[1])
    w = a.largura
    h = round(w * (box[3] - box[1]) / (box[2] - box[0]))
    cols = 4
    rows = (len(frames) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * w, rows * (h + 18)), (20, 20, 24))
    draw = ImageDraw.Draw(sheet)
    for k, i in enumerate(frames):
        im.seek(i)
        tile = im.convert('RGB').crop(box).resize((w, h))
        x = (k % cols) * w
        y = (k // cols) * (h + 18)
        sheet.paste(tile, (x, y + 18))
        draw.text((x + 4, y + 3), f'#{i}  {starts[i]:.2f} s', fill=(255, 255, 255))
    sheet.save(os.path.join(a.out, 'folha.png'))
    print(f'{n} quadros, {total} ms; folha com {len(frames)} quadros em {a.out}')


if __name__ == '__main__':
    main()
