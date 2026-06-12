# Bonus — Implementación Multi-Cloud

Cobertura de los 4 requisitos del bonus. El segundo proveedor (GCP/GKE) tiene
**IaC desplegable real** — root Terraform aislado `terraform/environments/gcp-dr/`
con cluster zonal spot + bucket GCS + SA de Velero — más overlay de app
(`k8s/gcp/`) y workflow `cd-gcp.yml`. Runbook completo en
`docs/DESPLIEGUE_GCP.md` (la ejecución contra el cloud real requiere
credenciales GCP). Estrategia de respaldo entre clouds (Velero activo-pasivo,
AKS→GCS), balanceo de carga entre proveedores (HAProxy `multicloud/haproxy.real.cfg`
sobre las IPs públicas reales de ambos clouds; demo local con dos clusters kind
en `multicloud/`), y comparativa de rendimiento/costos.

## Arquitectura

```
                      ┌──────────────────────────┐
   usuarios ────────► │  Balanceador global       │   (demo: HAProxy :8090;
                      │  health-check + failover  │    real: Traffic Manager
                      └─────────┬─────────┬──────┘    o Cloud DNS geo-LB)
                          50%   │         │   50%
                ┌───────────────▼──┐   ┌──▼───────────────┐
                │ AZURE — AKS      │   │ GCP — GKE (DR)   │
                │ cg-aks-prod      │   │ cg-gke-dr        │
                │ (activo)         │   │ (pasivo, Spot)   │
                │ environments/    │   │ terraform/        │
                │   prod/          │   │   multicloud.tf   │
                └────────┬─────────┘   └──▲───────────────┘
                         │   Velero        │ restore
                         └──► bucket GCS ──┘
                          (respaldo cruzado de cloud)
```

## 1. Despliegue en dos proveedores (IaC)

