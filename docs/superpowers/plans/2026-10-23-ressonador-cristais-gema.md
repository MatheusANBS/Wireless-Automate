# Ressonador: o cristal da câmara como gema (Parte A) — Plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa a tarefa. Os passos usam caixas de seleção (`- [ ]`).

**Meta:** o cristal da câmara do Ressonador deixa de ser uma bipirâmide pintada e vira uma gema de quartzo com profundidade, núcleo vivo, reflexo e quatro estados legíveis (vivo, Opaco, enchimento da recarga, rachado), com uma aura em estrela.

**Arquitetura:** só malha, shader e câmara. `CrystalMesh` passa a 32 triângulos (prisma de 8 lados com as pontas chanfradas); `CrystalParam` (puro) empacota semente, estado e o bit "longe" no parâmetro `short` do vértice; o `resonator_crystal.fsh` é reescrito sobre o `wa_common.glsl` (normal da faceta pelas derivadas, interior fingido em camadas, núcleo, dispersão, brilhos e os quatro estados); a aura é o modo 2 do `resonator_ribbon.fsh`.

**Tecnologia:** GLSL `#version 150` (core shaders do 1.21.1, `#moj_import`), Java 21, JUnit 5, e2e `WA_E2E_ONLY=ressonador`.

Spec: `docs/superpowers/specs/2026-10-23-ressonador-cristais-gema-design.md`. Continua no ramo `ressonador-plasma-qualidade` (decisão do dono), depois dos commits da spec anterior. A Parte B (energia) vem depois, com spec e plano próprios.

## Restrições globais

Valem para toda tarefa (da spec e das leis da `wa-performance`):

- Sem dependência nova. Fora de escopo: os feixes dos cristais e o conduto (B), a espiral, o anel e os estilhaços da recarga (C), o ambiente (D), o plasma e o desenho antigo sem shader (o modelo do item, para o shaderpack do Iris).
- Mantém o contrato dos vértices do `CrystalGem`: Position e Normal na pose, cor do tier e sintonia (0 a 255) em `Color`, cantos da faceta em UV0, x e z do cristal em UV1 como `(v + 1) × 10000`, altura relativa × 10000 em UV2.x e o parâmetro em UV2.y, tudo `short` (< 32768), e os limites `BOTTOM` −0,46 e `TOP` 0,58.
- Parado e longe custa o mesmo de hoje: a câmara só desenha com o Núcleo montado e perto; a malha é montada uma vez; nada alocado por quadro (rascunhos estáticos, nenhum objeto novo).
- Cristal longe (acima de 12 blocos, o `PlasmaCore.NEAR_SQ`): 1 amostra do interior em vez de 3.
- Teto de custo: o tempo de quadro da câmara (`camara plasma` do e2e) até ~1,5× o da linha de base da Task 1, em cada situação.
- Comentários em português, só o porquê (skill `wa-comentarios`); sem texto de jogo novo (nada de lang).
- Commits em português, sem linhas de atribuição, no ramo atual. Um Gradle ou cliente por vez (~3 GB).
- Como rodar o e2e no Windows, sem Xvfb (JDK 21 já é o padrão deste shell): usar o script do rascunho, ou na mão:
  `WA_E2E_ONLY=ressonador WA_E2E="$PWD/run/e2e" ./gradlew runClient --console=plain > run/e2e/client.log 2>&1`, esperar o `run/e2e/result.txt` (primeira linha `OK`) e encerrar o processo java do jogo (ele às vezes fica pendurado depois do `result.txt`; matar só o processo cujo comando tem `cpw.mods.bootstraplauncher` e `Wireless-Automate`). Ler a captura com `Read` no `.png`; `grep -i "resonator_crystal\|Error compiling\|wa_common" run/e2e/client.log` acha erro de shader (o jogo não cai: o cristal volta ao modelo do item e avisa no log).
- Ajuste de look: as constantes do shader são o botão de ajuste; mude, rode o e2e filtrado, olhe a captura. Até 3 rodadas por tarefa antes de reportar; o aceite visual é do dono, nos portões marcados.

## Estrutura de arquivos

| Arquivo | Papel |
| --- | --- |
| `client/resonator/render/CrystalParam.java` (novo) | pura: `pack`, `seed`, `state`, `far` do parâmetro de vértice, os códigos de estado e o `recharging(progress)` |
| `client/resonator/render/CrystalMesh.java` | a malha de 32 triângulos e o sinal por triângulo que desliga a aresta da diagonal |
| `client/resonator/render/CrystalGem.java` | escreve a malha no buffer; usa o `CrystalParam`; passa o bit longe |
| `client/resonator/render/ChamberCrystals.java` | escolhe o estado por cristal (inclui o rachado) e o bit longe |
| `client/resonator/render/PlasmaCore.java` | `NEAR_SQ` passa a package-private |
| `.../shaders/core/resonator_crystal.fsh` | reescrito |
| `.../shaders/core/resonator_ribbon.fsh` | o modo 2 (aura) |
| `client/resonator/render/CrystalAura.java` | `SIZE` da aura |
| `client/dev/E2eResonatorFlowSteps.java`, `E2eResonatorScreenSteps.java` | a captura nova `ressonador-cristais-estados` |
| `src/test/.../CrystalParamTest.java` (novo), `CrystalMeshTest.java` | JUnit |
| `CLAUDE.md`, `docs/progresso.md` | docs |

---

### Task 1: A captura dos quatro estados e a linha de base

**Custo:** nenhum no jogo; só e2e.

**Files:**
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/client/dev/E2eResonatorScreenSteps.java:184` (`cracked` deixa de ser `private`)
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/client/dev/E2eResonatorFlowSteps.java:155` (a captura nova)

**Interfaces:**
- Produces: a captura `ressonador-cristais-estados.png` (câmera de perto, com cristais vivos, um Opaco e um rachado) e `run/e2e-antes-cristais/` com as capturas e o `result.txt` de antes desta parte.

- [ ] **Step 1: Tornar o `cracked` do e2e acessível**

Em `E2eResonatorScreenSteps.java`, `private static ItemStack cracked(ItemStack stack) {` vira `static ItemStack cracked(ItemStack stack) {`.

- [ ] **Step 2: O passo que põe um rachado ao lado do Opaco e fotografa**

Em `E2eResonatorFlowSteps.rechargeSteps`, logo depois de `list.add(capture("ressonador-camara-cristais"));` (e antes de `E2eResonatorScreenSteps.crackedChamberSteps(list, origin);`), acrescente (a câmera continua a de perto, `CLOSE_VIEW`, e a última câmara continua Opaca, sem redstone):

```java
        list.add(new Step("Ressonador: um rachado ao lado do Opaco", STEP_TIMEOUT_MS, () -> onServer(server -> {
            ResonanceCoreBlockEntity core = (ResonanceCoreBlockEntity) server.overworld()
                    .getBlockEntity(core(origin[0]));
            core.setCrystalAt(0, E2eResonatorScreenSteps.cracked(new ItemStack(ModItems.SIGNAL_CRYSTAL.get())));
            return null;
        }), () -> view(origin).chambers()[0] == 5 && view(origin).rechargeStart() < 0,
                () -> "rachado no cliente"));
        list.add(DevEndToEnd.wait("o rachado assentar", 20));
        list.add(capture("ressonador-cristais-estados"));
```

- [ ] **Step 3: Rodar o e2e e guardar a linha de base**

