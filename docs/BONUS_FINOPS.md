# Bonus — FinOps

Cobertura de los 5 puntos del bonus: monitoreo de costos (OpenCost en cluster,
en vivo), políticas de ahorro como código (Spot, autoscaling, scale-to-zero),
dashboard de costos y utilización (Grafana), análisis de optimización con
ahorros cuantificados, y documentación de estrategias.

## 1. Monitoreo de costos — OpenCost (verificado en vivo)

OpenCost 1.120 instalado vía Helm en el cluster (`ns opencost`) con un
Prometheus dedicado (kube-state-metrics + node-exporter + cAdvisor). Asignación
de costos por namespace consultada en vivo
(`/allocation/compute?window=30m&aggregate=namespace`):

```
namespace              cpuCost   ramCost   totalCost  cpuEff
chaos-mesh              0.00012   0.00009    0.00021    1.6%
circleguard-chaos       0.00015   0.00003    0.00019    0.4%
kube-system             0.00059   0.00002    0.00061    2.2%
opencost                0.00001   0.00001    0.00002    9.5%
```

(`docs/finops/opencost-allocation-sample.txt` y `opencost-raw.json`.)
La columna `cpuEff` (uso real / requests) expone el sobredimensionamiento —
insumo directo del análisis de right-sizing (§4).

```bash
helm install opencost opencost/opencost -n opencost --create-namespace \
  --set opencost.prometheus.internal.serviceName=prometheus-server \
  --set opencost.prometheus.internal.namespaceName=opencost \
  --set opencost.prometheus.internal.port=80
```

## 2. Costos de la infraestructura (Terraform / Azure)

Costo estimado mensual por ambiente (precios públicos de referencia,
pay-as-you-go, 730 h/mes; AKS Free tier ⇒ control plane $0). Dev se desplegó
realmente en `centralus`; stage/prod son estimaciones de la arquitectura
diseñada y no representan recursos activos en la suscripción Students. Para cifras al
día se deja cableado Infracost
(`infracost breakdown --path circleguard-infra/terraform/environments/<env> --terraform-var-file <env>.tfvars`,
requiere API key gratuita):

| Ambiente | Cómputo | Detalle | Discos (128 GB) | Otros | Total/mes |
|---|---|---|---|---|---|
| dev | $60.74 | 2 × B2s ($30.37) | ~$24 | — | **~$85** |
| stage | $182.21 | 3 × B2ms ($60.74) + burst Spot 0-3 (base $0) | ~$36 | — | **~$218** |
| prod | $668.10 | system 3 × B4ms ($121.47) + user 5 × B2ms ($60.74) | ~$96 | ACR Standard $20 | **~$784** |
| **Total** | | | | | **~$1,087/mes** |

## 3. Políticas de ahorro implementadas (como código)

1. **Spot instances** (`circleguard-infra/terraform/environments/stage`, módulo `aks-cluster`): pool `burst`
   de stage en `priority = "Spot"` con `spot_max_price = -1` y autoscaling
   **0→3** (scale-to-zero cuando no hay carga). Spot en Azure: hasta **-90%**
   vs on-demand (B2ms ~$60.74 → ~$6-18/mes por nodo).
   - Corrección aplicada: el pool `system` de prod estaba marcado Spot — mala
     práctica (evicciones del plano de sistema) y además ignorado
     silenciosamente por el módulo (el primer pool va a `default_node_pool`,
     que no admite priority). Ahora system = on-demand y Spot solo en pools
     de usuario tolerantes a evicción.
2. **Autoscaling** en todos los pools de stage/prod (min/max declarados);
   dev con tamaño mínimo (2 × B2s burstable).
3. **Scale-to-zero fuera de horario** (`scripts/scale-to-zero.sh` en este repo):
   `az aks stop/start` de dev y stage programable (L-V 20:00→07:00 + fines de
   semana). Un cluster detenido no factura cómputo. Ahorro ≈ **65%** del
   cómputo de no-producción: (~$61 + $182) × 0.65 ≈ **$158/mes**. Para dev real:
   `AZURE_RG_DEV=rg-circle-guard-dev AKS_CLUSTER_DEV=cg-aks-dev ./scripts/scale-to-zero.sh stop --env dev`.
4. **VMs B-series (burstable)**: toda la flota usa B2s/B2ms/B4ms — créditos de
   CPU para cargas con valles, 30-50% más baratas que las D-series
   equivalentes (D2s_v3 ≈ $70 vs B2ms ≈ $61 con el doble de RAM que B2s).
5. **Retención acotada de observabilidad**: Prometheus 10d/40 GB
   (`circleguard-infra/k8s/master/observability/helm-values-prometheus-stack.yaml`) e ILM en
   Elasticsearch — el almacenamiento de métricas/logs no crece sin límite.
6. **Etiquetado de costos**: `tags` (Project/Environment/ManagedBy) en todos
   los recursos Terraform → cost allocation por ambiente en Azure Cost
   Management.

## 4. Dashboard de costos y utilización

`circleguard-infra/observability/grafana/dashboards/circleguard-costs.json` (uid `cg-costs`,
provisionado automáticamente con el stack de `circleguard-infra/observability/`; el mismo
directorio incluye además 8 dashboards por servicio en `dashboards/services/`, uid `cg-svc-<name>`):

- Costo total del cluster USD/h y proyección mensual (`node_total_hourly_cost`).
- Costo de CPU y RAM por namespace (asignación OpenCost).
- **Sobredimensionamiento**: uso real de CPU vs requests por namespace —
  ratios < 1 indican capacidad reservada pagada y no usada.

## 5. Análisis de optimización (con los datos medidos)

| Hallazgo | Evidencia | Acción | Ahorro estimado |
|---|---|---|---|
| `cpuEff` 0.4–2.2% en cargas demo | OpenCost (§1) | Right-sizing de requests (los Deployments de `circleguard-infra/chaos/00-target-env.yaml` ya declaran requests ajustados) | hasta 50% del costo asignado |
| Cómputo no-prod 24/7 | Tabla §2 | `aks-stop-start.sh` programado | ~$158/mes |
| Burst on-demand en stage | main.tf | Pool Spot 0-3 | ~$45-55/mes por nodo de ráfaga |
| Spot mal ubicado en system pool | main.tf (corregido) | Spot solo en pools de usuario | evita downtime (costo de incidente) |
| Sitio DR siempre encendido | multicloud.tf | GKE pasivo en nodos Spot + autoscaling min 1 | ~70% del costo del DR |

**Ahorro total estimado: ~$200-250/mes (~20% de la factura)** sin tocar
producción, más la reducción de riesgo del fix del system pool.
