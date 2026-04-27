#!/usr/bin/env bash
# ============================================================
# Start all four services in the correct order:
#   1. db-server      (port 8090)
#   2. fs-server      (port 8091)
#   3. web-server     (port 8092)
#   4. host           (port 8080) - last, since it connects to the others
# ============================================================
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
    echo "ERROR: ANTHROPIC_API_KEY env var is not set. Export it first."
    exit 1
fi

mkdir -p logs

echo ">> Building all modules..."
mvn -q -DskipTests clean package

echo ">> Starting db-server (port 8090)..."
java -jar db-server/target/db-server-1.0.0-SNAPSHOT.jar > logs/db-server.log 2>&1 &
echo $! > logs/db-server.pid

echo ">> Starting fs-server (port 8091)..."
java -jar fs-server/target/fs-server-1.0.0-SNAPSHOT.jar > logs/fs-server.log 2>&1 &
echo $! > logs/fs-server.pid

echo ">> Starting web-server (port 8092)..."
java -jar web-server/target/web-server-1.0.0-SNAPSHOT.jar > logs/web-server.log 2>&1 &
echo $! > logs/web-server.pid

echo ">> Waiting 15s for servers to initialize..."
sleep 15

echo ">> Starting host (port 8080)..."
java -jar host/target/host-1.0.0-SNAPSHOT.jar > logs/host.log 2>&1 &
echo $! > logs/host.pid

echo ""
echo "============================================================"
echo "  All services starting. Check logs/ for output."
echo "  Try:  curl 'http://localhost:8080/chat?q=How+many+CHECKING+transactions+failed+enrichment%3F'"
echo "  Stop: ./scripts/stop-all.sh"
echo "============================================================"
