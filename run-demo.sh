#!/usr/bin/env bash
set -e

# Demo launcher for SentinelNode (Manager + 2 Worker GUIs)
# Usage:
#   ./run-demo.sh             # launches manager + 2 worker GUIs
#   ./run-demo.sh --headless  # launches manager + 2 headless workers
#   ./run-demo.sh --kill      # stops everything started by run-demo.sh

cd "$(dirname "$0")"
export APP_PEPPER='local-dev-pepper'

if [ "${1:-}" = "--kill" ]; then
    for pid in manager.pid worker1.pid worker2.pid; do
        if [ -f "$pid" ]; then
            kill "$(cat "$pid")" 2>/dev/null || true
            rm -f "$pid"
        fi
    done
    echo "Stopped manager + workers."
    exit 0
fi

WORKER_MODE='worker-ui'
if [ "${1:-}" = "--headless" ]; then
    WORKER_MODE='worker'
fi

# Source demo worker tokens written by the first-run setup wizard.
DEMO_ENV="$HOME/.sentinelnode/demo-workers.env"
if [ -f "$DEMO_ENV" ]; then
    # shellcheck disable=SC1090
    source "$DEMO_ENV"
fi

W1_USER="${DEMO_WORKER_1_USERNAME:-alice}"
W1_TOKEN="${DEMO_WORKER_1_TOKEN:-}"
W2_USER="${DEMO_WORKER_2_USERNAME:-bob}"
W2_TOKEN="${DEMO_WORKER_2_TOKEN:-}"

echo "Starting manager (GUI)…"
nohup mvn -q exec:java >/tmp/sentinel-manager.log 2>&1 &
echo $! > manager.pid

echo "  → if this is your first run, complete the setup wizard, then sign in."
echo "  → workers retry for up to 90s so you have plenty of time."
sleep 8

echo "Starting worker 1 ($W1_USER) — mode: $WORKER_MODE"
WORKER_USERNAME="$W1_USER" WORKER_TOKEN="$W1_TOKEN" nohup mvn -q exec:java -Dexec.args="$WORKER_MODE worker-1" >/tmp/sentinel-worker1.log 2>&1 &
echo $! > worker1.pid
sleep 1

echo "Starting worker 2 ($W2_USER) — mode: $WORKER_MODE"
WORKER_USERNAME="$W2_USER" WORKER_TOKEN="$W2_TOKEN" nohup mvn -q exec:java -Dexec.args="$WORKER_MODE worker-2" >/tmp/sentinel-worker2.log 2>&1 &
echo $! > worker2.pid

echo
echo "  Manager log: /tmp/sentinel-manager.log"
echo "  Worker logs: /tmp/sentinel-worker1.log /tmp/sentinel-worker2.log"
echo "  Stop:        ./run-demo.sh --kill"
if [ -f "$DEMO_ENV" ]; then
    echo "  (sourced $DEMO_ENV — workers will authenticate with stored tokens)"
fi