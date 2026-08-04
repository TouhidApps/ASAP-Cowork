#!/usr/bin/env bash
# asap.sh — single entry point for ASAP-Cowork dev workflows.
# Combines what used to be two scripts (setup.sh + run-all.sh) behind one menu.
#
# Matches the Phase 1 scaffold: a root Gradle project with a `chat-gateway`
# module serving the backend (Ktor on :8081), and a `web-ui` React/Vite app
# for the frontend (Vite dev server on :8080, configured to proxy
# /api, /health, /ws to the backend).
set -uo pipefail
cd "$(dirname "$0")"

BACKEND_PORT=8081
FRONTEND_PORT=8080
BUILD_RUNNER_PORT=8090

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
ok()   { echo -e "  ${GREEN}✓${NC} $1"; }
warn() { echo -e "  ${YELLOW}!${NC} $1"; }
fail() { echo -e "  ${RED}✗${NC} $1"; }
step() { echo; echo "== $1 =="; }

# Prepends $1 to PATH unless PATH is unset (then just sets it) or $1 is
# already present — avoids growing PATH with duplicate entries when this
# script re-runs within the same shell session.
add_to_path() {
  local dir="$1"
  if [[ -z "${PATH:-}" ]]; then
    PATH="$dir"
  else
    case ":$PATH:" in
      *":$dir:"*) ;;
      *) PATH="$dir:$PATH" ;;
    esac
  fi
}

# Resolves the JDK home for a project-scoped openjdk@21, installed via
# Homebrew but never linked onto PATH/JAVA_HOME and never registered with
# `/usr/libexec/java_home` — so it can't become the default JDK for any
# other project on this machine. Prints the home path and returns 0 if
# it's actually installed; returns 1 (prints nothing) otherwise. Callers
# pass the result to `./gradlew -Dorg.gradle.java.home=...` so it's only
# ever used for this project's own build invocations.
resolve_project_java_home() {
  [[ "$(uname -s)" == "Darwin" ]] || return 1
  command -v brew >/dev/null 2>&1 || return 1
  local prefix home
  prefix="$(brew --prefix openjdk@21 2>/dev/null)" || return 1
  home="$prefix/libexec/openjdk.jdk/Contents/Home"
  [[ -x "$home/bin/java" ]] || return 1
  echo "$home"
}

