#!/usr/bin/env bash
# Roda o benchmark do Wireless Automate num servidor dedicado de desenvolvimento, sem jogador.
#
#   ./scripts/bench.sh                    todos os cenários (padrão abaixo), 3 repetições cada
#   ./scripts/bench.sh "many:500:3:soph"  só as tarefas dadas: cenário[:n[:reps[:vanilla|soph]]], separadas por ; ou ,
#
# Variáveis opcionais (ticks): WA_BENCH_BASELINE (100), WA_BENCH_WARMUP (do cenário), WA_BENCH_MEASURE (200).
# WA_BENCH_SPRINT=1 roda os ticks sem pausa (/tick sprint): só para perfis, os números de vazão por
# segundo de jogo continuam valendo, mas o MSPT sobe com a disputa de CPU.
# WA_BENCH_JFR=1 grava também um perfil do Java Flight Recorder (local) em run/bench/reports/<data>.jfr.
#
# O que ele faz:
#   - prepara run/bench: eula, server.properties com mundo plano novo, porta 25599, sem mobs;
#   - sobe ./gradlew runBenchServer (com Sophisticated Storage e Spark; ver build.gradle) com WA_BENCH;
#   - o mod mede o servidor vazio, roda as tarefas em sequência e para o servidor no fim;
#   - o relatório fica em run/bench/reports/<data>.md e o log do servidor ao lado.
# Cenários e números em docs/benchmark.md. Use uma máquina sem outra carga: os tempos são de parede.
set -euo pipefail

# Bloco lido inteiro antes de rodar: editar o script durante um benchmark não o faz rodar de novo.
{

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT/run/bench"
DEFAULT_SPEC="many:500:3:vanilla;many:500:3:soph;idle:500:3:vanilla;idle:500:3:soph;full:100:3:vanilla;full:500:3:vanilla;raw:2:3:vanilla;raw:2:3:soph;big:20:3:vanilla;big:20:3:soph;bigfull:20:3:vanilla;bigfull:20:3:soph;types:20:3:soph;mixed:498:3:vanilla;rebuild:500:3:vanilla"
SPEC="${1:-$DEFAULT_SPEC}"

case "${SPEC}" in
    -h|--help) sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
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
