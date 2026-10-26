"""Tira do mockup aprovado do Reparador (docs/preview/reparador-de-cristais.html) as referências de cada fase da
animação, para o lado_a_lado.py da skill wa-shaders comparar com as capturas do e2e (suíte reparador-animacao).

Uso:
    python -I scripts/preview/quadros_reparador.py SAIDA_DIR

Para cada fase, abre o mockup no modo de quadro (#quadro=<s/7>&energia=..&parado=..) no Edge ou no Chrome sem janela
e corta a imagem em <fase>-tela.png (a tela, 1056 x 816) e <fase>-mundo.png (o bloco no mundo, 1056 x 900).
Recortes úteis: a câmara da tela é 24,78,600,474 do -tela.png; o vidro do mundo, 240,186,816,642 do -mundo.png.
"""
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile

from PIL import Image

RAIZ = pathlib.Path(__file__).resolve().parents[2]
MOCKUP = RAIZ / 'docs' / 'preview' / 'reparador-de-cristais.html'
LARGURA, ALTURA, TELA = 1056, 1716, 816
# (nome, segundos da referência de 7 s, energia, parado): os mesmos do E2eRepairerAnimSteps
FASES = [
    ('puxa', 0.25, 1, 0), ('orbita', 0.8, 1, 0), ('raio', 2.0, 1, 0), ('solda', 3.5, 1, 0),
    ('fecha', 5.0, 1, 0), ('converge', 5.9, 1, 0), ('clarao', 6.45, 1, 0), ('onda', 6.75, 1, 0),
    ('parado', 0.0, 1, 1), ('sem-energia', 0.0, 0, 1),
]
NAVEGADORES = [
    r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
    r'C:\Program Files\Microsoft\Edge\Application\msedge.exe',
    r'C:\Program Files\Google\Chrome\Application\chrome.exe',
    'msedge', 'google-chrome', 'chromium', 'chrome',
]


def navegador():
    for nome in NAVEGADORES:
        caminho = nome if os.path.isabs(nome) and os.path.exists(nome) else shutil.which(nome)
        if caminho:
            return caminho
    sys.exit('quadros_reparador: sem Edge nem Chrome')


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    saida = pathlib.Path(sys.argv[1]).resolve()
    saida.mkdir(parents=True, exist_ok=True)
    exe = navegador()
    with tempfile.TemporaryDirectory() as perfil:
        for nome, segundos, energia, parado in FASES:
            inteira = saida / f'{nome}.png'
            url = f'{MOCKUP.as_uri()}#quadro={segundos / 7:.4f}&energia={energia}&parado={parado}'
            subprocess.run([exe, '--headless=new', '--hide-scrollbars', '--force-device-scale-factor=1',
                            f'--user-data-dir={perfil}', f'--window-size={LARGURA},{ALTURA}',
                            '--virtual-time-budget=4000', f'--screenshot={inteira}', url],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
            with Image.open(inteira) as img:
                img.crop((0, 0, LARGURA, TELA)).save(saida / f'{nome}-tela.png')
                img.crop((0, TELA, LARGURA, ALTURA)).save(saida / f'{nome}-mundo.png')
            inteira.unlink()
            print('ok', nome)


if __name__ == '__main__':
    main()
