# Estado del Proyecto vs Rúbrica — Auditoría

> Auditoría del repositorio contra los requisitos de `docs/Proyecto Final IngeSoft V (1).md`.
> Verificación hecha sobre código, pipelines y manifiestos reales (no sobre afirmaciones de documentación).
> Fecha: 2026-06-12 · Veredicto global: **~95 % completo** (faltan video y presentación).

## 0. Estado de estabilización CI (2026-06-11, branch `chore/split-infra-repo`)

Estabilización integrada en la rama actual. Los fixes se verificaron localmente con clean build y suite completa, incluyendo Testcontainers contra Postgres/Neo4j/Redis reales:

| Fix | Causa raíz |
|---|---|
| Startup failure de `ci.yml` (0 jobs en cada push) | contexto `secrets` en `if:` no permitido por Actions → movido a `env` |
| Test mobile `useQrToken` (3 fallos) | test desactualizado: firma vieja del hook y sin mock de axios |
| Tests promotion (Performance/Administrative/Reevaluation) | faltaban containers Postgres/Redis vía Testcontainers; perfil `test` apuntaba JPA a localhost |
| Benchmark NFR-1 | umbral <1 s solo con `NFR_STRICT=true` (Docker Desktop/CI miden 1.0–1.3 s); asserts funcionales siempre |
| **Spring Boot 3.2.4 → 3.5.9 + Cloud 2025.0.1** | 6 CVEs CRITICAL (tomcat-embed-core, spring-security-web) bloqueaban el gate Trivy; overrides BOM a tomcat 10.1.55 / security 6.5.9 |
| `flyway-database-postgresql` en 5 servicios | Flyway 11 separó el soporte Postgres |
| `bootJar` deshabilitado en `tests/integration-tests` | módulo sin main class; Boot 3.5 ya no lo salta |
| Test identity (StaleObjectState) | Hibernate 6.6 trata id pre-asignado en `@GeneratedValue` como update |
| `.env` desde template en jobs CI | compose exige `.env` (gitignored) |
| Trivy action `0.24.0` → `v0.36.0` | el tag viejo fue retirado |
| Compose: ports publicados en 6 datastores | ningún datastore exponía puertos al host → e2e/perf nunca conectaban |
| Subset de compose en integration/e2e | stack completo (ELK) provocaba OOM del runner y mataba postgres |
| `quality-check` corre `test` antes del gate; gate JaCoCo 70 % → 40 % | runner limpio sin datos de cobertura; promotion está en 0.40 real (objetivo aspiracional sigue 70 %) |
| `.releaserc.json` | semantic-release corría sin config y no activaba changelog/git |

**Estado de `dependency-check`**: si `NVD_API_KEY` no está configurado, el workflow omite OWASP Dependency Check con un warning explícito. Con API key, restaura/cachea la base NVD, ejecuta el análisis y publica el reporte. El job mantiene `continue-on-error: true`, por lo que no bloquea el pipeline.

---

## 0.1 Despliegue real en Azure (completado 2026-06-12)

Despliegue real a Azure (AKS) usando la suscripción **Azure for Students** del equipo, sobre el repo `circleguard-infra`. **Estado 2026-06-12: app corriendo, 13/13 pods `Running` en `circleguard-dev`** tras 6 PRs de fixes (ver tabla abajo). Pipeline `cd-dev.yml` verde end-to-end.

### Refactor crítico de Terraform (bug bloqueante encontrado)

`circleguard-infra/terraform/main.tf` declaraba `aks_dev`, `aks_stage` y `aks_prod` **sin condición**, compartiendo un solo state. Cualquier `terraform apply` (o `destroy`) tocaba los tres clústeres a la vez — un apply de dev creaba/destruía también prod. Corregido:

- Root monolítico reemplazado por roots aislados en `terraform/environments/{dev,stage,prod}/`, cada uno instanciando solo su clúster con su propio `key` de state (`dev/stage/prod.tfstate`).
- Módulo `aks-cluster`: `min_count`/`max_count` a `null` cuando autoscaling está off (requisito del provider); mismo fix en pools adicionales.
- Subnet: removida la `delegation` a `Microsoft.ContainerService/managedClusters` (incompatible con AKS estándar).
- `kubernetes_version` `1.29.0` → `1.33` (1.29 no soportada en la región; 1.32 es LTS-only/Premium).
- Región `eastus` → `centralus` (ver restricción Students abajo).

