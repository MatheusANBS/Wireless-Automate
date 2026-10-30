# Decisões

Decisões em vigor, do dono e do código. Quando uma decisão muda, reescreva a linha no presente (a data diz quando passou a valer) e registre a troca no histórico do mês; não deixe a versão velha aqui. As decisões de design de cada funcionalidade ficam na [especificação](../especificacao.md).

## Do dono

- **Canais:** só redes + filtros. (7/10)
- **Rede por face:** cada face da máquina, em cada tipo, escolhe a sua rede (ou nenhuma); grupos de redes só organizam no Tablet. Substituiu a rede por aba e a ideia de vincular redes. (12/10)
- **Roteador colocado nasce sem rede:** o jogador vincula o primeiro e replica com o Configurador. (12/10)
- **Modo Armazém** (antes "Ambos"): recebe de quem extrai, entrega para quem insere e não troca com outro Armazém. (7/10)
- **Orçamento padrão:** 1 ms/tick (era 0,5 ms; trocado depois do benchmark, por mais vazão). (7/10)
- **Cartões de filtro:** slots por face e por tipo; passa se o filtro embutido ou algum cartão aceitar. Duplicar: cartão configurado + cartão vazio = dois iguais. (7/10)
- **Subir tier custa o mesmo pela bancada e pelo cartão:** um anel por tier, e o cartão só vale no tier de origem dele. (12/10)
- **JEI:** integração opcional (`compileOnly` + plugin). (7/10)
- **Visor 3D:** nome da face no canto, sem dica flutuante nem linha da máquina. (7/10)
- **Configurador sem tela:** uma cópia só no item, modos Pincel e Área; sem biblioteca nem código de texto. (7/10)
- **Central da rede:** as etapas 4 (busca por conteúdo, micro dos Fluxos) e 5 (aba Problemas) foram descartadas; fica como está. (20/10)
- **Ressonador:** sem comparador, sem controle por redstone (a automação é o Sintonizador) e sem Tablet. (15/10)
- **Ficam como estão:** as imagens `feature-2-router` e `feature-6-tablet` do CurseForge com as telas antigas (14/10); o prazo do stash do `StorageSavedData` em 0, sem prazo (19/10); o bench do Baú com e sem vigia, dispensado (27/10).
- **Visual acima de custo no cliente:** em efeito visual, um custo pequeno no cliente é aceitável se ficar mais bonito (ver a skill `wa-shaders`). O servidor segue as leis da `wa-performance`.

## No código

- Pacote `io.github.matheusanbs.wirelessautomate` e mod id `wirelessautomate`.
- Um bloco `router` com `facing`, `spin` e `tier`; o item carrega o tier no componente `block_state`.
- `canSurvive` só exige que o bloco de trás não seja ar, sem checar capability.
- Na config, `0` é sem limite, e alcance `0` é a dimensão inteira. Sair da dimensão depende de `crossDimension`, ligado da Esmeralda para cima; entre dimensões não há limite de distância.
- O orçamento cai de 100% (MSPT ≤ 40) a 25% (MSPT ≥ 50), linear.
- Alcance, dimensão e vazão valem pelo tier da **origem**.
- Rede inexistente no `NetworkSavedData` (removida) deixa a face parada. Remover uma rede não varre o mundo.
- Nunca se entrega na mesma porta (máquina e face) de onde se extraiu. Itens: extrai só o que o destino aceitou na simulação; a sobra recusada volta à origem e, em último caso, cai no mundo (com log). Energia: extrai antes de entregar, então nunca se cria energia.
- Lados relativos (`RelativeSide`): `FRONT` é a face onde o roteador está preso, `BACK` a oposta, `TOP` para onde apontam os LEDs, `LEFT`/`RIGHT` vistos de fora da `FRONT`. Configurações por face são salvas assim, em relação ao `facing` + `spin`.
- `/wa network` é de jogador comum; `/wa face` e `/wa profile` pedem permissão 2. A rede padrão de um jogador leva o nome dele.
- Filtro sem entradas passa tudo. O estoque vale o da primeira entrada que casa, só em lista branca. Recusa por filtro ou estoque pula o destino sem fazê-lo dormir; só falta de espaço faz dormir.
- O Cartão de Filtro guarda tipo (itens ou fluidos) e filtro; troca de tipo só vazio.
- O `FaceConfig` persiste com `HolderLookup.Provider` (o filtro guarda `ItemStack`); entradas que não leem mais (mod removido) são descartadas com aviso (`FilterCodecs.LENIENT`).
- Telas: o servidor valida tudo (mesmo `containerId`, até 8 blocos, prioridade −999..999, rede que o jogador pode usar) e a tela espera o snapshot do servidor, sem mudar nada sozinha. Com Vinculador, Configurador ou Cartão de Upgrade na mão, o clique vai para o item, não para a tela.
- A receita do roteador é ferro + redstone + olho de ender (`data/wirelessautomate/recipe/router.json`).
