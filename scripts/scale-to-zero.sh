#!/usr/bin/env bash
# Scale-to-zero for CircleGuard non-production AKS clusters.
#
# Stops dev and stage clusters outside business hours (Mon–Fri 20:00→07:00 and
# weekends) to eliminate compute billing.  Estimated saving: ~$158/month
# (~65% of dev+stage compute).
#
# Usage:
#   ./scale-to-zero.sh stop  [--env dev|stage|all]
#   ./scale-to-zero.sh start [--env dev|stage|all]
#   ./scale-to-zero.sh auto            # stop or start based on current time
#
# Required env vars (or pass via CLI):
#   AZURE_SUBSCRIPTION  – Azure subscription ID
#   AZURE_RG_DEV        – resource group for dev cluster   (default: circleguard-dev-rg)
#   AZURE_RG_STAGE      – resource group for stage cluster (default: circleguard-stage-rg)
#   AKS_CLUSTER_DEV     – AKS cluster name for dev         (default: circleguard-dev-aks)
#   AKS_CLUSTER_STAGE   – AKS cluster name for stage       (default: circleguard-stage-aks)
#
# Cron schedule (add to crontab or Azure Automation):
#   0 20 * * 1-5  /path/to/scale-to-zero.sh stop  --env all   # weeknights 20:00
#   0  7 * * 1-5  /path/to/scale-to-zero.sh start --env all   # weekdays   07:00
#   0 20 * * 5    /path/to/scale-to-zero.sh stop  --env all   # Friday evening
#   0  7 * * 1    /path/to/scale-to-zero.sh start --env all   # Monday morning

set -euo pipefail

# ---------------------------------------------------------------------------
# Defaults (override with env vars or export before calling)
# ---------------------------------------------------------------------------
AZURE_SUBSCRIPTION="${AZURE_SUBSCRIPTION:-}"
AZURE_RG_DEV="${AZURE_RG_DEV:-circleguard-dev-rg}"
AZURE_RG_STAGE="${AZURE_RG_STAGE:-circleguard-stage-rg}"
AKS_CLUSTER_DEV="${AKS_CLUSTER_DEV:-circleguard-dev-aks}"
AKS_CLUSTER_STAGE="${AKS_CLUSTER_STAGE:-circleguard-stage-aks}"

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
ACTION=""
TARGET_ENV="all"

while [[ $# -gt 0 ]]; do
  case "$1" in
    stop|start|auto)
      ACTION="$1"
      shift
      ;;
    --env)
      TARGET_ENV="${2:-all}"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$ACTION" ]]; then
  echo "Usage: $0 stop|start|auto [--env dev|stage|all]" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Auto mode: decide based on current UTC hour and day-of-week
# ---------------------------------------------------------------------------
if [[ "$ACTION" == "auto" ]]; then
  HOUR=$(date -u +"%H")
  DOW=$(date -u +"%u")   # 1=Mon … 7=Sun

  # Business hours: Mon–Fri 07:00–20:00 UTC
  if [[ "$DOW" -le 5 && "$HOUR" -ge 7 && "$HOUR" -lt 20 ]]; then
    ACTION="start"
  else
    ACTION="stop"
  fi
  echo "Auto mode → $ACTION (UTC $(date -u '+%a %H:%M'), DOW=$DOW)"
fi

# ---------------------------------------------------------------------------
# Prerequisites
# ---------------------------------------------------------------------------
if ! command -v az &>/dev/null; then
  echo "ERROR: Azure CLI (az) not found. Install: https://aka.ms/installazurecliwindows" >&2
  exit 1
fi

if [[ -n "$AZURE_SUBSCRIPTION" ]]; then
  az account set --subscription "$AZURE_SUBSCRIPTION"
fi

# ---------------------------------------------------------------------------
# Cluster operations
# ---------------------------------------------------------------------------
run_az_aks() {
  local op="$1"   # stop | start
  local rg="$2"
  local cluster="$3"

  echo "→ az aks $op --resource-group $rg --name $cluster"
  if az aks show --resource-group "$rg" --name "$cluster" \
       --query "provisioningState" -o tsv 2>/dev/null | grep -q "Succeeded"; then
    az aks "$op" --resource-group "$rg" --name "$cluster" --no-wait
    echo "  Submitted (--no-wait). Use 'az aks show' to track state."
  else
    echo "  WARN: cluster $cluster in $rg not found or not in Succeeded state — skipping."
  fi
}

echo "===== CircleGuard scale-to-zero: $ACTION ($TARGET_ENV) ====="

case "$TARGET_ENV" in
  dev)
    run_az_aks "$ACTION" "$AZURE_RG_DEV" "$AKS_CLUSTER_DEV"
    ;;
  stage)
    run_az_aks "$ACTION" "$AZURE_RG_STAGE" "$AKS_CLUSTER_STAGE"
    ;;
  all)
    run_az_aks "$ACTION" "$AZURE_RG_DEV"   "$AKS_CLUSTER_DEV"
    run_az_aks "$ACTION" "$AZURE_RG_STAGE" "$AKS_CLUSTER_STAGE"
    ;;
  *)
    echo "ERROR: --env must be dev|stage|all" >&2
    exit 1
    ;;
esac

echo "===== Done ====="