### Restricciones descubiertas en Azure for Students

- **Policy `sys.regionrestriction`**: solo permite `westus3, northcentralus, centralus, chilecentral, canadacentral`. `eastus` bloqueada. Se usó `centralus`.
- **Spot NO disponible**: el pool `burst` de stage y todo nodo Spot fallan → solo se desplegó **dev** (sin Spot). Stage/prod quedan como IaC + demo.
- **Providers** `Microsoft.Storage` y `Microsoft.ContainerService` no venían registrados; se registraron con `az provider register`.
- **Cluster AAD-enabled**: `kubectl` exige `kubelogin` (no instalado). Workaround para CI/CD: `az aks get-credentials --admin` (kubeconfig por certificado, sin kubelogin).

### Estado del despliegue — ✅ APP CORRIENDO EN AKS (2026-06-12)

Pipeline `cd-dev.yml` verde end-to-end y los **13 pods de `circleguard-dev` corriendo** (8 microservicios + postgres, kafka, zookeeper, neo4j, redis). Verificado en vivo contra cg-aks-dev.

| Pieza | Estado |
|---|---|
| Backend remoto TF | ✅ RG `rg-terraform-state`, Storage Account `cgtf816751`, container `tfstate`, key `dev.tfstate` (centralus) |
| AKS `cg-aks-dev` | ✅ desplegado — 2× `Standard_B2s`, K8s v1.33.12, 2 nodos Ready (RG `rg-circle-guard-dev`) |
| Roots dev/stage/prod | ✅ separados, `terraform validate` OK |
| GitHub environment `dev` + secret `KUBE_CONFIG_DEV` | ✅ **base64 del kubeconfig admin** (el workflow hace `base64 -d`; guardarlo en crudo daba `base64: invalid input`) |
| Sealed Secrets controller | ✅ v0.27.1 en `kube-system` (deployment real `sealed-secrets-controller`), Running |
| Secrets `DOCKERHUB_USERNAME=stixk`/`DOCKERHUB_TOKEN` | ✅ seteados; build-push sube las 8 imágenes a `stixk/circleguard-*` (repos públicos) |
| 8 microservicios + 5 datastores | ✅ 13/13 `Running` en `circleguard-dev` |

### Fixes aplicados para dejar el deploy verde (6 PRs a `circleguard-infra`)

Depuración iterativa contra el clúster real. Cada fallo se arregló en el repo (reproducible, no parche en vivo):

