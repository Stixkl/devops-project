# Pipelines CI/CD - Circle Guard

## Arquitectura dual: GitHub Actions (CI) + Jenkins (CD)

El proyecto usa **dos motores complementarios**, no redundantes:

| Capa | Motor | Responsabilidad | Definición |
|------|-------|-----------------|------------|
| **CI** | GitHub Actions | Build, unit/integración, calidad (SonarQube, JaCoCo), seguridad (OWASP DC, Trivy, ZAP), performance (Locust), release notes automáticas (semantic-release), notificación de fallos (issue + Slack) | `.github/workflows/ci.yml` (12 jobs) |
| **CD** | Jenkins | Despliegue multi-ambiente con promoción controlada dev → stage → prod, gate de aprobación manual a producción, smoke tests post-deploy, tagging de release, notificación de fallos por mail | `jenkins/Jenkinsfile-{dev,stage,master}` |

Razón del diseño: Actions corre en cada push sin infraestructura propia y
centraliza el feedback de calidad en el PR; Jenkins vive junto a los clusters
y gobierna la promoción entre ambientes (credenciales de despliegue nunca
salen de la red del cluster). Un fallo en CI bloquea el merge; un fallo en CD
bloquea la promoción y dispara el plan de rollback
(`docs/CHANGE_MANAGEMENT.md`).

## Dual checkout: app + infra