run_setup() {
  OS="$(uname -s)"

  # --- Homebrew (macOS only — needed to install everything else below) ---
  if [[ "$OS" == "Darwin" ]]; then
    step "Homebrew"
    if command -v brew >/dev/null 2>&1; then
      ok "installed"
    else
      warn "not found — installing..."
      /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
      if [[ -d /opt/homebrew/bin ]]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
      elif [[ -d /usr/local/Homebrew/bin ]]; then
        eval "$(/usr/local/bin/brew shellenv)"
      fi
      ok "installed"
    fi
  fi

  # --- Git ---
  step "Git"
  if command -v git >/dev/null 2>&1; then
    ok "installed ($(git --version))"
  else
    warn "not found — installing..."
    if [[ "$OS" == "Darwin" ]]; then
      brew install git
    else
      fail "install git manually for your OS, then re-run this script"
      exit 1
    fi
    ok "installed"
  fi

  # --- Git repository ---
  step "Git repository"
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    ok "already a git repository"
  else
    warn "not a git repository — running git init"
    git init
    ok "initialized"
  fi

  # --- Java 21 (project-scoped — required by Kotlin/Ktor) ---
  # Installed via Homebrew but deliberately never linked onto PATH/JAVA_HOME,
  # never registered with `/usr/libexec/java_home`, and never written into
  # any gradle.properties (project or global). run_all() passes its home
  # path straight to `./gradlew -Dorg.gradle.java.home=...`, so it's used
  # only for this project's own builds and can't change what JDK any other
  # Java/Android project on this machine resolves to.
  step "Java (project-scoped, openjdk@21)"
  if [[ "$OS" == "Darwin" ]]; then
    if ! brew list --formula openjdk@21 >/dev/null 2>&1; then
      warn "installing openjdk@21 via Homebrew (kept project-scoped — not linked onto system PATH)..."
      brew install openjdk@21
    fi
    PROJECT_JAVA_HOME="$(resolve_project_java_home)"
    if [[ -n "$PROJECT_JAVA_HOME" ]]; then
      ok "openjdk@21 ready at $PROJECT_JAVA_HOME — used only for this project's ./gradlew runs"
    else
      fail "openjdk@21 installed but its JDK home could not be resolved at $(brew --prefix openjdk@21 2>/dev/null)/libexec/openjdk.jdk/Contents/Home"
      exit 1
    fi
  else
    java_ver=""
    if command -v java >/dev/null 2>&1; then
      java_ver=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')
    fi
    if [[ -z "$java_ver" || "$java_ver" -lt 21 ]]; then
      fail "install a JDK 21+ manually for your OS, then re-run this script"
      exit 1
    fi
    ok "java $java_ver installed"
  fi

  # --- Gradle (global; only used to bootstrap ./gradlew if it's missing —
  #     actual builds always run through ./gradlew, which pins its own
  #     Gradle version via gradle/wrapper/gradle-wrapper.properties and gets
  #     -Dorg.gradle.java.home passed explicitly, so neither the Gradle
  #     version nor the JDK used to build this project depends on anything
  #     global.) ---
  step "Gradle"
  if command -v gradle >/dev/null 2>&1; then
    ok "installed ($(gradle -v 2>/dev/null | grep -m1 '^Gradle ')) — only used to bootstrap ./gradlew if missing"
  else
    warn "not found — installing (only needed to bootstrap ./gradlew if missing)..."
    if [[ "$OS" == "Darwin" ]]; then
      brew install gradle
    else
      fail "install Gradle manually for your OS, then re-run this script"
      exit 1
    fi
    ok "installed"
  fi

  if [[ -f "$HOME/.gradle/gradle.properties" ]] && grep -q '^org\.gradle\.java\.home=' "$HOME/.gradle/gradle.properties" 2>/dev/null; then
    warn "note: ~/.gradle/gradle.properties pins org.gradle.java.home globally — left untouched; this project's ./gradlew runs override it with openjdk@21 via -Dorg.gradle.java.home"
  fi

  # --- Android SDK (adb/emulator/sdkmanager, android-34 system image, AVD —
  #     required by the android-agent and debugging-agent tool integrations) ---
  step "Android SDK"
  ANDROID_SDK_DEFAULT="$HOME/Library/Android/sdk"
  if [[ "$OS" != "Darwin" ]]; then
    warn "automatic Android SDK setup is only implemented for macOS here — install the SDK (Android Studio or cmdline-tools) manually and set ANDROID_HOME"
  else
    if command -v sdkmanager >/dev/null 2>&1; then
      ok "cmdline-tools installed"
    else
      warn "cmdline-tools not found — installing via Homebrew (this can take a while)..."
      brew install --cask android-commandlinetools
    fi

    android_home="${ANDROID_HOME:-$ANDROID_SDK_DEFAULT}"
    cmdline_bin="$(brew --prefix)/share/android-commandlinetools/cmdline-tools/latest/bin"
    export ANDROID_HOME="$android_home"
    export ANDROID_SDK_ROOT="$android_home"
    add_to_path "$android_home/emulator"
    add_to_path "$android_home/platform-tools"
    add_to_path "$cmdline_bin"
    export PATH

    if [[ "$(uname -m)" == "arm64" ]]; then
      abi="arm64-v8a"
    else
      abi="x86_64"
    fi
    system_image="system-images;android-34;google_apis;$abi"
    system_image_dir="$android_home/system-images/android-34/google_apis/$abi"

    image_present() { [[ -d "$system_image_dir" && -n "$(ls -A "$system_image_dir" 2>/dev/null)" ]]; }

    yes | sdkmanager --sdk_root="$android_home" --licenses >/dev/null 2>&1

    if image_present && command -v adb >/dev/null 2>&1 && command -v emulator >/dev/null 2>&1; then
      ok "platform-tools, emulator, and the android-34 system image are installed"
    else
      echo "  downloading platform-tools, emulator, platform 34, build-tools, and a system image (~1.5-2GB total)..."
      yes | sdkmanager --sdk_root="$android_home" --install \
        "platform-tools" "emulator" "platforms;android-34" "build-tools;34.0.0" "$system_image"

      if ! image_present; then
        warn "system image didn't extract cleanly on the first try — retrying once..."
        yes | sdkmanager --sdk_root="$android_home" --install "$system_image"
      fi

      if image_present; then
        ok "installed platform-tools, emulator, and the android-34 system image"
      else
        fail "system image still missing after retrying — check your network connection and re-run this script (AVD creation skipped for now)"
      fi
    fi

    if image_present; then
      avd_config="$HOME/.android/avd/ASAP_Pixel.avd/config.ini"
      avd_broken=false
      if avdmanager list avd 2>/dev/null | grep -q "ASAP_Pixel" && [[ -f "$avd_config" ]]; then
        image_rel_dir=$(grep '^image.sysdir.1=' "$avd_config" | cut -d= -f2)
        [[ -n "$image_rel_dir" && ! -d "$android_home/$image_rel_dir" ]] && avd_broken=true
      fi

      if [[ "$avd_broken" == true ]]; then
        warn "existing ASAP_Pixel AVD pointed at a missing system image — recreating it"
        avdmanager delete avd --name "ASAP_Pixel" >/dev/null 2>&1 || true
      fi

      if avdmanager list avd 2>/dev/null | grep -q "ASAP_Pixel"; then
        ok "AVD \"ASAP_Pixel\" already exists and its system image is present"
      else
        echo "no" | avdmanager create avd --name "ASAP_Pixel" --package "$system_image" --device "pixel_6" >/dev/null
        ok "created AVD \"ASAP_Pixel\""
      fi
    fi

    shell_rc="$HOME/.zshrc"
    home_line="export ANDROID_HOME=\"$android_home\""
    root_line="export ANDROID_SDK_ROOT=\"\$ANDROID_HOME\""
    path_line="export PATH=\"$cmdline_bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/emulator:\$PATH\""
    for line in "$home_line" "$root_line" "$path_line"; do
      grep -qxF "$line" "$shell_rc" 2>/dev/null || echo "$line" >> "$shell_rc"
    done
    if ! command -v adb >/dev/null 2>&1; then
      warn "added Android SDK env vars/PATH to $shell_rc — restart your terminal (or run: source $shell_rc) before running the backend"
    fi
  fi

  # --- Flutter SDK (optional — only needed for the flutter-agent) ---
  step "Flutter SDK (optional)"
  if command -v flutter >/dev/null 2>&1; then
    ok "installed ($(flutter --version 2>/dev/null | head -1))"
  elif [[ "$OS" != "Darwin" ]]; then
    warn "not found — automatic install is only implemented for macOS here. Install manually (https://docs.flutter.dev/get-started/install) if you want Flutter app support."
  elif [[ ! -t 0 ]]; then
    warn "not found — skipping (optional, and this script isn't running interactively to ask). Install later with: brew install --cask flutter"
  else
    read -r -p "  Flutter SDK not found — install it now for Flutter app support? [y/N] " install_flutter
    if [[ "$install_flutter" =~ ^[Yy]$ ]]; then
      warn "installing via Homebrew (this can take a while)..."
      brew install --cask flutter
      if command -v flutter >/dev/null 2>&1; then
        flutter precache --android >/dev/null 2>&1
        ok "installed ($(flutter --version 2>/dev/null | head -1))"
      else
        fail "flutter still not on PATH after install — restart your terminal (or run: source ~/.zshrc) and re-run this script"
      fi
    else
      warn "skipped — install anytime later with: brew install --cask flutter"
    fi
  fi

  # --- Node.js / npm (required by web-ui) ---
  step "Node.js / npm"
  if command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1; then
    ok "node $(node --version), npm $(npm --version) installed"
  else
    warn "not found — installing..."
    if [[ "$OS" == "Darwin" ]]; then
      brew install node
    else
      fail "install Node.js manually for your OS, then re-run this script"
      exit 1
    fi
    ok "installed"
  fi

  # --- Firebase CLI (required by the publishing-agent's App Distribution tool) ---
  step "Firebase CLI"
  if command -v firebase >/dev/null 2>&1; then
    ok "installed ($(firebase --version))"
  else
    warn "not found — installing..."
    npm install -g firebase-tools
    ok "installed"
  fi
  warn "run 'firebase login:ci' yourself once to get a CI token, then set it (with your Firebase App ID) via the admin panel once it exists — this can't be automated by this script"

  # --- Tailscale (used for remote access / Funnel option below) ---
  step "Tailscale"
  if command -v tailscale >/dev/null 2>&1; then
    ok "installed"
  else
    warn "not found — installing..."
    if [[ "$OS" == "Darwin" ]]; then
      brew install tailscale
      brew services start tailscale
    else
      fail "install Tailscale manually for your OS, then re-run this script"
      exit 1
    fi
    ok "installed"
  fi

  if tailscale status >/dev/null 2>&1; then
    ok "running and connected"
  else
    warn "installed but not connected — run: sudo tailscale up"
  fi

  # --- Gradle wrapper executable (once the root project exists) ---
  step "Gradle wrapper"
  if [[ -x gradlew ]]; then
    ok "gradlew is executable"
  elif [[ -f gradlew ]]; then
    chmod +x gradlew
    ok "made gradlew executable"
  else
    warn "no root gradlew yet — nothing to do until Phase 1 scaffolding lands"
  fi

  # --- .env files (once the modules exist) ---
  step ".env"
  if [[ -f .env.example && ! -f .env ]]; then
    cp .env.example .env
    warn "created .env from .env.example — edit it to set your LLM API keys"
  elif [[ -f .env ]]; then
    ok ".env exists"
  else
    warn "no .env.example yet — nothing to do until Phase 1 scaffolding lands"
  fi

  if [[ -f web-ui/.env.example && ! -f web-ui/.env ]]; then
    cp web-ui/.env.example web-ui/.env
    ok "created web-ui/.env from .env.example"
  fi

  echo
  echo "Setup check complete. Run ./asap.sh and choose option 1 or 2 to start the app."
}

