package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

/**
 * As formas da Armilar contra os assets gravados, sem passar pelo gerador: lê os blockstates dos sete blocos e os
 * modelos que eles apontam, gira os elementos pelo {@code y} da variante com a conta própria deste teste e confere
 * que cada elemento do modelo cabe na forma do Java. Pega erro de índice e de giro entre o Java e os assets.
 * (A conferência contrária, toda caixa toca um elemento, é do gerador: os elementos internos sem face não estão
 * no JSON.)
 */
class ArmilarShapesTest {
    private static final Path ASSETS = Path.of(System.getProperty("user.dir"), "src", "main", "resources", "assets",
            "wirelessautomate");
    private static final String PREFIXO = "wirelessautomate:block/";
    private static final List<String> BLOCOS = List.of("resonator_casing", "resonator_column", "resonator_cradle",
            "resonance_core", "resonator_energy_port", "resonator_crystal_port", "resonator_tuner");
    /** Folga numérica da conta em ponto flutuante, em blocos (bem abaixo de 1 px). */
    private static final double FOLGA = 1e-6;

    @Test
    void todoElementoDoModeloCabeNaFormaDoEstado() throws IOException {
        int conferidas = 0;
        for (String bloco : BLOCOS) {
            for (Map.Entry<String, JsonElement> variante : variantes(bloco).entrySet()) {
                JsonObject dados = variante.getValue().getAsJsonObject();
                int y = dados.has("y") ? dados.get("y").getAsInt() : 0;
                assertFalse(dados.has("x"), bloco + " " + variante.getKey() + ": giro em x não tem forma");
                VoxelShape forma = ArmilarShapes.forma(bloco, variante.getKey());
                for (AABB caixa : caixasDoModelo(dados.get("model").getAsString(), y)) {
                    assertTrue(cabe(caixa, forma),
                            bloco + " " + variante.getKey() + ": elemento " + caixa + " fora da forma");
                }
                conferidas++;
            }
        }
        assertTrue(conferidas >= 150, "variantes conferidas: " + conferidas);
    }

    @Test
    void aFormaCabeNoBloco() throws IOException {
        for (String nome : BLOCOS) {
            for (String chave : variantes(nome).keySet()) {
                VoxelShape forma = ArmilarShapes.forma(nome, chave);
                assertFalse(forma.isEmpty(), nome + " " + chave + ": forma vazia");
                AABB limites = forma.bounds();
                assertTrue(limites.minX >= 0 && limites.minY >= 0 && limites.minZ >= 0
                        && limites.maxX <= 1 && limites.maxY <= 1 && limites.maxZ <= 1,
                        nome + " " + chave + ": forma fora do bloco " + limites);
            }
        }
    }

    @Test
    void lajeMontadaNoCantoNaoECuboCheio() {
        for (String frente : List.of("north", "east", "south", "west")) {
            VoxelShape canto = ArmilarShapes.forma("resonator_casing", "facing=" + frente + ",position=p00");
            assertNotEquals(Shapes.block(), canto, "a Laje p00 de frente " + frente + " é cubo cheio");
        }
        assertNotEquals(Shapes.block(), ArmilarShapes.forma("resonator_column", "corner=se,part=lower"));
        assertNotEquals(Shapes.block(), ArmilarShapes.forma("resonator_cradle", "part=upper"));
    }

    private static JsonObject variantes(String bloco) throws IOException {
        try (Reader leitor = Files.newBufferedReader(ASSETS.resolve("blockstates/" + bloco + ".json"))) {
            return JsonParser.parseReader(leitor).getAsJsonObject().getAsJsonObject("variants");
        }
    }

    /** A caixa envolvente de cada elemento do modelo, em blocos, já girada `y` graus (conta própria deste teste). */
    private static List<AABB> caixasDoModelo(String modelo, int y) throws IOException {
        Path arquivo = ASSETS.resolve("models/block/" + modelo.substring(PREFIXO.length()) + ".json");
        JsonArray elementos;
        try (Reader leitor = Files.newBufferedReader(arquivo)) {
            elementos = JsonParser.parseReader(leitor).getAsJsonObject().getAsJsonArray("elements");
        }
        List<AABB> caixas = new ArrayList<>();
        for (JsonElement e : elementos) {
            caixas.add(girada(envolvente(e.getAsJsonObject()), y));
        }
        return caixas;
    }

    /** Os oito cantos do elemento, girados pela `rotation` dele (se houver), e a caixa que os envolve, em pixels. */
    private static double[] envolvente(JsonObject elemento) {
        double[] de = numeros(elemento.getAsJsonArray("from"));
        double[] ate = numeros(elemento.getAsJsonArray("to"));
        JsonObject rotacao = elemento.has("rotation") ? elemento.getAsJsonObject("rotation") : null;
        double[] caixa = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
                -Double.MAX_VALUE};
        for (int i = 0; i < 8; i++) {
            double[] p = {(i & 1) == 0 ? de[0] : ate[0], (i & 2) == 0 ? de[1] : ate[1], (i & 4) == 0 ? de[2] : ate[2]};
            if (rotacao != null) {
                p = rotacionar(p, rotacao);
            }
            for (int k = 0; k < 3; k++) {
                caixa[k] = Math.min(caixa[k], p[k]);
                caixa[k + 3] = Math.max(caixa[k + 3], p[k]);
            }
        }
        return caixa;
    }

    private static double[] rotacionar(double[] ponto, JsonObject rotacao) {
        double[] origem = numeros(rotacao.getAsJsonArray("origin"));
        double angulo = Math.toRadians(rotacao.get("angle").getAsDouble());
        double c = Math.cos(angulo);
        double s = Math.sin(angulo);
        double x = ponto[0] - origem[0];
        double y = ponto[1] - origem[1];
        double z = ponto[2] - origem[2];
        switch (rotacao.get("axis").getAsString()) {
            case "x" -> {
                double ny = y * c - z * s;
                z = y * s + z * c;
                y = ny;
            }
            case "y" -> {
                double nx = x * c + z * s;
                z = -x * s + z * c;
                x = nx;
            }
            default -> {
                double nx = x * c - y * s;
                y = x * s + y * c;
                x = nx;
            }
        }
        return new double[] {x + origem[0], y + origem[1], z + origem[2]};
    }

    /** Gira a caixa (pixels) `y` graus no sentido do blockstate: (x, z) vai a (16 - z, x) a cada 90; sai em blocos. */
    private static AABB girada(double[] caixa, int y) {
        double x0 = caixa[0];
        double z0 = caixa[2];
        double x1 = caixa[3];
        double z1 = caixa[5];
        for (int giro = 0; giro < y / 90; giro++) {
            double novoX0 = 16 - z1;
            double novoX1 = 16 - z0;
            z0 = x0;
            z1 = x1;
            x0 = novoX0;
            x1 = novoX1;
        }
        return new AABB(x0 / 16, caixa[1] / 16, z0 / 16, x1 / 16, caixa[4] / 16, z1 / 16);
    }

    private static double[] numeros(JsonArray lista) {
        double[] saida = new double[lista.size()];
        for (int i = 0; i < saida.length; i++) {
            saida[i] = lista.get(i).getAsDouble();
        }
        return saida;
    }

    /** A caixa está inteira dentro da forma (nada dela sobra fora). */
    private static boolean cabe(AABB caixa, VoxelShape forma) {
        return !Shapes.joinIsNotEmpty(Shapes.create(caixa.deflate(FOLGA)), forma, BooleanOp.ONLY_FIRST);
    }
}
