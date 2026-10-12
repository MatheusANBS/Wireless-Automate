# Rede por face

**Estado:** aprovado pelo dono em 2026-10-12 (mockup `docs/preview/rede-por-face.html`, v2). Ramo `roteador-de-alcance`: entra no mesmo update grande do roteador de alcance.

## 1. O que muda

Hoje cada aba (tipo de recurso) de um roteador está numa rede, e as seis faces da máquina seguem essa rede. A partir daqui, **a rede é única e somente da face**: cada face da máquina, em cada tipo, está numa rede ou em nenhuma. Não existe mais rede da aba, nem herança, nem "da aba".

Exemplo: uma Fornalha com um roteador só. Na aba Itens, a face Cima insere pela rede Mina e a face Baixo extrai pela rede Armazém.

As abas continuam, só como tipo de recurso: cada tipo tem, por face, modo, prioridade, redstone, filtro, cartões e agora a rede.

## 2. Decisões

1. **Rede no `FaceConfig`.** O `FaceConfig` ganha o campo `network` (`UUID`, `null` = sem rede), salvo pelo codec na chave `network` (ausente = sem rede; uma face padrão continua uma tag vazia). Como o perfil de grupo e a exceção do roteador de alcance (`MachineConfig`) e o preset do Configurador (`RouterPreset`) já são feitos de `FaceConfig`, eles levam a rede de cada face sem estrutura nova. `FaceConfig.isDefault()` passa a exigir `network == null`.
2. **Sem rede da aba.** O array `networks[]` do `RouterBlockEntity` deixa de existir como estado. A chave NBT antiga `networks` (e a ainda mais antiga, única) só é lida para migrar e deixa de ser escrita.
3. **Migração de mundos antigos.** Na carga, se a tag do roteador tem `networks`: para cada tipo com rede, essa rede vai para as **seis faces da máquina presa** daquele tipo e para as **seis faces de cada perfil de grupo e de cada exceção** daquele tipo (as remotas usavam a rede da aba). Faces que já têm `network` próprio não são sobrescritas (não deve acontecer, mas a regra é essa). Depois da migração o roteador se salva sem `networks`. Um mundo antigo funciona igual.
4. **Colocar o roteador:** como hoje, o roteador nasce sem rede (todas as faces sem rede).
5. **Vincular uma máquina remota (modo Máquinas):** um grupo novo nasce com as seis faces de todos os tipos na rede ativa de quem vinculou (como colocar um roteador). Um grupo que já existe não muda.
6. **Rede apagada:** as faces que estavam nela ficam sem rede (a face guarda o `UUID`; o motor trata uma rede que não existe como hoje, sem rotas). Quando a rede é removida, o `NetworkSavedData` não varre os roteadores: a face com rede inexistente é lida como "sem rede" na tela e no Tablet, e a tela mostra "Sem rede". Nada de varredura do mundo.
7. **Lados relativos à máquina em todo lugar.** Tela do roteador (também na presa) e Vinculador usam Frente, Trás, Esquerda, Direita, Cima e Baixo, pelo quadro do `RemoteSide` (Esquerda e Direita vistas de quem olha a frente da máquina). A frente da máquina presa vem do `BlockState` dela pela mesma regra das remotas (`HORIZONTAL_FACING` ou `FACING` horizontal). **Máquina sem frente, ou com frente vertical: Frente = Norte**, e a tela diz isso.
   - O armazenamento da presa **não muda**: continua por `RelativeSide` (ao `facing` + `spin` do roteador). Os lados relativos à máquina são só a apresentação e a entrada (tela, Vinculador, payloads de tela pelo quadro canônico). Se a máquina girar, os nomes na tela acompanham a frente nova e a configuração fica no mesmo lado absoluto.
8. **Vinculador.** Linha **Faces** abaixo do mapa: `[Todas] [F] [T] [E] [D] [C] [B]`, lados relativos à máquina (do roteador clicado; na área, de cada roteador). Componente novo no item, `linker_faces` (máscara de 6 bits por `RemoteSide`, padrão 63 = todas). "Todas" marca as seis; com nenhuma marcada, vincular avisa e não faz nada.
   - Vincular (clique ou área): as faces marcadas, nas abas marcadas, vão para a rede ativa.
   - "Nenhuma (desvincular)": as faces marcadas, nas abas marcadas, ficam sem rede.
   - "Já estavam" conta o roteador em que todas as faces marcadas, nas abas marcadas, já estavam na rede alvo.
   - A linha Faces some no modo Máquinas. Sem atalho de roda para as faces (Shift + roda continua nas abas).
   - Botão: "Vincular 3", "Vincular 3 · T, B", "Desvincular T, B · 3" (abreviado pelo `GuiText`, inteiro na dica). Action bar: "3 roteadores vinculados a Armazém (Itens, todas as faces)", "3 roteadores: faces T, B em Armazém (Itens)", "3 roteadores: faces T, B sem rede (Itens)".
