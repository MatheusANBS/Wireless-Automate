"""Põe lado a lado o quadro da referência e a captura do e2e da mesma fase, para comparar elemento por elemento.

Uso:
    python -I lado_a_lado.py REFERENCIA.gif CAPTURAS_DIR SAIDA.png QUADRO:captura [QUADRO:captura ...]
        [--recorte-ref x0,y0,x1,y1] [--recorte-cap x0,y0,x1,y1] [--largura W]

Cada par QUADRO:captura liga o número do quadro da referência ao nome do PNG em CAPTURAS_DIR (sem a extensão).
Os recortes enquadram a mesma região nas duas; sem eles, as imagens inteiras. A saída tem uma linha por par.
"""
import argparse

from PIL import Image, ImageDraw


def box(text, size):
    return tuple(int(v) for v in text.split(',')) if text else (0, 0, size[0], size[1])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('ref')
    ap.add_argument('shots')
    ap.add_argument('out')
    ap.add_argument('pairs', nargs='+')
    ap.add_argument('--recorte-ref', default='')
    ap.add_argument('--recorte-cap', default='')
    ap.add_argument('--largura', type=int, default=560)
    a = ap.parse_args()

    ref = Image.open(a.ref)
    pairs = []
    for p in a.pairs:
        frame, name = p.split(':', 1)
        pairs.append((int(frame), name))
    rbox = box(a.recorte_ref, ref.size)
    w = a.largura
    h = round(w * (rbox[3] - rbox[1]) / (rbox[2] - rbox[0]))
    sheet = Image.new('RGB', (2 * w + 10, len(pairs) * (h + 20)), (20, 20, 24))
    draw = ImageDraw.Draw(sheet)
    for k, (frame, name) in enumerate(pairs):
        ref.seek(frame)
        left = ref.convert('RGB').crop(rbox).resize((w, h))
        shot = Image.open(f'{a.shots}/{name}.png').convert('RGB')
        right = shot.crop(box(a.recorte_cap, shot.size)).resize((w, h))
        y = k * (h + 20)
        draw.text((6, y + 4), f'referência #{frame}', fill=(255, 255, 255))
        draw.text((w + 16, y + 4), name, fill=(255, 255, 255))
        sheet.paste(left, (0, y + 20))
        sheet.paste(right, (w + 10, y + 20))
    sheet.save(a.out)
    print('ok', a.out)


if __name__ == '__main__':
    main()
