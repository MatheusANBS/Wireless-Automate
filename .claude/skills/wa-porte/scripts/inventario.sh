#!/usr/bin/env bash
# Inventário das APIs sensíveis a versão no código do Wireless Automate: quantos arquivos usam cada
# uma. Rode na raiz do ramo que vai ser portado. A saída é uma tabela em Markdown, para colar no
# relatório de impacto (docs/portes/<alvo>-impacto.md).
#
#   bash .claude/skills/wa-porte/scripts/inventario.sh [pasta do código]
set -euo pipefail
SRC="${1:-src/main/java}"

linha() {
    local area="$1" padrao="$2"
    local n
    n=$(grep -rlE "$padrao" "$SRC" 2>/dev/null | wc -l | tr -d ' ')
    printf '| %s | `%s` | %s |\n' "$area" "$padrao" "$n"
}

echo "Arquivos .java: $(find "$SRC" -name '*.java' | wc -l | tr -d ' ')"
echo
echo "| Área | Padrão | Arquivos |"
echo "| --- | --- | --- |"
linha "Componentes de item (1.20.5+)" 'DataComponentType|ModDataComponents\.'
linha "Payloads e StreamCodec (1.20.5+)" 'CustomPacketPayload|StreamCodec|RegistryFriendlyByteBuf'
linha "Codecs de item e registro" 'ItemStack\.CODEC|RegistryOps|HolderLookup'
linha "Capabilities de bloco (NeoForge 1.20.3+)" 'BlockCapabilityCache|Capabilities\.|RegisterCapabilitiesEvent'
linha "Handlers de item, fluido e energia" 'IItemHandler|IFluidHandler|IEnergyStorage'
linha "ResourceLocation (vira Identifier no 26.1)" 'ResourceLocation'
linha "NBT direto" 'CompoundTag|ListTag'
linha "Salvar block entity" 'saveAdditional|loadAdditional'
linha "SavedData" 'SavedData'
linha "Receitas" 'RecipeInput|CraftingInput|CustomRecipe|RecipeSerializer'
linha "Registros" 'DeferredRegister|DeferredHolder|DeferredBlock|DeferredItem'
linha "Eventos" '@EventBusSubscriber|@SubscribeEvent'
linha "Config" 'ModConfigSpec'
linha "Telas (GuiGraphics)" 'GuiGraphics'
linha "Desenho direto (vértices, shaders)" 'BufferBuilder|Tesselator|RenderSystem|VertexConsumer'
linha "Render de modelo e mundo" 'BlockRenderDispatcher|ModelBlockRenderer|RenderType|LevelRenderer'
linha "GameTests" '@GameTestHolder|GameTestHelper'
linha "Comandos" 'CommandDispatcher|Commands\.literal'
linha "Chunk loading" 'TicketController|ForcedChunk'
linha "Mekanism" 'mekanism\.'
linha "Ars Nouveau" 'hollingsworth'
linha "JEI" 'mezz\.jei'
