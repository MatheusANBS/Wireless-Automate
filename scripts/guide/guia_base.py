"""Base do livro-guia: o que várias páginas usam (cabeçalho, imagens de item, tiers e tabelas).

Cada página fica em paginas/<pagina>.py; o gerar_guia.py junta tudo. O que vale lembrar:
- as páginas vão para assets/wirelessautomate/guide/<en_us|pt_br>/ e o livro é uma tela do próprio mod;
- o cabeçalho de cada página traz o grupo (um dos sete de GRUPOS) como chave de topo;
- os links de imagem no texto (images/port_x.png) são relativos a assets/wirelessautomate/guide/;
- os números das tabelas são os padrões do ResourceType, do StorageKind e do RouterTier (mantenha em dia).
"""
import os


ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')


BASE = os.path.join(ROOT, 'src/main/resources/assets/wirelessautomate/guide')


GRUPOS = ('comecar', 'ferramentas', 'armazenamentos', 'energia', 'painel', 'integracoes', 'referencia')


PASTAS = {'en': 'en_us', 'pt': 'pt_br'}


def write(lang, name, text):
    folder = os.path.join(BASE, PASTAS[lang])
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, name), 'w', encoding='utf-8', newline='\n') as fh:
        fh.write(text.strip('\n') + '\n')


def front(title, icon, group, position=None, parent='index.md', item_ids=()):
    assert group in GRUPOS, f'grupo desconhecido: {group}'
    lines = ['---', f'group: {group}', 'navigation:', f'  title: {title}', f'  icon: {icon}']
    if parent:
        lines.append(f'  parent: {parent}')
    if position is not None:
        lines.append(f'  position: {position}')
    if item_ids:
        lines.append('item_ids:')
        lines += [f'  - {i}' for i in item_ids]
    lines.append('---')
    return '\n'.join(lines) + '\n\n'


def fill(template, **kw):
    for k, v in kw.items():
        template = template.replace('{' + k + '}', v)
    return template


def item(id_):
    return f'<ItemImage id="wirelessautomate:{id_}" />'

# ------------------------------------------------------------------ textos repetidos

# Tiers na ordem do RouterTier: id, nome pt, nome en, só com o Allthemodium. Os números abaixo são os padrões da
# config (ResourceType e StorageKind); 0 = sem limite.


TIERS = [
    ('basic', 'Básico', 'Basic', False),
    ('advanced', 'Avançado', 'Advanced', False),
    ('elite', 'Elite', 'Elite', False),
    ('emerald', 'Esmeralda', 'Emerald', False),
    ('allthemodium', 'Allthemodium', 'Allthemodium', True),
    ('vibranium', 'Vibranium', 'Vibranium', True),
    ('unobtainium', 'Unobtainium', 'Unobtainium', True),
    ('ultimate', 'Ultimate', 'Ultimate', False),
]


RATES = {  # por tier: itens/s, fluido e químico mB/s, energia FE/t, Source/s
    'item': [32, 256, 2_048, 16_384, 131_072, 1_048_576, 8_388_608, 0],
    'fluid': [8_000, 64_000, 512_000, 4_096_000, 32_768_000, 262_144_000, 2_097_152_000, 0],
    'energy': [454, 29_100, 63_000, 1_350_000, 67_200_000, 84_500_000, 158_000_000, 0],
    'source': [1_000, 8_000, 64_000, 512_000, 4_096_000, 32_768_000, 262_144_000, 0],
}


RANGE = {
    'pt': ['64 blocos', '512 blocos', 'A dimensão inteira'] + ['Todas as dimensões'] * 5,
    'en': ['64 blocks', '512 blocks', 'The whole dimension'] + ['Every dimension'] * 5,
}


CAPACITY = {
    'chest': [115_200, 921_600, 7_372_800, 58_982_400, 471_859_200, 3_774_873_600, 30_198_988_800, 0],
    'tank': [28_800_000, 230_400_000, 1_843_200_000, 14_745_600_000, 117_964_800_000, 943_718_400_000,
             7_549_747_200_000, 0],
    'battery': [31_104_000, 1_990_656_000, 4_320_000_000, 92_160_000_000, 4_608_000_000_000,
                5_788_800_000_000, 10_785_600_000_000, 0],
    'source_tank': [3_600_000, 28_800_000, 230_400_000, 1_843_200_000, 14_745_600_000, 117_964_800_000,
                    943_718_400_000, 0],
}


UNLIMITED = {'pt': 'Sem limite', 'en': 'Unlimited'}


ATM_NOTE = {'pt': '¹ Só com o mod Allthemodium.', 'en': '¹ Only with the Allthemodium mod.'}


def num(lang, n):
    """Número com o separador de milhar do idioma, ou "sem limite" para 0."""
    if n == 0:
        return UNLIMITED[lang]
    return f'{n:,}'.replace(',', '.' if lang == 'pt' else ',')


def tier_name(lang, i):
    _, pt, en, atm = TIERS[i]
    return (pt if lang == 'pt' else en) + ('¹' if atm else '')


def capacity_table(lang, kind, unit):
    rows = [f'| Tier | {unit} |', '| --- | --- |']
    for i in range(len(TIERS)):
        rows.append(f'| **{tier_name(lang, i)}** | {num(lang, CAPACITY[kind][i])} |')
    return '\n'.join(rows) + '\n\n' + ATM_NOTE[lang]


SCENE_STORAGE_PAIR = '''<GameScene zoom="4" interactive={true}>
  <Block id="wirelessautomate:{BLOCK}" x="0" y="0" z="0" p:tier="elite" />
  <Block id="wirelessautomate:router" x="0" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <Block id="wirelessautomate:{BLOCK}" x="4" y="0" z="0" p:tier="ultimate" />
  <Block id="wirelessautomate:router" x="4" y="1" z="0" p:facing="up" p:tier="ultimate" />
  <LineAnnotation from="0.5 1.5 0.5" to="4.5 1.5 0.5" color="#a46cff" thickness="0.08">
    {LINE}
  </LineAnnotation>
  <IsometricCamera yaw="200" pitch="30" />
</GameScene>'''


COMMON_PT = '''
## Em comum com o Baú

- **Tiers:** sobe com os mesmos [Cartões de Upgrade](upgrade-cards.md), no mundo ou na bancada, sem perder o conteúdo.
- **Quebrar:** o item leva o conteúdo{FILTER_PT}, e o tooltip mostra o total. Cheio, ele sempre vira item (no criativo, sem picareta ou numa explosão).
- **Comparador:** o sinal sobe com a ocupação.
- **Outros mods** o veem como {WHAT_PT} comum.
- **Faces:** as seis são iguais. Ele não empurra nem puxa sozinho: quem move é o roteador, ou um cabo ou cano de outro mod. Uma máquina só encostada nele não recebe nada.
'''


COMMON_EN = '''
## Shared with the Chest

- **Tiers:** raised with the same [Upgrade Cards](upgrade-cards.md), in the world or in a crafting table, keeping the contents.
- **Breaking:** the item takes the contents{FILTER_EN}, and the tooltip shows the total. A full one always becomes an item (in creative, without a pickaxe or in an explosion).
- **Comparator:** the signal rises with how full it is.
- **Other mods** see it as a regular {WHAT_EN}.
- **Faces:** all six are the same. It doesn't push or pull by itself: the router moves things, or another mod's cable or pipe. A machine just placed against it gets nothing.
'''


def page(name, pt, en):
    """Uma página: o nome do arquivo e o texto em português e em inglês, lado a lado."""
    return name, pt, en
