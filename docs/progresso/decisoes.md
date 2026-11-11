# Decisões

Decisões em vigor, do dono e do código. Quando uma decisão muda, reescreva a linha no presente (a data diz quando passou a valer) e registre a troca no histórico do mês; não deixe a versão velha aqui. As decisões de design de cada funcionalidade ficam na [especificação](../especificacao.md).

## Do dono

- **Canais:** só redes + filtros. (7/10)
- **Rede por face:** cada face da máquina, em cada tipo, escolhe a sua rede (ou nenhuma); grupos de redes só organizam no Tablet. Substituiu a rede por aba e a ideia de vincular redes. (12/10)
- **Roteador colocado nasce sem rede:** o jogador vincula o primeiro e replica com o perfil do Tablet. (12/10)
- **Modo Armazém** (antes "Ambos"): recebe de quem extrai, entrega para quem insere e não troca com outro Armazém. (7/10)
- **Orçamento padrão:** 1 ms/tick (era 0,5 ms; trocado depois do benchmark, por mais vazão). (7/10)
- **Cartões de filtro:** slots por face e por tipo; passa se o filtro embutido ou algum cartão aceitar. Duplicar: cartão configurado + cartão vazio = dois iguais. (7/10)
- **Subir tier custa o mesmo pela bancada e pelo cartão:** um anel por tier, e o cartão só vale no tier de origem dele. (12/10)
- **JEI:** integração opcional (`compileOnly` + plugin). (7/10)
- **Visor 3D:** nome da face no canto, sem dica flutuante nem linha da máquina. (7/10)
- **O Configurador é o Afinador:** propõe e aplica só os modos das faces de uma máquina vinculada (prioridade, redstone, filtro e rede ficam), sem Desfazer (o segundo clique é a confirmação, a pintura mostra antes); armazenamentos ganham Armazém numa face só; sem roteador, avisa e não faz nada. A Sonda e o Molde ficam para depois. (02/11)
- **A proposta do Afinador apagada no cliente some nos dois lados:** mirar outro bloco por 2 s (ou renascer) manda o `ConfiguratorCancelPayload`, e o servidor descarta a proposta; o próximo clique na máquina faz uma análise nova. (03/11)
- **Afinador: na dúvida, Insere em vez de Armazém.** Uma máquina só de energia ou de Source só vira Armazém se for a Bateria ou o Tanque de Source do mod (um consumidor com o `EnergyStorage` comum lê igual a uma bateria, e Armazém o esvaziaria); com outro armazenamento junto (baú, tanque), vale o Armazém. O botão segurado nunca confirma nem apaga: o cliente segura os usos até a tecla soltar, e o servidor só confirma depois de uma pausa de mais de 5 ticks entre tentativas. (03/11)
- **O Afinador se chama "Auto-Setup" em inglês** ("Tuner" confundia com o Tuner do Ressonador); em português segue Afinador, e as classes Java ficam `Tuner*`. (02/11)
- **Vinculador: o desvincular é num clique** (saiu a confirmação da etapa 4), e **Ctrl + clique numa máquina vincula, direto, as do mesmo bloco no raio**, das mais perto do roteador até o limite do tier. Ctrl + clique para desvincular em massa fica fora. (02/11)
- **Cartões devolvidos ao desvincular um grupo ou ao Atualizar vão para o inventário de quem clicou** (o que não cabe cai aos pés). A cópia de perfil por máquina é do jogador, fica no servidor (`MachineClipboard`) e vale até ele sair do mundo; os cartões não vão junto. (02/11)
- **A presa é máquina do grupo do bloco dela:** perfil, exceção e cartões do grupo, lados pela frente da máquina; editar a presa sozinha no grupo muda o perfil, com outras do bloco vira exceção; não conta no limite e vale sem o Upgrade de Alcance. A máquina vinculada segue o perfil. Bloco trocado no lugar leva a presa com o perfil; ar mantém o grupo. (02/11)
- **Central da rede:** as etapas 4 (busca por conteúdo, micro dos Fluxos) e 5 (aba Problemas) foram descartadas; fica como está. (20/10)
- **Ressonador:** sem comparador, sem controle por redstone (a automação é o Sintonizador) e sem Tablet. (15/10)
- **Ficam como estão:** as imagens `feature-2-router` e `feature-6-tablet` do CurseForge com as telas antigas (14/10); o prazo do stash do `StorageSavedData` em 0, sem prazo (19/10); o bench do Baú com e sem vigia, dispensado (27/10).
- **Visual acima de custo no cliente:** em efeito visual, um custo pequeno no cliente é aceitável se ficar mais bonito (ver a skill `wa-shaders`). O servidor segue as leis da `wa-performance`.
- Doca de Padrões (11/11): a sobra de um pedido trava só a máquina que o recebeu, e sai só nela (a receita não se parte); a máquina que sai do lado passa a sobra a outra do lado. O pendente guarda uma linha por item com a quantidade em `long`, e o formato antigo é migrado ao carregar, sem perder nada.
- **A remota quebrada ou trocada sai sozinha do roteador de alcance**, como a máquina da Doca: com o chunk carregado e outro bloco no lugar (ar ou outro id; girada fica), ela é desvinculada pelo caminho de sempre e a vaga volta; os cartões do grupo que esvazia caem no roteador. Chunk descarregado não é quebra. A tela segue com "sumida" e "trocada", o Desvincular e o Atualizar para os limites aceitos (a troca com o upgrade fora, a remota inativa). (11/11)
- **Doca, a volta nos tiers baixos fica com a vazão do roteador por lado** (Básica 32 e Avançada 256 itens/s por lado): o tier é a escolha do jogador, sem vazão por máquina nem multiplicador na config. (11/11)
- **Doca, a rajada acima de 1 ms do começo do pedido com o Quantum é aceita:** o orçamento já limita e a dívida sai no tick seguinte (0,53 ms/tick em média com cinco Infinite Factories). (11/11)

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
- Telas: o servidor valida tudo (mesmo `containerId`, até 8 blocos, prioridade −999..999, rede que o jogador pode usar) e a tela espera o snapshot do servidor, sem mudar nada sozinha. Com Vinculador ou Cartão de Upgrade na mão, o clique vai para o item, não para a tela (o Configurador, num roteador, abre a tela como qualquer item).
- A receita do roteador é ferro + redstone + olho de ender (`data/wirelessautomate/recipe/router.json`).
