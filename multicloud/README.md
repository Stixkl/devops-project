# Multi-Cloud — demo local de balanceo y failover

Simula la arquitectura multi-cloud (AKS activo + GKE respaldo) con dos
clusters kind y un balanceador global HAProxy. Evidencia y diseño completo en
`docs/BONUS_MULTICLOUD.md`; IaC real del segundo proveedor en
`terraform/modules/gke-cluster` + `terraform/multicloud.tf`.

## Levantar la demo

```bash
# 1. Dos clusters ("clouds")
kind create cluster --config multicloud/kind-azure.yaml   # localhost:31080
kind create cluster --config multicloud/kind-gcp.yaml     # localhost:32080

# 2. Workload identificando su cloud en cada cluster
sed 's/${CLOUD_PROVIDER}/azure/' multicloud/gateway-echo.yaml | \
  kubectl --context kind-cg-azure-sim apply -f -
sed 's/${CLOUD_PROVIDER}/gcp/' multicloud/gateway-echo.yaml | \
  kubectl --context kind-cg-gcp-sim apply -f -

# 3. Balanceador global
docker compose -f multicloud/docker-compose.yml up -d

# 4. Verificar balanceo round-robin entre clouds
for i in 1 2 3 4 5 6; do curl -s localhost:8090; done
# stats del LB: http://localhost:8404

# 5. Failover: "cae Azure"
kubectl --context kind-cg-azure-sim scale deploy/gateway-echo --replicas=0
for i in 1 2 3 4; do curl -s localhost:8090; done   # 100% cloud=gcp
```

## Limpieza

```bash
docker compose -f multicloud/docker-compose.yml down
kind delete cluster --name cg-azure-sim
kind delete cluster --name cg-gcp-sim
```
