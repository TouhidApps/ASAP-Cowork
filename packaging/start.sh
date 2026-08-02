#!/usr/bin/env bash
# Starts ASAP-Cowork: build-runner (background) + chat-gateway (foreground),
# then opens the chat UI in your browser once it's ready. Press Ctrl+C in
# this window to stop everything.
set -e
cd "$(dirname "$0")"

if ! command -v java >/dev/null 2>&1; then
    echo "Java is not installed or not on your PATH. Install a Java 21+ runtime and try again." >&2
    exit 1
fi

echo "Starting build-runner..."
java -jar build-runner-all.jar > build-runner.log 2>&1 &
BUILD_RUNNER_PID=$!

cleanup() {
    echo ""
    echo "Stopping ASAP-Cowork..."
    kill "$BUILD_RUNNER_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Wait for build-runner to come up before starting chat-gateway, which
# talks to it — see build-runner.log if this takes longer than ~30s.
BUILD_RUNNER_READY=0
for _ in $(seq 1 30); do
    if curl -s -o /dev/null "http://localhost:8090/health" 2>/dev/null; then
        BUILD_RUNNER_READY=1
        break
    fi
    sleep 1
done
if [ "$BUILD_RUNNER_READY" -ne 1 ]; then
    echo ""
    echo "WARNING: build-runner did not respond on port 8090 after 30s."
    echo "It may have failed to start (often because something else is already using port 8090 — close any other ASAP-Cowork window and try again) or is still starting; check build-runner.log for details."
    echo "Continuing to start chat-gateway anyway, but builds/emulator/device features won't work until build-runner is up."
    echo ""
fi

# Open the browser in the background once chat-gateway itself answers,
# without blocking chat-gateway's own startup below.
(
    for _ in $(seq 1 30); do
        sleep 1
        if curl -s -o /dev/null "http://localhost:8081/health" 2>/dev/null; then
            if command -v open >/dev/null 2>&1; then
                open "http://localhost:8081"
            elif command -v xdg-open >/dev/null 2>&1; then
                xdg-open "http://localhost:8081"
            fi
            break
        fi
    done
) &

echo "Starting chat-gateway..."
echo "Once ready, ASAP-Cowork will open at http://localhost:8081"
echo ""
java -jar chat-gateway-all.jar
