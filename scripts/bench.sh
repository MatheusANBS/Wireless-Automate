#!/usr/bin/env bash
# Roda o benchmark do Wireless Automate num servidor dedicado de desenvolvimento, sem jogador.
#
#   ./scripts/bench.sh                    todos os cenários (padrão abaixo), 3 repetições cada
#   ./scripts/bench.sh "many:500:3:soph"  só as tarefas dadas: cenário[:n[:reps[:vanilla|soph]]], separadas por ; ou ,
#
# Variáveis opcionais (ticks): WA_BENCH_BASELINE (100), WA_BENCH_WARMUP (do cenário), WA_BENCH_MEASURE (200).
# WA_BENCH_SPRINT=1 roda os ticks sem pausa (/tick sprint): só para perfis, os números de vazão por
# segundo de jogo continuam valendo, mas o MSPT sobe com a disputa de CPU.
# WA_BENCH_JFR=1 grava também um perfil do Java Flight Recorder (local) em run-1.20.1/bench/reports/<data>.jfr.
#
# Benchmark comparativo (docs/benchmark-logistics-network.md): um quinto campo diz quem transporta, wa (padrão),
# wa-full, ln, ln-rr ou ln-async, como em "many:500:3:vanilla:ln". "./scripts/bench.sh comparativo" roda a lista
# COMPARATIVE_SPEC abaixo. Com alguma tarefa ln, o script põe o jar do Logistics Network em run-1.20.1/bench/mods
# (baixado uma vez para run-1.20.1/bench-ln/, fora do git; o mod é All Rights Reserved e não vai para o repositório);
# sem nenhuma, tira o jar, para as rodadas só do mod continuarem com o mesmo conjunto de mods.
#
# O que ele faz:
#   - prepara run-1.20.1/bench: eula, server.properties com mundo plano novo, porta 25599, sem mobs;
#   - sobe ./gradlew runBenchServer (com Sophisticated Storage e Spark; ver build.gradle) com WA_BENCH;
#   - o mod mede o servidor vazio, roda as tarefas em sequência e para o servidor no fim;
#   - o relatório fica em run-1.20.1/bench/reports/<data>.md e o log do servidor ao lado.
# Cenários e números em docs/benchmark.md. Use uma máquina sem outra carga: os tempos são de parede.
set -euo pipefail

# Bloco lido inteiro antes de rodar: editar o script durante um benchmark não o faz rodar de novo.
{

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT/run-1.20.1/bench"
DEFAULT_SPEC="many:500:3:vanilla;many:500:3:soph;idle:500:3:vanilla;idle:500:3:soph;full:100:3:vanilla;full:500:3:vanilla;raw:2:3:vanilla;raw:2:3:soph;big:20:3:vanilla;big:20:3:soph;bigfull:20:3:vanilla;bigfull:20:3:soph;types:20:3:soph;mixed:498:3:vanilla;rebuild:500:3:vanilla;sparse:500:3:vanilla;sparse:500:3:soph;stock:20:3:soph;bigstack:2:3:vanilla;redstone:100:3:vanilla;tablet:1000:3:vanilla"
# Cada cenário comparável nos cinco transportes, um atrás do outro (mesma ordem em todas as rodadas).
COMPARATIVE_SPEC=""
for task in many:100:3:vanilla many:500:3:vanilla many:1000:3:vanilla idle:500:3:vanilla full:500:3:vanilla \
        sparse:500:3:vanilla raw:2:3:vanilla big:20:3:vanilla bigfull:20:3:vanilla mixed:498:3:vanilla \
        redstone:100:3:vanilla inf:2:3:vanilla inf:100:3:vanilla many:500:3:soph \
        pairs:100:3:vanilla pairs:500:3:vanilla pairs:1000:3:vanilla; do
    for transport in wa wa-full ln ln-rr ln-async; do
        COMPARATIVE_SPEC="${COMPARATIVE_SPEC:+$COMPARATIVE_SPEC;}$task:$transport"
    done
done
LN_VERSION="1.21.1-1.17.2"
LN_URL="https://cursemaven.com/curse/maven/logistics-network-1448257/9086994/logistics-network-1448257-9086994.jar"

SPEC="${1:-$DEFAULT_SPEC}"

case "${SPEC}" in
    -h|--help) sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    comparativo) SPEC="$COMPARATIVE_SPEC" ;;
esac

log() { printf '\033[1;36m==>\033[0m %s\n' "$*"; }

mkdir -p "$RUN_DIR/reports"
STAMP="$(date +%Y%m%d-%H%M%S)"
REPORT="$RUN_DIR/reports/$STAMP.md"
SERVER_LOG="$RUN_DIR/reports/$STAMP-server.log"

log "Preparando $RUN_DIR (mundo plano novo)"
echo "eula=true" > "$RUN_DIR/eula.txt"
rm -rf "$RUN_DIR/bench-world"
cat > "$RUN_DIR/server.properties" <<'EOF'
level-name=bench-world
level-type=minecraft\:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
level-seed=wireless-automate-bench
generate-structures=false
difficulty=peaceful
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
online-mode=false
server-port=25599
enable-query=false
enable-rcon=false
view-distance=4
simulation-distance=4
max-tick-time=-1
sync-chunk-writes=false
motd=Wireless Automate benchmark
EOF

mkdir -p "$RUN_DIR/mods"
rm -f "$RUN_DIR"/mods/LogisticsNetworks-*.jar
if printf '%s' "$SPEC" | grep -Eq ':ln(-async)?([;,]|$)'; then
    LN_JAR="$ROOT/run-1.20.1/bench-ln/LogisticsNetworks-$LN_VERSION.jar"
    if [ ! -s "$LN_JAR" ]; then
        log "Baixando o Logistics Network $LN_VERSION (só para o benchmark local)"
        mkdir -p "$(dirname "$LN_JAR")"
        curl -fsSL -o "$LN_JAR" "$LN_URL"
    fi
    cp "$LN_JAR" "$RUN_DIR/mods/"
    log "Logistics Network $LN_VERSION em run-1.20.1/bench/mods"
fi

log "Tarefas: $SPEC"
log "Relatório: $REPORT"
EXTRA=()
if [ -n "${WA_BENCH_JFR:-}" ]; then
    EXTRA+=("-PbenchJfr=$RUN_DIR/reports/$STAMP.jfr")
    log "Perfil JFR: $RUN_DIR/reports/$STAMP.jfr"
fi
# Sem cache de configuração: o ambiente do processo do servidor precisa ver o WA_BENCH desta chamada.
WA_BENCH="$SPEC" WA_BENCH_OUT="$REPORT" \
    "$ROOT/gradlew" -p "$ROOT" runBenchServer --no-configuration-cache --console=plain "${EXTRA[@]}" 2>&1 | tee "$SERVER_LOG" \
    | grep --line-buffered -E "\[bench\]|Exception|Error|spark" || true

if [ -s "$REPORT" ]; then
    log "Pronto: $REPORT"
    cat "$REPORT"
else
    echo "O relatório não foi gerado; veja $SERVER_LOG" >&2
    exit 1
fi
exit 0
}
