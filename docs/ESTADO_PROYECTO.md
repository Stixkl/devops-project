# Estado del Proyecto vs Rúbrica — Auditoría

> Auditoría del repositorio contra los requisitos de `docs/Proyecto Final IngeSoft V (1).md`.
> Verificación hecha sobre código, pipelines y manifiestos reales (no sobre afirmaciones de documentación).
> Fecha: 2026-06-10 · Veredicto global: **~90 % completo**.

---

## 1. Cumplimiento por rubro

| # | Rubro (peso) | Estado | Evidencia clave |
|---|--------------|--------|-----------------|
| 1 | Metodología ágil y branching (10 %) | ✅ Completo | `docs/AGILE_METHODOLOGY.md` (Scrum, 2 sprints HU-01→HU-11), `BRANCHING_STRATEGY.md` (GitHub Flow + Conventional Commits), `BACKLOG.md` (9 épicas mapeadas a rúbrica), 47+ commits convencionales, ramas feature/release reales |
| 2 | Terraform IaC (20 %) | ✅ Completo | Módulos `circleguard-infra/terraform/modules/aks-cluster` y `gke-cluster`; 3 ambientes con `envs/*.tfvars`; backend remoto azurerm (`providers.tf:18` + `circleguard-infra/terraform/scripts/init-backend.sh`); diagrama en `docs/architecture/circleguard-architecture.drawio` |
| 3 | Patrones de diseño (10 %) | ✅ Completo | Circuit Breaker R4j en 3 clientes con fallbacks (`PromotionClient`, `IdentityClient`, `AuthServiceClient`); Retry (`PushServiceImpl` + `@EnableRetry`); Feature Toggle doble (property `DashboardProperties` + DB `SystemSettings`); External Configuration (`@ConfigurationProperties` + ConfigMaps); 10 patrones existentes catalogados en `docs/DESIGN_PATTERNS.md` |
| 4 | CI/CD avanzado (15 %) | ✅ Completo | SonarQube (`ci.yml` job `sonarqube` + plugin Gradle); Trivy (`ci.yml:427`, Jenkinsfiles); versionado semántico (semantic-release + `getNextVersion()` en Jenkinsfile-master); notificaciones de fallo (issue GitHub + Slack + mail Jenkins); approval gates (environment `production` + input manual en stage con submitters) |
| 5 | Pruebas completas (15 %) | ✅ Completo | 56 unitarias (8 servicios), 7 de integración (`tests/integration-tests`), 5 specs E2E Cypress, Locust con 4 clases de usuario, **OWASP ZAP baseline** (`ci.yml:276`), JaCoCo con verificación 70 % + codecov; todo automatizado en pipelines |
| 6 | Change Management (5 %) | ✅ Completo | `docs/CHANGE_MANAGEMENT.md` (proceso formal + tipos de cambio); Release Notes automáticas (semantic-release + `scripts/generate-release-notes.sh`); rollback multicapa (kubectl rollout undo, Flyway aditivo, Terraform revert, feature toggles); git tag en Jenkinsfile-master |
| 7 | Observabilidad (10 %) | ✅ Completo | Prometheus+Grafana en k8s (Helm values); ELK 8.15 en k8s master; Jaeger production-strategy (collectors + agents DaemonSet); 13 alert rules + Alertmanager con routing por severidad; probes liveness/readiness en los 8 servicios; **métricas de negocio** (`NotificationMetrics`, `AuthMetrics`); 8 ServiceMonitors |
| 8 | Seguridad (5 %) | ✅ Completo | Escaneo continuo (Trivy + OWASP DC + ZAP); RBAC (`circleguard-infra/k8s/rbac.yaml` + `k8s/jenkins-rbac.yaml`, least-privilege por entorno); TLS público (cert-manager + Let's Encrypt); secretos dev con Bitnami Sealed Secrets (brecha #4 resuelta) |
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
| 4 | ~~**Secretos dev en claro**~~ | — | ✅ **RESUELTO**: `k8s/dev/secrets.yaml` reemplazado por Bitnami Sealed Secrets — controller vía Helm + `./scripts/seal-dev-secrets.sh` (repo infra) genera `k8s/dev/sealed-secrets.yaml` cifrado; stage/master mantienen plantillas envsubst con credenciales Jenkins |
| 5 | **OpenCost sin manifest de despliegue** (solo datos capturados) | Consistencia bono FinOps | Añadir manifest/helm de instalación |
| 6 | **Script scale-to-zero** documentado pero inexistente en `scripts/` | Consistencia bono FinOps | Crear script `az aks stop/start` programado |
| 7 | ~~**Dashboards Grafana escasos**~~ | — | ✅ **RESUELTO**: 8 dashboards por servicio (uid `cg-svc-<name>`) en `circleguard-infra/observability/grafana/dashboards/services/` + ConfigMap in-cluster `k8s/master/observability/grafana-dashboards-services-configmap.yaml` |
| 8 | **HPA solo en master** | Menor (stage/dev opcional) | HPA en stage si se quiere paridad |

---

## 3. Organización del repositorio

Estado actual: **dos repositorios separados**:
- **devops-project** (este repo): aplicación (services/), docker/, jenkins/, .github/workflows, tests/, scripts/, docs/.
- **[circleguard-infra](https://github.com/JuanAmor8/circleguard-infra)**: terraform/, k8s/, observability/, chaos/, multicloud/, deploy-all.sh. Los Jenkinsfiles lo clonan en `./infra/` durante los stages de Deploy.

Limpieza realizada (2026-06-10):
- Eliminados: `docs/TALLER2_README.md`, `docs/INFORME_TALLER2.md` (taller anterior), `final.md` (duplicado del enunciado con encoding roto), `commit_msg.txt` (scratch)
- `docs/ux/` — assets de diseño UX (mockups, html de exploración visual)
- `docs/assets/` — capturas/imágenes de evidencia
- `docs/architecture/` — diagrama `.drawio` + PNG
- `docs/finops/` — datos OpenCost

Ver discusión mono-repo vs repos separados (infra/deployment) en `docs/PROJECT_OVERVIEW.md` §11 y la recomendación del equipo.