run_all() {
  local with_funnel="$1"

  if [[ ! -x gradlew ]]; then
    fail "no root gradlew found — run option 3 (setup) first, and make sure Phase 1 scaffolding has been applied"
    exit 1
  fi
  if [[ ! -d web-ui ]]; then
    fail "no web-ui/ directory found — make sure Phase 1 scaffolding has been applied"
    exit 1
  fi

  # Pick up the Android SDK setup installs without requiring a terminal
  # restart — the backend process needs adb/emulator on PATH for the
  # debugging-agent's emulator/logcat/screenshot tools to work.
  if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    add_to_path "$ANDROID_HOME/emulator"
    add_to_path "$ANDROID_HOME/platform-tools"
    export PATH
  fi

  for port in "$FRONTEND_PORT" "$BACKEND_PORT" "$BUILD_RUNNER_PORT"; do
    pid=$(lsof -ti:"$port" -sTCP:LISTEN 2>/dev/null || true)
    if [ -n "$pid" ]; then
      echo "Stopping existing process on port $port (pid $pid)"
      kill "$pid" 2>/dev/null || true
    fi
  done
  sleep 1

  # Project-scoped JDK (see resolve_project_java_home) — passed explicitly
  # to every ./gradlew call below so this project's builds use the right
  # Java version regardless of system PATH/JAVA_HOME or any global
  # ~/.gradle/gradle.properties override, without ever touching either.
  gradle_java_home_flag=()
  project_java_home="$(resolve_project_java_home)"
  if [[ -n "$project_java_home" ]]; then
    echo "Using project-scoped JDK for Gradle builds: $project_java_home"
    gradle_java_home_flag=(-Dorg.gradle.java.home="$project_java_home")
  else
    warn "openjdk@21 not found for this project (run option 3 setup first) — falling back to gradle's default JVM resolution"
  fi

  cleanup() {
    if [ "${with_funnel:-false}" = "true" ]; then
      echo "Stopping Tailscale Funnel..."
      tailscale funnel "$FRONTEND_PORT" off >/dev/null 2>&1 || true
    fi
    kill 0
  }
  trap cleanup EXIT

  # build-runner owns all Gradle/adb/emulator execution (PLAN.md §5) — start
  # it first so chat-gateway's android-agent tools have somewhere to call.
  ./gradlew "${gradle_java_home_flag[@]}" :build-runner:run &

  echo "Waiting for build-runner on :$BUILD_RUNNER_PORT..."
  for i in $(seq 1 60); do
    if curl -s -o /dev/null "http://localhost:$BUILD_RUNNER_PORT/health"; then
      echo "build-runner is up."
      break
    fi
    sleep 1
  done

  ./gradlew "${gradle_java_home_flag[@]}" :chat-gateway:run &

  echo "Waiting for backend on :$BACKEND_PORT..."
  for i in $(seq 1 60); do
    if curl -s -o /dev/null "http://localhost:$BACKEND_PORT/health"; then
      echo "Backend is up."
      break
    fi
    sleep 1
  done

  (cd web-ui && npm run dev) &

  if [ "$with_funnel" = "true" ]; then
    echo "Starting Tailscale Funnel on :$FRONTEND_PORT..."
    tailscale funnel --bg "$FRONTEND_PORT"
    tailscale funnel status
  fi

  wait
}

echo "ASAP-Cowork"
echo "1) Run All"
echo "2) Run All with Tailscale Funnel (exposes :$FRONTEND_PORT publicly over your tailnet's HTTPS)"
echo "3) ASAP Cowork Setup"
read -rp "Choose an option [1]: " choice
choice=${choice:-1}

case "$choice" in
  1) run_all "false" ;;
  2) run_all "true" ;;
  3) run_setup ;;
  *) fail "unknown option: $choice"; exit 1 ;;
esac