```bash
mkdir -p run/e2e && rm -f run/e2e/result.txt run/e2e/*.png
WA_E2E_ONLY=ressonador WA_E2E="$PWD/run/e2e" ./gradlew runClient --console=plain > run/e2e/client.log 2>&1
head -1 run/e2e/result.txt
grep -i "camara.*ms/quadro" run/e2e/result.txt | sed 's/.*; //'
rm -rf run/e2e-antes-cristais && cp -r run/e2e run/e2e-antes-cristais
```

Expected: `OK`; a captura `run/e2e-antes-cristais/ressonador-cristais-estados.png` existe. Abra-a e confirme que mostra cristais vivos, um Opaco caído e um rachado (que hoje é igual ao Opaco). Anote os tempos de `camara plasma` (parado, com conduto, com espiral): o teto das tarefas seguintes é 1,5× cada um (use a média das duas medições de cada situação; há picos isolados que não contam).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/client/dev docs/superpowers/specs/2026-10-23-ressonador-cristais-gema-design.md docs/superpowers/specs/2026-10-23-ressonador-energia-raio-design.md docs/superpowers/plans/2026-10-23-ressonador-cristais-gema.md
git commit -m "Ressonador: specs dos cristais e da energia, plano dos cristais e captura dos quatro estados no e2e"
```

---

### Task 2: O parâmetro do vértice e o estado rachado

**Custo:** nenhum a mais por quadro; um `boolean` por cristal (a distância à câmera, uma conta por quadro, sem alocar, com os campos `Vec3` que a câmera e a cena já têm).

**Files:**
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/client/resonator/render/CrystalParam.java`
- Create: `src/test/java/io/github/matheusanbs/wirelessautomate/client/resonator/render/CrystalParamTest.java`
- Modify: `.../client/resonator/render/CrystalGem.java` (reescrito abaixo)
- Modify: `.../client/resonator/render/ChamberCrystals.java:57-97`
- Modify: `.../client/resonator/render/PlasmaCore.java` (`NEAR_SQ`)
- Modify: `src/main/resources/assets/wirelessautomate/shaders/core/resonator_crystal.fsh:44-47,67`

**Interfaces:**
- Produces: `CrystalParam.pack(int seed, int state, boolean far) → int`, `seed(int)`, `state(int)`, `far(int)`, as constantes `LIVE`, `DULL`, `RECHARGE`, `STEPS`, `CRACKED` e `recharging(double progress) → int`; `CrystalGem.render(PoseStack.Pose, MultiBufferSource, int tierColor, int tune, int state, int seed, boolean far)`.

- [ ] **Step 1: O teste que falha**

```java
package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrystalParamTest {
    @Test
    void idaEVoltaDeTodasAsCombinacoes() {
        for (int seed = 0; seed < 16; seed++) {
            for (int state = 0; state <= CrystalParam.CRACKED; state++) {
                for (boolean far : new boolean[] {false, true}) {
                    int p = CrystalParam.pack(seed, state, far);
                    assertEquals(seed, CrystalParam.seed(p));
                    assertEquals(state, CrystalParam.state(p));
                    assertEquals(far, CrystalParam.far(p));
                    assertTrue(p >= 0 && p < 32768, "o parâmetro cabe num short: " + p);
                }
            }
        }
    }

    @Test
    void oRachadoFicaDepoisDoFimDaRecarga() {
        assertEquals(1003, CrystalParam.CRACKED);
        assertEquals(1002, CrystalParam.recharging(1.0));
        assertEquals(2, CrystalParam.recharging(0.0));
        assertEquals(2, CrystalParam.recharging(-1.0));
        assertEquals(1002, CrystalParam.recharging(7.0));
    }

    @Test
    void oBitLongeNaoMexeNoEstado() {
        int near = CrystalParam.pack(5, CrystalParam.recharging(0.5), false);
        int far = CrystalParam.pack(5, CrystalParam.recharging(0.5), true);
        assertEquals(CrystalParam.state(near), CrystalParam.state(far));
        assertFalse(CrystalParam.far(near));
        assertTrue(CrystalParam.far(far));
    }
}
```

Run: `./gradlew test --tests "*CrystalParamTest"`
Expected: FALHA de compilação (`CrystalParam` não existe).

- [ ] **Step 2: A classe**

```java
package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * O parâmetro de vértice do cristal (UV2.y, um {@code short}, então tudo fica abaixo de 32768): a semente (o índice da
 * câmara, 4 bits), o estado (10 bits) e, no bit 14, se o cristal está longe da câmera. O estado é 0 vivo, 1 Opaco,
 * 2 a 1002 a recarga de 0 a 100%, e 1003 o rachado. O máximo é 16384 + (1003 << 4) + 15 = 32447.
 */
final class CrystalParam {
    static final int LIVE = 0;
    static final int DULL = 1;
    static final int RECHARGE = 2;
    static final int STEPS = 1000;
    /** Depois do fim da recarga (2 + 1000 = 1002), para não conflitar com ela. */
    static final int CRACKED = RECHARGE + STEPS + 1;
    private static final int SEED_MASK = 15;
    private static final int STATE_SHIFT = 4;
    private static final int STATE_MASK = 1023;
    private static final int FAR_BIT = 1 << 14;

    private CrystalParam() {
    }

    static int pack(int seed, int state, boolean far) {
        return (seed & SEED_MASK) | (state << STATE_SHIFT) | (far ? FAR_BIT : 0);
    }

    static int seed(int param) {
        return param & SEED_MASK;
    }

    static int state(int param) {
        return (param >> STATE_SHIFT) & STATE_MASK;
    }

    static boolean far(int param) {
        return (param & FAR_BIT) != 0;
    }

    /** O estado da recarga em {@code progress} (0 a 1). */
    static int recharging(double progress) {
        return RECHARGE + (int) Math.round(Math.clamp(progress, 0.0, 1.0) * STEPS);
    }
}
```

Run: `./gradlew test --tests "*CrystalParamTest"`
Expected: PASS (3 testes).

- [ ] **Step 3: O `CrystalGem` sobre o `CrystalParam`**

Substitua o arquivo `CrystalGem.java` por:

```java
package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3f;

/**
 * Escreve a malha do {@link CrystalMesh} no buffer do {@code resonator_crystal}, no formato {@code NEW_ENTITY}:
 * posição e normal pela pose; a cor do tier no RGB e a sintonia (0 a 255) no alfa; os cantos da faceta no UV0; x e z
 * do cristal no UV1, como {@code (v + 1) × 10000}; a altura relativa × 10000 e o parâmetro (o {@link CrystalParam})
 * no UV2. O UV1 e o UV2 são {@code short} no vértice, então tudo fica abaixo de 32768.
 */
final class CrystalGem {
    private static final float SCALE = 10000f;
    /** Rascunhos do desenho, que é só na thread de render: nada alocado por quadro. */
    private static final Vector3f POS = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();

    private CrystalGem() {
    }

    /**
     * Os triângulos do cristal na pose de agora; {@code state} é um dos estados do {@link CrystalParam} e
     * {@code far} diz que o cristal está longe da câmera.
     */
    static void render(PoseStack.Pose pose, MultiBufferSource buffer, int tierColor, int tune, int state, int seed,
            boolean far) {
        VertexConsumer out = buffer.getBuffer(ChamberRenderTypes.crystal());
        int r = (tierColor >> 16) & 0xFF;
        int g = (tierColor >> 8) & 0xFF;
        int b = tierColor & 0xFF;
        int param = CrystalParam.pack(seed, state, far);
        for (int t = 0; t < CrystalMesh.TRIANGLES; t++) {
            pose.normal().transform(CrystalMesh.normalX(t), CrystalMesh.normalY(t), CrystalMesh.normalZ(t), NORMAL)
                    .normalize();
            for (int k = 0; k < 3; k++) {
                int v = 3 * t + k;
                float x = CrystalMesh.x(v);
                float z = CrystalMesh.z(v);
                pose.pose().transformPosition(x, CrystalMesh.y(v), z, POS);
                out.addVertex(POS.x, POS.y, POS.z)
                        .setColor(r, g, b, tune)
                        .setUv(CrystalMesh.u(v), CrystalMesh.v(v))
                        .setUv1(Math.round((x + 1) * SCALE), Math.round((z + 1) * SCALE))
                        .setUv2(Math.round(CrystalMesh.height(v) * SCALE), param)
                        .setNormal(NORMAL.x, NORMAL.y, NORMAL.z);
            }
        }
    }
}
```

