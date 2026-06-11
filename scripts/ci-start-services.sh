#!/usr/bin/env bash
# Start the 6 core CircleGuard services from their bootJars for CI E2E runs.
# Assumes infra (postgres, kafka, redis, neo4j, openldap) is already up on localhost
# via docker-compose.dev.yml. Services read their application.yml defaults, which
# already target localhost infra. PIDs are written to /tmp/cg-pids for teardown.

set -euo pipefail

declare -A PORTS=(
  [auth]=8180 [identity]=8083 [gateway]=8087
  [form]=8086 [promotion]=8088 [notification]=8082
)

PID_FILE=/tmp/cg-pids
: > "$PID_FILE"

# Wait for the datastores the services need at startup; promotion-service in
# particular opens a Neo4j connection from a @PostConstruct and crashes if
# bolt is not up yet.
echo "Waiting for datastores..."
for target in "postgres:5432" "redis:6379" "kafka:9092" "neo4j:7687" "openldap:389"; do
  name=${target%%:*}; port=${target##*:}
  ready=false
  for i in $(seq 1 40); do
    if (exec 3<>"/dev/tcp/localhost/$port") 2>/dev/null; then
      exec 3>&- 3<&- || true
      echo "  $name ready on :$port"
      ready=true
      break
    fi
    sleep 3
  done
  if [ "$ready" = false ]; then
    echo "  ERROR: $name never opened :$port"; docker ps -a; exit 1
  fi
done

echo "Building bootJars..."
./gradlew \
  :services:circleguard-auth-service:bootJar \
  :services:circleguard-identity-service:bootJar \
  :services:circleguard-gateway-service:bootJar \
  :services:circleguard-form-service:bootJar \
  :services:circleguard-promotion-service:bootJar \
  :services:circleguard-notification-service:bootJar \
  --no-daemon

for svc in "${!PORTS[@]}"; do
  port=${PORTS[$svc]}
  jar=$(ls services/circleguard-$svc-service/build/libs/*.jar | head -1)
  echo "Starting $svc-service on :$port ($jar)"
  java -jar "$jar" --server.port="$port" > "/tmp/$svc.log" 2>&1 &
  echo $! >> "$PID_FILE"
done

echo "Waiting for services to become healthy..."
for svc in "${!PORTS[@]}"; do
  port=${PORTS[$svc]}
  ready=false
  for i in $(seq 1 40); do
    if curl -sf "http://localhost:$port/actuator/health" -o /dev/null; then
      echo "  $svc-service ready"
      ready=true
      break
    fi
    sleep 3
  done
  if [ "$ready" = false ]; then
    echo "  WARNING: $svc-service did not become healthy; dumping last log lines:"
    tail -n 80 "/tmp/$svc.log" || true
  fi
done

echo "Service startup phase complete."