> Todas las rutas de este documento viven en el repo
> [circleguard-infra](https://github.com/JuanAmor8/circleguard-infra).

- **Azure (activo)**: módulo `circleguard-infra/terraform/modules/aks-cluster` × 3 ambientes,
  cada uno en su propio root aislado (`terraform/environments/{dev,stage,prod}/`),
  con state separado (`dev.tfstate`/`stage.tfstate`/`prod.tfstate`).
- **GCP (respaldo)**: módulo espejo `circleguard-infra/terraform/modules/gke-cluster`
  (`google_container_cluster` + node pools con autoscaling y Spot, interfaz
  de variables equivalente a la del módulo AKS) instanciado en el root aislado
  `circleguard-infra/terraform/environments/gcp-dr/` como `cg-gke-dr` (zonal
  `us-central1-a`, 2× `e2-medium` spot), con state separado (`gcp-dr.tfstate`).
  El root también crea el bucket GCS `cg-velero-dr-<project>` y la SA de Velero.
  Desplegable real con `terraform apply -var-file=gcp-dr.tfvars` (ver
  `docs/DESPLIEGUE_GCP.md`).
- **Validación**: `terraform init -backend=false && terraform validate` →
  `Success! The configuration is valid` (incluye ambos providers).

## 2. Estrategia de respaldo entre clouds

Activo-pasivo con **respaldo cruzado de proveedor**:

- `circleguard-infra/k8s/dr/velero-schedule.yaml`: backup diario (02:00 UTC, TTL 7d) de los
  namespaces circleguard desde AKS hacia un **bucket GCS** — perder Azure no
  pierde también los respaldos.
- Restauración en el sitio pasivo: `velero restore create --from-backup <id>`
  sobre GKE.
- Objetivos: **RPO 24 h** (frecuencia del schedule), **RTO < 1 h** (cluster
  GKE ya provisionado en caliente con capacidad mínima Spot + autoscaling).
- Datos: PostgreSQL con backup lógico al mismo bucket (documentado en el
  manifest); Kafka/Redis se consideran reconstruibles (event replay/cache).

## 3. Balanceo de carga entre proveedores (demo en vivo)

Dos clusters kind simulan los clouds (`circleguard-infra/multicloud/kind-{azure,gcp}.yaml`),
cada uno corre el workload `gateway-echo` que se identifica con su cloud;
HAProxy (`multicloud/haproxy.cfg`) balancea round-robin con health checks.

**Round-robin entre clouds** (capturado en vivo):

```
$ for i in 1..6; do curl -s localhost:8090; done
{"service":"circleguard-gateway","cloud":"azure","status":"UP"}
{"service":"circleguard-gateway","cloud":"gcp","status":"UP"}
{"service":"circleguard-gateway","cloud":"azure","status":"UP"}
{"service":"circleguard-gateway","cloud":"gcp","status":"UP"}
...
```

**Failover automático** — se tumba "Azure" (`scale --replicas=0`); el health
check (fall 2 × 2s) lo expulsa y el 100% va al otro cloud:

```
=== Failover (Azure caído) ===
{"cloud":"gcp"} {"cloud":"gcp"} {"cloud":"gcp"} {"cloud":"gcp"}
=== Recuperación (Azure de vuelta, rise 2) ===
{"cloud":"gcp"} {"cloud":"azure"} {"cloud":"gcp"} {"cloud":"azure"} ...
```

Reproducción: `circleguard-infra/multicloud/README.md`. En producción este rol lo cumple Azure
Traffic Manager / GCP Cloud DNS con health checks equivalentes.

## 4. Comparativa entre clouds

> Números reales AKS vs GKE (loop curl contra las IPs públicas de cada gateway)
> se capturan al ejecutar el runbook `docs/DESPLIEGUE_GCP.md` §7 y se pegan aquí.
> Abajo, el benchmark de la demo local mientras tanto.

**Benchmark local** (20 req por sitio, misma máquina — mide el camino completo
LB→NodePort→pod; en clouds reales dominaría la geografía):

```
azure-sim: promedio 213.8 ms (20 req)
gcp-sim:   promedio 212.3 ms (20 req)
```

**Comparativa real AKS vs GKE** (precios públicos eastus / us-central1):

| Dimensión | AKS (Azure) | GKE (GCP) |
|---|---|---|
| Control plane | Free tier $0 (SLA 99.5%) / Standard $73/mes (SLA 99.95%) | $74.40/mes por cluster (primer cluster zonal con crédito $74.40/mes) |
| Nodo 2 vCPU / 4-8 GB | B2s $30.37 / B2ms $60.74 | e2-medium $24.46 / e2-standard-2 $48.92 |
| Spot/preemptible | hasta -90% | hasta -91% |
| Autoscaling | cluster-autoscaler gestionado | node auto-provisioning (más granular) |
| Versiones k8s | canal manual/auto | release channels (REGULAR usado en el módulo) |
| Veredicto | activo (ecosistema del proyecto: ACR, AAD RBAC, GitHub Actions CD) | pasivo DR (nodos ~20% más baratos + Spot agresivo → DR barato) |

## Archivos

| Pieza | Ruta |
|---|---|
| Módulo GKE | `circleguard-infra/terraform/modules/gke-cluster/` |
| Root DR (real) | `circleguard-infra/terraform/environments/gcp-dr/` (cluster + bucket Velero + SA) |
| Overlay app GKE | `circleguard-infra/k8s/gcp/` + `k8s/namespaces/namespace-dr.yaml` |
| Re-sellado secretos GKE | `circleguard-infra/scripts/seal-gcp-secrets.sh` |
| Workflow CD GKE | `devops-project/.github/workflows/cd-gcp.yml` |
| Respaldo cruzado | `circleguard-infra/k8s/dr/velero-schedule.yaml` + `scripts/velero-install-gcp.sh` |
| Balanceo (real + demo) | `circleguard-infra/multicloud/` (`haproxy.real.cfg` IPs reales + kind ×2) |
| Runbook | `devops-project/docs/DESPLIEGUE_GCP.md` |
