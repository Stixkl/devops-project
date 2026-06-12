# Pipelines CI/CD - Circle Guard

## Arquitectura: todo en GitHub Actions (CI + CD)

El proyecto ejecuta **CI y CD en GitHub Actions**, con workflows separados por
responsabilidad:

| Capa | Workflow | Responsabilidad |
|------|----------|-----------------|
| **CI** | `.github/workflows/ci.yml` | Build matrix, unit/integración/E2E/performance, calidad (SonarQube, JaCoCo), seguridad (OWASP DC, ZAP, Trivy en `docker-build-scan`), release notes automáticas (semantic-release, job `release`), notificación de fallos (`notify-failure` crea un issue) |
| **CD dev** | `.github/workflows/cd-dev.yml` | Push a `dev` → build+push de imágenes y despliegue a K8s DEV |
| **CD stage** | `.github/workflows/cd-stage.yml` | Push a `release/**` → build+push y despliegue a K8s STAGE |
| **CD prod** | job `deploy-prod` de `ci.yml` | Push a `master` → despliegue a producción, gateado por el environment `production` (aprobación manual con required reviewers) |

Razón del diseño: un solo motor elimina la divergencia entre dos líneas de
trabajo, centraliza el feedback de calidad en el PR y gobierna la promoción
entre ambientes con **GitHub Environments** (secretos por ambiente, gate de
aprobación en producción). Un fallo en CI bloquea el merge; un fallo en CD
abre un issue (`notify-failure`, label `cd-failure`) y dispara el plan de
rollback (`docs/CHANGE_MANAGEMENT.md`).

## Dual checkout: app + infra