- [ ] **Step 4: O estado e o bit longe em `ChamberCrystals`**

Em `PlasmaCore.java`, `private static final double NEAR_SQ = 12 * 12;` vira `static final double NEAR_SQ = 12 * 12;`.

Em `ChamberCrystals.java`: acrescente `import net.minecraft.world.phys.Vec3;`; em `render`, depois de `int recharging = recharging(chambers, view);` acrescente

```java
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 world = scene.worldCenter();
        double ex = eye.x - world.x;
        double ey = eye.y - world.y;
        double ez = eye.z - world.z;
        boolean far = ex * ex + ey * ey + ez * ez > PlasmaCore.NEAR_SQ;
```

e troque o bloco `if (gem) { ... }` por

```java
            if (gem) {
                int state = !dull ? CrystalParam.LIVE
                        : recharging == i ? CrystalParam.recharging(progress)
                        : chambers[i] >= CRACKED_SIGNAL ? CrystalParam.CRACKED : CrystalParam.DULL;
                CrystalGem.render(poseStack.last(), buffer, tierColor, tune, state, i, far);
            } else {
```

- [ ] **Step 5: O shader lê o parâmetro novo (sem mudar o visual)**

Em `resonator_crystal.fsh`, as linhas 44 a 47 viram:

```glsl
    // o parâmetro: a semente (4 bits), o estado (10 bits: 0 vivo, 1 Opaco, 2 a 1002 recarga de 0 a 1, 1003 rachado) e
    // o bit 14 (longe)
    float seed = float(param & 15) * 0.73 + 0.1;
    int state = (param >> 4) & 1023;
    float rec = state >= 2 && state <= 1002 ? float(state - 2) / 1000.0 : -1.0;
```

e a linha `float lit = state == 1 ? 0.0 : 1.0;` vira `float lit = state == 1 || state > 1002 ? 0.0 : 1.0;` (o rachado ainda se vê como o Opaco; a Task 5 o diferencia).

- [ ] **Step 6: Build, e2e e conferir que nada mudou**

```bash
./gradlew build --console=plain 2>&1 | tail -5
# e2e (comando das Restrições globais)
```

Expected: BUILD SUCCESSFUL; `result.txt` com `OK`; `grep -i "resonator_crystal\|Error compiling" run/e2e/client.log` sem saída. Compare `run/e2e/ressonador-cristais-estados.png` com a de `run/e2e-antes-cristais/`: o visual é o mesmo (a gema, o Opaco e o rachado iguais).

- [ ] **Step 7: Commit**

```bash
git add src
git commit -m "Ressonador: parâmetro do cristal com estado rachado e bit longe"
```

---

### Task 3: A malha de quartzo de 8 lados

**Custo:** 32 triângulos e 96 vértices por cristal em vez de 12 e 36, ≤ 8 cristais: ~770 vértices por quadro (uma transformação de posição por vértice e uma de normal por triângulo, sem alocar). A malha é montada uma vez no `static`.

**Files:**
- Modify: `.../client/resonator/render/CrystalMesh.java` (reescrito abaixo)
- Modify: `.../client/resonator/render/CrystalGem.java` (o UV0 codificado)
- Modify: `src/test/java/.../client/resonator/render/CrystalMeshTest.java` (reescrito abaixo)
- Modify: `.../shaders/core/resonator_crystal.fsh` (decodifica o sinal da diagonal)

**Interfaces:**
- Produces: `CrystalMesh.TRIANGLES = 32`, `VERTICES = 96`, `hidden(int triangle) → int` (o canto, 0 a 2, cuja aresta oposta é a diagonal e não acende; −1 sem diagonal) e `encodedU(int vertex) → float` (o `u` mais 8 × (`hidden` + 1), o que vai para o UV0). `u`, `v`, `x`, `y`, `z`, `height`, `normalX/Y/Z` mantêm a assinatura.

- [ ] **Step 1: O `CrystalMeshTest` novo (falha)**

