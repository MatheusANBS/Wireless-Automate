"""Compara as capturas do DevScreenshot de antes e de depois de uma divisão (skill wa-modular).

Para cada PNG com o mesmo nome nas duas pastas, mede quantos pixels mudaram no retângulo do painel
(a área que não é o fundo do mundo, achada pela diferença contra a borda da imagem) e no resto. O mundo
atrás da tela anima, então diferença só fora do painel é normal; dentro dele, abra as duas imagens e a
máscara gravada em <depois>/diferenca/.

    python -I .claude/skills/wa-modular/scripts/comparar.py run/shots-antes run/shots-depois
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops

LIMIAR = 24  # diferença por canal abaixo disso conta como igual (compressão, cintilação)


def painel(img):
    """Retângulo do painel: o maior bloco que não muda entre a borda e o centro é o fundo; usa o bbox
    dos pixels que diferem da cor média da borda, encolhido 2 px."""
    cinza = img.convert('L')
    w, h = cinza.size
    borda = [cinza.getpixel((x, 0)) for x in range(0, w, 8)] + [cinza.getpixel((0, y)) for y in range(0, h, 8)]
    media = sum(borda) / len(borda)
    mascara = cinza.point(lambda v: 255 if abs(v - media) > 40 else 0)
    caixa = mascara.getbbox()
    return caixa or (0, 0, w, h)


def main():
    sys.stdout.reconfigure(encoding='utf-8')
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    antes, depois = Path(sys.argv[1]), Path(sys.argv[2])
    saida = depois / 'diferenca'
    saida.mkdir(exist_ok=True)
    nomes = sorted(p.name for p in antes.glob('*.png') if (depois / p.name).is_file())
    so_antes = sorted({p.name for p in antes.glob('*.png')} - set(nomes))
    so_depois = sorted({p.name for p in depois.glob('*.png')} - set(nomes))
    suspeitas = 0
    for nome in nomes:
        a = Image.open(antes / nome).convert('RGB')
        b = Image.open(depois / nome).convert('RGB')
        if a.size != b.size:
            print(f'TAMANHO  {nome}: {a.size} -> {b.size}')
            suspeitas += 1
            continue
        dif = ImageChops.difference(a, b).convert('L').point(lambda v: 255 if v > LIMIAR else 0)
        x0, y0, x1, y1 = painel(a)
        dentro = dif.crop((x0, y0, x1, y1))
        total_dentro = max(1, (x1 - x0) * (y1 - y0))
        mudou_dentro = sum(1 for v in dentro.getdata() if v)
        mudou_total = sum(1 for v in dif.getdata() if v)
        pct = 100 * mudou_dentro / total_dentro
        marca = 'IGUAL   ' if mudou_dentro == 0 else 'OLHAR   ' if pct > 0.05 else 'quase   '
        if mudou_dentro:
            dif.save(saida / nome)
        if marca.startswith('OLHAR'):
            suspeitas += 1
        print(f'{marca}{nome}: painel {mudou_dentro} px ({pct:.3f}%), fora {mudou_total - mudou_dentro} px')
    for nome in so_antes:
        print(f'SUMIU    {nome} (só no antes)')
    for nome in so_depois:
        print(f'NOVA     {nome} (só no depois)')
    print(f'\n{len(nomes)} comparadas, {suspeitas} para olhar. Máscaras em {saida}.')


if __name__ == '__main__':
    main()
