#!/usr/bin/env bash
# Stop all four services using their saved PIDs.
set -euo pipefail

cd "$(dirname "$0")/.."

for svc in host db-server fs-server web-server; do
    if [ -f "logs/${svc}.pid" ]; then
        pid=$(cat "logs/${svc}.pid")
        if kill -0 "$pid" 2>/dev/null; then
            echo ">> Stopping $svc (pid $pid)..."
            kill "$pid" || true
        fi
        rm -f "logs/${svc}.pid"
    fi
done
echo ">> All stopped."
