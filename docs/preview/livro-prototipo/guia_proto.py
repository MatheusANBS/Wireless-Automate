import sys
from pathlib import Path
sys.path.insert(0, sys.argv[1])
import gerar_texturas as gt
from PIL import Image

GUIA_NOVO = [
    "................",
    "..LgggggggggL...",
    "..gmhhhhhhhmiW..",
    "..dhhhhhhhhhiq..",
    "..ghhhhxhhhhiW..",
    "..dhhhxxxhhhiq..",
    "..ghhxxxxxhhiW..",
    "..dhhhxxxhhhiq..",
    "..ghhhhxhhhhiW..",
    "..dhhhhhhhhhiq..",
    "..ghhhhhhhhhiW..",
    "..dhhhhhhhhhiq..",
    "..gmhhhhhhhmiW..",
    "..LiiiiiiiiiLq..",
    "...WqWqWdeqWqr..",
    "........de......",
]

def guia_novo():
    q = []
    for k in range(4):
        img = gt.corpo(GUIA_NOVO)
        gt.pulso(img, 7, 6, "porcelana", 2, k, 4)
        q.append(img)
    return q

out = Path(sys.argv[2])
novos = guia_novo()
antigo = Image.open(sys.argv[3]).convert("RGBA")
fundo = (198, 198, 198, 255)
folha = Image.new("RGBA", (16 * 4 * 10 + 30, 16 * 2 * 10 + 30), fundo)
for k in range(4):
    a = antigo.crop((0, 16 * k, 16, 16 * k + 16)).resize((160, 160), Image.NEAREST)
    n = novos[k].resize((160, 160), Image.NEAREST)
    folha.alpha_composite(a, (10 + k * 165, 10))
    folha.alpha_composite(n, (10 + k * 165, 180))
folha.save(out / "guia_compara.png")
tira = Image.new("RGBA", (16, 64)); [tira.paste(novos[k], (0, 16 * k)) for k in range(4)]
tira.save(out / "guia_novo.png")
inv = Image.new("RGBA", (16 * 3 + 8, 24), (139, 139, 139, 255))
inv.alpha_composite(novos[0], (4, 4)); inv.alpha_composite(novos[1], (24, 4))
inv.resize((inv.width * 4, inv.height * 4), Image.NEAREST).save(out / "guia_inv.png")
print("ok")
