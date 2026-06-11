# CircleGuard — Contexto del Proyecto y Estado de Implementación

> Documento de contexto general: qué es el proyecto, qué problema resuelve, cómo está construido y qué se encuentra implementado a la fecha.

---

## 1. ¿Qué es CircleGuard?

CircleGuard es una **plataforma de rastreo de contactos y alertas de salud** para la comunidad universitaria (ICESI). Permite detectar y notificar cadenas de exposición ante casos confirmados de enfermedades contagiosas, preservando la privacidad de los usuarios mediante anonimización de identidades (enfoque FERPA-compliant).

El proyecto se desarrolla como **Proyecto Final de Ingeniería de Software V** (enunciado en `docs/Proyecto Final IngeSoft V (1).md`), con énfasis en: arquitectura de microservicios, contenedores, orquestación con Kubernetes, pipelines CI/CD, observabilidad completa, seguridad e infraestructura como código.

### Flujo funcional principal (E2E)

1. Un usuario se autentica (`auth-service`) vía LDAP universitario o como invitado.
2. Su identidad real se anonimiza (`identity-service`) — el resto del sistema solo conoce un `anonymousId`.
3. El usuario diligencia encuestas de salud (`form-service`), que publican eventos a Kafka.
4. El motor de cascada (`promotion-service`) procesa el evento, recorre el grafo de contactos en Neo4j (aristas con TTL de 14 días) y promueve estados de riesgo (p. ej. contacto → expuesto → caso).
5. Los cambios de estado generan eventos que el `notification-service` convierte en notificaciones multi-canal (Email, SMS vía Twilio, Push vía Gotify).
6. El acceso físico a espacios se valida con códigos QR (`gateway-service`, con caché Redis).
7. Analistas consultan hotspots y estadísticas (`dashboard-service`).
8. Certificados y documentos se gestionan en `file-service`.

---

## 2. Stack tecnológico

| Capa | Tecnología |
|------|-----------|
| Backend | Java 21 · Spring Boot · Gradle |
| Mensajería | Apache Kafka 7.6 + Zookeeper |
| Datos | PostgreSQL 16 · Neo4j 5.26 · Redis 7.2 · OpenLDAP |
| Contenedores | Docker (multi-stage: `gradle:8.7-jdk21` → `eclipse-temurin:21-jre-alpine`) |
| Orquestación | Kubernetes (AKS) · 3 namespaces (`circleguard-dev/stage/master`) |
| Service Mesh | Linkerd (mTLS, canary 90/10, retries, circuit breaking) |
| CI | GitHub Actions (`.github/workflows/ci.yml`) |
| CD | GitHub Actions (`cd-dev.yml`, `cd-stage.yml` y job `deploy-prod` de `ci.yml` con aprobación manual) |
| Registro | Docker Hub (`juanamor8/circleguard-*`) |
| IaC | Terraform sobre Azure (AKS, VNets, ACR, backend remoto en Azure Storage) |
| Observabilidad | Prometheus · Grafana · Alertmanager · ELK (Elasticsearch/Logstash/Kibana) · Filebeat · Jaeger (OTLP) |
| Resiliencia | Resilience4j (Circuit Breaker, Retry), Feature Toggles, configuración externa |

---

## 3. Microservicios implementados (8)

| Servicio | Puerto | Responsabilidad | Almacenes |
|----------|--------|-----------------|-----------|
| `circleguard-auth-service` | 8180 | Autenticación dual (LDAP universitario + invitados locales), emisión JWT, permisos | PostgreSQL, OpenLDAP |
| `circleguard-identity-service` | 8083 | Mapeo identidad real → `anonymousId` (anonimización criptográfica) | PostgreSQL |
| `circleguard-form-service` | 8086 | Encuestas/cuestionarios de salud dinámicos; productor Kafka | PostgreSQL |
| `circleguard-promotion-service` | 8088 | Motor de cascada de estados sobre grafo de contactos; consumidor/productor Kafka | Neo4j, PostgreSQL, Redis |
| `circleguard-notification-service` | 8082 | Notificaciones multi-canal (Email/SMS/Push); consumidor Kafka | PostgreSQL |
| `circleguard-gateway-service` | 8087 | Validación de entrada por QR con caché distribuido | Redis |
| `circleguard-file-service` | 8085 | Gestión de certificados y documentos | PostgreSQL / FS |
| `circleguard-dashboard-service` | 8084 | Hotspots geoespaciales y analítica | Neo4j, PostgreSQL |

### Comunicación entre servicios

**Síncrona (REST + Circuit Breaker Resilience4j):**
- `dashboard` → `promotion` (estadísticas; fallback con caché Caffeine 30 min)
- `auth` → `identity` (mapeo de identidad; fallback `Optional.empty()`)
- `notification` → `auth` (usuarios con permiso `alert:receive_priority`; fallback `BROADCAST_ALL`)

**Asíncrona (eventos Kafka):**

| Topic | Productor | Consumidor |
|-------|-----------|------------|
| `form.submitted` | form-service | promotion-service |
| `promotion.status.changed` | promotion-service | notification-service |
| `circle.fenced` | promotion-service | notification-service |
| `exposure.notification` | promotion-service | notification-service |
| `alert.priority` | (alertas masivas) | notification-service |

