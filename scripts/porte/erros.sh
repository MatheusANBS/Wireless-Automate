#!/usr/bin/env bash
# Porte 1.20.1 (temporário, até o build inteiro compilar): erros de compilação por pacote.
#
#   bash scripts/porte/erros.sh           # contagem por pacote (pasta sob wirelessautomate/) e o total
#   bash scripts/porte/erros.sh network   # lista os erros daquele pacote ("raiz" para os da pasta base)
#
# Roda ./gradlew compileJava (com -Xmaxerrs alto no build.gradle, para ver todos) e sai com 0 mesmo
# com erros.
set -u
cd "$(dirname "$0")/../.."

log=$(mktemp)
trap 'rm -f "$log"' EXIT
./gradlew compileJava --console=plain >"$log" 2>&1

# Linhas "<arquivo>.java:<linha>: error: <mensagem>" do javac, sem as repetidas no resumo do Gradle
# (que vêm indentadas). O javac para na fase em que achou erros: enquanto houver erro de sintaxe
# (Java 21 no Java 17), os de símbolo ainda não aparecem. A barra do Windows vira a do Unix e cada
# linha vira "<pacote>\t<arquivo relativo>:<linha>: error: <mensagem>".
erros=$(grep -E '^[^[:space:]].*\.java:[0-9]+: error:' "$log" | sed 's#[\\]#/#g' \
    | sed -E 's#^.*/wirelessautomate/##' \
    | awk '{ split($0, partes, ":"); arq = partes[1]; n = split(arq, p, "/");
             pacote = (n > 1) ? p[1] : "raiz"; print pacote "\t" $0 }')

if [ $# -ge 1 ]; then
    printf '%s\n' "$erros" | awk -F'\t' -v alvo="$1" '$1 == alvo { print $2 }'
    exit 0
fi

if [ -z "$erros" ]; then
    if grep -q 'BUILD SUCCESSFUL' "$log"; then
        echo "compileJava sem erros"
    else
        echo "compileJava falhou sem erros do javac; veja a saída do Gradle:"
        tail -30 "$log"
    fi
    exit 0
fi

printf '%s\n' "$erros" | awk -F'\t' '{ c[$1]++; t++ }
    END { printf "%-12s %6s\n", "pacote", "erros";
          for (p in c) printf "%-12s %6d\n", p, c[p] | "sort";
          close("sort"); printf "%-12s %6d\n", "total", t }'
exit 0