```java
package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrystalMeshTest {
    @Test
    void temTrintaEDoisTriangulos() {
        assertEquals(32, CrystalMesh.TRIANGLES);
        assertEquals(96, CrystalMesh.VERTICES);
    }

    @Test
    void asNormaisSaemParaForaEBatemComOSentidoDosVertices() {
        for (int t = 0; t < CrystalMesh.TRIANGLES; t++) {
            int a = 3 * t;
            float nx = CrystalMesh.normalX(t);
            float ny = CrystalMesh.normalY(t);
            float nz = CrystalMesh.normalZ(t);
            assertEquals(1.0, Math.sqrt(nx * nx + ny * ny + nz * nz), 1e-5);
            double mx = (CrystalMesh.x(a) + CrystalMesh.x(a + 1) + CrystalMesh.x(a + 2)) / 3;
            double my = (CrystalMesh.y(a) + CrystalMesh.y(a + 1) + CrystalMesh.y(a + 2)) / 3 - 0.05;
            double mz = (CrystalMesh.z(a) + CrystalMesh.z(a + 1) + CrystalMesh.z(a + 2)) / 3;
            assertTrue(nx * mx + ny * my + nz * mz > 0, "triângulo " + t + " com a normal para dentro");
            double ex = CrystalMesh.x(a + 1) - CrystalMesh.x(a);
            double ey = CrystalMesh.y(a + 1) - CrystalMesh.y(a);
            double ez = CrystalMesh.z(a + 1) - CrystalMesh.z(a);
            double fx = CrystalMesh.x(a + 2) - CrystalMesh.x(a);
            double fy = CrystalMesh.y(a + 2) - CrystalMesh.y(a);
            double fz = CrystalMesh.z(a + 2) - CrystalMesh.z(a);
            double cx = ey * fz - ez * fy;
            double cy = ez * fx - ex * fz;
            double cz = ex * fy - ey * fx;
            assertTrue(cx * nx + cy * ny + cz * nz > 0, "triângulo " + t + " em sentido horário visto de fora");
        }
    }

    @Test
    void asAlturasVaoDeZeroAUm() {
        float min = 1;
        float max = 0;
        for (int v = 0; v < CrystalMesh.VERTICES; v++) {
            float h = CrystalMesh.height(v);
            assertTrue(h >= 0 && h <= 1);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        assertEquals(0, min, 1e-6);
        assertEquals(1, max, 1e-6);
    }

    @Test
    void cadaTrianguloTemOsTresCantosDaFaceta() {
        for (int t = 0; t < CrystalMesh.TRIANGLES; t++) {
            int a = 3 * t;
            assertEquals(0, CrystalMesh.u(a));
            assertEquals(0, CrystalMesh.v(a));
            assertEquals(1, CrystalMesh.u(a + 1));
            assertEquals(0, CrystalMesh.v(a + 1));
            assertEquals(0, CrystalMesh.u(a + 2));
            assertEquals(1, CrystalMesh.v(a + 2));
        }
    }

    @Test
    void osAneisDoCorpoFicamNoRaioENaAlturaDaSpec() {
        int lower = 0;
        int upper = 0;
        for (int v = 0; v < CrystalMesh.VERTICES; v++) {
            float y = CrystalMesh.y(v);
            if (y == CrystalMesh.TOP || y == CrystalMesh.BOTTOM) {
                assertEquals(0, CrystalMesh.x(v));
                assertEquals(0, CrystalMesh.z(v));
                continue;
            }
            double r = Math.hypot(CrystalMesh.x(v), CrystalMesh.z(v));
            assertTrue(r >= 0.25 - 1e-6 && r <= 0.31 + 1e-6, "raio " + r);
            if (y < 0) {
                lower++;
                assertTrue(y >= -0.22 - 1e-6 && y <= -0.18 + 1e-6, "altura do anel de baixo " + y);
            } else {
                upper++;
                assertTrue(y >= 0.24 - 1e-6 && y <= 0.28 + 1e-6, "altura do anel de cima " + y);
            }
        }
        assertTrue(lower > 0 && upper > 0);
    }

    @Test
    void asDiagonaisDoCorpoNaoAcendemEAsPontasNaoTemDiagonal() {
        int sides = 0;
        for (int t = 0; t < CrystalMesh.TRIANGLES; t++) {
            int hidden = CrystalMesh.hidden(t);
            if (hidden < 0) {
                continue;
            }
            sides++;
            assertTrue(hidden <= 2);
        }
        assertEquals(2 * CrystalMesh.SIDES, sides, "um par de triângulos por face lateral");
        // os dois triângulos de uma face escondem a mesma aresta: a que une os mesmos dois pontos
        for (int face = 0; face < CrystalMesh.SIDES; face++) {
            int[] first = hiddenEdge(4 * face);
            int[] second = hiddenEdge(4 * face + 1);
            assertTrue(samePoint(first[0], second[0]) && samePoint(first[1], second[1])
                    || samePoint(first[0], second[1]) && samePoint(first[1], second[0]),
                    "a face " + face + " esconde arestas diferentes");
        }
        assertEquals(-1, CrystalMesh.hidden(2));
        assertEquals(-1, CrystalMesh.hidden(3));
    }

    @Test
    void oUvCodificadoTrazOSinalSomadoDeOito() {
        for (int t = 0; t < CrystalMesh.TRIANGLES; t++) {
            for (int k = 0; k < 3; k++) {
                int v = 3 * t + k;
                assertEquals(CrystalMesh.u(v) + 8f * (CrystalMesh.hidden(t) + 1), CrystalMesh.encodedU(v), 1e-6);
            }
        }
    }

    /** Os dois vértices (índices) da aresta oposta ao canto escondido do triângulo {@code t}. */
    private static int[] hiddenEdge(int t) {
        int hidden = CrystalMesh.hidden(t);
        int[] edge = new int[2];
        int n = 0;
        for (int k = 0; k < 3; k++) {
            if (k != hidden) {
                edge[n++] = 3 * t + k;
            }
        }
        return edge;
    }

    private static boolean samePoint(int a, int b) {
        return CrystalMesh.x(a) == CrystalMesh.x(b) && CrystalMesh.y(a) == CrystalMesh.y(b)
                && CrystalMesh.z(a) == CrystalMesh.z(b);
    }
}
```

Run: `./gradlew test --tests "*CrystalMeshTest"`
Expected: FALHA (compilação: `hidden` e `encodedU` não existem; depois, 32 triângulos).

- [ ] **Step 2: A malha nova**

Substitua `CrystalMesh.java` por:

```java
package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * A malha do cristal da câmara, um prisma de quartzo de 8 lados com as pontas chanfradas: 32 triângulos, em unidades
 * do cristal (antes da escala do desenho). Dois anéis de 8 vértices formam o corpo (o de cima torcido ~12° em relação
 * ao de baixo, então as facetas laterais não são retângulos perfeitos) e uma pirâmide fecha cada ponta. O raio e a
 * altura de cada vértice têm um desvio fixo, para o cristal ser irregular. Montada uma vez; o desenho só lê as tabelas.
 *
 * <p>Por vértice: a posição, os cantos da faceta (0,0), (1,0) e (0,1), que o shader usa para acender as arestas, e a
 * altura relativa no cristal, de 0 na ponta de baixo a 1 na de cima. Por triângulo: a normal plana, para fora, e o
 * canto cuja aresta oposta é a diagonal de uma face lateral (que não é aresta do cristal e não deve acender); os
 * vértices vêm em sentido anti-horário vistos de fora.
 */
final class CrystalMesh {
    static final int SIDES = 8;
    /** 16 do corpo (2 por face lateral) e 16 das pontas. */
    static final int TRIANGLES = 4 * SIDES;
    static final int VERTICES = 3 * TRIANGLES;
    static final float TOP = 0.58f;
    static final float BOTTOM = -0.46f;
    private static final double RADIUS = 0.28;
    private static final float LOWER_Y = -0.20f;
    private static final float UPPER_Y = 0.26f;
    private static final double TWIST = Math.toRadians(12);
    /** Um ponto dentro do cristal, para a normal sair para fora. */
    private static final float INSIDE_Y = 0.05f;
    /** O valor somado ao u de cada vértice, por canto escondido mais um, para o shader decodificar. */
    private static final float HIDDEN_STEP = 8f;
    /** x, y, z de cada vértice. */
    private static final float[] POSITIONS = new float[VERTICES * 3];
    /** u, v de cada vértice. */
    private static final float[] CORNERS = new float[VERTICES * 2];
    /** A altura relativa de cada vértice. */
    private static final float[] HEIGHTS = new float[VERTICES];
    /** nx, ny, nz de cada triângulo. */
    private static final float[] NORMALS = new float[TRIANGLES * 3];
    /** O canto (0 a 2) de cada triângulo cuja aresta oposta é a diagonal, ou −1. */
    private static final byte[] HIDDEN = new byte[TRIANGLES];

    static {
        float[] lower = new float[SIDES * 3];
        float[] upper = new float[SIDES * 3];
        for (int i = 0; i < SIDES; i++) {
            double a = 2 * Math.PI * i / SIDES;
            double r1 = RADIUS * (0.9 + 0.2 * jitter(i, 1));
            double r2 = RADIUS * (0.9 + 0.2 * jitter(i, 3));
            lower[i * 3] = (float) (Math.cos(a) * r1);
            lower[i * 3 + 1] = (float) (LOWER_Y + 0.04 * (jitter(i, 2) - 0.5));
            lower[i * 3 + 2] = (float) (Math.sin(a) * r1);
            upper[i * 3] = (float) (Math.cos(a + TWIST) * r2);
            upper[i * 3 + 1] = (float) (UPPER_Y + 0.04 * (jitter(i, 4) - 0.5));
            upper[i * 3 + 2] = (float) (Math.sin(a + TWIST) * r2);
        }
        float[] top = {0, TOP, 0};
        float[] bottom = {0, BOTTOM, 0};
        int t = 0;
        for (int i = 0; i < SIDES; i++) {
            int j = (i + 1) % SIDES;
            // a face lateral i: li, lj, uj, ui; a diagonal li-uj não é aresta
            triangle(t++, lower, i, lower, j, upper, j, 1);
            triangle(t++, lower, i, upper, j, upper, i, 2);
            triangle(t++, upper, i, upper, j, top, 0, -1);
            triangle(t++, lower, j, lower, i, bottom, 0, -1);
        }
    }

    private CrystalMesh() {
    }

    /** O desvio fixo do vértice {@code i} do anel, de 0 a 1. */
    static double jitter(int i, int k) {
        double s = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453;
        return s - Math.floor(s);
    }

    static float x(int vertex) {
        return POSITIONS[vertex * 3];
    }

    static float y(int vertex) {
        return POSITIONS[vertex * 3 + 1];
    }

    static float z(int vertex) {
        return POSITIONS[vertex * 3 + 2];
    }

    /** O canto da faceta (0 ou 1), sem o sinal da diagonal. */
    static float u(int vertex) {
        return CORNERS[vertex * 2];
    }

    static float v(int vertex) {
        return CORNERS[vertex * 2 + 1];
    }

    /** O {@code u} que vai para o buffer: o canto mais 8 × (o canto escondido + 1), que o shader separa. */
    static float encodedU(int vertex) {
        return CORNERS[vertex * 2] + HIDDEN_STEP * (HIDDEN[vertex / 3] + 1);
    }

    /** O canto (0 a 2) do triângulo cuja aresta oposta é a diagonal de uma face lateral, ou −1 sem diagonal. */
    static int hidden(int triangle) {
        return HIDDEN[triangle];
    }

    static float height(int vertex) {
        return HEIGHTS[vertex];
    }

    static float normalX(int triangle) {
        return NORMALS[triangle * 3];
    }

    static float normalY(int triangle) {
        return NORMALS[triangle * 3 + 1];
    }

    static float normalZ(int triangle) {
        return NORMALS[triangle * 3 + 2];
    }

    /**
     * Grava o triângulo {@code t} de {@code a}, {@code b}, {@code c}, trocando a ordem se a normal sair para dentro.
     * {@code hiddenSlot} é o canto (0, 1 ou 2, na ordem dada) cuja aresta oposta é a diagonal, ou −1; a troca de
     * ordem leva o canto junto.
     */
    private static void triangle(int t, float[] pa, int ia, float[] pb, int ib, float[] pc, int ic, int hiddenSlot) {
        float ax = pa[ia * 3];
        float ay = pa[ia * 3 + 1];
        float az = pa[ia * 3 + 2];
        float bx = pb[ib * 3];
        float by = pb[ib * 3 + 1];
        float bz = pb[ib * 3 + 2];
        float cx = pc[ic * 3];
        float cy = pc[ic * 3 + 1];
        float cz = pc[ic * 3 + 2];
        float[] n = cross(bx - ax, by - ay, bz - az, cx - ax, cy - ay, cz - az);
        float mx = (ax + bx + cx) / 3;
        float my = (ay + by + cy) / 3 - INSIDE_Y;
        float mz = (az + bz + cz) / 3;
        int slot = hiddenSlot;
        if (n[0] * mx + n[1] * my + n[2] * mz < 0) {
            float sx = bx;
            float sy = by;
            float sz = bz;
            bx = cx;
            by = cy;
            bz = cz;
            cx = sx;
            cy = sy;
            cz = sz;
            n[0] = -n[0];
            n[1] = -n[1];
            n[2] = -n[2];
            slot = hiddenSlot == 1 ? 2 : hiddenSlot == 2 ? 1 : hiddenSlot;
        }
        NORMALS[t * 3] = n[0];
        NORMALS[t * 3 + 1] = n[1];
        NORMALS[t * 3 + 2] = n[2];
        HIDDEN[t] = (byte) slot;
        vertex(3 * t, ax, ay, az, 0, 0);
        vertex(3 * t + 1, bx, by, bz, 1, 0);
        vertex(3 * t + 2, cx, cy, cz, 0, 1);
    }

    private static void vertex(int vertex, float x, float y, float z, float u, float v) {
        POSITIONS[vertex * 3] = x;
        POSITIONS[vertex * 3 + 1] = y;
        POSITIONS[vertex * 3 + 2] = z;
        CORNERS[vertex * 2] = u;
        CORNERS[vertex * 2 + 1] = v;
        HEIGHTS[vertex] = (y - BOTTOM) / (TOP - BOTTOM);
    }

    private static float[] cross(float ax, float ay, float az, float bx, float by, float bz) {
        float x = ay * bz - az * by;
        float y = az * bx - ax * bz;
        float z = ax * by - ay * bx;
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / length, y / length, z / length};
    }
}
```

