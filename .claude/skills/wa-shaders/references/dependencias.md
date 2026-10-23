# Dependências de efeitos visuais (NeoForge 1.21.1)

Ponto de partida, levantado em 23/10/2026: cada linha é uma hipótese a conferir, não um fato. **Confira versão, licença e compatibilidade na hora** (Modrinth, CurseForge e o repositório do projeto) e procure dependências novas que não estejam aqui: isto envelhece. Nenhuma é a padrão; a escolha é por efeito, pelo método da `SKILL.md`.

| Dependência | O que dá | Licença | Observações |
| --- | --- | --- | --- |
| **Veil** (FoundryMC) | Pipeline de pós-processamento por JSON, framebuffers próprios, modificação de shaders, luzes dinâmicas | LGPL-3.0-only (Modrinth) | Builds NeoForge 1.21.1 (4.x em 2026). Bom para bloom, god rays, distorção de tela. Testar com Iris e Embeddium. |
| **Photon** (Photon Editor, sobre LDLib2) | Editor de efeitos estilo Unity: partículas, feixes, trilhas, malhas, física e bloom embutido sem shaderpack | LDLib2 é LGPL-3.0; a do Photon, conferir | Build NeoForge 1.21.1 (2.x), beta em 2026; compatível com Iris/Oculus desde 1.0.2. Efeitos montados num editor visual. |
| **Lodestone** (LodestarMC, base do Malum) | Partículas muito configuráveis, render types aditivos, eventos de mundo, shaders, buffer de normais | LGPL-3.0-only (listagem de terceiros; conferir no repositório) | Build NeoForge 1.21.1 (1.8.x em 2026). |
| **GeckoLib** | Modelos e animações por keyframes (Blockbench) para blocos, itens e entidades | MIT | Já existe em muitos packs (ATM10 Lite traz 4.8.x). Bom para peças mecânicas, não para brilho procedural. |
| **Shine** | Bloom seletivo em blocos emissivos, luz colorida, rim light | All Rights Reserved | NeoForge 1.21.1, só com Sodium, beta. Não embutir; no máximo compatibilidade. |
| **Iris / Oculus** | Shaderpacks do jogador | — | Não é ferramenta do mod, é o ambiente: o efeito precisa conviver com ele. |

## Nativo (sem dependência)

| Caminho | O que dá | Limite |
| --- | --- | --- |
| Core shader + BER (o que o mod usa hoje) | Qualquer efeito num quad ou malha, GLSL 150 | Sem pós-processamento; brilho só fingido no próprio quad |
| Framebuffer próprio (`TextureTarget`) + passes de tela cheia | Bloom de verdade, distorção, composição | Mais código de pipeline; precisa de caminho para o Iris |
| `PostChain` do vanilla (JSON em `shaders/post`) | Passes de tela cheia prontos (blur, etc.) | Alvos de tamanho fixo ou da tela; menos controle que um pipeline próprio |
| Partículas do vanilla | Brasas, fumaça, faíscas | Ordem de desenho: translúcidas somem atrás de vidro fora do Fabulous |

## O que conferir antes de propor

1. Existe build para NeoForge 1.21.1 e para a versão do NeoForge que o mod exige?
2. Licença permite depender (e, se for embutir, redistribuir)?
3. Está no ATM10? (Se já está, o custo para o jogador é zero.)
4. Funciona com Iris/Oculus e Embeddium/Sodium?
5. Manutenção: último lançamento, issues abertas, autor ativo.
6. Pode ser opcional (skill `wa-mod-opcional`)? Qual é o caminho sem ela?
