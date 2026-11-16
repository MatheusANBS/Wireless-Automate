package io.github.matheusanbs.wirelessautomate.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Os ganchos de evento que desenham os efeitos da {@link FxQueue}.
 * <ul>
 *   <li>Sem shaderpack: no estágio {@code AFTER_TRANSLUCENT_BLOCKS}, depois da água e do vidro, o buffer é desenhado
 *   na hora.</li>
 *   <li>Com shaderpack do Iris: no mesmo estágio os jobs só enchem o buffer; no {@code AFTER_LEVEL} a profundidade do
 *   alvo principal é guardada ({@link FxDepth}); depois da composição do pacote, no {@code RenderGuiEvent.Pre},
 *   a profundidade volta, as matrizes do quadro são reaplicadas e o buffer sai por cima da imagem final.</li>
 *   <li>A luz no mundo ({@link FxLightPass}): sem pacote, no {@code AFTER_LEVEL}; com pacote, no gancho tardio, logo
 *   depois do buffer, e a de {@code AFTER_LEVEL} fica desligada para não sair dobrada.</li>
 *   <li>Nada de efeito na passada de sombra: quem anota na fila confere {@link ShaderPackCheck#shadowPass()}, e este
 *   gancho nem toca na fila durante ela.</li>
 * </ul>
 * <p>
 * A decisão do gancho tardio (Passo 1 da Tarefa 3): é o evento {@code RenderGuiEvent.Pre}, sem mixin. A evidência
 * está no NeoForge 21.1.248: {@code GuiLayerManager.render} posta o {@code RenderGuiEvent.Pre} e só depois percorre as
 * camadas, e o {@code options.hideGui} não entra aí: ele só liga e desliga as camadas (as lambdas
 * {@code () -> !minecraft.options.hideGui} em {@code Gui.<init>}). O {@code GameRenderer.render} chama o
 * {@code gui.render} sempre que há mundo, também com o HUD escondido, e isso é depois do {@code renderLevel} (e do
 * {@code finalizeLevelRendering} do Iris, que fecha o quadro no fim do {@code LevelRenderer.renderLevel}) e antes do
 * HUD. Então o e2e, que liga {@code hideGui} nas capturas, também vê os efeitos.
 * <p>
 * Fabulous: com os gráficos Fabulous o alvo aberto em {@code AFTER_TRANSLUCENT_BLOCKS} é o translúcido, e não o
 * principal. Isso ainda não foi testado; fica para a rodada da Tarefa 11.
 * <p>
 * O alvo principal tem a profundidade limpa logo antes do gancho ({@code RenderSystem.clear(256)} do
 * {@code GameRenderer.render}); por isso o gancho a restaura e, no fim, limpa de novo, para o HUD não ser testado
 * contra a profundidade do mundo.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class FxStages {
    /** O corte dos efeitos: a 48 blocos, ao quadrado. */
    private static final double CULL_SQ = 48.0 * 48.0;

    private static final FxContext CTX = new FxContext();
    private static FxBatch batch;
    /** Com pacote: o buffer tem dados e a profundidade foi guardada, então o gancho tardio desenha. */
    private static boolean latePending;

    private FxStages() {
    }

    /** O contexto do quadro (os campos valem depois do estágio dos efeitos). */
    public static FxContext context() {
        return CTX;
    }

    /**
     * O começo de cada quadro, antes de quem anota na fila (a sonda e os BERs): a fila e o buffer tardio do quadro
     * anterior não valem mais. Cobre o estágio que não veio, a guarda de sombra e o pacote desligado com dados
     * guardados: nada de vértice velho desenhado no quadro seguinte.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onFrameStart(RenderFrameEvent.Pre event) {
        FxQueue.clear();
        FxLights.clear();
        dropLate();
    }

    private static void dropLate() {
        latePending = false;
        if (batch != null && batch.hasData()) {
            batch.close();
            batch = null;
        }
    }

    @SubscribeEvent
    static void onLevelStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            fillOrDraw(event);
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (ShaderPackCheck.active()) {
                saveForLate(event);
            } else {
                drawLights(event);
            }
        }
    }

    /** Sem pacote: a passada de luz no mundo sobre o quadro todo, no último estágio do mundo. */
    private static void drawLights(RenderLevelStageEvent event) {
        if (FxLights.count() == 0) {
            return;
        }
        CTX.copyMatrices(event.getModelViewMatrix(), event.getProjectionMatrix());
        FxLightPass.apply(CTX);
        FxLights.clear();
    }

    /** Encher o buffer com a fila; sem pacote, desenhar já. */
    private static void fillOrDraw(RenderLevelStageEvent event) {
        if (FxQueue.isEmpty()) {
            return;
        }
        if (ShaderPackCheck.shadowPass()) {
            // a passada de sombra não recebe efeito; a fila é da passada do mundo
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !FxShaders.ready()) {
            FxQueue.clear();
            return;
        }
        if (batch == null) {
            batch = new FxBatch();
        }
        CTX.begin(event.getCamera(), event.getModelViewMatrix(), event.getProjectionMatrix(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false), level.getGameTime());
        for (int i = 0; i < FxQueue.size(); i++) {
            FxJob job = FxQueue.get(i);
            if (job.distanceSq(CTX) <= CULL_SQ) {
                job.draw(CTX, batch.source());
            }
        }
        FxQueue.clear();
        if (!ShaderPackCheck.active()) {
            batch.flush();
        }
    }

    /** Com pacote e dados no buffer ou luzes: guarda a profundidade e as matrizes, no último estágio do mundo. */
    private static void saveForLate(RenderLevelStageEvent event) {
        if (!lateWanted() || !ShaderPackCheck.active() || ShaderPackCheck.shadowPass()) {
            return;
        }
        FxDepth.save();
        CTX.copyMatrices(event.getModelViewMatrix(), event.getProjectionMatrix());
        latePending = true;
    }

    /** O gancho tardio tem o que fazer: dados no buffer ou luzes anotadas. */
    private static boolean lateWanted() {
        return (batch != null && batch.hasData()) || FxLights.count() > 0;
    }

    /** O gancho tardio: depois do pacote e antes do HUD, o buffer por cima da imagem, com a profundidade do mundo. */
    @SubscribeEvent
    static void onGuiPre(RenderGuiEvent.Pre event) {
        if (!latePending) {
            return;
        }
        latePending = false;
        if (!lateWanted() || ShaderPackCheck.shadowPass()) {
            return;
        }
        if (!ShaderPackCheck.active()) {
            // o pacote foi desligado com dados guardados: o lote e as luzes não valem mais
            FxLights.clear();
            dropLate();
            return;
        }
        FxDepth.restore();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        RenderSystem.backupProjectionMatrix();
        // a matriz é a do contexto (estática, nada nosso é alocado); a cópia por chamada é do próprio RenderSystem
        RenderSystem.setProjectionMatrix(CTX.projection, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(CTX.modelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        try {
            if (batch != null && batch.hasData()) {
                batch.flush();
            }
            // a luz vai depois do buffer, sobre a imagem final, com a profundidade que o saveForLate guardou
            FxLightPass.apply(CTX);
            FxLights.clear();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            // a profundidade do mundo não vale para o HUD
            RenderSystem.depthMask(true);
            RenderSystem.clear(256, Minecraft.ON_OSX);
            // o estado com que o vanilla entra no gancho: o clear do tipo de desenho desligou o teste de profundidade
            // e deixou o cull, o blend e a textura ativa como o fim de um RenderType; o resto (máscara) já voltou
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        }
    }
}
