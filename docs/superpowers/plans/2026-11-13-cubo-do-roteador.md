# Plano: cubo do alcance do roteador no mundo

Spec: `docs/superpowers/specs/2026-11-13-cubo-do-roteador-design.md` (sem mockup, por decisão do dono). Com o Vinculador na mão e um roteador escolhido, o cliente desenha o cubo do raio de máquinas do roteador e as máquinas vinculadas, reaproveitando o pacote e o desenho do cubo da Doca, generalizados para os dois donos.

Pacote base: `src/main/java/io/github/matheusanbs/wirelessautomate/` (abreviado abaixo).

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; textos nos dois `lang`, sempre os dois.
- Chaves de NBT, componentes e config **não mudam**. `ModPayloads.VERSION` vai de `"51"` para `"52"` (com a linha do histórico no javadoc dele): o `DockReachPayload` e o `DockReachRequestPayload` dão lugar ao `LinkerReachPayload` e ao `LinkerReachRequestPayload`.
- **Lado do cliente:** classes de desenho só em `client/`; o código comum só cita o desenho dentro da lambda do handler do pacote (como o `packet/DockPayloads` faz hoje), que o servidor dedicado nunca roda. O `runGameTestServer` é um servidor dedicado e pega o erro.
- `RouterBlockEntity` tem 999 linhas: **nenhuma linha nova nele**. O que o roteador precisar fica em `range/RouterReachSync`, `RangeLinks` e `SameBlockLinks`.
- **Performance** (skill `wa-performance`, leia `.claude/skills/wa-performance/SKILL.md`, áreas "Menus e payloads" e "Cliente"): nada por tick no servidor; o pacote só nos eventos (escolher, vincular, desvincular, pedido), só ao jogador que agiu; índices de cor em vez de cores (um byte por máquina); no cliente, vértices montados quando o pacote chega e o quadro só soma a câmera e desenha (nada de alocar por quadro); só o que está a menos de 128 blocos; sem cubo guardado, o tick só lê os itens das mãos.
- **Tamanho** (skill `wa-modular`): telas e desenho em `client/` até 500 linhas; servidor até 1000. `python -I .claude/skills/wa-modular/scripts/medir.py <arquivos>` antes do commit.
- **Comentários** (skill `wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py` antes do commit; os javadocs que falam "da Doca" no que passou a valer para os dois são reescritos.
- **Varredura de performance:** `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` antes do commit; resolva ou justifique cada candidato no relatório.
- **GameTests:** `@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, template `"empty"`, `timeoutTicks` de no máximo 300; pertinência, nunca contagem global; `thenWaitUntil` para o `onLoad`; jogador só por `TestPlayers`; config mudada num lote `wirelessautomate_config_<assunto>` com `try/finally`.
- **Verificação de cada tarefa:** a da tarefa. Compile antes (`./gradlew compileJava`). **Nunca dois Gradle ou jogos ao mesmo tempo.** Não mexa em `run/options.txt`.
- **Commits:** um por tarefa, mensagem curta em português, `git commit --author="MatheusANBS <matheusanbs@hotmail.com>" ...` (nunca mude o `user.name`, o `user.email` nem o committer: o ambiente assina como committer), **sem** `Co-Authored-By` ou `Claude-Session`. Sem push.

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `packet/LinkerReachPayload.java` (novo, do `DockReachPayload`) | Servidor → cliente: o cubo de um roteador ou de uma Doca e as máquinas, com a cor de cada uma |
| `packet/LinkerReachRequestPayload.java` (novo, do `DockReachRequestPayload`) | Cliente → servidor: o pedido do cubo de um roteador ou de uma Doca |
| `packet/DockReachPayload.java`, `packet/DockReachRequestPayload.java` (apagar) | Substituídos |
| `packet/DockPayloads.java`, `packet/ModPayloads.java` (mudar) | O registro dos pacotes novos e o protocolo 52 |
| `dock/DockReachSync.java` (mudar) | O pacote genérico com `kind` Doca e a cor do lado |
| `range/RouterReachSync.java` (novo) | O pacote do roteador, o envio e a resposta ao pedido |
| `range/RangeLinks.java`, `range/SameBlockLinks.java` (mudar) | Mandar o cubo ao escolher, vincular e desvincular |
| `client/linker/ReachPaint.java` (novo, do `client/dock/DockReachPaint`) | O desenho do cubo e das máquinas, de qualquer dono |
| `client/linker/ReachBox.java` (novo, do `client/dock/DockReachBox`) + o teste JUnit | Os limites e as arestas do cubo (lógica pura) |
| `gametest/RouterReachGameTests.java` (novo), `gametest/DockLinkGameTests.java` (mudar) | GameTests do pacote |
| `client/dev/E2eLinkerSteps.java`, `client/dev/E2eDockSteps.java` (mudar) | e2e do cubo do roteador; o da Doca pelo `ReachPaint` |
| `scripts/guide/paginas/linker.py` + páginas geradas, `docs/especificacao.md`, `docs/mapa/*.md` (mudar) | Guia e docs |

---

## Tarefa 1: o pacote genérico e o cubo do roteador no servidor

**Arquivos:** `packet/LinkerReachPayload.java` (novo), `packet/LinkerReachRequestPayload.java` (novo), `packet/DockReachPayload.java` e `packet/DockReachRequestPayload.java` (apagar), `packet/DockPayloads.java`, `packet/ModPayloads.java`, `dock/DockReachSync.java`, `range/RouterReachSync.java` (novo), `range/RangeLinks.java`, `range/SameBlockLinks.java`, `client/dock/DockReachPaint.java` (só o mínimo para ler o pacote novo), `client/dev/E2eDockSteps.java` (se precisar), `gametest/DockLinkGameTests.java`, `gametest/RouterReachGameTests.java` (novo).

**Interfaces que produz:**

```java
// packet/LinkerReachPayload.java
public record LinkerReachPayload(byte kind, BlockPos origin, int radius, int tier, long[] machines, byte[] colors,
        int ticks) implements CustomPacketPayload {
    public static final byte ROUTER = 0;
    public static final byte DOCK = 1;
    /** Sem cubo (o roteador sem o Upgrade de Alcance valendo): só as máquinas. */
    public static final int NO_CUBE = -1;
    /** A cor cinza: a máquina fora do raio ou além do limite. As cores 0 a 5 são as da paleta {@code DockColors.side}. */
    public static final byte INACTIVE = 6;
    public static final int MAX_MACHINES = RangeTiers.MAX_MACHINES;
    // TYPE "linker_reach", STREAM_CODEC como o do DockReachPayload (o kind num byte, o raio em VAR_INT com o −1
    // codificado como o raio + 1, ou num zigzag; as cores num byte cada; o decode recusa count fora de 0..MAX)
}