Run: `./gradlew test --tests "*CrystalMeshTest" --tests "*CrystalParamTest"`
Expected: PASS. Se `oAnelFicaNo...` ou o teste das diagonais falhar por tolerância de ruído (`jitter`), corrija a constante da malha (não o teste); o teste das normais tem que passar sem exceção.

- [ ] **Step 3: O `CrystalGem` escreve o UV codificado**

Em `CrystalGem.render`, `.setUv(CrystalMesh.u(v), CrystalMesh.v(v))` vira `.setUv(CrystalMesh.encodedU(v), CrystalMesh.v(v))`.

- [ ] **Step 4: O shader separa o sinal e desliga a aresta da diagonal**

Em `resonator_crystal.fsh`, troque a linha `vec3 bary = vec3(1.0 - facet.x - facet.y, facet.x, facet.y);` e as três seguintes até `float edge = ...;` por:

```glsl
    // o UV0 leva o canto da faceta e, somado de 8 por canto escondido mais um, qual canto tem a aresta oposta que é a
    // diagonal de uma face lateral: ela não é aresta do cristal e não acende
    float code = floor(facet.x * 0.125 + 0.02);
    vec2 corner = vec2(facet.x - 8.0 * code, facet.y);
    vec3 bary = vec3(1.0 - corner.x - corner.y, corner.x, corner.y);
    vec3 mask = vec3(1.0) - vec3(step(0.5, code) * step(code, 1.5), step(1.5, code) * step(code, 2.5), step(2.5, code));
    vec3 fw = fwidth(bary);
    vec3 e = max(smoothstep(vec3(0.0), fw * 1.3, bary), 1.0 - mask);
    float edge = 1.0 - min(min(e.x, e.y), e.z);
```

(`float small`, `warmEdge` e o resto ficam como estão.)

- [ ] **Step 5: Build, e2e e olhar**

```bash
./gradlew build --console=plain 2>&1 | tail -5
# e2e (comando das Restrições globais)
```

Expected: BUILD SUCCESSFUL; e2e `OK`; sem erro de shader no log. Abra `ressonador-cristais-estados.png` e `ressonador-camara-recarga-perto.png` e compare com as de `run/e2e-antes-cristais/`: o cristal é um prisma com as pontas, com as facetas laterais e as das pontas acesas só nas arestas reais (nenhuma linha atravessando uma face lateral na diagonal). Se a diagonal aparecer acesa, o código do canto está trocado: confira o `slot` da troca de ordem no `triangle`.

- [ ] **Step 6: Commit**

```bash
git add src
git commit -m "Ressonador: cristal como prisma de quartzo de 8 lados com as pontas chanfradas"
```

---

### Task 4: O shader da gema (o cristal vivo)

**Custo:** o fragment do cristal faz 3 amostras de `wa_fbm` de 2 oitavas (1 de longe, pelo bit longe), mais as derivadas da normal do objeto, dentro de poucos pixels por cristal (≤ 8). Sem mudança no Java. Prova: `camara plasma` do e2e até 1,5× a linha de base da Task 1, nas três situações.

**Files:**
- Modify: `src/main/resources/assets/wirelessautomate/shaders/core/resonator_crystal.fsh` (reescrito)

**Interfaces:**
- Consumes: o `wa_common.glsl` (`wa_fbm`, `wa_noise`, `wa_h21`, `wa_dither`) e o contrato do `CrystalGem` e do `CrystalParam`.
- Produces: o marcador `// ESTADOS` e `// FIM ESTADOS` no `main`, onde a Task 5 troca o corpo; os estados Opaco, recarga e rachado ficam, por enquanto, como estavam (cinza chapado, máscara de altura com a linha clara, rachado igual ao Opaco).

