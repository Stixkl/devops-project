# Backlog — CircleGuard DevOps Final Project

## Épicas

| ID | Épica | Peso |
|----|-------|------|
| E1 | Metodología Ágil y Branching | 10% |
| E2 | Infraestructura como Código (Terraform) | 20% |
| E3 | Patrones de Diseño | 10% |
| E4 | CI/CD Avanzado | 15% |
| E5 | Pruebas Completas | 15% |
| E6 | Change Management y Release Notes | 5% |
| E7 | Observabilidad y Monitoreo | 10% |
| E8 | Seguridad | 5% |
| E9 | Documentación y Presentación | 10% |

---

## Sprint 1 — Fundamentos y Base (Semanas 1–2)

**Objetivo:** Tener el proyecto corriendo localmente, la metodología ágil activa, la estrategia de branching definida y la estructura base de Terraform lista.

---

### HU-01 — Configuración del entorno de trabajo
> **Como** desarrollador, **quiero** tener el proyecto corriendo localmente **para** poder desarrollar y probar cambios sin depender de infraestructura externa.

**Criterios de aceptación:**
- Todos los servicios levantan con `docker-compose.dev.yml`
- La app móvil conecta con los servicios correctamente
- Existe documentación del setup local

**Tasks:**
- [ ] T01.1 — Clonar repo y verificar que `docker-compose.dev.yml` levanta todos los servicios
- [ ] T01.2 — Verificar conectividad entre microservicios (auth → gateway → services)
- [ ] T01.3 — Levantar frontend móvil y conectar con backend local
- [ ] T01.4 — Documentar pasos de setup en `README.md`

---

### HU-02 — Metodología ágil y tablero de proyecto
> **Como** equipo, **quiero** tener un sistema de gestión ágil activo **para** organizar el trabajo, dar seguimiento al progreso y cumplir el requisito de 2 iteraciones.

**Criterios de aceptación:**
- Tablero creado en GitHub Projects (o Trello/Jira)
- Backlog poblado con todas las HUs del proyecto
- Sprint 1 y Sprint 2 definidos con fechas
- Historias de usuario con criterios de aceptación documentados

**Tasks:**
- [ ] T02.1 — Crear tablero en GitHub Projects
- [ ] T02.2 — Crear columnas: Backlog / Sprint / En progreso / Revisión / Done
- [ ] T02.3 — Agregar todas las HUs de este documento al tablero
- [ ] T02.4 — Asignar responsables por tarea entre los dos integrantes
- [ ] T02.5 — Documentar metodología elegida (Scrum/Kanban) en `docs/metodologia.md`

---

### HU-03 — Estrategia de branching
> **Como** equipo, **quiero** una estrategia de branching definida y documentada **para** evitar conflictos y mantener el código organizado durante el desarrollo.

**Criterios de aceptación:**
- Estrategia documentada (GitFlow o GitHub Flow)
- Branches `main`, `develop`, `feature/*`, `release/*`, `hotfix/*` creados
- Reglas de protección en `main` y `develop` configuradas en GitHub
- Al menos 1 PR completo como ejemplo del flujo

**Tasks:**
- [ ] T03.1 — Definir y documentar estrategia de branching en `docs/branching.md`
- [ ] T03.2 — Crear branches base (`develop`, `feature/terraform-base`)
- [ ] T03.3 — Configurar branch protection rules en GitHub (require PR + review)
- [ ] T03.4 — Crear plantilla de PR en `.github/pull_request_template.md`

---

### HU-04 — Infraestructura base con Terraform
> **Como** equipo, **quiero** definir la infraestructura del proyecto como código con Terraform **para** poder reproducir los ambientes de forma consistente y automática.

**Criterios de aceptación:**
- Estructura modular de Terraform creada (`modules/`, `environments/`)
- Ambientes `dev`, `stage`, `prod` configurados
- Backend remoto configurado (S3 o Terraform Cloud)
- Diagrama de arquitectura de infraestructura documentado

**Tasks:**
- [ ] T04.1 — Crear estructura de directorios Terraform (`terraform/modules/`, `terraform/environments/dev|stage|prod`)
- [ ] T04.2 — Módulo `networking` (VPC, subnets, security groups)
- [ ] T04.3 — Módulo `kubernetes` (cluster EKS/GKE/AKS)
- [ ] T04.4 — Módulo `database` (PostgreSQL managed, Neo4j)
- [ ] T04.5 — Módulo `messaging` (Kafka / MSK)
- [ ] T04.6 — Configurar backend remoto para estado de Terraform
- [ ] T04.7 — Variables por ambiente (`dev.tfvars`, `stage.tfvars`, `prod.tfvars`)
- [ ] T04.8 — Documentar arquitectura con diagrama en `docs/infraestructura.md`

---

