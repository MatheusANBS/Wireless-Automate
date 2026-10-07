#!/usr/bin/env bash
# Prepara o ambiente para desenvolver o Wireless Automate (Linux, macOS ou WSL).
#
#   ./scripts/setup.sh              instala o que faltar e compila (build + testes de unidade)
#   ./scripts/setup.sh --gametest   também roda os GameTests num servidor headless
#   ./scripts/setup.sh --no-build   só instala as ferramentas
#
# O que ele garante:
#   - JDK 21 (pelo gerenciador de pacotes; sem root, baixa o Temurin 21 para .tools/jdk-21)
#   - git, curl e unzip
#   - Gradle via wrapper (./gradlew), NeoForge e Minecraft baixados e decompilados no primeiro build
# Pode rodar quantas vezes quiser: só instala o que estiver faltando.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/.tools"
JAVA_MAJOR=21

RUN_BUILD=1
RUN_GAMETEST=0
for arg in "$@"; do
    case "$arg" in
        --no-build) RUN_BUILD=0 ;;
        --gametest) RUN_GAMETEST=1 ;;
        -h|--help) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Opção desconhecida: $arg" >&2; exit 2 ;;
    esac
done

log() { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n' "$*" >&2; }

SUDO=""
if [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null 2>&1; then
    SUDO="sudo"
fi
can_install() { [ "$(id -u)" -eq 0 ] || [ -n "$SUDO" ]; }

PKG=""
if command -v apt-get >/dev/null 2>&1; then PKG=apt
elif command -v dnf >/dev/null 2>&1; then PKG=dnf
elif command -v pacman >/dev/null 2>&1; then PKG=pacman
elif command -v brew >/dev/null 2>&1; then PKG=brew
fi

APT_UPDATED=0
pkg_install() {
    case "$PKG" in
        apt)
            if [ "$APT_UPDATED" -eq 0 ]; then $SUDO apt-get update -qq; APT_UPDATED=1; fi
            $SUDO env DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "$@" ;;
        dnf) $SUDO dnf install -y -q "$@" ;;
        pacman) $SUDO pacman -S --needed --noconfirm "$@" ;;
        brew) brew install "$@" ;;
        *) return 1 ;;
    esac
}

# --- ferramentas básicas ---------------------------------------------------
for tool in git curl unzip; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        log "Instalando $tool"
        if [ "$PKG" = brew ] || can_install; then
            pkg_install "$tool" || warn "Não consegui instalar $tool; instale manualmente."
        else
            warn "$tool não encontrado e sem permissão para instalar."
        fi
    fi
done

# --- JDK 21 ----------------------------------------------------------------
java_major() {
    # Imprime a versão principal do javac em $1 (ou do PATH), ou 0.
    local javac="${1:-javac}"
    command -v "$javac" >/dev/null 2>&1 || { echo 0; return; }
    "$javac" -version 2>&1 | grep -v '^Picked up' | sed -E 's/^javac ([0-9]+).*/\1/' | head -n1
}

find_jdk() {
    if [ -n "${JAVA_HOME:-}" ] && [ "$(java_major "$JAVA_HOME/bin/javac")" -ge "$JAVA_MAJOR" ]; then
        echo "$JAVA_HOME"; return
    fi
    if [ -x "$TOOLS/jdk-$JAVA_MAJOR/bin/javac" ]; then
        echo "$TOOLS/jdk-$JAVA_MAJOR"; return
    fi
    if [ "$(java_major)" -ge "$JAVA_MAJOR" ]; then
        dirname "$(dirname "$(readlink -f "$(command -v javac)")")"; return
    fi
    for dir in /usr/lib/jvm/*21* /opt/homebrew/opt/openjdk@21 /usr/local/opt/openjdk@21; do
        if [ -x "$dir/bin/javac" ]; then echo "$dir"; return; fi
    done
}

install_jdk_package() {
    case "$PKG" in
        apt) pkg_install openjdk-21-jdk-headless ;;
        dnf) pkg_install java-21-openjdk-devel ;;
        pacman) pkg_install jdk21-openjdk ;;
        brew) pkg_install openjdk@21 ;;
        *) return 1 ;;
    esac
}

install_jdk_portable() {
    local os arch url
    case "$(uname -s)" in
        Linux) os=linux ;;
        Darwin) os=mac ;;
        *) warn "Sistema sem suporte para o download automático do JDK."; return 1 ;;
    esac
    case "$(uname -m)" in
        x86_64|amd64) arch=x64 ;;
        aarch64|arm64) arch=aarch64 ;;
        *) warn "Arquitetura sem suporte: $(uname -m)"; return 1 ;;
    esac
    url="https://api.adoptium.net/v3/binary/latest/$JAVA_MAJOR/ga/$os/$arch/jdk/hotspot/normal/eclipse"
    log "Baixando Temurin $JAVA_MAJOR para .tools/jdk-$JAVA_MAJOR"
    mkdir -p "$TOOLS"
    local tmp; tmp="$(mktemp -d)"
    curl -fsSL "$url" -o "$tmp/jdk.tar.gz"
    tar -xzf "$tmp/jdk.tar.gz" -C "$tmp"
    rm -rf "$TOOLS/jdk-$JAVA_MAJOR"
    local home; home="$(find "$tmp" -maxdepth 4 -type f -path '*/bin/javac' | head -n1)"
    mv "$(dirname "$(dirname "$home")")" "$TOOLS/jdk-$JAVA_MAJOR"
    rm -rf "$tmp"
}

JDK="$(find_jdk || true)"
if [ -z "$JDK" ]; then
    log "JDK $JAVA_MAJOR não encontrado"
    if { [ "$PKG" = brew ] || can_install; } && install_jdk_package; then
        JDK="$(find_jdk || true)"
    fi
    if [ -z "$JDK" ]; then
        install_jdk_portable
        JDK="$TOOLS/jdk-$JAVA_MAJOR"
    fi
fi
export JAVA_HOME="$JDK"
export PATH="$JAVA_HOME/bin:$PATH"
log "Usando JDK em $JAVA_HOME ($(java_major "$JAVA_HOME/bin/javac"))"

# --- Gradle e Minecraft ----------------------------------------------------
cd "$ROOT"
chmod +x gradlew

log "Baixando o Gradle do wrapper"
./gradlew --version

if [ "$RUN_BUILD" -eq 1 ]; then
    # O primeiro build baixa e decompila o Minecraft e o NeoForge (alguns minutos).
    log "Compilando e rodando os testes de unidade"
    ./gradlew build
    log "Jar do mod: $(ls build/libs/*.jar | grep -v -- '-sources' | head -n1)"
fi

if [ "$RUN_GAMETEST" -eq 1 ]; then
    log "Rodando os GameTests no servidor headless"
    ./gradlew runGameTestServer
fi

cat <<MSG

Pronto. Próximos passos:
  ./gradlew build               compila e roda os testes de unidade (jar em build/libs/)
  ./gradlew runGameTestServer   roda os GameTests dentro do Minecraft, sem tela
  ./gradlew runClient           abre o Minecraft com o mod (precisa de tela)
  ./gradlew runData             roda os geradores de dados

MSG
if [ "$JAVA_HOME" = "$TOOLS/jdk-$JAVA_MAJOR" ]; then
    echo "O JDK foi instalado em .tools/. Para usar no terminal:"
    echo "  export JAVA_HOME=\"$JAVA_HOME\" PATH=\"\$JAVA_HOME/bin:\$PATH\""
fi
