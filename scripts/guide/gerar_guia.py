"""Gera as páginas do livro-guia (GuideME) em inglês e em português.

    python scripts/guide/gerar_guia.py

As páginas vão para src/main/resources/assets/wirelessautomate/guides/wirelessautomate/guide/
(inglês, o padrão do GuideME) e para _pt_br/ (português). Cada página é um arquivo em paginas/,
com os dois idiomas lado a lado para não desalinharem; o que várias páginas usam fica em
guia_base.py. Edite esses arquivos, não os .md gerados.
"""
import importlib
import os
import sys

from PIL import Image

# Com python -I a pasta do script não entra no caminho de import.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from guia_base import BASE, ROOT, write  # noqa: E402

# A ordem em que as páginas são escritas (a do livro vem do position de cada uma).


PAGINAS = [
    'index',
    'getting_started',
    'router',
    'upgrade_cards',
    'networks',
    'filters',
    'filter_card',
    'linker',
    'configurator',
    'network_tablet',
    'chunk_loading',
    'wireless_chest',
    'wireless_tank',
    'wireless_battery',
    'wireless_chemical_tank',
    'chemicals',
    'source',
    'wireless_source_tank',
    'signal_quartz',
    'signal_resonator',
    'crystal_repairer',
    'signal_screen',
    'troubleshooting',
    'recipe_editor',
    'recipes',
]


def main():
    pt_dir = os.path.join(BASE, '_pt_br')
    for folder in (BASE, pt_dir):
        if os.path.isdir(folder):
            for name in os.listdir(folder):
                if name.endswith('.md'):
                    os.remove(os.path.join(folder, name))
    paginas = [importlib.import_module(f'paginas.{m}').PAGINA for m in PAGINAS]
    for name, pt, en in paginas:
        write('pt', name, pt)
        write('en', name, en)
    # Ícones das portas da tela, para as tabelas de modo.
    images = os.path.join(BASE, 'images')
    os.makedirs(images, exist_ok=True)
    gui = os.path.join(ROOT, 'src/main/resources/assets/wirelessautomate/textures/gui')
    for mode in ('extract', 'insert', 'both', 'none'):
        # Ampliado 2x sem suavização: o GuideME desenha a imagem pequena no meio do texto.
        Image.open(os.path.join(gui, f'port_{mode}.png')).resize((32, 32), Image.NEAREST).save(
            os.path.join(images, f'port_{mode}.png'))
    print(f'{len(paginas)} páginas em inglês e português')


if __name__ == '__main__':
    main()