### HU-05 — Identificación de patrones de diseño existentes
> **Como** equipo, **quiero** identificar y documentar los patrones de diseño ya presentes en la arquitectura **para** cumplir el requisito de análisis y tener base para los patrones adicionales.

**Criterios de aceptación:**
- Mínimo 5 patrones identificados en el código existente
- Cada patrón documentado con: nombre, ubicación en el código, propósito

**Tasks:**
- [ ] T05.1 — Revisar código de los 8 microservicios e identificar patrones
- [ ] T05.2 — Documentar patrones encontrados en `docs/patrones.md`
- [ ] T05.3 — Seleccionar los 3 patrones adicionales a implementar en Sprint 2

---

## Sprint 2 — CI/CD, K8s, Observabilidad y Pruebas (Semanas 3–4)

**Objetivo:** Tener pipelines CI/CD funcionales, los microservicios desplegados en Kubernetes, stack de observabilidad activo y pruebas automatizadas corriendo.

---

### HU-06 — Kubernetes manifests para todos los microservicios
> **Como** equipo, **quiero** todos los microservicios desplegados en Kubernetes **para** cumplir el requisito de integración en un entorno K8s.

**Criterios de aceptación:**
- Cada microservicio tiene su `Deployment`, `Service` y `ConfigMap`
- Health checks (`readinessProbe`, `livenessProbe`) configurados en cada servicio
- Ingress configurado para el gateway
- Namespace separado por ambiente

**Tasks:**
- [ ] T06.1 — Crear `k8s/` con estructura por servicio
- [ ] T06.2 — Manifest `Deployment` + `Service` para cada uno de los 8 microservicios
- [ ] T06.3 — `ConfigMap` y `Secret` para configuración y credenciales
- [ ] T06.4 — Configurar `readinessProbe` y `livenessProbe` en cada Deployment
- [ ] T06.5 — Crear `Ingress` para el gateway-service
- [ ] T06.6 — Namespace `circleguard-dev`, `circleguard-stage`, `circleguard-prod`
- [ ] T06.7 — Desplegar en cluster de prueba y verificar funcionamiento

---

### HU-07 — Pipeline CI/CD completo
> **Como** equipo, **quiero** pipelines automatizados de CI/CD **para** que cada cambio pase por build, test, análisis y despliegue de forma automática y controlada.

**Criterios de aceptación:**
- Pipeline CI ejecuta en cada PR: build, tests, SonarQube, Trivy
- Pipeline CD despliega automáticamente a `dev` y `stage`
- Despliegue a `prod` requiere aprobación manual
- Versionado semántico automático en cada merge a `main`
- Notificaciones configuradas para fallos

**Tasks:**
- [ ] T07.1 — Crear `.github/workflows/ci.yml` (build + test por microservicio)
- [ ] T07.2 — Integrar SonarQube en pipeline CI
- [ ] T07.3 — Integrar Trivy para escaneo de imágenes Docker
- [ ] T07.4 — Crear `.github/workflows/cd-dev.yml` (deploy automático a dev)
- [ ] T07.5 — Crear `.github/workflows/cd-stage.yml` (deploy automático a stage)
- [ ] T07.6 — Crear `.github/workflows/cd-prod.yml` (deploy con aprobación manual)
- [ ] T07.7 — Configurar versionado semántico automático (`semantic-release` o `standard-version`)
- [ ] T07.8 — Configurar notificaciones (Slack o email) para fallos en pipeline

---

### HU-08 — Implementar patrones de diseño adicionales
> **Como** equipo, **quiero** implementar 3 patrones de diseño adicionales **para** mejorar la resiliencia y configurabilidad de la arquitectura.

**Criterios de aceptación:**
- Circuit Breaker implementado (Resilience4j)
- External Configuration implementado (Spring Cloud Config o ConfigMap K8s)
- Un tercer patrón documentado e implementado
- Cada patrón documentado con propósito y beneficios

**Tasks:**
- [ ] T08.1 — Implementar Circuit Breaker con Resilience4j en `gateway-service`
- [ ] T08.2 — Implementar External Configuration con Spring Cloud Config o K8s ConfigMaps
- [ ] T08.3 — Implementar patrón adicional (Bulkhead, Retry o Feature Toggle)
- [ ] T08.4 — Documentar los 3 patrones en `docs/patrones.md`

---

### HU-09 — Suite de pruebas completa
> **Como** equipo, **quiero** pruebas automatizadas en todos los niveles **para** garantizar la calidad del software y cumplir el requisito del 15%.

**Criterios de aceptación:**
- Pruebas unitarias con cobertura ≥ 80% por servicio
- Pruebas de integración entre servicios relacionados
- Pruebas E2E para flujos principales del usuario
- Pruebas de rendimiento con Locust documentadas
- Pruebas de seguridad con OWASP ZAP ejecutadas
- Reporte de cobertura generado automáticamente en pipeline

