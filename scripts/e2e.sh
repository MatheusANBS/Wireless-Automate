#!/usr/bin/env bash
# Teste de ponta a ponta num mundo de verdade: cliente + servidor integrado (client/dev/DevEndToEnd.java).
# Guia completo em docs/e2e.md.
#
#   ./scripts/e2e.sh                              conjunto padrão, rápido (sem capturas), em run/e2e
#   ./scripts/e2e.sh --only reparador,ressonador  só essas suítes
#   ./scripts/e2e.sh --only tablet --capture tablet-   com as capturas que começam por "tablet-"
#   ./scripts/e2e.sh --completo                   tudo: capturas, variantes e as suítes opcionais
#   ./scripts/e2e.sh --lista                      as suítes, com descrição e pré-requisito
#   ./scripts/e2e.sh --only fx-base --capture fx-base --shaders complementary --out run/e2e-fx-comp   com shaderpack
#   ./scripts/e2e.sh --skip guia,escuro --out run/e2e-x
#   ./scripts/e2e.sh --vitrine todas              grava os quadros dos GIFs do CurseForge em run/vitrine, com o Complementary
#   ./scripts/e2e.sh --vitrine armilar            só uma cena (roteador, filtro, bau, doca, tela, armilar)
#
# Opções: --only a,b | --skip a,b | --capture off|todas|prefixos | --variants off|todas|pt,escuro |
#         --completo | --shaders nenhum|complementary|bsl | --out <dir> | --vitrine <cena|todas> | --lista. O primeiro argumento sem "--" ainda
#         vale como <dir> (como antes).
# --shaders: "nenhum" (o padrão) roda como sempre; "complementary" e "bsl" escrevem run/config/iris.properties (liga o
# Iris e escolhe o pacote de run/shaderpacks) e abrem o cliente com ./gradlew runClient -Pshaders (Iris e Sodium só
# no cliente de dev); "nenhum" grava enableShaders=false no mesmo arquivo, para um runClient -Pshaders manual depois
# subir sem shader. O run/options.txt nunca é tocado.
# --vitrine: em vez do roteiro, o jogo roda a vitrine (client/dev/vitrine): uma cena (ou todas) gravada quadro a quadro em
# <out>/<cena>/, com o Complementary por padrão; depois `python -I scripts/curseforge/gif.py run/vitrine` monta os GIFs.
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
SHADERS=""
VITRINE=""

while [ $# -gt 0 ]; do
    case "$1" in
        --only) ONLY="${2:?--only pede a lista de suítes}"; shift 2 ;;
        --skip) SKIP="${2:?--skip pede a lista de suítes}"; shift 2 ;;
        --capture) CAPTURE="${2:?--capture pede off, todas ou prefixos}"; shift 2 ;;
        --variants) VARIANTS="${2:?--variants pede off, todas, pt ou escuro}"; shift 2 ;;
        --out) OUT="${2:?--out pede o diretório}"; shift 2 ;;
        --shaders) SHADERS="${2:?--shaders pede nenhum, complementary ou bsl}"; shift 2 ;;
        --vitrine) VITRINE="${2:?--vitrine pede a cena ou todas}"; shift 2 ;;
        --completo) COMPLETO=1; shift ;;
        --lista) LISTA=1; shift ;;
        -h|--help) sed -n '2,28p' "${BASH_SOURCE[0]}"; exit 0 ;;
        --*) echo "e2e: opção desconhecida: $1 (veja --help)" >&2; exit 2 ;;
        *) OUT="$1"; shift ;;
    esac
done

if [ "$LISTA" -eq 1 ]; then
    echo "Suítes do e2e (nome | o que prova | pré-requisito | padrão ou opcional); detalhes em docs/e2e.md"
    grep -E '^\| `[a-z-]+` \|' "$ROOT/docs/e2e.md" | sed 's/`//g; s/^| //; s/ |$//'
    exit 0
fi