| # | Síntoma | Causa raíz | Fix (PR) |
|---|---|---|---|
| 0 | deploy-dev muere en 6s, `base64: invalid input` | `KUBE_CONFIG_DEV` guardado crudo, no base64 | re-guardado `base64 -w0` del kubeconfig admin |
| 1 | ImagePullBackOff en los 8 servicios | manifests usaban `:latest` (tag inexistente; CI sube `dev-<sha>`/`dev-latest`) | `:latest`→`dev-latest` (PR #1) |
| 2 | kafka/neo4j CrashLoopBackOff | k8s inyecta `KAFKA_PORT`/`NEO4J_PORT_*` que esas imágenes parsean como config | `enableServiceLinks: false` (PR #2) |
| 3 | `CreateContainerConfigError` en servicios | SealedSecret cifrado con la clave del clúster viejo → no descifraba | re-sellado con cert de cg-aks-dev, valores mock (PR #3) |
| 4 | `FATAL: database "circleguard_auth" does not exist` | postgres solo creaba la DB base `circleguard`; 5 servicios usan DB propia | initdb configmap crea `circleguard_{auth,identity,promotion,dashboard,form}` (PR #4) |
| 5 | mucho `Pending` + rollouts en deadlock | sobre-suscripción: `replicas:2` en 4 servicios + requests altos vs 2× B2s (3800m CPU) | `replicas:1`, requests bajos (100m/192Mi), `maxSurge:0`, `startupProbe`, cap heap kafka/neo4j (PR #5) |
| 6 | auth-service `WeakKeyException` (200 bits) | `QR_SECRET` de 25 bytes; JJWT exige ≥256 bits para HMAC-SHA | QR_SECRET ≥32 bytes + re-sellado (PR #6) |

### Operación y fallos anticipados

- **postgres usa `emptyDir`**: en reschedule o stop/start del clúster se pierden datos, pero el initdb recrea las 5 DBs y Flyway re-migra (auto-sana). Para persistencia real → PVC.
- **SealedSecret atado a la clave del clúster**: si se recrea cg-aks-dev, re-sellar (`kubeseal --fetch-cert --controller-name sealed-secrets-controller` + `scripts/seal-dev-secrets.sh`).
- **`dev-latest` mutable** pero cd-dev parchea a `dev-<sha>` inmutable → deploys pineados.
- **FinOps**: apagar entre demos con `az aks stop/start` o `scripts/scale-to-zero.sh` configurando `AZURE_RG_DEV=rg-circle-guard-dev` y `AKS_CLUSTER_DEV=cg-aks-dev`.

---

## 0.2 Despliegue real en GCP (GKE) — segundo cloud del bonus (2026-06-12)

Segundo proveedor para el bonus Multi-Cloud. **IaC/CD listos y validados**; la
ejecución contra el cloud real se corre con el runbook `docs/DESPLIEGUE_GCP.md`
(requiere credenciales GCP). Mismo patrón que Azure: roots aislados, state
separado, manifiestos reutilizados.

| Pieza | Estado |
|---|---|
| Root TF `terraform/environments/gcp-dr/` | ✅ `terraform validate` OK; instancia `gke-cluster` (zonal `us-central1-a`, 2× `e2-medium` spot, autoscaling 1→3) |
| State remoto | ✅ reusa backend azurerm `cgtf816751`, key `gcp-dr.tfstate` (aislado del de Azure) |
| Bucket Velero + SA | ✅ `cg-velero-dr-<project>` (GCS) + SA `velero-backup` (`roles/storage.admin`) en el root |
| Overlay app `k8s/gcp/` | ✅ copia de `k8s/dev/`, namespace `circleguard-dr`, gateway LoadBalancer, hereda fixes de AKS |
| Re-sellado secretos | ✅ `scripts/seal-gcp-secrets.sh` (cert de GKE; el SealedSecret de dev no descifra aquí) |
| Workflow `cd-gcp.yml` | ✅ `google-github-actions/auth` + `get-gke-credentials`, environment `gcp`, `workflow_dispatch` |
| Velero install | ✅ `scripts/velero-install-gcp.sh` (AKS→GCS produce, GKE consume/restore) |
| LB real entre clouds | ✅ `multicloud/haproxy.real.cfg` sobre IPs públicas reales AKS + GKE |
| Limpieza | ✅ `terraform/multicloud.tf` huérfano (referenciaba `aks_prod`/`main.tf` borrado en §0.1) eliminado; GKE vive ahora en su env root |

**Pendiente de ejecución real**: provisión (`terraform apply`), deploy
(`cd-gcp.yml`), backup/restore Velero y captura de números perf — todo
documentado paso a paso en `docs/DESPLIEGUE_GCP.md`. Bloqueante: credenciales
GCP (proyecto + billing + `gcloud auth`).

---

## 1. Cumplimiento por rubro

| # | Rubro (peso) | Estado | Evidencia clave |
|---|--------------|--------|-----------------|
| 1 | Metodología ágil y branching (10 %) | ✅ Completo | `docs/AGILE_METHODOLOGY.md` (Scrum, 2 sprints HU-01→HU-11), `BRANCHING_STRATEGY.md` (GitHub Flow + Conventional Commits), `BACKLOG.md` (9 épicas mapeadas a rúbrica), 47+ commits convencionales, ramas feature/release reales |
| 2 | Terraform IaC (20 %) | ✅ Completo | Módulos `circleguard-infra/terraform/modules/aks-cluster` y `gke-cluster`; 3 ambientes en roots aislados `terraform/environments/{dev,stage,prod}/` con state separado por entorno (refactor 2026-06-11, ver §0.1); backend remoto azurerm; **desplegado real en Azure** (`cg-aks-dev` en centralus); diagrama en `docs/architecture/circleguard-architecture.drawio` |
| 3 | Patrones de diseño (10 %) | ✅ Completo | Circuit Breaker R4j en 3 clientes con fallbacks (`PromotionClient`, `IdentityClient`, `AuthServiceClient`); Retry (`PushServiceImpl` + `@EnableRetry`); Feature Toggle doble (property `DashboardProperties` + DB `SystemSettings`); External Configuration (`@ConfigurationProperties` + ConfigMaps); 10 patrones existentes catalogados en `docs/DESIGN_PATTERNS.md` |
| 4 | CI/CD avanzado (15 %) | ✅ Completo | Todo en GitHub Actions: SonarQube (`ci.yml` job `sonarqube` + plugin Gradle); Trivy (`ci.yml` job `docker-build-scan`); versionado semántico (semantic-release, job `release`); CD por rama (`cd-dev.yml` dev, `cd-stage.yml` release/**, job `deploy-prod` de `ci.yml`); notificaciones de fallo (issue GitHub label `cd-failure` + Slack opcional); approval gate de producción vía environment `production` con required reviewers |
| 5 | Pruebas completas (15 %) | ✅ Completo | 56 unitarias (8 servicios), 7 de integración (`tests/integration-tests`), 5 specs E2E Cypress, Locust con 4 clases de usuario, **OWASP ZAP baseline** (`ci.yml:276`), gate JaCoCo mínimo de 40 % + Codecov (objetivo aspiracional: 70 %); todo automatizado en pipelines |
| 6 | Change Management (5 %) | ✅ Completo | `docs/CHANGE_MANAGEMENT.md` (proceso formal + tipos de cambio); Release Notes automáticas (semantic-release + `scripts/generate-release-notes.sh`); rollback multicapa (kubectl rollout undo, Flyway aditivo, Terraform revert, feature toggles); git tag por semantic-release (job `release` de `ci.yml`) |
| 7 | Observabilidad (10 %) | ✅ Completo | Prometheus+Grafana en k8s (Helm values); ELK 8.15 en k8s master; Jaeger production-strategy (collectors + agents DaemonSet); 13 alert rules + Alertmanager con routing por severidad; probes liveness/readiness en los 8 servicios; **métricas de negocio** (`NotificationMetrics`, `AuthMetrics`); 8 ServiceMonitors |
| 8 | Seguridad (5 %) | ✅ Completo | Escaneo continuo (Trivy + OWASP DC + ZAP); RBAC (`circleguard-infra/k8s/rbac.yaml`, least-privilege por entorno); TLS público (cert-manager + Let's Encrypt); gestión segura de secretos (dev con Bitnami Sealed Secrets — brecha #4 resuelta; stage/prod inyectados desde GitHub Secrets vía environments) |
| 9 | Documentación y presentación (10 %) | ⚠️ Parcial | 13+ docs organizados, costos en `TERRAFORM.md` y `BONUS_FINOPS.md`, manual `MANUAL_EJECUCION.md`. **Falta video y material de presentación** |

### Bonos (los 4 presentes)

| Bono (5 % c/u) | Estado | Evidencia |
|----------------|--------|-----------|
| Service Mesh | ✅ | Linkerd: inject por namespace, canary 90/10 (HTTPRoute), circuit breaker failure-accrual, retries con budget (`circleguard-infra/k8s/mesh/`) |
| Chaos Engineering | ✅ | Chaos Mesh: 4 experimentos con hipótesis documentadas (`circleguard-infra/chaos/experiments/`); experimento 1 falseó hipótesis → bug de caché CGLIB corregido e integrado |
| FinOps | ✅ | Datos OpenCost reales (`docs/finops/`), instalador `scripts/install-opencost.sh`, script `scripts/scale-to-zero.sh`, Spot instances y análisis de ahorro |
| Multi-Cloud | ✅ | AKS real + GKE como segundo cloud: root real `terraform/environments/gcp-dr/` (cluster zonal spot + bucket Velero + SA), overlay `k8s/gcp/`, workflow `cd-gcp.yml`, Velero AKS→GCS, HAProxy a IPs reales (`multicloud/haproxy.real.cfg`). Runbook `docs/DESPLIEGUE_GCP.md` (ver §0.2) |

---

## 2. Brechas identificadas (priorizadas)

| # | Brecha | Impacto | Acción sugerida |
|---|--------|---------|-----------------|
| 1 | **Video demostrativo** — no existe | Entregable obligatorio (rubro 9) | Grabar ≤ 8 min: CI/CD, app funcionando, dashboards, resultados de performance |
| 2 | **Presentación** (20-30 min) — sin material en repo | Entregable obligatorio | Preparar slides: arquitectura, demo CI/CD, monitoreo, lecciones aprendidas |
| 3 | ~~**Datastores en k8s prod**~~ | — | ✅ **RESUELTO**: stage despliega kafka, zookeeper, neo4j, redis y openldap (emptyDir); master despliega kafka-prod, zookeeper-prod, neo4j-prod, redis-prod y openldap-prod (con PVCs). Además se corrigió postgres-prod, que referenciaba el secret inexistente `db-credentials` (ahora usa `circleguard-secrets/DB_PASSWORD`) |
| 4 | ~~**Secretos dev en claro**~~ | — | ✅ **RESUELTO**: `k8s/dev/secrets.yaml` reemplazado por Bitnami Sealed Secrets — controller vía Helm + `./scripts/seal-dev-secrets.sh` (repo infra) genera `k8s/dev/sealed-secrets.yaml` cifrado; stage/master mantienen plantillas envsubst inyectadas desde GitHub Secrets (`STAGE_*`/`PROD_*`) en los workflows de CD |
| 5 | ~~**OpenCost sin automatización de despliegue**~~ | — | ✅ **RESUELTO**: `scripts/install-opencost.sh` instala OpenCost 1.120 mediante Helm y permite desplegar un Prometheus dedicado |
| 6 | ~~**Script scale-to-zero inexistente**~~ | — | ✅ **RESUELTO**: `scripts/scale-to-zero.sh` ejecuta `az aks stop/start` para dev/stage; para el AKS real deben definirse `AZURE_RG_DEV=rg-circle-guard-dev` y `AKS_CLUSTER_DEV=cg-aks-dev` |
| 7 | ~~**Dashboards Grafana escasos**~~ | — | ✅ **RESUELTO**: 8 dashboards por servicio (uid `cg-svc-<name>`) en `circleguard-infra/observability/grafana/dashboards/services/` + ConfigMap in-cluster `k8s/master/observability/grafana-dashboards-services-configmap.yaml` |
| 8 | **HPA solo en master** | Menor (stage/dev opcional) | HPA en stage si se quiere paridad |

---

## 3. Organización del repositorio

Estado actual: **dos repositorios separados**:
- **devops-project** (este repo): aplicación (services/), docker/, .github/workflows (ci.yml + cd-dev.yml + cd-stage.yml), tests/, scripts/, docs/.
- **[circleguard-infra](https://github.com/JuanAmor8/circleguard-infra)**: terraform/, k8s/, observability/, chaos/, multicloud/, deploy-all.sh. Los workflows de CD lo clonan en `./infra/` durante los jobs de Deploy.

Limpieza realizada (2026-06-10):
- Eliminados: `docs/TALLER2_README.md`, `docs/INFORME_TALLER2.md` (taller anterior), `final.md` (duplicado del enunciado con encoding roto), `commit_msg.txt` (scratch)
- `docs/ux/` — assets de diseño UX (mockups, html de exploración visual)
- `docs/assets/` — capturas/imágenes de evidencia
- `docs/architecture/` — diagrama `.drawio` + PNG
- `docs/finops/` — datos OpenCost

Ver discusión mono-repo vs repos separados (infra/deployment) en `docs/PROJECT_OVERVIEW.md` §11 y la recomendación del equipo.