- [ ] **Step 1: Reescrever o `resonator_crystal.fsh`**

```glsl
#version 150

#moj_import <wirelessautomate:wa_common.glsl>

// A gema: o interior é fingido em camadas de ruído deslocadas para dentro ao longo da normal do objeto da faceta (a
// normal sai das derivadas das coordenadas do cristal, sem atributo novo), com um núcleo de energia visto através
// delas; duas luzes de estúdio e um reflexo de ambiente em gradiente; a borda se separa por canal (dispersão); as
// arestas reais e brilhos que piscam em arestas sorteadas. O ruído usa só as coordenadas do cristal (crystal.x, a
// altura e crystal.z), então não escorrega quando a câmera anda. Fora de sintonia, os veios aceleram e o cristal
// cintila em saltos. O parâmetro leva a semente (4 bits), o estado (10 bits) e, no bit 14, se o cristal está longe.

uniform float GameTime;

in vec3 rel;
in vec3 normal;
in vec2 facet;
in vec3 crystal;
flat in vec4 tint;
flat in int param;

out vec4 fragColor;

const float BOTTOM = -0.46;
const float TOP = 0.58;
const vec3 CENTER = vec3(0.0, 0.05, 0.0);
const vec3 DEEP = vec3(0.40, 0.067, 0.071);
const vec3 CORAL = vec3(0.949, 0.569, 0.478);
const vec3 HOT = vec3(1.0, 0.878, 0.8);
const vec3 STONE_DARK = vec3(0.149, 0.145, 0.141);
const vec3 STONE_LIGHT = vec3(0.361, 0.341, 0.322);
const vec3 STONE_EDGE = vec3(0.275, 0.265, 0.25);

void main() {
    float seed = float(param & 15) * 0.73 + 0.1;
    int state = (param >> 4) & 1023;
    bool far = ((param >> 14) & 1) != 0;
    bool dull = state == 1 || state > 1002;
    float rec = state >= 2 && state <= 1002 ? float(state - 2) / 1000.0 : -1.0;
    float mis = 1.0 - tint.a;
    float t = GameTime * 1200.0;
    vec3 tier = tint.rgb;

    vec3 N = normalize(normal);
    vec3 V = normalize(-rel);
    float h = clamp(crystal.y, 0.0, 1.0);
    vec3 obj = vec3(crystal.x, mix(BOTTOM, TOP, h), crystal.z);

    // a normal da faceta no espaço do cristal, para fora
    vec3 No = cross(dFdx(obj), dFdy(obj));
    float nl = length(No);
    No = nl > 1e-12 ? No / nl : normalize(obj - CENTER);
    if (dot(No, obj - CENTER) < 0.0) {
        No = -No;
    }

    // luz: duas de estúdio e o reflexo de ambiente
    float f = 1.0 - abs(dot(N, V));
    float cosv = max(abs(dot(N, V)), 0.25);
    vec3 L1 = normalize(vec3(0.35, 0.9, 0.4));
    vec3 L2 = normalize(vec3(-0.6, 0.25, -0.5));
    float diff = 0.30 + 0.50 * max(dot(N, L1), 0.0) + 0.28 * max(dot(N, L2), 0.0);
    vec3 R = reflect(-V, N);
    vec3 env = mix(vec3(0.05, 0.04, 0.06), CORAL * 0.55, smoothstep(-0.3, 0.9, R.y));
    env += HOT * (pow(max(dot(R, normalize(vec3(0.3, 0.8, -0.5))), 0.0), 18.0) * 0.55
                + pow(max(dot(R, normalize(vec3(-0.7, 0.4, 0.6))), 0.0), 30.0) * 0.35);

    // o interior: camadas de ruído em pontos deslocados para dentro, a de dentro mais fraca e mais quente
    vec3 inner = vec3(0.0);
    float coreSum = 0.0;
    float veinTop = 0.0;
    float weights = 0.0;
    for (int k = 0; k < 3; k++) {
        if (far && k > 0) break;
        float w = 1.0 / float(k + 1);
        vec3 p = obj - No * (0.07 * float(k + 1) / cosv);
        vec3 q = p * 3.6 + vec3(seed * 3.1, 0.0, seed * 1.7);
        float vein = wa_fbm(q - vec3(0.0, t * 0.35, 0.0), 2);
        if (mis > 0.01) {
            vein = mix(vein, wa_fbm(q - vec3(0.0, t * 1.75, 0.0), 2), mis);
        }
        if (k == 0) {
            veinTop = vein;
        }
        inner += mix(DEEP, CORAL, smoothstep(0.30, 0.75, vein)) * w;
        coreSum += exp(-dot(p.xz, p.xz) * 18.0) * (1.0 - smoothstep(0.0, 0.6, abs(p.y - 0.02))) * w;
        weights += w;
    }
    inner /= weights;
    coreSum /= weights;

    float pulse = 0.55 + 0.25 * sin(t * 2.0 + seed * 7.0);
    vec3 live = inner * (0.50 + 0.50 * diff);
    live += HOT * coreSum * pulse * (0.55 + 0.45 * tint.a);
    // a dispersão: a borda de cada canal com a sua potência, o vermelho mais largo e o azul mais estreito
    vec3 fres = vec3(pow(f, 1.8), pow(f, 2.5), pow(f, 3.4));
    live += mix(CORAL, tier, 0.35) * fres * 0.9;
    live += env * (0.20 + 0.65 * f);
    live *= 1.0 - mis * 0.6 * step(0.55, wa_h21(vec2(floor(t * 16.0), seed * 13.0)));

    // ESTADOS
    vec3 grey = mix(STONE_DARK, STONE_LIGHT, smoothstep(0.3, 0.7, veinTop)) * diff + vec3(0.5) * f * f * 0.18;
    float lit = dull ? 0.0 : 1.0;
    if (rec >= 0.0) {
        lit = step(h, rec);
    }
    vec3 col = mix(grey, live, lit);
    if (rec >= 0.0) {
        float front = (1.0 - smoothstep(0.0, 0.04, abs(h - rec))) * step(0.001, rec) * step(rec, 0.999);
        col += vec3(1.0, 0.72, 0.6) * front * 1.8;
    }
    // FIM ESTADOS

    // o UV0 leva o canto da faceta e, somado de 8 por canto escondido mais um, qual canto tem a aresta oposta que é a
    // diagonal de uma face lateral: ela não é aresta do cristal e não acende
    float code = floor(facet.x * 0.125 + 0.02);
    vec2 corner = vec2(facet.x - 8.0 * code, facet.y);
    vec3 bary = vec3(1.0 - corner.x - corner.y, corner.x, corner.y);
    vec3 mask = vec3(1.0) - vec3(step(0.5, code) * step(code, 1.5), step(1.5, code) * step(code, 2.5), step(2.5, code));
    vec3 fw = fwidth(bary);
    vec3 e = max(smoothstep(vec3(0.0), fw * 1.3, bary), 1.0 - mask);
    float edge = 1.0 - min(min(e.x, e.y), e.z);
    // a aresta tem largura em pixels: na gema pequena na tela ela cobriria a faceta inteira, então enfraquece
    float small = clamp(1.0 - 12.0 * max(max(fw.x, fw.y), fw.z), 0.35, 1.0);
    vec3 warmEdge = mix(CORAL, HOT, 0.5) * mix(vec3(1.0), tier * 1.6, 0.15);
    col += mix(STONE_EDGE, warmEdge, lit) * edge * 0.45 * small;
    // brilhos que piscam em arestas sorteadas: o código da faceta vem da normal do objeto
    float fid = wa_h21(vec2(dot(floor(No * 7.0 + 8.0), vec3(1.0, 17.0, 59.0)), seed * 13.0));
    float glint = step(0.8, wa_h21(vec2(fid * 97.0, floor(t * 5.0))));
    col += HOT * glint * edge * 1.4 * lit * small;
    col += HOT * pow(max(dot(reflect(-L1, N), V), 0.0), 56.0) * (0.25 + 0.45 * lit);
    // a curva no maior canal e a cor escalada junto: a matiz do coral não desbota quando a luz soma
    float m = max(max(col.r, col.g), col.b);
    col *= (1.0 - exp(-1.3 * m)) / max(m, 1e-4);
    fragColor = vec4(wa_dither(col), 1.0);
}
```

