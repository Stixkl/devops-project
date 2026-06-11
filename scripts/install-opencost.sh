#!/usr/bin/env bash
# Install OpenCost 1.120 + dedicated Prometheus stack in the target AKS cluster.
#
# OpenCost provides real-time cost allocation by namespace (CPU, RAM, storage)
# feeding the Grafana cost dashboard (uid: cg-costs) and the FinOps analysis
# documented in docs/BONUS_FINOPS.md.
#
# Usage:
#   ./install-opencost.sh [--context <kubectl-context>] [--namespace opencost]
#
# Prerequisites:
#   - kubectl configured for the target cluster
#   - helm 3.x installed
#   - Prometheus already running, OR use --install-prometheus to deploy a
#     dedicated instance (recommended for isolated cost data)
#
# What this installs:
#   1. opencost Helm chart (opencost/opencost) in namespace `opencost`
#   2. Dedicated kube-prometheus-stack (optional) for cost metrics isolation
#   3. ServiceMonitor so the main Grafana stack can also scrape opencost metrics

set -euo pipefail

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------
KUBE_CONTEXT="${KUBE_CONTEXT:-}"
NAMESPACE="${NAMESPACE:-opencost}"
INSTALL_PROMETHEUS="${INSTALL_PROMETHEUS:-false}"
PROMETHEUS_NS="${PROMETHEUS_NS:-opencost}"
PROMETHEUS_SVC="${PROMETHEUS_SVC:-prometheus-server}"
PROMETHEUS_PORT="${PROMETHEUS_PORT:-80}"
OPENCOST_VERSION="${OPENCOST_VERSION:-1.120.0}"

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --context)       KUBE_CONTEXT="$2";       shift 2 ;;
    --namespace)     NAMESPACE="$2";           shift 2 ;;
    --install-prometheus) INSTALL_PROMETHEUS="true"; shift ;;
    --opencost-version)  OPENCOST_VERSION="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

KUBECTL_OPTS=""
[[ -n "$KUBE_CONTEXT" ]] && KUBECTL_OPTS="--context $KUBE_CONTEXT"
HELM_OPTS=""
[[ -n "$KUBE_CONTEXT" ]] && HELM_OPTS="--kube-context $KUBE_CONTEXT"

# ---------------------------------------------------------------------------
# Prerequisites
# ---------------------------------------------------------------------------
for cmd in kubectl helm; do
  if ! command -v "$cmd" &>/dev/null; then
    echo "ERROR: $cmd not found." >&2
    exit 1
  fi
done

echo "===== Installing OpenCost $OPENCOST_VERSION into namespace '$NAMESPACE' ====="

# ---------------------------------------------------------------------------
# 1. Helm repos
# ---------------------------------------------------------------------------
echo "→ Adding Helm repos..."
helm repo add opencost https://opencost.github.io/opencost-helm-chart          $HELM_OPTS 2>/dev/null || true
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts \
                                                                                $HELM_OPTS 2>/dev/null || true
helm repo update $HELM_OPTS

kubectl $KUBECTL_OPTS create namespace "$NAMESPACE" --dry-run=client -o yaml \
  | kubectl $KUBECTL_OPTS apply -f -

# ---------------------------------------------------------------------------
# 2. Optional: dedicated Prometheus
# ---------------------------------------------------------------------------
if [[ "$INSTALL_PROMETHEUS" == "true" ]]; then
  echo "→ Installing kube-prometheus-stack (dedicated for OpenCost)..."
  helm upgrade --install prometheus-opencost prometheus-community/prometheus \
    $HELM_OPTS \
    --namespace "$NAMESPACE" \
    --set server.persistentVolume.size=8Gi \
    --set server.retention=15d \
    --set alertmanager.enabled=false \
    --set pushgateway.enabled=false \
    --wait
  PROMETHEUS_SVC="prometheus-opencost-server"
  PROMETHEUS_NS="$NAMESPACE"
  PROMETHEUS_PORT="80"
fi

# ---------------------------------------------------------------------------
# 3. OpenCost
# ---------------------------------------------------------------------------
echo "→ Installing OpenCost..."
helm upgrade --install opencost opencost/opencost \
  $HELM_OPTS \
  --namespace "$NAMESPACE" \
  --version "$OPENCOST_VERSION" \
  --set opencost.exporter.defaultClusterId=circleguard \
  --set opencost.prometheus.internal.enabled=true \
  --set opencost.prometheus.internal.serviceName="$PROMETHEUS_SVC" \
  --set opencost.prometheus.internal.namespaceName="$PROMETHEUS_NS" \
  --set opencost.prometheus.internal.port="$PROMETHEUS_PORT" \
  --set opencost.ui.enabled=true \
  --set opencost.ui.service.type=ClusterIP \
  --wait

# ---------------------------------------------------------------------------
# 4. ServiceMonitor so the main Grafana stack scrapes opencost metrics
# ---------------------------------------------------------------------------
echo "→ Creating ServiceMonitor for opencost..."
kubectl $KUBECTL_OPTS apply -f - <<'EOF'
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: opencost
  namespace: opencost
  labels:
    app: opencost
    release: prometheus   # matches the main prometheus-operator serviceMonitorSelector
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: opencost
  namespaceSelector:
    matchNames:
      - opencost
  endpoints:
    - port: http
      path: /metrics
      interval: 60s
EOF

# ---------------------------------------------------------------------------
# 5. Smoke-test
# ---------------------------------------------------------------------------
echo "→ Waiting for opencost pod to be ready..."
kubectl $KUBECTL_OPTS rollout status deployment/opencost -n "$NAMESPACE" --timeout=120s

echo ""
echo "===== OpenCost installed ====="
echo ""
echo "Port-forward to access the UI:"
echo "  kubectl $KUBECTL_OPTS port-forward svc/opencost 9090:9090 -n $NAMESPACE"
echo "  Then open: http://localhost:9090"
echo ""
echo "Query allocation API:"
echo "  kubectl $KUBECTL_OPTS port-forward svc/opencost 9003:9003 -n $NAMESPACE"
echo "  curl 'http://localhost:9003/allocation/compute?window=30m&aggregate=namespace'"
echo ""
echo "Grafana dashboard uid: cg-costs (provisioned via circleguard-infra/observability/)"
