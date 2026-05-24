# Circle Guard - Taller 2: CI/CD con Jenkins, Docker y Kubernetes

## Resumen del Proyecto

Circle Guard es un sistema de 8 microservicios para gestión de salud pública (COVID-19) desarrollado con Spring Boot (Kotlin), Gradle, PostgreSQL, Neo4j, Kafka y Redis.

## Microservicios Incluidos (Taller 2)

Para este taller se seleccionaron **6 microservicios** que forman un flujo end-to-end completo:

| Servicio | Puerto | Tecnología | Rol |
|----------|--------|------------|-----|
| auth-service | 8180 | Spring Boot + LDAP + JWT | Autenticación y autorización |
| identity-service | 8083 | Spring Boot + PostgreSQL | Vault de identidades anónimas |
| gateway-service | 8087 | Spring Boot + Redis | Validación QR + cache |
| form-service | 8086 | Spring Boot + Kafka | Encuestas de salud |
| promotion-service | 8088 | Spring Boot + Neo4j + Kafka | Cascada de estados |
| notification-service | 8082 | Spring Boot + Kafka | Notificaciones email/SMS/push |

## Flujo End-to-End

```
Usuario → Auth Service (login/visitor handoff)
        → Identity Service (anonymous ID)
        → Form Service (health survey)
        → Kafka (form.submitted)
        → Promotion Service (status cascade via Neo4j)
        → Kafka (promotion.status.changed)
        → Notification Service (multi-channel alerts)
        → Gateway Service (QR validation + Redis cache)
```

## Estructura del Proyecto

```
circle-guard-public/
├── docker/                    # Dockerfiles + docker-compose
├── jenkins/                   # Jenkinsfiles (dev, stage, master)
├── k8s/                       # Manifiestos Kubernetes
│   ├── namespaces/            # dev, stage, master namespaces
│   ├── dev/                   # deployments dev
│   ├── stage/                 # deployments stage
│   └── master/                # deployments prod
├── tests/
│   ├── integration-tests/     # 8 tests de integración
│   ├── e2e/                   # 23 tests E2E (Cypress API)
│   └── performance/           # Locust + stress tests
├── scripts/                   # Scripts de automatización
└── docs/                      # Documentación del taller
```

## Rúbrica del Taller (100%)

| Punto | % | Descripción |
|-------|---|-------------|
| 1 | 10% | Configurar Jenkins + Docker + Kubernetes |
| 2 | 15% | Pipelines DEV (≥6 microservicios) |
| 3 | 30% | Pruebas (5+ unitarias, 5+ integración, 5+ E2E, Locust) |
| 4 | 15% | Pipelines STAGE en Kubernetes |
| 5 | 15% | Pipeline MASTER + Release Notes |
| 6 | 15% | Documentación + video (≤8 min) |

## Pipelines

### Pipeline DEV (Jenkinsfile-dev)
- Checkout → Build → Unit Tests → Security Scan → Docker Build → Push → Deploy to K8s dev → Smoke Tests

### Pipeline STAGE (Jenkinsfile-stage)
- Checkout → Build → Docker Build → Push → Deploy to K8s stage → Integration Tests → E2E Tests (Cypress) → Performance Tests (Locust) → Approval Gate

### Pipeline MASTER (Jenkinsfile-master)
- Checkout → Build → Docker Build → Security Scans → Push → Deploy to K8s prod → Smoke Tests → Generate Release Notes → Git Tag

## Cómo Ejecutar

### 1. Construir imágenes Docker localmente
```bash
cd circle-guard-public
docker compose -f docker/docker-compose.yml build
```

### 2. Desplegar a Kubernetes
```bash
kubectl apply -f k8s/namespaces/
kubectl apply -f k8s/dev/
kubectl get pods -n circleguard-dev
```

### 3. Ejecutar pruebas
```bash
# Unitarias
./gradlew test

# Integración
./gradlew :tests:integration-tests:test

# E2E
cd tests/e2e && npm install && npx cypress run

# Performance
cd tests/performance
pip install -r requirements.txt
locust -f locustfile.py --headless -u 50 -r 5 -t 5m --html report.html
```

## Credenciales Requeridas en Jenkins
- `dockerhub-credentials` (username/password)
- `kubeconfig-dev`, `kubeconfig-stage`, `kubeconfig-master` (secret files)
- `DOCKER_USERNAME`, `DOCKER_PASSWORD` (environment variables)

## Tecnologías Usadas
- **CI/CD**: Jenkins
- **Containerization**: Docker + Docker Compose
- **Orchestration**: Kubernetes (Minikube)
- **Testing**: JUnit 5, Cypress, Locust
- **Infrastructure**: PostgreSQL, Neo4j, Apache Kafka, Redis, OpenLDAP, Mailhog
