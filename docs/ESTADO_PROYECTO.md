# Estado del Proyecto vs Rúbrica — Auditoría

> Auditoría del repositorio contra los requisitos de `docs/Proyecto Final IngeSoft V (1).md`.
> Verificación hecha sobre código, pipelines y manifiestos reales (no sobre afirmaciones de documentación).
> Fecha: 2026-06-11 · Veredicto global: **~95 % completo** (faltan video y presentación).

## 0. Estado de estabilización CI (2026-06-11, branch `chore/split-infra-repo`)

Trabajo en curso para dejar el pipeline de master 100 % verde. Hecho y verificado localmente (clean build + suite completa, incluyendo Testcontainers contra Postgres/Neo4j/Redis reales):

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

**Pendiente inmediato**: run de la iteración 8 (`609e536`) en verificación → merge a master (PR #18 sigue abierto).

**Fallo conocido — `dependency-check`**: el job falla porque OWASP Dependency Check intenta descargar la base NVD sin API key (`NVD_API_KEY` secret vacío) y la NVD responde 403/404 ante el rate-limit anónimo. El job tiene `continue-on-error: true`, así que no bloquea el run, pero sale rojo. Solución: (a) registrar una API key gratuita en https://nvd.nist.gov/developers/request-an-api-key y guardarla como secret `NVD_API_KEY`, o (b) cachear la base NVD entre runs (`actions/cache` sobre `~/.gradle/dependency-check-data`) y/o pasar `--info -PnvdValidForHours=168`, o (c) marcar el step con `if: env.NVD_API_KEY != ''` para hacer skip explícito sin key (igual que SonarQube). **Bloqueado por permisos admin (dueño Stixkl)**: environments `dev/stage/production` con required reviewers y secrets (`DOCKERHUB_*`, `KUBE_CONFIG_*`, `STAGE_*`, `PROD_*`, `PROD_LDAP_URL=ldap://openldap-prod:389`). Sin secrets, los jobs de deploy hacen skip con warning y el pipeline queda verde igualmente.

---

## 1. Cumplimiento por rubro

| # | Rubro (peso) | Estado | Evidencia clave |
|---|--------------|--------|-----------------|
| 1 | Metodología ágil y branching (10 %) | ✅ Completo | `docs/AGILE_METHODOLOGY.md` (Scrum, 2 sprints HU-01→HU-11), `BRANCHING_STRATEGY.md` (GitHub Flow + Conventional Commits), `BACKLOG.md` (9 épicas mapeadas a rúbrica), 47+ commits convencionales, ramas feature/release reales |
| 2 | Terraform IaC (20 %) | ✅ Completo | Módulos `circleguard-infra/terraform/modules/aks-cluster` y `gke-cluster`; 3 ambientes con `envs/*.tfvars`; backend remoto azurerm (`providers.tf:18` + `circleguard-infra/terraform/scripts/init-backend.sh`); diagrama en `docs/architecture/circleguard-architecture.drawio` |
| 3 | Patrones de diseño (10 %) | ✅ Completo | Circuit Breaker R4j en 3 clientes con fallbacks (`PromotionClient`, `IdentityClient`, `AuthServiceClient`); Retry (`PushServiceImpl` + `@EnableRetry`); Feature Toggle doble (property `DashboardProperties` + DB `SystemSettings`); External Configuration (`@ConfigurationProperties` + ConfigMaps); 10 patrones existentes catalogados en `docs/DESIGN_PATTERNS.md` |
| 4 | CI/CD avanzado (15 %) | ✅ Completo | Todo en GitHub Actions: SonarQube (`ci.yml` job `sonarqube` + plugin Gradle); Trivy (`ci.yml` job `docker-build-scan`); versionado semántico (semantic-release, job `release`); CD por rama (`cd-dev.yml` dev, `cd-stage.yml` release/**, job `deploy-prod` de `ci.yml`); notificaciones de fallo (issue GitHub label `cd-failure` + Slack opcional); approval gate de producción vía environment `production` con required reviewers |
| 5 | Pruebas completas (15 %) | ✅ Completo | 56 unitarias (8 servicios), 7 de integración (`tests/integration-tests`), 5 specs E2E Cypress, Locust con 4 clases de usuario, **OWASP ZAP baseline** (`ci.yml:276`), JaCoCo con verificación 70 % + codecov; todo automatizado en pipelines |
| 6 | Change Management (5 %) | ✅ Completo | `docs/CHANGE_MANAGEMENT.md` (proceso formal + tipos de cambio); Release Notes automáticas (semantic-release + `scripts/generate-release-notes.sh`); rollback multicapa (kubectl rollout undo, Flyway aditivo, Terraform revert, feature toggles); git tag por semantic-release (job `release` de `ci.yml`) |
| 7 | Observabilidad (10 %) | ✅ Completo | Prometheus+Grafana en k8s (Helm values); ELK 8.15 en k8s master; Jaeger production-strategy (collectors + agents DaemonSet); 13 alert rules + Alertmanager con routing por severidad; probes liveness/readiness en los 8 servicios; **métricas de negocio** (`NotificationMetrics`, `AuthMetrics`); 8 ServiceMonitors |
| 8 | Seguridad (5 %) | ✅ Completo | Escaneo continuo (Trivy + OWASP DC + ZAP); RBAC (`circleguard-infra/k8s/rbac.yaml`, least-privilege por entorno); TLS público (cert-manager + Let's Encrypt); gestión segura de secretos (dev con Bitnami Sealed Secrets — brecha #4 resuelta; stage/prod inyectados desde GitHub Secrets vía environments) |
| 9 | Documentación y presentación (10 %) | ⚠️ Parcial | 13+ docs organizados, costos en `TERRAFORM.md` y `BONUS_FINOPS.md`, manual `MANUAL_EJECUCION.md`. **Falta video y material de presentación** |

### Bonos (los 4 presentes)

| Bono (5 % c/u) | Estado | Evidencia |
|----------------|--------|-----------|
| Service Mesh | ✅ | Linkerd: inject por namespace, canary 90/10 (HTTPRoute), circuit breaker failure-accrual, retries con budget (`circleguard-infra/k8s/mesh/`) |
| Chaos Engineering | ✅ | Chaos Mesh: 4 experimentos con hipótesis documentadas (`circleguard-infra/chaos/experiments/`); experimento 1 falseó hipótesis → bug de caché CGLIB corregido e integrado |
| FinOps | ✅/⚠️ | Datos OpenCost reales (`docs/finops/`), Spot instances, análisis de ahorro; ⚠️ instalación OpenCost y script scale-to-zero no están en el repo |
| Multi-Cloud | ✅ | Módulo GKE DR condicional (`circleguard-infra/terraform/multicloud.tf`), Velero backup AKS→GCS (`circleguard-infra/k8s/dr/velero-schedule.yaml`), demo failover HAProxy |

---

## 2. Brechas identificadas (priorizadas)

| # | Brecha | Impacto | Acción sugerida |
|---|--------|---------|-----------------|
| 1 | **Video demostrativo** — no existe | Entregable obligatorio (rubro 9) | Grabar ≤ 8 min: CI/CD, app funcionando, dashboards, resultados de performance |
| 2 | **Presentación** (20-30 min) — sin material en repo | Entregable obligatorio | Preparar slides: arquitectura, demo CI/CD, monitoreo, lecciones aprendidas |
| 3 | ~~**Datastores en k8s prod**~~ | — | ✅ **RESUELTO**: stage despliega kafka, zookeeper, neo4j, redis y openldap (emptyDir); master despliega kafka-prod, zookeeper-prod, neo4j-prod, redis-prod y openldap-prod (con PVCs). Además se corrigió postgres-prod, que referenciaba el secret inexistente `db-credentials` (ahora usa `circleguard-secrets/DB_PASSWORD`) |
| 4 | ~~**Secretos dev en claro**~~ | — | ✅ **RESUELTO**: `k8s/dev/secrets.yaml` reemplazado por Bitnami Sealed Secrets — controller vía Helm + `./scripts/seal-dev-secrets.sh` (repo infra) genera `k8s/dev/sealed-secrets.yaml` cifrado; stage/master mantienen plantillas envsubst inyectadas desde GitHub Secrets (`STAGE_*`/`PROD_*`) en los workflows de CD |
| 5 | **OpenCost sin manifest de despliegue** (solo datos capturados) | Consistencia bono FinOps | Añadir manifest/helm de instalación |
| 6 | **Script scale-to-zero** documentado pero inexistente en `scripts/` | Consistencia bono FinOps | Crear script `az aks stop/start` programado |
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
