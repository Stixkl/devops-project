#!/usr/bin/env bash
# FinOps: scale-to-zero de los clusters AKS de no-producción fuera de horario.
# Azure no cobra compute de un cluster detenido (solo discos/IPs).
# Programable con cron / GitHub Actions schedule:
#   - parada:  L-V 20:00  ->  ./aks-stop-start.sh stop dev stage
#   - arranque: L-V 07:00 ->  ./aks-stop-start.sh start dev stage
# Ahorro: ~65% del compute de dev/stage (13h/día parados + fines de semana).
set -euo pipefail

ACTION="${1:?uso: $0 <stop|start> <env...>}"
shift

declare -A RG=( [dev]="rg-circle-guard-dev" [stage]="rg-circle-guard-stage" )
declare -A CLUSTER=( [dev]="cg-aks-dev" [stage]="cg-aks-stage" )

for env in "$@"; do
  rg="${RG[$env]:?entorno desconocido: $env (dev|stage)}"
  cluster="${CLUSTER[$env]}"
  echo ">> az aks $ACTION --name $cluster --resource-group $rg"
  az aks "$ACTION" --name "$cluster" --resource-group "$rg" --no-wait
done
