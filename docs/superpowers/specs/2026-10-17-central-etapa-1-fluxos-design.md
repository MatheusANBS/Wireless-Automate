# Central da rede, etapa 1: medição e Fluxos

Data: 17/10/2026. Ramo: `central-da-rede`, sobre o `origin/main` 05be9c7 (2.1.0 + etapa 0). Mockup aprovado: `docs/preview/central-da-rede.html` (seção Fluxos, macro). A central sai numa versão só, no fim; esta etapa é mesclada no `main` sem versão.

## O que entrega

A aba **Estatísticas** do Tablet vira **Fluxos**, com o total do que entra e sai de cada tipo, por rede, medido por porta no motor e mostrado como média dos últimos 5 s. As outras abas não mudam nesta etapa (Grupos entra em Redes na etapa 3; Problemas na etapa 5).

## Definições (decididas pelo dono)

- **Entra** (produção): o que as faces **Extrai** entregaram aos destinos.
- **Sai** (consumo): o que as faces **Insere** receberam.
- **Saldo dos Armazéns**: o que as faces **Armazém** receberam menos o que deram. Positivo = "enchendo", negativo = "esvaziando"; não aparece quando é zero.
- A janela é **fixa em 5 s**, sem histórico salvo. Como o amostrador roda sempre, a aba já abre com a janela cheia; uma janela ainda curta (logo depois de o servidor subir, ou numa rede nova) mostra a média dos segundos que tem, e só sem nenhum segundo aparece "—".
- **Estados** de cada porta na janela: movendo (moveu algo), vazia (origem dormindo sem o que dar), esperando destino (face Extrai dormindo com algo a dar, ou sem destino ao alcance; um Armazém nunca espera, porque guarda o que não tem para onde ir), cheia (destino dormindo depois de recusas, a regra `NodeProbe.isFull`), parada por redstone (face com modo que o redstone desliga) e sem rede (face com modo e sem rede). As duas últimas vêm da configuração, não do motor.

## Motor

- Cada `Port` ganha dois contadores `long`: o que entregou como origem e o que recebeu como destino. Eles são somados nos pontos em que a quantidade já é conhecida (fim da visita da origem; a entrega a cada destino, junto do `destinationBackoff.wake()` que já existe), em `ItemTransfer`, `ItemBulkTransfer`, `FluidTransfer`, `ScalarTransfer` e `ChemicalTransfer`. Duas somas por entrega, sem alocação; 16 B por porta.
- Um **amostrador** (`network/FlowSampler`) roda no tick do `NetworkManager` a cada 20 ticks, **sempre**, com ou sem alguém olhando (decisão do dono depois das capturas: a aba abre com os números na hora). Quem está com a aba Fluxos aberta só decide para quem o pacote sai. O trabalho é espalhado em 20 baldes de fase pela posição do nó (como no Ressonador): a cada tick ele percorre só as portas com rota dos nós do balde da vez, lê e zera os contadores de cada uma (a primeira volta do gerenciador só zera), e a volta pelos 20 baldes é o segundo; soma por rede × tipo pelo modo da porta (Extrai, Insere, Armazém) e conta os estados. As faces paradas por redstone e sem rede vêm da configuração dos nós carregados (presa, perfis e exceções), contada uma vez por nó e guardada até o `changeVersion` do roteador, a versão das redes ou a config mudarem. O resultado só é montado quando alguém pede, uma vez por volta. Uma janela de 5 baldes de 1 s por rede × tipo (`network/FlowWindow`, pura, com JUnit) dá a média.
- Custo esperado: as duas somas por entrega e O(portas + nós) por segundo em todo servidor, com ou sem Tablet, dividido em 20 ticks (sem pico); o custo por amostra sai no `/wa profile`. O `/wa bench` mede antes e depois nos cenários many, raw, mixed, idle e tablet (vanilla, 3 repetições).

## Protocolo

- Servidor → cliente `TabletFlowsPayload`: as linhas por rede × tipo (rede, tipo, entra, sai, saldo, os contadores de estado, os ms/tick da rede e se está pausada), mais as contagens "sem rede" por tipo e o tempo do mod e o teto. Vai só com o Tablet aberto **na aba Fluxos**, no máximo uma vez por segundo, e só se mudou.
- O que a aba Estatísticas usava no cabeçalho do Tablet e ninguém mais usa sai do cabeçalho.
- `ModPayloads.VERSION` sobe de `19` para `20`.

## Tela (aba Fluxos)

- **Topo:** a barra "Tempo do mod · 0,42 de 1,00 ms" (a de hoje) e o seletor **Por tipo | Por rede** (padrão Por tipo, lembrado como a aba).
- **Por tipo:** uma linha por tipo em uso, com o total de todas as redes não pausadas: ícone, nome, "entra → sai" na unidade do tipo (itens/s, mB/s ou B/s pelo corte de 1.000 do resto do mod, FE/t, Source/s) e, à direita, o saldo e os estados em texto curto, só quando não são zero ("+16.500 enchendo", "3 esperando · 1 cheio"). ▸ abre o tipo com uma linha por rede (bolinha da cor da rede, nome, "entra → sai", saldo). Rede pausada fica fora dos totais e aparece apagada.
- **Por rede:** cabeçalho da rede (bolinha, nome, estados em texto curto à direita) e, embaixo, uma linha por tipo; rede pausada só com o nome e "pausada".
- **Dicas:** na linha do tipo ou da rede, a contagem completa dos estados (origens: movendo, vazias, esperando destino; destinos: movendo, cheios; paradas por redstone; sem rede) e, no nome da rede, o tempo do mod nela.
- **Clique** numa linha de tipo ou de rede abre a Lista filtrada por aquele tipo (e rede), como o cartão das Estatísticas faz hoje. (Na etapa 4, passa a descer para o micro.)
- **Vazio:** sem nenhuma rede, "Nenhuma rede ainda" com o convite de hoje; sem movimento, as linhas com "0 → 0".
- Regra do dono: não poluir. Nada de barras, gráficos ou colunas por linha.
- Redimensionável como hoje; tema claro e escuro; textos variáveis pelo `GuiText`; Químicos e Source só com o Mekanism e o Ars.

## Guia do jogador

A página do Tablet troca Estatísticas por Fluxos, em pt e en: o que é entra, sai e saldo, Por tipo e Por rede, e o clique que leva à Lista.

## Testes

- JUnit: `FlowWindow` (média, janela que enche, balde que vira, zero).
- GameTests: os contadores da porta em itens, fluidos e energia (Extrai → Insere, Extrai → Armazém → Insere); o amostrador roda sem ninguém olhando; os totais por rede × tipo e o saldo do Armazém; os estados (vazia, espera, cheio, redstone, sem rede); o payload só na aba Fluxos e só quando muda; nas runs de Químicos e Source, uma linha de cada tipo.
- e2e: capturas `7c-tablet-fluxos` (Por tipo, com um tipo aberto), `7c-tablet-fluxos-rede`, `-pt` e no escuro.
