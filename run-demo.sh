#!/usr/bin/env bash
set -e

# Demo launcher for SentinelNode (Manager + 2 Workers)
# Usage: ./run-demo.sh

export APP_PEPPER='local-dev-pepper'

echo "Starting Manager..."
nohup mvn exec:java > manager.log 2>&1 &
echo $! > manager.pid
sleep 2

echo "Starting worker 1 (alice)..."
WORKER_USERNAME=alice nohup mvn exec:java -Dexec.args='worker worker-1 127.0.0.1 6000' > worker1.log 2>&1 &
echo $! > worker1.pid
sleep 1

echo "Starting worker 2 (bob)..."
WORKER_USERNAME=bob nohup mvn exec:java -Dexec.args='worker worker-2 127.0.0.1 6000' > worker2.log 2>&1 &
echo $! > worker2.pid

echo "Started Manager and 2 workers."

echo "Logs: manager.log, worker1.log, worker2.log"
echo "PIDs: manager.pid, worker1.pid, worker2.pid"