**Tasks:**
- [ ] T09.1 — Revisar y completar pruebas unitarias existentes en cada microservicio
- [ ] T09.2 — Crear pruebas de integración (`auth` ↔ `gateway`, `form` ↔ `notification`)
- [ ] T09.3 — Crear pruebas E2E para flujo: registro → formulario → estado → notificación
- [ ] T09.4 — Crear scripts de Locust para pruebas de rendimiento (`tests/performance/`)
- [ ] T09.5 — Ejecutar OWASP ZAP contra el gateway y documentar resultados
- [ ] T09.6 — Configurar reporte de cobertura en pipeline CI (JaCoCo para Java)

---

### HU-10 — Stack de observabilidad
> **Como** equipo, **quiero** monitoreo y logging centralizados **para** tener visibilidad del estado de todos los servicios en producción.

**Criterios de aceptación:**
- Prometheus recolectando métricas de todos los servicios
- Grafana con dashboards por servicio
- ELK Stack recibiendo logs de todos los servicios
- Jaeger con tracing distribuido activo
- Alertas configuradas para situaciones críticas
- Health checks y probes funcionando

**Tasks:**
- [ ] T10.1 — Desplegar Prometheus en K8s y configurar scraping de métricas
- [ ] T10.2 — Desplegar Grafana y crear dashboards para cada microservicio
- [ ] T10.3 — Agregar métricas de negocio (formularios enviados, entradas al campus, etc.)
- [ ] T10.4 — Desplegar ELK Stack (Elasticsearch + Logstash + Kibana)
- [ ] T10.5 — Configurar Logstash para recibir logs de todos los servicios
- [ ] T10.6 — Desplegar Jaeger e integrar con los microservicios (OpenTelemetry)
- [ ] T10.7 — Configurar alertas en Grafana (servicio caído, latencia alta, errores 5xx)

---

### HU-11 — Seguridad y gestión de secretos
> **Como** equipo, **quiero** implementar prácticas de seguridad en la infraestructura **para** proteger los datos sensibles y cumplir el requisito de seguridad.

**Criterios de aceptación:**
- Secrets gestionados con Kubernetes Secrets o HashiCorp Vault
- RBAC configurado en el cluster K8s
- TLS habilitado en servicios expuestos públicamente
- Escaneo continuo de vulnerabilidades en pipeline

**Tasks:**
- [ ] T11.1 — Migrar credenciales hardcodeadas a Kubernetes Secrets
- [ ] T11.2 — Configurar RBAC en el cluster K8s (roles por namespace)
- [ ] T11.3 — Configurar TLS en el Ingress del gateway (cert-manager + Let's Encrypt)
- [ ] T11.4 — Verificar que Trivy está escaneando todas las imágenes en CI/CD

---

### HU-12 — Change Management y Release Notes
> **Como** equipo, **quiero** un proceso formal de gestión de cambios y release notes automáticas **para** documentar cada versión del proyecto.

**Criterios de aceptación:**
- Release Notes generadas automáticamente en cada release
- Sistema de etiquetado de versiones funcionando
- Plan de rollback documentado

**Tasks:**
- [ ] T12.1 — Configurar generación automática de release notes (`semantic-release` o GitHub Releases)
- [ ] T12.2 — Documentar proceso de Change Management en `docs/change-management.md`
- [ ] T12.3 — Documentar plan de rollback por servicio en `docs/rollback.md`
- [ ] T12.4 — Crear template de release notes en `.github/release.yml`

---

### HU-13 — Documentación final y presentación
> **Como** equipo, **quiero** documentación completa del proyecto **para** la entrega final y la presentación de 20-30 minutos.

**Criterios de aceptación:**
- `README.md` actualizado con arquitectura completa
- Diagramas de arquitectura e infraestructura incluidos
- Manual de operaciones básico escrito
- Análisis de costos de infraestructura documentado
- Video demostrativo grabado

**Tasks:**
- [ ] T13.1 — Actualizar `README.md` con arquitectura completa y diagramas
- [ ] T13.2 — Crear `docs/operaciones.md` (manual básico de operaciones)
- [ ] T13.3 — Crear `docs/costos.md` con análisis de costos de infraestructura
- [ ] T13.4 — Preparar slides de presentación (arquitectura, CI/CD, demo, monitoreo, pruebas)
- [ ] T13.5 — Grabar video demostrativo del sistema funcionando

---

## Resumen por integrante (sugerido)

| Integrante | Área principal |
|-----------|---------------|
| **Integrante 1** | Terraform, Kubernetes, CI/CD (HU-04, HU-06, HU-07) |
| **Integrante 2** | Pruebas, Observabilidad, Seguridad (HU-09, HU-10, HU-11) |
| **Ambos** | Setup, Ágil, Patrones, Docs (HU-01, HU-02, HU-03, HU-05, HU-08, HU-12, HU-13) |