if [ -z "$SHADERS" ]; then
    if [ -n "$VITRINE" ]; then SHADERS="complementary"; else SHADERS="nenhum"; fi
fi
case "$SHADERS" in
    nenhum) PACK=""; GRADLE_FLAGS=() ;;
    complementary) PACK="ComplementaryReimagined_r5.9.3.zip"; GRADLE_FLAGS=(-Pshaders) ;;
    bsl) PACK="BSL_v10.1.8.zip"; GRADLE_FLAGS=(-Pshaders) ;;
    *) echo "e2e: --shaders pede nenhum, complementary ou bsl (veio: $SHADERS)" >&2; exit 2 ;;
esac

if [ -n "$VITRINE" ]; then OUT="${OUT:-$ROOT/run/vitrine}"; else OUT="${OUT:-$ROOT/run/e2e}"; fi
case "$OUT" in
    /*|[A-Za-z]:*) ;;
    *) OUT="$PWD/$OUT" ;;
esac
# no Windows (Git Bash) o jogo lê o caminho com a letra do disco
command -v cygpath >/dev/null 2>&1 && OUT="$(cygpath -m "$OUT")"
if [ -n "$VITRINE" ]; then
    TIMEOUT="${E2E_TIMEOUT:-1200}"
elif [ "$COMPLETO" -eq 1 ]; then
    TIMEOUT="${E2E_TIMEOUT:-1500}"
else
    TIMEOUT="${E2E_TIMEOUT:-600}"
fi

if [ -n "$VITRINE" ]; then
    # a pixel art e o ícone das Telas da vitrine
    python -I "$ROOT/scripts/curseforge/arte_vitrine.py" "$OUT/entrada" >/dev/null || exit 2
    export WA_VITRINE="$OUT"
    [ "$VITRINE" != "todas" ] && export WA_VITRINE_ONLY="$VITRINE"
else
    export WA_E2E="$OUT"
fi
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

if [ -n "$PACK" ]; then
    if [ ! -f "$ROOT/run/shaderpacks/$PACK" ]; then
        echo "e2e: falta o pacote run/shaderpacks/$PACK" >&2
        exit 2
    fi
    # as chaves do Iris 1.8.12 (IrisConfig): enableShaders e shaderPack; o cliente roda dentro de run/
    mkdir -p "$ROOT/run/config"
    printf 'enableShaders=true\nshaderPack=%s\n' "$PACK" > "$ROOT/run/config/iris.properties"
    echo "e2e: shaderpack $PACK (run/config/iris.properties)"
fi

if [ -z "$PACK" ]; then
    # sem pacote: deixa o Iris desligado, para um runClient -Pshaders manual depois não subir com o da rodada anterior
    mkdir -p "$ROOT/run/config"
    printf 'enableShaders=false\n' >"$ROOT/run/config/iris.properties"
fi

mkdir -p "$OUT"
rm -f "$OUT"/result.txt "$OUT"/*.png
echo "e2e: rodando o cliente (até ${TIMEOUT}s); log em $OUT/client.log"

if [ -n "$VITRINE" ]; then
    # sem o JEI nem a barra lateral do FTB Library (a lista, os botões e os atalhos de criativo sujam os filmes); a
    # barra volta ao que era quando a rodada termina
    GRADLE_FLAGS+=(-PsemJei)
    FTB="$ROOT/run/config/ftblibrary-client.snbt"
    if [ -f "$FTB" ]; then
        cp "$FTB" "$FTB.vitrine"
        trap 'mv -f "$FTB.vitrine" "$FTB"' EXIT
        sed -i '/# Enable the sidebar/{n;n;s/true/false/}' "$FTB"
    fi
fi

cd "$ROOT"
START=$(date +%s)
timeout --kill-after=30 "$TIMEOUT" "${RUNNER[@]}" ./gradlew runClient "${GRADLE_FLAGS[@]}" --console=plain > "$OUT/client.log" 2>&1
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