Los manifiestos Kubernetes viven en el repo separado
[circleguard-infra](https://github.com/JuanAmor8/circleguard-infra). Cada
Jenkinsfile tiene un stage **Checkout Infra** que clona ese repo dentro del
workspace en `infra/`; los comandos de despliegue usan rutas `infra/k8s/...`
(p. ej. `kubectl apply -f infra/k8s/dev/ -n circleguard-dev`). Así el código
de aplicación y la infraestructura versionan por separado pero se despliegan
juntos.

## Arquitectura de Pipelines

```
Git Repository
    │
    ├── [develop] ──→ Jenkinsfile-dev ──→ Docker Image ──→ K8s DEV
    │
    ├── [release/*] ──→ Jenkinsfile-stage ──→ Docker Image ──→ K8s STAGE
    │                                    └── Integration + E2E + Performance Tests
    │
    └── [master] ──→ Jenkinsfile-master ──→ Docker Image ──→ K8s PROD
                                               └── Release Notes + Git Tag
```

## Pipeline DEV (jenkins/Jenkinsfile-dev)

**Trigger**: push a `develop` o `master`

| Stage | Descripción |
|-------|-------------|
| Checkout | Descarga código fuente |
| Checkout Infra | Clona `circleguard-infra` en `infra/` |
| Build & Compile | `./gradlew :services:circleguard-<svc>-service:build -x test` |
| Unit Tests | `./gradlew test` + JUnit reports |
| Security Scan | OWASP Dependency Check |
| Docker Build | Construye imagen con Dockerfile.<service> |
| Docker Security Scan | Trivy image scan |
| Push to Registry | docker push a Docker Hub |
| Deploy to Dev K8s | `kubectl apply -f infra/k8s/dev/` en namespace `circleguard-dev` |
| Smoke Tests | Health check del deployment |

**Variables de entorno**:
- `SERVICE_NAME`: auth, identity, gateway, form, notification, promotion
- `DOCKER_IMAGE`: circleguard/${SERVICE_NAME}
- `KUBECONFIG`: credencial kubeconfig-dev

## Pipeline STAGE (jenkins/Jenkinsfile-stage)

**Trigger**: push a branch `release/*` o manual

| Stage | Descripción |
|-------|-------------|
| Checkout | Descarga código fuente |
| Checkout Infra | Clona `circleguard-infra` en `infra/` |
| Build | `./gradlew build` completo |
| Docker Build | Imagen con tag `stage-${BUILD_NUMBER}` |
| Push to Registry | docker push |
| Deploy to Stage K8s | `kubectl apply -f infra/k8s/stage/` en namespace `circleguard-stage` (incluye datastores: kafka, zookeeper, neo4j, redis, openldap) |
| Integration Tests | `./gradlew :tests:integration-tests:test` |
| E2E Tests (Cypress) | `npx cypress run` contra APIs |
| Performance Tests (Locust) | locust headless 100 users, 5 min |
| Approval Gate | Aprobación manual antes de producción |

**Credenciales requeridas**:
- `kubeconfig-stage`
- `DOCKER_USERNAME`, `DOCKER_PASSWORD`

## Pipeline MASTER (jenkins/Jenkinsfile-master)

**Trigger**: push a `master`

| Stage | Descripción |
|-------|-------------|
| Checkout | Descarga + genera versión semver |
| Checkout Infra | Clona `circleguard-infra` en `infra/` |
| Build | `./gradlew clean build` (excluye integration/e2e) |
| Docker Build | Tags: `${RELEASE_VERSION}` + `latest` |
| Security Scan (parallel) | OWASP + Trivy |
| Push to Registry | docker push ambas tags |
| Deploy to Production K8s | `kubectl apply -f infra/k8s/master/` en `circleguard-master` (incluye datastores `*-prod` con PVCs) |
| Smoke Tests | curl health endpoint |
| Generate Release Notes | `scripts/generate-release-notes.sh` |
| Tag & Notify | git tag + archivado |

**Versiones**: El pipeline calcula automáticamente la siguiente versión semver desde el último tag git.

## Diagrama de Flujo (ASCII)

```
┌─────────────────────────────────────────────────────────┐
│                     DEVELOPER                           │
└─────────────────────┬───────────────────────────────────┘
                      │ git push
          ┌───────────┼───────────┐
          │           │           │
       develop     release/*    master
          │           │           │
          ▼           ▼           ▼
    ┌──────────┐ ┌──────────┐ ┌──────────┐
    │  DEV     │ │  STAGE   │ │  MASTER  │
    │ PIPELINE │ │ PIPELINE │ │ PIPELINE │
    └────┬─────┘ └────┬─────┘ └────┬─────┘
         │            │            │
         ▼            ▼            ▼
    ┌─────────┐  ┌──────────┐ ┌──────────┐
    │  K8s    │  │ Integration│ │  K8s    │
    │  DEV    │  │ + E2E + Perf│ │  PROD   │
    └─────────┘  └────┬─────┘ └────┬─────┘
                      │            │
                 Approval?     Release Notes
                      │            │
                      │         ┌──┴──┐
                      │         │ TAG │
                      │         └─────┘
                      │
                  ┌───┴───┐
                  │ MERGE │
                  └───────┘
```

## Credentials en Jenkins

Crear en Jenkins → Manage Credentials:

| ID | Tipo | Descripción |
|----|------|-------------|
| `dockerhub-credentials` | Username/Password | Docker Hub login |
| `kubeconfig-dev` | Secret file | Kubeconfig para namespace dev |
| `kubeconfig-stage` | Secret file | Kubeconfig para namespace stage |
| `kubeconfig-master` | Secret file | Kubeconfig para namespace master |
| `LDAP_URL` | Secret text | URL de LDAP por entorno; en prod debe ser `ldap://openldap-prod:389` |

Los secretos de stage/master se generan en deploy con plantillas `envsubst`
alimentadas por estas credenciales (la plantilla de master incluye la clave
`NEO4J_AUTH`); dev usa Sealed Secrets (ver repo infra).

## Multibranch Pipeline Configuration

1. New Item → Multibranch Pipeline
2. Branch Sources → Git
3. Project Recognizer → by Jenkinsfile path: `jenkins/Jenkinsfile-dev`
4. Behaviors → Add `Discover branches`
5. Build Configuration → Mode: by Jenkinsfile
6. Add Property: `SERVICE_NAME=<service-name>`
