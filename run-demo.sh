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

echo "Starting manager (GUI)…"
nohup mvn -q exec:java >/tmp/sentinel-manager.log 2>&1 &
echo $! > manager.pid

echo "  → sign in (or register) on the manager window before workers can connect."
echo "  → workers will retry up to 5 times so you have time to sign in."
sleep 4

echo "Starting worker 1 (alice) — mode: $WORKER_MODE"
WORKER_USERNAME=alice nohup mvn -q exec:java -Dexec.args="$WORKER_MODE worker-1" >/tmp/sentinel-worker1.log 2>&1 &
echo $! > worker1.pid
sleep 1

echo "Starting worker 2 (bob) — mode: $WORKER_MODE"
WORKER_USERNAME=bob nohup mvn -q exec:java -Dexec.args="$WORKER_MODE worker-2" >/tmp/sentinel-worker2.log 2>&1 &
echo $! > worker2.pid

echo
echo "  Manager log: /tmp/sentinel-manager.log"
echo "  Worker logs: /tmp/sentinel-worker1.log /tmp/sentinel-worker2.log"
echo "  Stop:        ./run-demo.sh --kill"