9. **Tela do roteador.**
   - Sai o seletor de rede da linha das abas. No canto direito dessa linha, um resumo discreto "N redes" (redes distintas das faces da aba no alvo editado; "Sem rede" se nenhuma), com a dica listando face → rede.
   - Na coluna da direita, abaixo dos botões de modo, a linha **Rede** da face selecionada: um seletor com as redes que o jogador pode usar e "Sem rede". Pílula com a borda e o ponto na cor da rede; cinza em "Sem rede".
   - Cada botão de face tem um ponto na cor da rede dela; sem ponto, sem rede. A dica da face: modo, "Rede: X" ou "Sem rede", e o lado absoluto junto ("Trás · Norte").
   - Abaixo dos botões de face, uma linha discreta: "Frente da máquina: Sul", ou "Sem frente: Frente = Norte". No grupo do alcance: "Lados pela frente de cada máquina".
   - Para caber no tamanho mínimo (300 × 240): os botões de modo baixam de 18 para 14 px e os cartões descem 4 px.
   - No grupo e na exceção (roteador de alcance), a linha Rede edita a rede da face do perfil ou da exceção, como o modo.
   - O visor 3D continua igual; a face selecionada ganha o contorno na cor da rede quando tem rede.
10. **Motor.** Uma porta já é (nó, máquina, tipo, face). Agora cada porta pertence à rede da sua face. Um nó é membro de uma rede se alguma face ativa de algum tipo dele (presa ou remota) está nela. Mudar a rede de uma face suja o tipo na rede antiga e na nova. O custo por tick não muda: as rotas são montadas por rede como hoje, só que filtrando as portas pela rede delas. Os cursores e o sono sobrevivem às remontagens como hoje.
11. **Tablet.**
    - O índice de nós (`NodeIndex`) guarda, por tipo, as redes distintas das faces do nó (presa, perfis e exceções). Chave NBT nova `face_networks` (tipo → lista); a antiga `networks` é lida como migração (uma rede por tipo).
    - O roteador aparece na Lista de cada rede em que tem alguma face, com "faces em 2 redes" quando tem mais de uma. As Estatísticas contam o nó em cada rede em que ele tem face.
    - Uma face numa rede que não existe mais: aviso na linha do roteador, "faces sem rede: rede removida".
    - **Mover nós pelo Tablet** (aba T, para a rede B): todas as faces do tipo T (presa, perfis e exceções) vão para B; sem aba, todos os tipos. A Lista não tem filtro por rede. Mover uma face sozinha pelo Tablet fica de fora.
12. **Configurador.** O preset copia a rede de cada face (dentro dos `FaceConfig`). Colar segue a regra de hoje, por rede distinta: uma rede que o jogador não pode usar não é aplicada, e as faces que a teriam ficam com a rede que já tinham; a action bar avisa como hoje. O preset antigo, com `networks` por tipo, é lido como "essa rede nas seis faces do tipo".
13. **Comandos.** `/wa face ...` ganha o argumento de rede da face, se for simples; senão fica como está.
14. **Protocolo:** `ModPayloads.VERSION` passa de `"13"` para `"14"` (uma vez). O `SetNetworkPayload` da tela vira a rede de uma face (alvo de edição, tipo, face no quadro do alvo como os outros payloads da tela, rede opcional). O `RouterSnapshot` manda a rede de cada face em vez da rede de cada aba, e a frente da máquina presa. O `LinkerActionPayload`/snapshot leva a máscara de faces.

## 3. Fora do escopo

- Uma face em duas redes ao mesmo tempo.
- Rede por cartão de filtro ou por slot da máquina.
- A linha Faces no modo Máquinas do Vinculador.
- Mover uma face sozinha entre redes pelo Tablet.
- Atalho de roda para as faces no Vinculador.
- Varrer roteadores quando uma rede é apagada.

## 4. Performance

- Nada de tick novo, nada de varredura. A rede da face é lida na montagem das rotas, junto com o resto do `FaceConfig`.
- Membros de uma rede: o `NodePorts` guarda, por tipo, as redes distintas das faces (normalmente uma ou duas), recalculadas só no `nodeChanged`.
- A frente da máquina presa é lida do `BlockState` só para a tela e o Vinculador (no snapshot e no clique), nunca por tick.

## 5. Testes

- GameTests: duas redes num roteador só (Cima numa, Baixo noutra) movendo de uma para a outra pela máquina; trocar a rede de uma face suja as duas redes e move a porta; rede apagada deixa a face parada; migração da tag `networks` antiga (presa e perfil de grupo); Vinculador por faces relativas numa máquina com frente e numa sem frente (Frente = Norte); desvincular faces deixa sem rede; Configurador copiando e colando redes por face (com rede proibida); Tablet listando o nó nas duas redes e movendo só as faces da rede filtrada; roteador de alcance com perfil de grupo em duas redes.
- JUnit para a lógica pura nova (máscara de faces do Vinculador, rótulos de faces marcadas).
- e2e: capturas da tela do roteador com a linha Rede e os pontos, o resumo "N redes", o Vinculador com a linha Faces, e o `GuiText.clipCount() == 0`.
