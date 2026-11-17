package io.github.matheusanbs.wirelessautomate.resonator;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Gerado por scripts/textures/armilar_formas.py (gerar_texturas.py). Não edite.
 *
 * <p>As formas de colisão e seleção dos sete blocos da Armilar, soltos ou montados, tiradas do desenho de cada
 * modelo base: as caixas de cada um em pixels ({@link #BASES}) e, de cada variante dos blockstates, o modelo e o
 * giro em y ({@link #ESTADOS}, do mesmo dado que escreve os blockstates). O giro é o do blockstate: {@code y} de
 * 90 leva (x, z) a (16 - z, x). As formas giradas são montadas uma vez, na carga da classe; cada bloco monta a
 * tabela dos seus estados no construtor e o {@code getShape} só a consulta.
 */
final class ArmilarShapes {
    static final String CASING = "resonator_casing";
    static final String COLUMN = "resonator_column";
    static final String CRADLE = "resonator_cradle";
    static final String ENERGY_PORT = "resonator_energy_port";
    static final String CRYSTAL_PORT = "resonator_crystal_port";
    static final String TUNER = "resonator_tuner";
    static final String RESONANCE_CORE = "resonance_core";

    /** Os modelos base (`nome`, depois as caixas `x0,y0,z0,x1,y1,z1` em pixels, separadas por `;`). */
    private static final String[] BASES = {
        "resonator_casing_loose=0,0,0,16,16,16",
        "resonator_column_loose=1,0,1,15,3,15;4,3,4,12,16,12;2,13,2,14,16,4;2,13,4,4,16,14;12,13,4,14,16,14;4,13,12,12,16,14",
        "resonator_cradle_loose=1,0,1,15,3,15;5,3,5,11,12,11;2,10,2,14,11,5;2,10,5,5,11,14;11,10,5,14,11,14;5,10,11,11,11,14;2,11,2,14,14,3;2,11,3,3,14,14;13,11,3,14,14,14;3,11,13,13,14,14",
        "resonance_core_basic_loose=0,0,1,16,16,16;4,4,0,12,12,1",
        "resonator_energy_port_loose=0,0,2,16,16,16;2,6,0,5,10,2;11,6,0,14,10,2",
        "resonator_crystal_port_loose=0,0,2,16,16,16;4,4,1,12,12,2;12,6,1,13,10,2",
        "resonator_tuner_loose=0,0,2,16,16,16;3,3,1,13,13,2;7,7,0,9,9,1",
        "armilar/casing_p00=10,0,0,16,3,16;8,0,2,10,3,16;6,0,4,8,3,16;4,0,6,6,3,16;2,0,8,4,3,16;0,0,10,2,3,16;12,3,2,16,14,16;10,3,4,12,14,16;8,3,6,10,14,16;6,3,8,8,14,16;4,3,10,6,14,16;2,3,12,4,14,16;14,14,4,16,16,16;12,14,6,14,16,16;10,14,8,12,16,16;8,14,10,10,16,16;6,14,12,8,16,16;4,14,14,6,16,16",
        "armilar/casing_p01=0,0,0,16,3,16;2,3,0,16,14,16;4,14,0,16,16,16",
        "armilar/casing_p04=0,0,0,16,3,6;2,0,6,16,3,8;4,0,8,16,3,10;6,0,10,16,3,12;8,0,12,16,3,14;10,0,14,16,3,16;2,3,0,16,14,4;4,3,4,16,14,6;6,3,6,16,14,8;8,3,8,16,14,10;10,3,10,16,14,12;12,3,12,16,14,14;4,14,0,16,16,2;6,14,2,16,16,4;8,14,4,16,16,6;10,14,6,16,16,8;12,14,8,16,16,10;14,14,10,16,16,12",
        "armilar/casing_p10=0,0,0,16,3,16;0,3,2,16,14,16;0,14,4,16,16,16",
        "armilar/casing_p14=0,0,0,16,3,16;0,3,0,16,14,14;0,14,0,16,16,12",
        "armilar/casing_p40=0,0,0,6,3,16;6,0,2,8,3,16;8,0,4,10,3,16;10,0,6,12,3,16;12,0,8,14,3,16;14,0,10,16,3,16;0,3,2,4,14,16;4,3,4,6,14,16;6,3,6,8,14,16;8,3,8,10,14,16;10,3,10,12,14,16;12,3,12,14,14,16;0,14,4,2,16,16;2,14,6,4,16,16;4,14,8,6,16,16;6,14,10,8,16,16;8,14,12,10,16,16;10,14,14,12,16,16",
        "armilar/casing_p41=0,0,0,16,3,16;0,3,0,14,14,16;0,14,0,12,16,16",
        "armilar/casing_p44=0,0,0,16,3,6;0,0,6,14,3,8;0,0,8,12,3,10;0,0,10,10,3,12;0,0,12,8,3,14;0,0,14,6,3,16;0,3,0,14,14,4;0,3,4,12,14,6;0,3,6,10,14,8;0,3,8,8,14,10;0,3,10,6,14,12;0,3,12,4,14,14;0,14,0,12,16,2;0,14,2,10,16,4;0,14,4,8,16,6;0,14,6,6,16,8;0,14,8,4,16,10;0,14,10,2,16,12",
        "armilar/column_lower=12,0,12,16,16,16",
        "armilar/cradle_lower=4,0,2,12,2,14;3,0,3,4,2,13;12,0,3,13,2,13;2,0,4,3,2,12;13,0,4,14,2,12;5,2,5,11,16,11",
        "armilar/column_upper=12,0,12,16,14,16",
        "armilar/cradle_upper=5,0,4,11,4,12;4,0,5,5,4,11;11,0,5,12,4,11;3,2,3,13,6,4;3,2,4,5,4,5;11,2,4,13,4,5;3,2,5,4,6,13;12,2,5,13,6,13;4,2,11,5,4,13;11,2,11,12,4,13;5,2,12,11,6,13;3,4,4,4,6,5;12,4,4,13,6,5;4,4,12,5,6,13;11,4,12,12,6,13",
        "armilar/core_basic=0,0,0,16,3,16;0,3,0,16,14,14;0,5,14,16,10,15;0,14,0,16,16,12",
        "armilar/energy_port=0,0,0,16,3,16;0,3,0,16,14,14;2,6,14,6,10,16;10,6,14,14,10,16;0,14,0,16,16,12",
        "armilar/crystal_port=0,0,0,16,3,16;0,3,0,16,14,14;1,4,14,15,11,15;5,7,15,11,8,16;0,14,0,16,16,12",
        "armilar/tuner=0,0,0,16,3,16;0,3,0,16,14,14;4,4,14,12,12,15;0,14,0,16,16,12"
    };

    /** As variantes dos blockstates: `bloco|chave|modelo|y`. */
    private static final String[] ESTADOS = {
        "resonator_casing|facing=south,position=loose|resonator_casing_loose|0",
        "resonator_casing|facing=south,position=p00|armilar/casing_p00|0",
        "resonator_casing|facing=south,position=p01|armilar/casing_p01|0",
        "resonator_casing|facing=south,position=p03|armilar/casing_p01|0",
        "resonator_casing|facing=south,position=p04|armilar/casing_p04|0",
        "resonator_casing|facing=south,position=p10|armilar/casing_p10|0",
        "resonator_casing|facing=south,position=p11|resonator_casing_loose|0",
        "resonator_casing|facing=south,position=p12|resonator_casing_loose|0",
        "resonator_casing|facing=south,position=p13|resonator_casing_loose|0",
        "resonator_casing|facing=south,position=p14|armilar/casing_p14|0",
        "resonator_casing|facing=south,position=p20|armilar/casing_p10|0",
        "resonator_casing|facing=south,position=p21|resonator_casing_loose|0",
        "resonator_casing|facing=south,position=p22|resonator_casing_loose|0",
        "resonator_casing|facing=south,position=p23|resonator_casing_loose|0",
        "resonator_casing|facing=south,position=p30|armilar/casing_p10|0",
        "resonator_casing|facing=south,position=p31|resonator_casing_loose|0",
        "resonator_casing|facing=south,position=p32|resonator_casing_loose|0",
        "resonator_casing|facing=south,position=p33|resonator_casing_loose|0",
        "resonator_casing|facing=south,position=p34|armilar/casing_p14|0",
        "resonator_casing|facing=south,position=p40|armilar/casing_p40|0",
        "resonator_casing|facing=south,position=p41|armilar/casing_p41|0",
        "resonator_casing|facing=south,position=p43|armilar/casing_p41|0",
        "resonator_casing|facing=south,position=p44|armilar/casing_p44|0",
        "resonator_casing|facing=west,position=loose|resonator_casing_loose|0",
        "resonator_casing|facing=west,position=p00|armilar/casing_p00|90",
        "resonator_casing|facing=west,position=p01|armilar/casing_p01|90",
        "resonator_casing|facing=west,position=p03|armilar/casing_p01|90",
        "resonator_casing|facing=west,position=p04|armilar/casing_p04|90",
        "resonator_casing|facing=west,position=p10|armilar/casing_p10|90",
        "resonator_casing|facing=west,position=p11|resonator_casing_loose|90",
        "resonator_casing|facing=west,position=p12|resonator_casing_loose|90",
        "resonator_casing|facing=west,position=p13|resonator_casing_loose|90",
        "resonator_casing|facing=west,position=p14|armilar/casing_p14|90",
        "resonator_casing|facing=west,position=p20|armilar/casing_p10|90",
        "resonator_casing|facing=west,position=p21|resonator_casing_loose|90",
        "resonator_casing|facing=west,position=p22|resonator_casing_loose|90",
        "resonator_casing|facing=west,position=p23|resonator_casing_loose|90",
        "resonator_casing|facing=west,position=p30|armilar/casing_p10|90",
        "resonator_casing|facing=west,position=p31|resonator_casing_loose|90",
        "resonator_casing|facing=west,position=p32|resonator_casing_loose|90",
        "resonator_casing|facing=west,position=p33|resonator_casing_loose|90",
        "resonator_casing|facing=west,position=p34|armilar/casing_p14|90",
        "resonator_casing|facing=west,position=p40|armilar/casing_p40|90",
        "resonator_casing|facing=west,position=p41|armilar/casing_p41|90",
        "resonator_casing|facing=west,position=p43|armilar/casing_p41|90",
        "resonator_casing|facing=west,position=p44|armilar/casing_p44|90",
        "resonator_casing|facing=north,position=loose|resonator_casing_loose|0",
        "resonator_casing|facing=north,position=p00|armilar/casing_p00|180",
        "resonator_casing|facing=north,position=p01|armilar/casing_p01|180",
        "resonator_casing|facing=north,position=p03|armilar/casing_p01|180",
        "resonator_casing|facing=north,position=p04|armilar/casing_p04|180",
        "resonator_casing|facing=north,position=p10|armilar/casing_p10|180",
        "resonator_casing|facing=north,position=p11|resonator_casing_loose|180",
        "resonator_casing|facing=north,position=p12|resonator_casing_loose|180",
        "resonator_casing|facing=north,position=p13|resonator_casing_loose|180",
        "resonator_casing|facing=north,position=p14|armilar/casing_p14|180",
        "resonator_casing|facing=north,position=p20|armilar/casing_p10|180",
        "resonator_casing|facing=north,position=p21|resonator_casing_loose|180",
        "resonator_casing|facing=north,position=p22|resonator_casing_loose|180",
        "resonator_casing|facing=north,position=p23|resonator_casing_loose|180",
        "resonator_casing|facing=north,position=p30|armilar/casing_p10|180",
        "resonator_casing|facing=north,position=p31|resonator_casing_loose|180",
        "resonator_casing|facing=north,position=p32|resonator_casing_loose|180",
        "resonator_casing|facing=north,position=p33|resonator_casing_loose|180",
        "resonator_casing|facing=north,position=p34|armilar/casing_p14|180",
        "resonator_casing|facing=north,position=p40|armilar/casing_p40|180",
        "resonator_casing|facing=north,position=p41|armilar/casing_p41|180",
        "resonator_casing|facing=north,position=p43|armilar/casing_p41|180",
        "resonator_casing|facing=north,position=p44|armilar/casing_p44|180",
        "resonator_casing|facing=east,position=loose|resonator_casing_loose|0",
        "resonator_casing|facing=east,position=p00|armilar/casing_p00|270",
        "resonator_casing|facing=east,position=p01|armilar/casing_p01|270",
        "resonator_casing|facing=east,position=p03|armilar/casing_p01|270",
        "resonator_casing|facing=east,position=p04|armilar/casing_p04|270",
        "resonator_casing|facing=east,position=p10|armilar/casing_p10|270",
        "resonator_casing|facing=east,position=p11|resonator_casing_loose|270",
        "resonator_casing|facing=east,position=p12|resonator_casing_loose|270",
        "resonator_casing|facing=east,position=p13|resonator_casing_loose|270",
        "resonator_casing|facing=east,position=p14|armilar/casing_p14|270",
        "resonator_casing|facing=east,position=p20|armilar/casing_p10|270",
        "resonator_casing|facing=east,position=p21|resonator_casing_loose|270",
        "resonator_casing|facing=east,position=p22|resonator_casing_loose|270",
        "resonator_casing|facing=east,position=p23|resonator_casing_loose|270",
        "resonator_casing|facing=east,position=p30|armilar/casing_p10|270",
        "resonator_casing|facing=east,position=p31|resonator_casing_loose|270",
        "resonator_casing|facing=east,position=p32|resonator_casing_loose|270",
        "resonator_casing|facing=east,position=p33|resonator_casing_loose|270",
        "resonator_casing|facing=east,position=p34|armilar/casing_p14|270",
        "resonator_casing|facing=east,position=p40|armilar/casing_p40|270",
        "resonator_casing|facing=east,position=p41|armilar/casing_p41|270",
        "resonator_casing|facing=east,position=p43|armilar/casing_p41|270",
        "resonator_casing|facing=east,position=p44|armilar/casing_p44|270",
        "resonator_column|corner=se,part=loose|resonator_column_loose|0",
        "resonator_column|corner=se,part=lower|armilar/column_lower|0",
        "resonator_column|corner=se,part=upper|armilar/column_upper|0",
        "resonator_column|corner=sw,part=loose|resonator_column_loose|0",
        "resonator_column|corner=sw,part=lower|armilar/column_lower|90",
        "resonator_column|corner=sw,part=upper|armilar/column_upper|90",
        "resonator_column|corner=nw,part=loose|resonator_column_loose|0",
        "resonator_column|corner=nw,part=lower|armilar/column_lower|180",
        "resonator_column|corner=nw,part=upper|armilar/column_upper|180",
        "resonator_column|corner=ne,part=loose|resonator_column_loose|0",
        "resonator_column|corner=ne,part=lower|armilar/column_lower|270",
        "resonator_column|corner=ne,part=upper|armilar/column_upper|270",
        "resonator_cradle|part=loose|resonator_cradle_loose|0",
        "resonator_cradle|part=lower|armilar/cradle_lower|0",
        "resonator_cradle|part=upper|armilar/cradle_upper|0",
        "resonator_energy_port|facing=north,formed=false|resonator_energy_port_loose|0",
        "resonator_energy_port|facing=north,formed=true|armilar/energy_port|180",
        "resonator_energy_port|facing=south,formed=false|resonator_energy_port_loose|180",
        "resonator_energy_port|facing=south,formed=true|armilar/energy_port|0",
        "resonator_energy_port|facing=east,formed=false|resonator_energy_port_loose|90",
        "resonator_energy_port|facing=east,formed=true|armilar/energy_port|270",
        "resonator_energy_port|facing=west,formed=false|resonator_energy_port_loose|270",
        "resonator_energy_port|facing=west,formed=true|armilar/energy_port|90",
        "resonator_crystal_port|facing=north,formed=false|resonator_crystal_port_loose|0",
        "resonator_crystal_port|facing=north,formed=true|armilar/crystal_port|180",
        "resonator_crystal_port|facing=south,formed=false|resonator_crystal_port_loose|180",
        "resonator_crystal_port|facing=south,formed=true|armilar/crystal_port|0",
        "resonator_crystal_port|facing=east,formed=false|resonator_crystal_port_loose|90",
        "resonator_crystal_port|facing=east,formed=true|armilar/crystal_port|270",
        "resonator_crystal_port|facing=west,formed=false|resonator_crystal_port_loose|270",
        "resonator_crystal_port|facing=west,formed=true|armilar/crystal_port|90",
        "resonator_tuner|facing=north,formed=false|resonator_tuner_loose|0",
        "resonator_tuner|facing=north,formed=true|armilar/tuner|180",
        "resonator_tuner|facing=south,formed=false|resonator_tuner_loose|180",
        "resonator_tuner|facing=south,formed=true|armilar/tuner|0",
        "resonator_tuner|facing=east,formed=false|resonator_tuner_loose|90",
        "resonator_tuner|facing=east,formed=true|armilar/tuner|270",
        "resonator_tuner|facing=west,formed=false|resonator_tuner_loose|270",
        "resonator_tuner|facing=west,formed=true|armilar/tuner|90",
        "resonance_core|facing=north,formed=false,tier=basic|resonance_core_basic_loose|0",
        "resonance_core|facing=north,formed=true,tier=basic|armilar/core_basic|180",
        "resonance_core|facing=north,formed=false,tier=advanced|resonance_core_basic_loose|0",
        "resonance_core|facing=north,formed=true,tier=advanced|armilar/core_basic|180",
        "resonance_core|facing=north,formed=false,tier=elite|resonance_core_basic_loose|0",
        "resonance_core|facing=north,formed=true,tier=elite|armilar/core_basic|180",
        "resonance_core|facing=north,formed=false,tier=emerald|resonance_core_basic_loose|0",
        "resonance_core|facing=north,formed=true,tier=emerald|armilar/core_basic|180",
        "resonance_core|facing=north,formed=false,tier=allthemodium|resonance_core_basic_loose|0",
        "resonance_core|facing=north,formed=true,tier=allthemodium|armilar/core_basic|180",
        "resonance_core|facing=north,formed=false,tier=vibranium|resonance_core_basic_loose|0",
        "resonance_core|facing=north,formed=true,tier=vibranium|armilar/core_basic|180",
        "resonance_core|facing=north,formed=false,tier=unobtainium|resonance_core_basic_loose|0",
        "resonance_core|facing=north,formed=true,tier=unobtainium|armilar/core_basic|180",
        "resonance_core|facing=north,formed=false,tier=ultimate|resonance_core_basic_loose|0",
        "resonance_core|facing=north,formed=true,tier=ultimate|armilar/core_basic|180",
        "resonance_core|facing=south,formed=false,tier=basic|resonance_core_basic_loose|180",
        "resonance_core|facing=south,formed=true,tier=basic|armilar/core_basic|0",
        "resonance_core|facing=south,formed=false,tier=advanced|resonance_core_basic_loose|180",
        "resonance_core|facing=south,formed=true,tier=advanced|armilar/core_basic|0",
        "resonance_core|facing=south,formed=false,tier=elite|resonance_core_basic_loose|180",
        "resonance_core|facing=south,formed=true,tier=elite|armilar/core_basic|0",
        "resonance_core|facing=south,formed=false,tier=emerald|resonance_core_basic_loose|180",
        "resonance_core|facing=south,formed=true,tier=emerald|armilar/core_basic|0",
        "resonance_core|facing=south,formed=false,tier=allthemodium|resonance_core_basic_loose|180",
        "resonance_core|facing=south,formed=true,tier=allthemodium|armilar/core_basic|0",
        "resonance_core|facing=south,formed=false,tier=vibranium|resonance_core_basic_loose|180",
        "resonance_core|facing=south,formed=true,tier=vibranium|armilar/core_basic|0",
        "resonance_core|facing=south,formed=false,tier=unobtainium|resonance_core_basic_loose|180",
        "resonance_core|facing=south,formed=true,tier=unobtainium|armilar/core_basic|0",
        "resonance_core|facing=south,formed=false,tier=ultimate|resonance_core_basic_loose|180",
        "resonance_core|facing=south,formed=true,tier=ultimate|armilar/core_basic|0",
        "resonance_core|facing=east,formed=false,tier=basic|resonance_core_basic_loose|90",
        "resonance_core|facing=east,formed=true,tier=basic|armilar/core_basic|270",
        "resonance_core|facing=east,formed=false,tier=advanced|resonance_core_basic_loose|90",
        "resonance_core|facing=east,formed=true,tier=advanced|armilar/core_basic|270",
        "resonance_core|facing=east,formed=false,tier=elite|resonance_core_basic_loose|90",
        "resonance_core|facing=east,formed=true,tier=elite|armilar/core_basic|270",
        "resonance_core|facing=east,formed=false,tier=emerald|resonance_core_basic_loose|90",
        "resonance_core|facing=east,formed=true,tier=emerald|armilar/core_basic|270",
        "resonance_core|facing=east,formed=false,tier=allthemodium|resonance_core_basic_loose|90",
        "resonance_core|facing=east,formed=true,tier=allthemodium|armilar/core_basic|270",
        "resonance_core|facing=east,formed=false,tier=vibranium|resonance_core_basic_loose|90",
        "resonance_core|facing=east,formed=true,tier=vibranium|armilar/core_basic|270",
        "resonance_core|facing=east,formed=false,tier=unobtainium|resonance_core_basic_loose|90",
        "resonance_core|facing=east,formed=true,tier=unobtainium|armilar/core_basic|270",
        "resonance_core|facing=east,formed=false,tier=ultimate|resonance_core_basic_loose|90",
        "resonance_core|facing=east,formed=true,tier=ultimate|armilar/core_basic|270",
        "resonance_core|facing=west,formed=false,tier=basic|resonance_core_basic_loose|270",
        "resonance_core|facing=west,formed=true,tier=basic|armilar/core_basic|90",
        "resonance_core|facing=west,formed=false,tier=advanced|resonance_core_basic_loose|270",
        "resonance_core|facing=west,formed=true,tier=advanced|armilar/core_basic|90",
        "resonance_core|facing=west,formed=false,tier=elite|resonance_core_basic_loose|270",
        "resonance_core|facing=west,formed=true,tier=elite|armilar/core_basic|90",
        "resonance_core|facing=west,formed=false,tier=emerald|resonance_core_basic_loose|270",
        "resonance_core|facing=west,formed=true,tier=emerald|armilar/core_basic|90",
        "resonance_core|facing=west,formed=false,tier=allthemodium|resonance_core_basic_loose|270",
        "resonance_core|facing=west,formed=true,tier=allthemodium|armilar/core_basic|90",
        "resonance_core|facing=west,formed=false,tier=vibranium|resonance_core_basic_loose|270",
        "resonance_core|facing=west,formed=true,tier=vibranium|armilar/core_basic|90",
        "resonance_core|facing=west,formed=false,tier=unobtainium|resonance_core_basic_loose|270",
        "resonance_core|facing=west,formed=true,tier=unobtainium|armilar/core_basic|90",
        "resonance_core|facing=west,formed=false,tier=ultimate|resonance_core_basic_loose|270",
        "resonance_core|facing=west,formed=true,tier=ultimate|armilar/core_basic|90"
    };

    /** A forma de cada modelo base nos quatro giros de 90 graus. */
    private static final Map<String, VoxelShape[]> GIRADAS = girar();

    private ArmilarShapes() {
    }

    private static Map<String, VoxelShape[]> girar() {
        Map<String, VoxelShape[]> formas = new Object2ObjectOpenHashMap<>();
        for (String base : BASES) {
            String[] partes = base.split("=");
            VoxelShape[] giros = new VoxelShape[4];
            for (int giro = 0; giro < 4; giro++) {
                VoxelShape forma = Shapes.empty();
                for (String texto : partes[1].split(";")) {
                    int[] c = Arrays.stream(texto.split(",")).mapToInt(Integer::parseInt).toArray();
                    forma = Shapes.or(forma, caixa(c[0], c[1], c[2], c[3], c[4], c[5], giro));
                }
                giros[giro] = forma.optimize();
            }
            formas.put(partes[0], giros);
        }
        return formas;
    }

    /** A caixa girada `giro` vezes 90 graus em torno do centro vertical do bloco, no sentido do `y` do blockstate. */
    private static VoxelShape caixa(int x0, int y0, int z0, int x1, int y1, int z1, int giro) {
        int ax = x0;
        int az = z0;
        int bx = x1;
        int bz = z1;
        for (int i = 0; i < giro; i++) {
            int novoAx = 16 - bz;
            int novoBx = 16 - az;
            az = ax;
            bz = bx;
            ax = novoAx;
            bx = novoBx;
        }
        return Block.box(ax, y0, az, bx, y1, bz);
    }

    /**
     * A forma da variante `chave` (`facing=north,position=p00`, propriedades em ordem alfabética) do bloco. Só o
     * construtor dos blocos e o teste a usam; faz uma varredura linear de `ESTADOS`, então não serve ao `getShape`.
     */
    static VoxelShape forma(String bloco, String chave) {
        String prefixo = bloco + "|" + chave + "|";
        for (String estado : ESTADOS) {
            if (estado.startsWith(prefixo)) {
                String[] partes = estado.substring(prefixo.length()).split("\\|");
                return GIRADAS.get(partes[0])[Integer.parseInt(partes[1]) / 90];
            }
        }
        throw new IllegalArgumentException("sem forma para " + bloco + " " + chave);
    }

    /** A forma de cada estado do bloco, montada uma vez (no construtor); estado sem variante é erro, não buraco. */
    static Map<BlockState, VoxelShape> tabela(Block bloco, String nome) {
        Map<BlockState, VoxelShape> tabela = new Reference2ObjectOpenHashMap<>();
        for (BlockState estado : bloco.getStateDefinition().getPossibleStates()) {
            tabela.put(estado, forma(nome, chave(estado)));
        }
        return tabela;
    }

    /** A chave da variante do estado: as propriedades em ordem alfabética, `nome=valor` e vírgula. */
    private static String chave(BlockState estado) {
        List<Property<?>> propriedades = new ArrayList<>(estado.getProperties());
        propriedades.sort(Comparator.comparing(Property::getName));
        StringBuilder chave = new StringBuilder();
        for (Property<?> propriedade : propriedades) {
            if (chave.length() > 0) {
                chave.append(',');
            }
            chave.append(par(estado, propriedade));
        }
        return chave.toString();
    }

    private static <T extends Comparable<T>> String par(BlockState estado, Property<T> propriedade) {
        return propriedade.getName() + "=" + propriedade.getName(estado.getValue(propriedade));
    }
}