// packet/LinkerReachRequestPayload.java
public record LinkerReachRequestPayload(byte kind, BlockPos pos) implements CustomPacketPayload { /* "linker_reach_request" */ }

// range/RouterReachSync.java
public final class RouterReachSync {
    public static LinkerReachPayload payload(RouterBlockEntity router);   // ticks 0
    public static void send(ServerPlayer player, RouterBlockEntity router);
    public static void request(@Nullable ServerPlayer player, BlockPos pos);
}
```

**Passos:**

1. `LinkerReachPayload` e `LinkerReachRequestPayload` a partir dos dois da Doca, com o `kind`; apague os da Doca. Registre os novos no `DockPayloads` (ou num `packet/LinkerPayloads.java` novo, se ficar mais claro; o `ModPayloads.register` chama). O pedido despacha pelo `kind`: `ROUTER` → `RouterReachSync.request`, `DOCK` → `DockReachSync.request`. O handler do cliente chama o desenho dentro da lambda.
2. `ModPayloads.VERSION` = `"52"`, com a linha no javadoc do histórico ("52: o cubo do alcance do roteador; o pacote do cubo da Doca virou o `LinkerReachPayload`, com o pedido `LinkerReachRequestPayload`").
3. `DockReachSync.payload`: monta o `LinkerReachPayload` com `kind = DOCK`, a cor = o ordinal do lado (como o `sides` de hoje), o resto igual.
4. `RouterReachSync.payload(router)`: `kind = ROUTER`, o raio `Config.machineRadius(tier)` se `RangeLinks.upgradeRefusal(router) == NONE`, senão `NO_CUBE`; as remotas na ordem de vínculo até `MAX_MACHINES` (a presa não), cada uma com a cor pelo `router.machineReach()[i]`: `ACTIVE` → a cor do grupo dela (`DockColors.ORDER[g % 6]`, com `g` a posição do grupo na lista de grupos do roteador), senão `INACTIVE`. `send` como o `DockReachSync.send` (jogador falso e canal não negociado: nada). `request(player, pos)`: responde só se o Vinculador de uma das mãos tem esse roteador escolhido (`LinkerItem.router`), na dimensão do jogador, carregado, inteiro e com `mayInteract`.
5. Chame `RouterReachSync.send(player, router)` no `RangeLinks.choose` (quando escolhe, não quando solta), no `RangeLinks.toggle` (depois de vincular e de desvincular) e no `SameBlockLinks.link` e `unlink` (no fim, com o estado novo). Nada nas recusas.
6. `client/dock/DockReachPaint`: o mínimo para compilar com o pacote novo (o `receive` lê o `LinkerReachPayload`, ignora o `kind ROUTER` nesta tarefa, a cor do lado vem de `colors`; o pedido manda `LinkerReachRequestPayload(DOCK, pos)`). A tarefa 2 o generaliza.
7. `gametest/DockLinkGameTests`: os testes do pacote da Doca pelo `LinkerReachPayload` (`colors` no lugar de `sides`, `kind == DOCK`).
8. `gametest/RouterReachGameTests` (run comum):
   - com o upgrade: o pacote tem o raio do tier, as remotas na ordem, a cor do grupo (dois grupos de blocos diferentes, cores diferentes, `DockColors.ORDER[0]` e `[1]`), e a presa fora;
   - uma remota fora do raio (vinculada direto pelo `linkMachine`) vem `INACTIVE`; sem o upgrade, `radius == NO_CUBE` e todas `INACTIVE`;
   - o teto: com mais remotas que `MAX_MACHINES` (vinculadas direto com a config do limite subida num lote próprio, ou com `MAX_MACHINES` lido do pacote), o pacote para no teto;
   - o pedido: `RouterReachSync.request` não manda nada sem o Vinculador com esse roteador (o teste confere pelo caminho que o `DockLinkGameTests` usa para o pedido da Doca; se o jogador de teste não negocia canais, prove a regra pela função que decide, deixando-a pacote-privada ou pública com javadoc).

**Custo:** nada por tick; um pacote de ~9 bytes por máquina (até 512) por evento do jogador que agiu.

**Verificação:** `./gradlew build runGameTestServer runGameTestServerAe2 runGameTestServerRefined`. Commit: `Cubo do roteador: o pacote genérico e o envio no servidor`.

---

## Tarefa 2: o desenho dos dois cubos no cliente e o e2e

**Arquivos:** `client/linker/ReachPaint.java` (novo, do `client/dock/DockReachPaint.java`, que sai), `client/linker/ReachBox.java` (novo, do `client/dock/DockReachBox.java`, que sai) e o teste JUnit dele (`src/test/java/.../client/dock/DockReachBoxTest.java` → `client/linker/ReachBoxTest.java`), `packet/DockPayloads.java` (ou o registro novo), `client/dev/E2eDockSteps.java`, `client/dev/E2eLinkerSteps.java`.

**Passos:**

1. `ReachPaint`: o `DockReachPaint` generalizado. Guarda o `kind` e a origem do cubo; o "Vinculador nesta mão tem este dono escolhido" confere o `linker_router` (`LinkerItem.router`, um `GlobalPos`) para `ROUTER` e o `linker_dock` (`LinkerItem.dock(...).pos()`) para `DOCK`; o "o dono sumiu" confere `RouterBlock` ou `PatternDockBlock` no lugar; o pedido sem cubo escolhe o `kind` pelo que o Vinculador da mão tem escolhido (roteador ou Doca) e manda o `LinkerReachRequestPayload`. Raio `NO_CUBE`: sem as arestas do cubo, só as máquinas. A cor `INACTIVE` é um cinza fixo (uma constante no `ReachPaint`, por exemplo `0x8A8378`), as outras pela `DockColors.side`. Todo o resto (prazo, `grace`, recuo dos pedidos, `REACH`, o desenho por quadro sem alocar) fica como está.
2. `ReachBox`: o `DockReachBox` renomeado, com o JUnit junto (mesmos casos).
3. O handler do cliente chama `ReachPaint.receive`.
4. `E2eDockSteps`: os passos do cubo da Doca pelo `ReachPaint` (mesmas asserções).
5. `E2eLinkerSteps`: depois do Ctrl + clique dos dois funis, um passo "Vinculador: o cubo do roteador" que confere `ReachPaint.shown()`, o `kind` roteador e `machineCount() == 2` (o roteador do passo tem o Upgrade de Alcance), com a captura `vinculador-cubo` (só quando pedida; veja como o `E2eDockSteps` faz a `doca-cubo`); e, guardado o Vinculador, o cubo some (`!shown()` e `stored()`), e pego de novo volta.
6. Olhe a captura `vinculador-cubo` (rode com `--capture vinculador-cubo`) e descreva no relatório o que aparece (o cubo na cor do tier, os dois funis com a moldura).

**Custo:** o mesmo do cubo da Doca: vértices no pacote, o quadro só desenha; sem cubo, o tick lê as duas mãos.

**Verificação:** `./gradlew build runGameTestServer` e `./scripts/e2e.sh --only vinculador,doca --capture vinculador-cubo,doca-cubo` (`E2E_TIMEOUT=1200` se precisar), `run/e2e/result.txt` = `OK`. Commit: `Cubo do roteador: o desenho no cliente`.

---

## Tarefa 3: guia e docs

**Arquivos:** `scripts/guide/paginas/linker.py` + as páginas geradas, `docs/especificacao.md`, `docs/mapa/roteador.md`, `docs/mapa/alcance.md`, `docs/mapa/doca.md`, `docs/mapa/cliente-e-pacotes.md`.

**Passos:**

1. Guia (pt e en), uma frase no "Como usar" do Vinculador: "Com o Vinculador na mão e um roteador escolhido, o cubo do raio dele aparece no mundo, e as máquinas vinculadas acendem na cor do grupo; as que estão fora do raio ou além do limite ficam cinza." Regere com `python -I scripts/guide/gerar_guia.py`; só as páginas do Vinculador mudam.
2. Especificação: na linha do **Vincular**, o cubo do roteador (quando aparece e some, cores, a spec nova); na da Doca, o pacote genérico.
3. Mapas: as linhas das classes novas e mudadas (`RouterReachSync`, `DockReachSync`, `ReachPaint`, `ReachBox`, os pacotes).

**Verificação:** `./gradlew build` e o e2e `guia` (`./scripts/e2e.sh --only guia`), `OK`. Commit: `Cubo do roteador: guia e docs`.

---

## Fechamento (o condutor)

- As oito runs de GameTest e o e2e `vinculador,doca,guia`.
- Sem bench de motor (nada no motor); o custo do cliente é o do cubo da Doca, já medido no e2e `doca-desempenho`.
- Revisão final do ramo; `docs/progresso/`.