- [ ] **Step 2: Rodar o e2e e olhar**

Comando das Restrições globais, e `grep -i "resonator_crystal\|Error compiling\|wa_common" run/e2e/client.log` (esperado: nada). Abra `ressonador-camara-recarga-perto.png`, `ressonador-cristais-estados.png` e `ressonador-camara.png` (de longe, o bit longe) e compare com `run/e2e-antes-cristais/`. Confira: o cristal vivo tem profundidade (camadas e veios por dentro, núcleo claro no eixo), o reflexo e as bordas na cor do tier, os brilhos aparecem em algumas arestas, sem quadriculado nem banding, e de longe o cristal continua bom. Ajuste só constantes (a profundidade `0.07`, os pesos do `inner`, o `pulse`, as potências do `fres`, o `env`, o limiar do `glint`) em até 3 rodadas. Confira o `result.txt`: a linha `camara plasma` até 1,5× a da Task 1 nas três situações.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/assets/wirelessautomate/shaders/core/resonator_crystal.fsh
git commit -m "Ressonador: shader da gema com interior em camadas, núcleo e dispersão"
```

**Portão visual 1:** relate ao dono o antes e o depois do cristal vivo (capturas) antes da Task 5; se ele pedir ajuste de look, é nesta tarefa.

---

### Task 5: Os estados Opaco, recarga e rachado

**Custo:** o fragment ganha, só nos cristais Opacos, rachados ou em recarga, duas leituras de `wa_noise` (as fraturas) ou um hash de grade (poeira, bolhas): desprezível. Sem mudança no Java. Prova: `camara plasma` até 1,5× a base.

**Files:**
- Modify: `src/main/resources/assets/wirelessautomate/shaders/core/resonator_crystal.fsh` (o bloco `// ESTADOS` e duas funções novas)

**Interfaces:**
- Consumes: o marcador `// ESTADOS` … `// FIM ESTADOS` da Task 4 e as variáveis do `main` (`live`, `veinTop`, `diff`, `f`, `env`, `coreSum`, `obj`, `h`, `seed`, `t`, `rec`, `dull`, `state`).

- [ ] **Step 1: As funções do Opaco e das fraturas**

Antes de `void main()`, acrescente:

```glsl
// O Opaco: vidro fosco cinza, com poeira fixa no cristal e uma brasa quase apagada no núcleo.
vec3 dullColor(float veinTop, float diff, float f, vec3 env, float coreSum, vec3 obj, float seed, float t) {
    vec3 frost = mix(STONE_DARK, STONE_LIGHT, smoothstep(0.30, 0.70, veinTop)) * diff;
    frost += vec3(0.5) * f * f * 0.18 + env * 0.10;
    vec3 gc = floor(obj * 46.0);
    frost += vec3(0.6) * step(0.965, wa_h21(vec2(gc.x + gc.z * 57.0, gc.y + seed * 19.0))) * 0.12;
    frost += vec3(0.50, 0.13, 0.08) * coreSum * (0.10 + 0.05 * sin(t * 0.7 + seed * 5.0));
    return frost;
}

// As fraturas do rachado: fios finos (o ruído no limiar do zero), uma escala larga e outra fina.
float crackMask(vec3 obj, float seed) {
    float a = abs(wa_noise(obj * 5.5 + seed * 2.3));
    float b = abs(wa_noise(obj * 12.0 - seed));
    return max(1.0 - smoothstep(0.0, 0.03, a), (1.0 - smoothstep(0.0, 0.022, b)) * 0.6);
}
```

- [ ] **Step 2: O bloco de estados**

Troque o conteúdo entre `// ESTADOS` e `// FIM ESTADOS` (as linhas de `grey` até o `}` do `if (rec >= 0.0)` final) por:

```glsl
    vec3 dullCol = dullColor(veinTop, diff, f, env, coreSum, obj, seed, t);
    float lit = dull ? 0.0 : 1.0;
    vec3 col = lit > 0.5 ? live : dullCol;
    if (state > 1002) {
        // rachado: o Opaco com fraturas que brilham fraco nas frestas
        float cr = crackMask(obj, seed);
        col = col * (1.0 - 0.45 * cr) + vec3(0.95, 0.16, 0.08) * cr * (0.55 + 0.45 * sin(t * 1.3 + seed * 3.0)) * 0.8;
    }
    if (rec >= 0.0) {
        // recarga: um nível de luz líquida com menisco ondulado e bolhas que sobem; acima dele, o Opaco
        float ripple = 0.014 * sin(obj.x * 38.0 + t * 6.0) * sin(obj.z * 38.0 - t * 5.0);
        float level = rec + ripple;
        lit = 1.0 - smoothstep(-0.012, 0.012, h - level);
        float meniscus = exp(-abs(h - level) * 55.0) * step(0.001, rec) * step(rec, 0.999);
        vec3 bc = obj * vec3(16.0, 11.0, 16.0) + vec3(0.0, -t * 1.3, 0.0);
        vec3 bid = floor(bc);
        float bubble = step(0.93, wa_h21(vec2(bid.x + bid.z * 57.0, bid.y)))
            * (1.0 - smoothstep(0.0, 0.28, length(fract(bc) - 0.5)));
        col = mix(dullCol, live * 1.1, lit) + HOT * meniscus * 1.2 + HOT * bubble * lit * 0.9;
    }
```

(`vec3 grey` some. O resto do `main` usa `lit` para as arestas e o brilho: ele agora é o da recarga ou 0/1.)

- [ ] **Step 3: Rodar o e2e e olhar os quatro estados**

Comando das Restrições globais e `grep` de erro de shader (nada). Abra `ressonador-cristais-estados.png` (vivo, Opaco, rachado), `ressonador-camara-recarga-perto.png` (o enchimento em curso, com o menisco e as bolhas), `ressonador-camara-recarga.png` e `ressonador-camara-rachado.png`. Confira: o Opaco é vidro fosco com poeira e uma brasa fraca (não um cinza chapado); o rachado tem fraturas visíveis, com um brilho vermelho fraco, e se distingue do Opaco; a recarga mostra a luz líquida subindo, com a borda do nível ondulando e bolhas dentro; acima do nível o Opaco. Ajuste constantes (a espessura e a escala das fraturas, a força do brilho vermelho, a altura da ondulação, a densidade das bolhas) em até 3 rodadas. `camara plasma` até 1,5× a base.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/assets/wirelessautomate/shaders/core/resonator_crystal.fsh
git commit -m "Ressonador: Opaco fosco, recarga com menisco e rachado com fraturas no cristal"
```

**Portão visual 2:** relate ao dono os quatro estados (antes e depois) antes da Task 6.

---

### Task 6: A aura em estrela

**Custo:** a aura é o mesmo quad de antes por cristal vivo (≤ 8) e só dentro de `RANGE_SQ`; o fragment troca uma exponencial por um halo e uma estrela (4 exponenciais) e um `wa_noise` fora de sintonia. Sem mudança de contrato. Prova: `camara plasma` até 1,5× a base.

**Files:**
- Modify: `src/main/resources/assets/wirelessautomate/shaders/core/resonator_ribbon.fsh` (o modo 2)
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/client/resonator/render/CrystalAura.java:16` (`SIZE`)

