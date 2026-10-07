#!/usr/bin/env bash
# Teste de ponta a ponta num mundo de verdade: cliente + servidor integrado (client/DevEndToEnd.java).
#
#   ./scripts/e2e.sh                 roda o teste e guarda tudo em run/e2e
#   ./scripts/e2e.sh <dir>           guarda em <dir>
#   E2E_TIMEOUT=900 ./scripts/e2e.sh tempo-limite total em segundos (padrão 600)
#
# Abre o jogo com WA_E2E=<dir> sob o Xvfb (xvfb-run). Na tela de título o mod cria o mundo plano
# "wa-e2e" (apaga o anterior), põe dois baús com roteadores, clica pela tela de verdade (Extrai,
# Insere, Editar filtro, regra por tag, renomear, trocar de rede), confere cada passo no servidor e
# na tela, salva capturas em <dir> e escreve <dir>/result.txt ("OK" ou "FALHA: <passo> <motivo>"
# na primeira linha, depois o log dos passos). Este script lê o resultado e sai com 0 (OK) ou
# 1 (falha, sem resultado ou tempo esgotado). O log do jogo fica em <dir>/client.log.
#
# Precisa de xvfb-run (pacote xvfb) e do JDK 21; o CI não roda este teste porque não garante o Xvfb.
# Não rode junto de outro runClient do mesmo diretório (dividem run/).
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-$ROOT/run/e2e}"
TIMEOUT="${E2E_TIMEOUT:-600}"

case "$OUT" in
    /*) ;;
    *) OUT="$PWD/$OUT" ;;
esac

if ! command -v xvfb-run >/dev/null 2>&1; then
    echo "e2e: xvfb-run não encontrado (instale o pacote xvfb)" >&2
    exit 1
fi

mkdir -p "$OUT"
rm -f "$OUT"/result.txt "$OUT"/*.png
echo "e2e: rodando o cliente (até ${TIMEOUT}s); log em $OUT/client.log"

cd "$ROOT"
WA_E2E="$OUT" timeout --kill-after=30 "$TIMEOUT" \
    xvfb-run -a -s "-screen 0 1280x800x24" ./gradlew runClient --console=plain > "$OUT/client.log" 2>&1
STATUS=$?

if [ ! -f "$OUT/result.txt" ]; then
    if [ "$STATUS" -eq 124 ] || [ "$STATUS" -eq 137 ]; then
        echo "e2e: FALHA: tempo esgotado (${TIMEOUT}s) sem resultado; veja $OUT/client.log" >&2
    else
        echo "e2e: FALHA: o jogo saiu (código $STATUS) sem escrever result.txt; veja $OUT/client.log" >&2
    fi
    tail -n 30 "$OUT/client.log" >&2
    exit 1
fi

cat "$OUT/result.txt"
ls "$OUT"/*.png 2>/dev/null | sed 's/^/e2e: captura /'
if [ "$(head -n 1 "$OUT/result.txt")" = "OK" ]; then
    exit 0
fi
exit 1
