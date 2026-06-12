# Change Management y Release Notes — CircleGuard

Proceso formal de gestión de cambios, generación automática de release notes,
planes de rollback y etiquetado de releases (rúbrica E6, 5%).

## 1. Proceso formal de cambios

Todo cambio sigue el mismo camino; lo que varía es el nivel de aprobación:

| Tipo | Ejemplos | Aprobación | Camino |
|------|----------|-----------|--------|
| **Estándar** | feature, fix, docs | 1 revisor (PR) | feat/* → dev → release/* → main |
| **Normal (prod)** | despliegue a producción | Environment `production` de GitHub con **required reviewers**: el job `deploy-prod` queda en espera hasta aprobación manual | release/* → main |
| **Emergencia** | hotfix de incidente | 1 revisor + notificación a oncall; retro post-mortem obligatoria | hotfix/* → main (cherry-pick a dev) |

Etapas del cambio (automatizadas en los pipelines):

1. **Solicitud**: PR con descripción, issue vinculado y Conventional Commit.
2. **Evaluación**: CI completo (build, unit/integración, SonarQube, OWASP DC,
   Trivy, ZAP) — un fallo bloquea el merge.
3. **Aprobación**: revisión cruzada + gate manual para prod.
4. **Implementación**: despliegue por pipeline (nunca manual), con
   `kubectl annotate ... kubernetes.io/change-cause="release vX.Y.Z"` para
   trazabilidad del rollout.
5. **Verificación**: smoke tests post-deploy (`curl /actuator/health` en el
   job `deploy-prod` de `ci.yml`) + monitoreo de alertas 30 min.
6. **Cierre o rollback** (ver §3).

## 2. Release notes y etiquetado automáticos

- **GitHub Actions** (`ci.yml`, job `release`): `semantic-release` calcula la
  versión desde los Conventional Commits, genera changelog/release notes y
  publica el tag `vX.Y.Z` + GitHub Release en cada push a la rama de
  producción. `scripts/generate-release-notes.sh` queda solo como fallback
  manual.
- El job `deploy-prod` resuelve la versión desde ese último tag y las
  imágenes Docker quedan etiquetadas con la misma versión
  (`stixk/circleguard-<svc>-service:<version>` + `:latest`) →
  correlación release ↔ artefacto.

## 3. Planes de rollback

### Aplicación (Kubernetes) — RTO minutos
```bash
# Historial con change-cause anotado por el pipeline
kubectl rollout history deployment/<svc> -n circleguard-master
# Volver a la revisión anterior (imagen previa, sin rebuild)
kubectl rollout undo deployment/<svc> -n circleguard-master
# o a una revisión específica
kubectl rollout undo deployment/<svc> -n circleguard-master --to-revision=N
```
Las probes de readiness garantizan que la versión restaurada no recibe
tráfico hasta estar sana. Si el fallo es de release completa: re-desplegar el
tag anterior con `kubectl set image` (las imágenes versionadas permanecen en
Docker Hub) o re-ejecutar el job `deploy-prod` sobre el tag previo.

### Base de datos
Migraciones Flyway **aditivas** (no se borran columnas en el mismo release
que deja de usarlas) → la versión N-1 siempre puede correr contra el esquema
N. Restauración mayor: backup diario de PostgreSQL + Velero
(`circleguard-infra/k8s/dr/velero-schedule.yaml`).

### Infraestructura (Terraform)
Estado remoto versionado (backend azurerm). Rollback = `git revert` del
cambio de IaC + `terraform plan` (revisión del diff) + `apply`. Nunca cambios
manuales en el portal.

### Configuración / features
- Feature Toggle por propiedad (`circleguard.features.*`): apagar con env var
  + restart — sin redeploy de código.
- Toggle runtime DB (promotion `SystemSettings`): `POST
  /api/v1/admin/settings/toggle-unconfirmed-fencing` — kill-switch inmediato.

## 4. Disparadores de rollback

- Smoke test post-deploy falla (automático: el workflow queda FAILED y
  notifica — issue con label `cd-failure` + Slack en Actions).
- Alerta crítica sostenida >5 min tras release (`ServiceDown`,
  `HighErrorRate`, `CircuitBreakerOpen` — Prometheus rules).
- Decisión del oncall durante la ventana de verificación.

Cada rollback genera un issue post-mortem (causa, impacto, acción preventiva)
revisado en la retrospectiva del sprint.