---

## 4. Infraestructura implementada

### 4.1 Entorno local (Docker Compose)

`docker-compose.dev.yml` levanta: PostgreSQL, Neo4j, Kafka+Zookeeper, Redis, OpenLDAP, Prometheus, Grafana, Elasticsearch, Kibana, Jaeger y Filebeat. Stack de observabilidad adicional en `circleguard-infra/observability/docker-compose.observability.yml`.

> **Nota**: toda la infraestructura (`terraform/`, `k8s/`, `observability/`, `chaos/`, `multicloud/`) vive en el repo separado [circleguard-infra](https://github.com/JuanAmor8/circleguard-infra), clonado lado a lado con este repo.

### 4.2 Kubernetes (`circleguard-infra/k8s/`)

| Entorno | Namespace | Características |
|---------|-----------|-----------------|
| dev | `circleguard-dev` | 2 réplicas servicios, probes liveness/readiness, Linkerd inject |
| stage | `circleguard-stage` | 3+ réplicas, HPA, pruebas de integración/E2E |
| master (prod) | `circleguard-master` | 3+ réplicas, **HPA min=3 max=10**, RollingUpdate, anti-affinity |

- **Datastores por entorno**: dev, stage (kafka, zookeeper, neo4j, redis, openldap con `emptyDir`) y master (kafka-prod, zookeeper-prod, neo4j-prod, redis-prod, openldap-prod con PVCs).
- **Ingress + TLS**: NGINX Ingress → `gateway-service`, certificados Let's Encrypt vía cert-manager (`circleguard-infra/k8s/master/ingress/`). Único servicio expuesto; el resto ClusterIP.
- **Service Mesh (Linkerd, `circleguard-infra/k8s/mesh/`)**: mTLS automático, circuit breaker (failure accrual), canary 90/10 con Gateway API HTTPRoute, retry policies con budget.
- **Secretos**: dev usa Bitnami Sealed Secrets (`k8s/dev/sealed-secrets.yaml`, cifrado y commiteable); stage/master usan plantillas `envsubst` alimentadas por los GitHub Secrets `STAGE_*`/`PROD_*`.

### 4.3 Terraform / Azure (`circleguard-infra/terraform/`)

- Módulo reutilizable `modules/aks-cluster` instanciado 3 veces (dev/stage/prod) con `envs/*.tfvars`.
- **dev**: 1 nodepool (2× B2s) · **stage**: 2 nodepools (3× B2ms + burst Spot 0-3) · **prod**: system 3× B4ms + user 5× B2ms con autoscaling, ACR Standard.
- Backend remoto: Azure Storage (`tfstate`, state locking).
- FinOps: Spot instances (-90 %), scale-to-zero nocturno/fines de semana (-65 % no-prod), VMs B-series, etiquetado para asignación de costos. Costo estimado total ≈ **$1,087/mes**.

---

## 5. CI/CD — Todo en GitHub Actions

### CI (`.github/workflows/ci.yml`)

Trigger: push a `dev`/`master`, PR a `dev`. Jobs:
- Build en matriz (9 módulos) + pruebas unitarias + cobertura JaCoCo (upload a Codecov)
- Pruebas de integración (docker-compose) y E2E (Cypress contra API real)
- Pruebas de performance (Locust: 50 usuarios, 5 min)
- Escaneo de seguridad: OWASP Dependency Check + ZAP + SonarQube + Trivy (`docker-build-scan`)
- `semantic-release` (job `release`): CHANGELOG + GitHub Release + tag `vX.Y.Z`
- `notify-failure`: abre un issue en GitHub si el pipeline falla

### CD (workflows de GitHub Actions)

| Pipeline | Trigger | Environment / Destino | Pasos clave |
|----------|---------|----------------------|-------------|
| `cd-dev.yml` | push a `dev` | `dev` / `circleguard-dev` | Build+Push matrix 8 servicios (`dev-<sha>`, `dev-latest`) → checkout infra → `kubectl apply` → set image → rollout → Smoke |
| `cd-stage.yml` | push a `release/**` | `stage` / `circleguard-stage` | Build+Push (`stage-<run_number>`) → envsubst configmaps/secrets (`STAGE_*`) → Deploy → Smoke |
| `deploy-prod` (job de `ci.yml`) | push a `master` | `production` (**aprobación manual** con required reviewers) / `circleguard-master` | Versión del último tag (semantic-release) → Push `:<version>`+`latest` → envsubst (`PROD_*`) → Deploy → rollout con change-cause → Smoke |

Si el secret `KUBE_CONFIG_<ENV>` no está definido, el deploy al cluster se omite con un warning (las imágenes igual se publican). Los fallos de CD abren un issue con label `cd-failure`.

---

## 6. Observabilidad (3 señales)

| Señal | Flujo |
|-------|-------|
| **Métricas** | Servicios exponen `/actuator/prometheus` (Micrometer) → Prometheus (scrape 15 s) → Grafana (dashboards `cg-overview`, `cg-costs` y 8 dashboards por servicio `cg-svc-<name>` en `circleguard-infra/observability/grafana/dashboards/services/`) + Alertmanager |
| **Logs** | Logback + `logstash-logback-encoder` → TCP JSON a Logstash :5000 (`LOGSTASH_HOST`) → Elasticsearch (`logstash-*`) → Kibana. Filebeat recoge logs de contenedores |
| **Trazas** | Micrometer Tracing + OTel exporter → Jaeger (OTLP :4318, UI :16686), sampling 1.0 |

Alertas definidas (`circleguard-infra/observability/prometheus/alert-rules.yml`): `ServiceDown` (crítica), `HighRequestLatencyP95`, `HighHttp5xxErrorRate`, `HighJvmHeapUsage`.

---

## 7. Estrategia de pruebas

- **Unitarias**: JUnit 5 + Mockito por servicio, cobertura JaCoCo.
- **Integración**: `tests/integration-tests` contra stack docker-compose.
- **E2E**: Cypress sobre la API.
- **Performance**: Locust (50 usuarios concurrentes, 5 min).
- **Smoke**: post-deploy en cada workflow de CD (dev, stage y prod).

Detalle en `docs/TESTING_STRATEGY.md`.

---

## 8. Patrones de diseño y resiliencia implementados

- **Circuit Breaker** (Resilience4j) en todos los clientes REST inter-servicio, con fallbacks definidos.
- **Retry** con políticas y budgets (Linkerd ServiceProfile).
- **Feature Toggles** y **configuración externa** (perfiles Spring + ConfigMaps).
- **Event-Driven Architecture** con Kafka para desacoplar el flujo principal.
- **Anonimización por diseño**: ningún servicio aguas abajo conoce identidades reales.

Detalle en `docs/DESIGN_PATTERNS.md`.

---

## 9. Bonos implementados

| Bono | Documento | Resumen |
|------|-----------|---------|
| Service Mesh | `docs/BONUS_SERVICE_MESH.md` | Linkerd: mTLS, traffic shifting canary, circuit breaker, retries |
| FinOps | `docs/BONUS_FINOPS.md` | OpenCost, Spot, autoscaling, ahorros cuantificados |
| Chaos Engineering | `docs/BONUS_CHAOS_ENGINEERING.md` | Chaos Mesh, 4 experimentos con validación de hipótesis |
| Multi-Cloud | `docs/BONUS_MULTICLOUD.md` | AKS + GKE, failover, balanceador global |

---

## 10. Mapa de documentación

| Tema | Archivo |
|------|---------|
| Visión general y roadmap | `README.md` |
| Enunciado del proyecto (requisitos + rúbrica) | `docs/Proyecto Final IngeSoft V (1).md` |
| Auditoría estado vs rúbrica + brechas | `docs/ESTADO_PROYECTO.md` |
| Metodología ágil | `docs/AGILE_METHODOLOGY.md` |
| Estrategia de branching | `BRANCHING_STRATEGY.md` |
| Change Management | `docs/CHANGE_MANAGEMENT.md` |
| Manual de ejecución/operación | `docs/MANUAL_EJECUCION.md` |
| Pipelines CI/CD | `docs/PIPELINES.md` |
| Terraform / IaC | `docs/TERRAFORM.md` |
| Estrategia de pruebas | `docs/TESTING_STRATEGY.md` |
| Patrones de diseño | `docs/DESIGN_PATTERNS.md` |
| Observabilidad | `circleguard-infra/observability/README.md` |
| Diagrama de arquitectura | `docs/architecture/circleguard-architecture.drawio` (+ PNG exportado) |

---

## 11. Estructura de los repositorios (resumen)

El proyecto está dividido en **dos repos** que se clonan lado a lado:

```
devops-project/                # Repo de aplicación (este repo)
├── services/                  # 8 microservicios Spring Boot
│   └── circleguard-*-service/
├── docker/                    # Dockerfiles multi-stage + filebeat
├── docker-compose.dev.yml     # Stack local completo
├── .github/workflows/         # CI + CD (ci.yml, cd-dev.yml, cd-stage.yml)
├── tests/                     # Integración, E2E, performance
├── scripts/                   # Scripts de soporte
└── docs/                      # Documentación + architecture/

circleguard-infra/             # Repo de infraestructura
│                              # https://github.com/JuanAmor8/circleguard-infra
├── k8s/                       # Manifiestos por entorno + mesh + ingress + dr
│   ├── namespaces/  dev/  stage/  master/  mesh/
├── terraform/                 # IaC Azure (módulo aks-cluster + gke-cluster + envs)
├── observability/             # Prometheus, Grafana, Logstash, Alertmanager
├── chaos/                     # Experimentos Chaos Mesh
├── multicloud/                # Demo failover kind ×2 + HAProxy
└── scripts/                   # deploy-all.sh, seal-dev-secrets.sh
```

Los workflows de GitHub Actions de este repo hacen un segundo `actions/checkout` de `circleguard-infra` en `./infra/` durante los jobs de Deploy, por lo que los comandos de despliegue usan rutas `infra/k8s/...`.