**Interfaces:**
- Consumes: o parâmetro do modo 2 de hoje (a sintonia de 0 a 100 nos 7 bits de baixo e a semente nos 4 seguintes, `CrystalAura`), `t`, `tier`, `CORAL`, `WHITE`, `wa_noise`.

- [ ] **Step 1: O modo 2 novo**

Em `resonator_ribbon.fsh`, troque o bloco `} else if (mode == 2) { ... }` (do comentário "aura do cristal" até a linha `col *= (1.0 - smoothstep(0.8, 1.0, r));`) por:

```glsl
    } else if (mode == 2) {
        // aura do cristal: um halo suave e uma estrela de quatro pontas que gira devagar e pulsa com a carga; fora de
        // sintonia a estrela encolhe e treme. O parâmetro leva a sintonia (7 bits) e a semente do cristal (4 bits)
        float seed = floor(arg / 128.0);
        float tuneV = clamp((arg - seed * 128.0) / 100.0, 0.0, 1.0);
        float mis = 1.0 - tuneV;
        vec2 pp = quad * 2.0 - 1.0;
        float r = length(pp);
        vec3 base = mix(CORAL, tier, 0.3);
        float pulse = 0.6 + 0.4 * sin(t * 4.0 + seed * 2.4);
        float halo = exp(-r * r * 7.0) * pulse;
        float ang = t * 0.25 + seed;
        vec2 rp = vec2(cos(ang) * pp.x + sin(ang) * pp.y, -sin(ang) * pp.x + cos(ang) * pp.y);
        float reach = mix(0.45, 1.0, tuneV);
        float arm = exp(-abs(rp.y) * 42.0) * exp(-abs(rp.x) * 3.0 / reach)
            + exp(-abs(rp.x) * 42.0) * exp(-abs(rp.y) * 3.0 / reach);
        arm *= 0.6 * (0.3 + 0.7 * tuneV) * pulse;
        float jitter = 1.0 + mis * 0.6 * wa_noise(vec3(t * 9.0, seed * 3.7, 0.5)) * 1.2;
        col += base * halo * 0.55 * jitter + mix(base, WHITE, 0.5) * arm * jitter;
        col *= (1.0 - smoothstep(0.8, 1.0, r));
```

(o `}` seguinte, que abre o `else` do modo 3, já existe; não o duplique).

- [ ] **Step 2: O quad da aura um pouco maior**

Em `CrystalAura.java`, `private static final float SIZE = 1.0f;` vira `1.3f` (as pontas da estrela precisam de espaço; o fragment já zera na borda).

- [ ] **Step 3: Rodar o e2e e olhar**

Comando das Restrições globais, sem erro de shader. Abra `ressonador-camara.png`, `ressonador-camara-recarga-perto.png` e `ressonador-camara-chiado.png` (fora de sintonia): cada cristal vivo tem um halo suave com uma estrela de quatro pontas, que parece um brilho de gema e não uma mancha; fora de sintonia a estrela é menor e instável; o Opaco não tem aura. Ajuste o comprimento das pontas (`reach`), o peso (`0.6`) e o halo (`0.55`) em até 3 rodadas. `camara plasma` até 1,5× a base.

- [ ] **Step 4: Commit**

```bash
git add src/main
git commit -m "Ressonador: aura do cristal como halo com estrela de quatro pontas"
```

---

### Task 7: Fechamento

**Custo:** nenhum no jogo; confere o orçamento e a documentação.

**Files:**
- Modify: `CLAUDE.md` (a linha `client/resonator/render/`)
- Modify: `docs/progresso.md` (a linha do Ressonador na tabela, o próximo passo e o histórico)

- [ ] **Step 1: Varreduras**

```bash
python -I .claude/skills/wa-performance/scripts/varrer_perf.py
python -I .claude/skills/wa-comentarios/scripts/varrer.py
```

Resolva ou justifique cada candidato nos arquivos mudados (`CrystalMesh`, `CrystalGem`, `CrystalParam`, `ChamberCrystals`, `CrystalAura`, `PlasmaCore`, os dois `E2eResonator*Steps`).

- [ ] **Step 2: Medida final e build**

Rode o e2e (comando das Restrições globais) e compare o `result.txt` com `run/e2e-antes-cristais/result.txt`: as linhas `camara plasma` (parado, com conduto, com espiral) até 1,5× a base; a linha `camara antiga` igual, dentro do ruído (o caminho antigo não mudou). Depois:

```bash
./gradlew build --console=plain 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL, com o `CrystalParamTest` e o `CrystalMeshTest` entre os JUnit. Não há GameTest novo (sem lógica de servidor).

- [ ] **Step 3: Docs**

No `CLAUDE.md`, na linha `client/resonator/render/`, onde fala do `CrystalMesh`: troque "a bipirâmide de 6 lados do `CrystalMesh` (12 triângulos, montada uma vez) escrita pelo `CrystalGem` no buffer (UV1 e UV2 `short`, tudo abaixo de 32768) e enchendo de baixo para cima na recarga" por o prisma de quartzo de 8 lados do `CrystalMesh` (32 triângulos, com o sinal por triângulo que desliga a aresta da diagonal, montado uma vez) escrito pelo `CrystalGem` no buffer (UV1 e UV2 `short`, tudo abaixo de 32768, o parâmetro pelo `CrystalParam`: semente, estado, que inclui o rachado, e o bit "longe"); diga que o shader `resonator_crystal` faz o interior em camadas, o núcleo, a dispersão, os brilhos e os quatro estados (a recarga como nível de luz líquida), e que a aura (modo 2 das fitas) é um halo com estrela de quatro pontas. No `docs/progresso.md`: a linha do Ressonador na tabela, o próximo passo (a Parte B, energia, spec `docs/superpowers/specs/2026-10-23-ressonador-energia-raio-design.md`; depois C e D) e uma linha de 2026-10-23 no histórico, com os tempos de antes e depois.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md docs/progresso.md
git commit -m "Ressonador: docs do cristal como gema"
```

---

## Autorrevisão

- **Cobertura da spec:** malha de 8 lados e sinal da diagonal (Task 3); parâmetro com o estado rachado e o bit longe, `CrystalParam` com JUnit (Task 2); normal da faceta pelas derivadas, luz de estúdio, interior em camadas, núcleo, dispersão, brilhos e o dither (Task 4); Opaco, recarga como luz líquida, rachado com fraturas (Task 5); aura em estrela (Task 6); `NEAR_SQ` e o bit longe, 1 amostra de longe (Tasks 2 e 4); custo medido pelo e2e e as varreduras (Task 7); captura dos quatro estados (Task 1); docs (Task 7); caminho antigo intacto, conferido pela linha `camara antiga` (Task 7).
- **Sem marcadores pendentes:** todo passo de código traz o código; as constantes de look são ajuste declarado, com limite de rodadas e portões do dono.
- **Consistência de nomes:** `CrystalParam.pack/seed/state/far/recharging`, `CrystalGem.render(..., int seed, boolean far)`, `CrystalMesh.hidden/encodedU`, os marcadores `// ESTADOS` e `// FIM ESTADOS` e o `PlasmaCore.NEAR_SQ` têm os mesmos nomes em todas as tarefas.
