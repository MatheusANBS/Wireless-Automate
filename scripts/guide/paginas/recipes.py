"""Página recipes.md do guia."""
from guia_base import *  # noqa: F401,F403


RECIPES = '''<RecipeFor id="wirelessautomate:router" />
<RecipeFor id="wirelessautomate:storage_chest" />
<RecipeFor id="wirelessautomate:storage_tank" />
<RecipeFor id="wirelessautomate:storage_battery" />
<RecipeFor id="wirelessautomate:storage_chemical_tank" />
<RecipeFor id="wirelessautomate:storage_source_tank" />
<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />
<RecipeFor id="wirelessautomate:filter_card" />
<RecipeFor id="wirelessautomate:linker" />
<RecipeFor id="wirelessautomate:configurator" />
<RecipeFor id="wirelessautomate:network_tablet" />
<RecipeFor id="wirelessautomate:chunk_loader_upgrade" />
<RecipeFor id="wirelessautomate:cut_signal_crystal" />
<RecipeFor id="wirelessautomate:resonator_casing" />
<RecipeFor id="wirelessautomate:resonator_glass" />
<RecipeFor id="wirelessautomate:resonance_core" />
<RecipeFor id="wirelessautomate:resonator_energy_port" />
<RecipeFor id="wirelessautomate:resonator_crystal_port" />
<RecipeFor id="wirelessautomate:resonator_tuner" />
<RecipeFor id="wirelessautomate:crystal_repairer" />
<Recipe id="wirelessautomate:guide" />'''


PAGINA = page('recipes.md', front('Todas as receitas', 'minecraft:crafting_table', 23) + '''
# Todas as receitas

Todas na bancada, com itens vanilla.

| Também na bancada | Como |
| --- | --- |
| **Subir o tier** | Roteador ou armazenamento (Baú, Tanque, Bateria, Tanque Químico, Tanque de Source) + o cartão de um tier acima, em qualquer posição. |
| **Copiar um Cartão de Filtro** | Cartão configurado + cartões vazios. O original volta. |
| **Este guia** | Livro + redstone. |

''' + RECIPES + '''
''', front('All recipes', 'minecraft:crafting_table', 23) + '''
# All recipes

All in a crafting table, with vanilla items.

| Also in the crafting table | How |
| --- | --- |
| **Raise the tier** | Router or storage (Chest, Tank, Battery, Chemical Tank, Source Tank) + the card one tier up, in any slots. |
| **Copy a Filter Card** | Configured card + blank cards. The original comes back. |
| **This guide** | Book + redstone. |

''' + RECIPES + '''
''')