Los manifiestos Kubernetes viven en el repo separado
[circleguard-infra](https://github.com/JuanAmor8/circleguard-infra). Los jobs
de deploy hacen un segundo `actions/checkout` con `repository:
JuanAmor8/circleguard-infra` y `path: infra`, de modo que los comandos de
despliegue usan rutas `infra/k8s/...` (p. ej. `kubectl apply -f
infra/k8s/dev/ -n circleguard-dev`). Así el código de aplicación y la
infraestructura versionan por separado pero se despliegan juntos.

## Arquitectura de Pipelines

```
Git Repository
    │
    ├── [dev] ──→ cd-dev.yml ──→ Docker Images (dev-<sha>, dev-latest) ──→ K8s DEV
    │
    ├── [release/*] ──→ cd-stage.yml ──→ Docker Images (stage-<run>) ──→ K8s STAGE
    │                                └── Integration + E2E + Performance (ci.yml)
    │
    └── [master] ──→ ci.yml (release + deploy-prod) ──→ Docker Images (<version>, latest) ──→ K8s PROD
                         └── semantic-release: CHANGELOG + GitHub Release + tag vX.Y.Z
                         └── Aprobación manual (environment `production`)
```

## Mapeo rama → ambiente

| Rama | Workflow | Environment | Namespace K8s | Tags de imagen | Gate |
|------|----------|-------------|---------------|----------------|------|
| `dev` | `cd-dev.yml` | `dev` | `circleguard-dev` | `dev-<sha>`, `dev-latest` | automático |
| `release/**` | `cd-stage.yml` | `stage` | `circleguard-stage` | `stage-<run_number>` | automático |
| `master` | `ci.yml` (`deploy-prod`) | `production` | `circleguard-master` | `<version>` (del tag semver), `latest` | **aprobación manual** (required reviewers) |

## Pipeline DEV (.github/workflows/cd-dev.yml)

**Trigger**: push a `dev`

| Job | Descripción |
|-----|-------------|
| `build-push` | Matrix de 8 servicios; construye cada imagen y publica en Docker Hub con tags `dev-<sha>` y `dev-latest` |
| `deploy-dev` | Environment `dev`; checkout de `circleguard-infra` en `infra/`; `kubectl apply -f infra/k8s/dev/`; `kubectl set image` por servicio; rollout status; smoke test. Si el secret `KUBE_CONFIG_DEV` no está definido, el despliegue al cluster se omite con un warning (las imágenes igual se publican) |
| `notify-failure` | Abre un issue de GitHub con label `cd-failure` si el pipeline falla |

## Pipeline STAGE (.github/workflows/cd-stage.yml)

**Trigger**: push a branch `release/**`

Mismo patrón que dev, con diferencias:

- Tags de imagen: `stage-<run_number>`.
- Renderiza configmaps/secrets de stage con `envsubst` a partir de los
  secretos `STAGE_*` de GitHub.
- Environment `stage`; kubeconfig en el secret `KUBE_CONFIG_STAGE` (si no
  está definido, se omite el deploy al cluster).
- Despliega `infra/k8s/stage/` en namespace `circleguard-stage` (incluye
  datastores: kafka, zookeeper, neo4j, redis, openldap).
- Las pruebas de integración, E2E (Cypress) y performance (Locust) corren en
  `ci.yml` sobre la misma rama.

## Pipeline PROD (job `deploy-prod` de .github/workflows/ci.yml)

**Trigger**: push a `master` (tras pasar el CI completo y el job `release`)

| Paso | Descripción |
|------|-------------|
| Gate de aprobación | El job usa el environment `production`, configurado con required reviewers → el deploy queda en espera hasta aprobación manual |
| Resolver versión | Toma la versión del último tag git (`vX.Y.Z`, creado por semantic-release) |
| Build & Push | Construye y publica las imágenes release `stixk/circleguard-<svc>-service:<version>` y `:latest` |
| Checkout Infra | `actions/checkout` de `JuanAmor8/circleguard-infra` en `infra/` |
| Render config | `envsubst` sobre `infra/k8s/master/{configmaps,secrets}.yaml` con los secretos `PROD_*` |
| Deploy | `kubectl apply -f infra/k8s/master/` en `circleguard-master` (incluye datastores `*-prod` con PVCs); `kubectl set image` por servicio con anotación change-cause |
| Verificación | `kubectl rollout status` + smoke test (health endpoint) |

Si el secret `KUBE_CONFIG_PROD` no está definido, el despliegue al cluster se
omite con un warning; las imágenes release igual se publican.

**Versiones**: semantic-release (job `release` de `ci.yml`) calcula la
siguiente versión semver desde los commits convencionales, actualiza el
CHANGELOG, crea el GitHub Release y el tag `vX.Y.Z`.
`scripts/generate-release-notes.sh` queda solo como fallback manual.

## Diagrama de Flujo (ASCII)

```
┌─────────────────────────────────────────────────────────┐
│                     DEVELOPER                           │
└─────────────────────┬───────────────────────────────────┘
                      │ git push
          ┌───────────┼───────────┐
          │           │           │
         dev      release/*    master
          │           │           │
          ▼           ▼           ▼
    ┌──────────┐ ┌──────────┐ ┌──────────────┐
    │ cd-dev   │ │ cd-stage │ │ ci.yml       │
    │ .yml     │ │ .yml     │ │ (CI completo)│
    └────┬─────┘ └────┬─────┘ └────┬─────────┘
         │            │            │
         ▼            ▼            ▼
    ┌─────────┐  ┌──────────┐  semantic-release
    │  K8s    │  │  K8s     │  (tag vX.Y.Z)
    │  DEV    │  │  STAGE   │       │
    └─────────┘  └────┬─────┘       ▼
                      │       Approval (env
                 Integration   `production`)
                 + E2E + Perf       │
                  (ci.yml)          ▼
                      │        ┌──────────┐
                  ┌───┴───┐    │  K8s     │
                  │ MERGE │    │  PROD    │
                  └───────┘    └──────────┘
```

## Configuración requerida en GitHub

### Environments (Settings → Environments)

| Environment | Uso | Protección |
|-------------|-----|------------|
| `dev` | `cd-dev.yml` → job `deploy-dev` | — |
| `stage` | `cd-stage.yml` → job de deploy | — |
| `production` | `ci.yml` → job `deploy-prod` | **Required reviewers** (gate de aprobación manual) |

### Secrets (Settings → Secrets and variables → Actions)

| Secret | Descripción |
|--------|-------------|
| `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN` | Login a Docker Hub |
| `KUBE_CONFIG_DEV` / `KUBE_CONFIG_STAGE` / `KUBE_CONFIG_PROD` | Kubeconfig en base64 por ambiente; si falta, el deploy al cluster se omite con warning |
| `STAGE_*` | Valores para `envsubst` de los secretos de stage: `DB_PASSWORD`, `JWT_SECRET`, `QR_SECRET`, `LDAP_BIND_PASSWORD`, `NEO4J_PASSWORD`, `VAULT_SECRET`, `VAULT_SALT`, `VAULT_HASH_SALT`, `TWILIO_SID/TOKEN/FROM`, `GOTIFY_TOKEN` |
| `PROD_*` | Igual que stage, más `DB_URL`, `DB_USERNAME`, `LDAP_URL` (debe ser `ldap://openldap-prod:389`), `LDAP_BIND_DN`, `NEO4J_USERNAME` |

Los secretos de stage/master se generan en deploy con plantillas `envsubst`
alimentadas por estos secretos de GitHub (la plantilla de master incluye la
clave `NEO4J_AUTH`); dev usa Sealed Secrets (ver repo infra).
