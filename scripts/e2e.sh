#!/usr/bin/env bash
# Teste de ponta a ponta num mundo de verdade: cliente + servidor integrado (client/dev/DevEndToEnd.java).
# Guia completo em docs/e2e.md.
#
#   ./scripts/e2e.sh                              conjunto padrão, rápido (sem capturas), em run/e2e
#   ./scripts/e2e.sh --only reparador,ressonador  só essas suítes
#   ./scripts/e2e.sh --only tablet --capture tablet-   com as capturas que começam por "tablet-"
#   ./scripts/e2e.sh --completo                   tudo: capturas, variantes e as suítes opcionais
#   ./scripts/e2e.sh --lista                      as suítes, com descrição e pré-requisito
#   ./scripts/e2e.sh --skip guia,escuro --out run/e2e-x
#
# Opções: --only a,b | --skip a,b | --capture off|todas|prefixos | --variants off|todas|pt,escuro |
#         --completo | --out <dir> | --lista. O primeiro argumento sem "--" ainda vale como <dir> (como antes).
# E2E_TIMEOUT=900 muda o tempo-limite total em segundos (padrão 600; 1500 com --completo).
#
# Abre o jogo com WA_E2E=<dir>. Na tela de título o mod cria o mundo plano "wa-e2e" (apaga o anterior), clica pela
# tela de verdade, confere cada passo no servidor e na tela e escreve <dir>/result.txt ("OK" ou "FALHA: <passo>
# <motivo>" na primeira linha, depois o log dos passos e o tempo por suíte). Este script sai com 0 (OK) ou 1 (falha,
# sem resultado ou tempo esgotado). O log do jogo fica em <dir>/client.log.
#
# Linux: roda sob o Xvfb (xvfb-run). Windows (Git Bash, sem xvfb-run): roda o jogo direto, numa janela de verdade;
# não mexa no mouse nem no teclado até acabar. Um jogo por vez (cada um usa ~3 GB e o mesmo diretório run/).
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT=""
ONLY=""
SKIP=""
CAPTURE=""
VARIANTS=""
COMPLETO=0
LISTA=0

while [ $# -gt 0 ]; do
    case "$1" in
        --only) ONLY="${2:?--only pede a lista de suítes}"; shift 2 ;;
        --skip) SKIP="${2:?--skip pede a lista de suítes}"; shift 2 ;;
        --capture) CAPTURE="${2:?--capture pede off, todas ou prefixos}"; shift 2 ;;
        --variants) VARIANTS="${2:?--variants pede off, todas, pt ou escuro}"; shift 2 ;;
        --out) OUT="${2:?--out pede o diretório}"; shift 2 ;;
        --completo) COMPLETO=1; shift ;;
        --lista) LISTA=1; shift ;;
        -h|--help) sed -n '2,22p' "${BASH_SOURCE[0]}"; exit 0 ;;
        --*) echo "e2e: opção desconhecida: $1 (veja --help)" >&2; exit 2 ;;
        *) OUT="$1"; shift ;;
    esac
done

if [ "$LISTA" -eq 1 ]; then
    echo "Suítes do e2e (nome | o que prova | pré-requisito | padrão ou opcional); detalhes em docs/e2e.md"
    grep -E '^\| `[a-z-]+` \|' "$ROOT/docs/e2e.md" | sed 's/`//g; s/^| //; s/ |$//'
    exit 0
fi

OUT="${OUT:-$ROOT/run/e2e}"
case "$OUT" in
    /*|[A-Za-z]:*) ;;
    *) OUT="$PWD/$OUT" ;;
esac
if [ "$COMPLETO" -eq 1 ]; then
    TIMEOUT="${E2E_TIMEOUT:-1500}"
else
    TIMEOUT="${E2E_TIMEOUT:-600}"
fi

export WA_E2E="$OUT"
[ -n "$ONLY" ] && export WA_E2E_ONLY="$ONLY"
[ -n "$SKIP" ] && export WA_E2E_SKIP="$SKIP"
[ -n "$CAPTURE" ] && export WA_E2E_CAPTURE="$CAPTURE"
[ -n "$VARIANTS" ] && export WA_E2E_VARIANTS="$VARIANTS"
[ "$COMPLETO" -eq 1 ] && export WA_E2E_PROFILE=completo

if command -v xvfb-run >/dev/null 2>&1; then
    RUNNER=(xvfb-run -a -s "-screen 0 1280x800x24")
else
    RUNNER=()
    echo "e2e: sem xvfb-run: o jogo vai abrir uma janela de verdade; não mexa no mouse nem no teclado até acabar."
fi

mkdir -p "$OUT"
rm -f "$OUT"/result.txt "$OUT"/*.png
echo "e2e: rodando o cliente (até ${TIMEOUT}s); log em $OUT/client.log"

cd "$ROOT"
START=$(date +%s)
timeout --kill-after=30 "$TIMEOUT" "${RUNNER[@]}" ./gradlew runClient --console=plain > "$OUT/client.log" 2>&1
STATUS=$?
ELAPSED=$(( $(date +%s) - START ))

if [ ! -f "$OUT/result.txt" ]; then
    if [ "$STATUS" -eq 124 ] || [ "$STATUS" -eq 137 ]; then
        echo "e2e: FALHA: tempo esgotado (${TIMEOUT}s) sem resultado; veja $OUT/client.log" >&2
    else
        echo "e2e: FALHA: o jogo saiu (código $STATUS) sem escrever result.txt; veja $OUT/client.log" >&2
    fi
    tail -n 30 "$OUT/client.log" >&2
    exit 1
fi

HEAD="$(head -n 1 "$OUT/result.txt")"
echo "$HEAD"
sed -n '/^# Tempo por suíte/,$p' "$OUT/result.txt"
echo "e2e: tempo de parede ${ELAPSED}s; resultado completo em $OUT/result.txt"
ls "$OUT"/*.png 2>/dev/null | sed 's/^/e2e: captura /'
if [ "$HEAD" = "OK" ]; then
    exit 0
fi
exit 1
