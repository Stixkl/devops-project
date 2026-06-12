# Guía de evidencias del proyecto final

> Trazabilidad contra `docs/Proyecto Final IngeSoft V (1).md`.
> Fecha de revisión: 2026-06-12.

## 0. Cómo usar esta guía

Esta guía indica, para cada punto de la rúbrica:

- **Estado:** `Completo`, `Parcial` o `Pendiente`.
- **Dónde:** archivo o directorio que implementa el requisito.
- **Evidencia:** qué mostrar durante la sustentación.
- **Cómo verificar:** comando reproducible.
- **Resultado esperado:** salida mínima que demuestra el punto.

Los estados distinguen entre código implementado y evidencia externa. Por ejemplo, un workflow puede existir en el repositorio, pero su ejecución debe mostrarse en GitHub Actions.

### Repositorios

El proyecto está dividido en dos repositorios hermanos:

```text
C:\Users\juanc\Videos\
├── devops-project\       # aplicación, CI/CD, pruebas y documentación
└── circleguard-infra\    # Terraform, Kubernetes, observabilidad y bonos
```

Si falta el repositorio de infraestructura:

```powershell
Set-Location C:\Users\juanc\Videos
git clone https://github.com/JuanAmor8/circleguard-infra.git
```

### Prerrequisitos

- Java 21.
- Docker y Docker Compose.
- Node.js y npm.
- Python 3 y pip.
- Git.
- Para infraestructura: Terraform, Azure CLI, kubectl y Helm.
- Para bonos locales: kind, Linkerd CLI y Git Bash o WSL.
- Para consultar pipelines: GitHub CLI (`gh auth login`).

### Resumen

| # | Rubro | Estado de evidencia |
|---|---|---|
| 1 | Metodología ágil y branching | Completo |
| 2 | Infraestructura como código | Completo; dev desplegado, stage/prod definidos |
| 3 | Patrones de diseño | Completo |
| 4 | CI/CD avanzado | Completo en código; ejecuciones se muestran en Actions |
| 5 | Pruebas completas | Parcial; suites automatizadas, falta consolidar resultados de performance |
| 6 | Change Management | Completo |
| 7 | Observabilidad | Completo |
| 8 | Seguridad | Completo en código; TLS público requiere ambiente prod |
| 9 | Documentación y presentación | Parcial; faltan video y diapositivas |
| B1 | Multi-Cloud | Parcial; AKS real, GKE como IaC/demo |
| B2 | Service Mesh | Completo |
| B3 | Chaos Engineering | Completo |
| B4 | FinOps | Completo |

---

## 1. Metodología ágil y estrategia de branching (10 %)

### 1.1 Implementar Scrum o Kanban

- **Estado:** Completo.
- **Dónde:** `docs/AGILE_METHODOLOGY.md`.
- **Evidencia:** Scrum adaptado a dos integrantes, roles, ceremonias, Definition of Done y sprints de dos semanas.
- **Cómo verificar:** `Get-Content docs\AGILE_METHODOLOGY.md | Select-String "Scrum|Sprints|Ceremonias|Definition of Done"`.
- **Resultado esperado:** aparecen marco Scrum, duración, ceremonias y Definition of Done.

### 1.2 Definir estrategia de branching

- **Estado:** Completo.
- **Dónde:** `BRANCHING_STRATEGY.md`.
- **Evidencia:** GitHub Flow con ramas de ambiente, reglas de PR, Conventional Commits, releases y hotfixes.
- **Cómo verificar:** `Get-Content BRANCHING_STRATEGY.md | Select-String "GitHub Flow|feature|release|hotfix|Conventional"`.
- **Resultado esperado:** se muestran flujo de ramas, convenciones y proceso de promoción.

### 1.3 Usar un sistema de gestión ágil

- **Estado:** Parcial como evidencia local; la configuración vive en GitHub Projects.
- **Dónde:** `docs/AGILE_METHODOLOGY.md`, `BACKLOG.md` y tablero GitHub Projects del repositorio.
- **Evidencia:** tablero con columnas Backlog, Sprint, En progreso, Revisión y Done; issues vinculados a PR.
- **Cómo verificar:** `gh issue list --state all --limit 100` y abrir la pestaña **Projects** en GitHub.
- **Resultado esperado:** issues/HU visibles y tablero con estados. Si el tablero no existe, debe crearse antes de la entrega.

### 1.4 Documentar sprints, historias y criterios de aceptación

- **Estado:** Completo.
- **Dónde:** `BACKLOG.md`.
- **Evidencia:** Sprint 1, Sprint 2, HU-01 a HU-13, criterios de aceptación y tareas.
- **Cómo verificar:** `rg -n "^## Sprint|^### HU-|Criterios de aceptación" BACKLOG.md`.
- **Resultado esperado:** dos sprints y las historias con criterios asociados.

### 1.5 Realizar al menos dos iteraciones

- **Estado:** Completo.
- **Dónde:** `docs/AGILE_METHODOLOGY.md` y `BACKLOG.md`.
- **Evidencia:** Iteración 1 Fundamentos e Iteración 2 Calidad/observabilidad, cada una con objetivo, review y retrospectiva.
- **Cómo verificar:** `rg -n "Iteración 1|Iteración 2|Review|Retrospectiva" docs\AGILE_METHODOLOGY.md`.
- **Resultado esperado:** dos ciclos completos documentados.

### Evidencia recomendada para sustentación

```powershell
git log --oneline --decorate --all -30
git branch -a
gh pr list --state all --limit 30
```

Mostrar backlog, tablero, ramas reales, PR y commits convencionales.

---

## 2. Infraestructura como código con Terraform (20 %)

### 2.1 Configurar infraestructura necesaria con Terraform

- **Estado:** Completo para infraestructura cloud de clúster; workloads se gestionan con manifiestos Kubernetes.
- **Dónde:** `../circleguard-infra/terraform/modules/aks-cluster/` y `../circleguard-infra/terraform/environments/`.
- **Evidencia:** recursos Azure para Resource Group, VNet, subnet, ACR opcional, AKS y node pools.
- **Cómo verificar:** `rg -n 'resource "azurerm_' ..\circleguard-infra\terraform\modules\aks-cluster`.
- **Resultado esperado:** recursos `azurerm_resource_group`, `azurerm_virtual_network`, `azurerm_subnet`, `azurerm_container_registry` y `azurerm_kubernetes_cluster`.

### 2.2 Implementar estructura modular

- **Estado:** Completo.
- **Dónde:** `../circleguard-infra/terraform/modules/aks-cluster/` y `../circleguard-infra/terraform/modules/gke-cluster/`.
- **Evidencia:** módulos reutilizables con `main.tf`, `variables.tf` y `outputs.tf`.
- **Cómo verificar:** `Get-ChildItem ..\circleguard-infra\terraform\modules -Recurse -Filter *.tf`.
- **Resultado esperado:** módulos AKS y GKE separados, con entradas y salidas tipadas.

### 2.3 Configurar dev, stage y prod

- **Estado:** Completo como IaC; solo dev tiene despliegue Azure verificado.
- **Dónde:** `../circleguard-infra/terraform/environments/dev/`, `stage/` y `prod/`.
- **Evidencia:** roots aislados, variables por ambiente y clúster independiente.
- **Cómo verificar:** `Get-ChildItem ..\circleguard-infra\terraform\environments\dev, ..\circleguard-infra\terraform\environments\stage, ..\circleguard-infra\terraform\environments\prod`.
- **Resultado esperado:** cada ambiente contiene `main.tf`, `providers.tf`, `variables.tf`, `outputs.tf`, `backend.hcl` y `.tfvars`.

### 2.4 Documentar arquitectura con diagramas

- **Estado:** Completo.
- **Dónde:** `docs/architecture/circleguard-architecture.drawio` y `docs/architecture/circleguard-architecture.png`.
- **Evidencia:** diagrama editable y exportado.
- **Cómo verificar:** `Get-Item docs\architecture\circleguard-architecture.drawio, docs\architecture\circleguard-architecture.png`.
- **Resultado esperado:** ambos archivos existen y el PNG puede abrirse durante la presentación.

### 2.5 Implementar backend remoto

- **Estado:** Completo.
- **Dónde:** `../circleguard-infra/terraform/environments/*/backend.hcl`, `providers.tf` y `terraform/scripts/init-backend.sh`.
- **Evidencia:** backend `azurerm`, contenedor `tfstate` y keys separadas por ambiente.
- **Cómo verificar:** `rg -n 'backend "azurerm"|key\s+=' ..\circleguard-infra\terraform\environments`.
- **Resultado esperado:** keys `dev.tfstate`, `stage.tfstate`, `prod.tfstate` y `gcp-dr.tfstate`.

### Validación sin crear recursos

Ejecutar por ambiente:

```powershell
Set-Location ..\circleguard-infra\terraform\environments\dev
terraform init -backend=false
terraform validate
```

Resultado esperado: `Success! The configuration is valid.`

### Evidencia del AKS dev real

```powershell
az aks show --resource-group rg-circle-guard-dev --name cg-aks-dev --output table
az aks get-credentials --admin --resource-group rg-circle-guard-dev --name cg-aks-dev --overwrite-existing
kubectl get nodes
kubectl get pods -n circleguard-dev
```

Resultado esperado: clúster `cg-aks-dev`, dos nodos `Ready` y 13 pods `Running` cuando el clúster está encendido.

---

## 3. Patrones de diseño (10 %)

### 3.1 Identificar patrones existentes

- **Estado:** Completo.
- **Dónde:** `docs/DESIGN_PATTERNS.md`.
- **Evidencia:** catálogo de Builder, Repository, Observer/Pub-Sub, Chain of Responsibility, Facade, Adapter, Strategy, DTO, Caching y Retry.
- **Cómo verificar:** `rg -n "Builder|Repository|Observer|Facade|Adapter|Strategy|DTO|Caching" docs\DESIGN_PATTERNS.md`.
- **Resultado esperado:** al menos diez patrones existentes documentados.

### 3.2 Implementar patrón de resiliencia

- **Estado:** Completo.
- **Dónde:** `PromotionClient.java`, `IdentityClient.java`, `AuthServiceClient.java` y sus pruebas.
- **Evidencia:** `@CircuitBreaker`, fallbacks, Resilience4j y recuperación degradada.
- **Cómo verificar:** `rg -n "@CircuitBreaker|fallbackMethod" services`.
- **Resultado esperado:** circuit breakers en dashboard, auth y notification.

### 3.3 Implementar patrón de configuración

- **Estado:** Completo.
- **Dónde:** `DashboardProperties.java`, archivos `application.yml` y ConfigMaps de `../circleguard-infra/k8s/`.
- **Evidencia:** `@ConfigurationProperties`, variables de entorno y configuración externa por ambiente.
- **Cómo verificar:** `rg -n "@ConfigurationProperties|CIRCLEGUARD_CLIENT|CIRCLEGUARD_FEATURES" services`.
- **Resultado esperado:** propiedades tipadas y valores sobreescribibles sin recompilar.

### 3.4 Implementar al menos tres patrones adicionales

- **Estado:** Completo.
- **Dónde:** `docs/DESIGN_PATTERNS.md`.
- **Evidencia:** Circuit Breaker, Retry, External Configuration y Feature Toggle.
- **Cómo verificar:** `rg -n "^### 1\\.[1-4]" docs\DESIGN_PATTERNS.md`.
- **Resultado esperado:** cuatro patrones nuevos/mejorados documentados.

### 3.5 Documentar propósito y beneficios

- **Estado:** Completo.
- **Dónde:** `docs/DESIGN_PATTERNS.md`.
- **Evidencia:** cada patrón incluye propósito, implementación, ubicación y beneficio.
- **Cómo verificar:** `rg -n "Propósito|Implementación|Beneficio|Dónde" docs\DESIGN_PATTERNS.md`.
- **Resultado esperado:** campos explicativos para los patrones principales.

### Pruebas de los patrones

```powershell
.\gradlew :services:circleguard-dashboard-service:test --tests "*PromotionClientTest" --tests "*AnalyticsServiceTest"
.\gradlew :services:circleguard-auth-service:test --tests "*IdentityClientTest"
.\gradlew :services:circleguard-notification-service:test --tests "*AuthServiceClientTest"
```

Resultado esperado: tareas Gradle `BUILD SUCCESSFUL`.

---

## 4. CI/CD avanzado (15 %)

### 4.1 Implementar pipelines completos

- **Estado:** Completo.
- **Dónde:** `.github/workflows/ci.yml`, `cd-dev.yml`, `cd-stage.yml` y `cd-gcp.yml`.
- **Evidencia:** CI, build/push Docker, despliegue Kubernetes, smoke tests y release.
- **Cómo verificar:** `rg -n "^  [a-zA-Z0-9_-]+:" .github\workflows`.
- **Resultado esperado:** jobs de build, pruebas, seguridad, release y deploy.

### 4.2 Ambientes separados y promoción controlada

- **Estado:** Completo en workflows; stage/prod cloud no están activos en la suscripción Students.
- **Dónde:** `.github/workflows/cd-dev.yml`, `cd-stage.yml`, `ci.yml` y `../circleguard-infra/k8s/{dev,stage,master}/`.
- **Evidencia:** `dev` despliega a `circleguard-dev`, `release/**` a stage y `master` a production.
- **Cómo verificar:** `rg -n "branches:|environment:|circleguard-dev|circleguard-stage|circleguard-master" .github\workflows`.
- **Resultado esperado:** mapeo rama-ambiente explícito.

### 4.3 Implementar SonarQube

- **Estado:** Completo en configuración; requiere `SONAR_TOKEN` y servidor configurado.
- **Dónde:** `build.gradle.kts` y job `sonarqube` de `.github/workflows/ci.yml`.
- **Evidencia:** plugin Sonar, propiedades JaCoCo y ejecución `./gradlew sonar`.
- **Cómo verificar:** `rg -n "org.sonarqube|sonar.projectKey|./gradlew sonar" build.gradle.kts .github\workflows\ci.yml`.
- **Resultado esperado:** plugin y job SonarQube visibles.

### 4.4 Implementar Trivy

- **Estado:** Completo.
- **Dónde:** job `docker-build-scan` de `.github/workflows/ci.yml`.
- **Evidencia:** escaneo SARIF y gate de vulnerabilidades críticas/altas.
- **Cómo verificar:** `rg -n "aquasecurity/trivy-action|sarif|CRITICAL,HIGH" .github\workflows\ci.yml`.
- **Resultado esperado:** dos pasos Trivy por imagen: reporte y gate.

### 4.5 Versionado semántico automático

- **Estado:** Completo en configuración; actualmente el clon local no muestra tags.
- **Dónde:** `.releaserc.json` y job `release` de `.github/workflows/ci.yml`.
- **Evidencia:** semantic-release, changelog, GitHub Release y tags `vX.Y.Z`.
- **Cómo verificar:** `Get-Content .releaserc.json; git tag --list; gh release list`.
- **Resultado esperado:** configuración semantic-release. Para evidencia total debe existir al menos un tag/release generado por pipeline.

### 4.6 Notificaciones automáticas de fallos

- **Estado:** Completo.
- **Dónde:** jobs `notify-failure` de los workflows.
- **Evidencia:** creación automática de issue con label `cd-failure`; Slack es opcional.
- **Cómo verificar:** `rg -n "notify-failure|cd-failure|issues.create" .github\workflows`.
- **Resultado esperado:** jobs condicionados a `failure()` y creación de issue.

### 4.7 Aprobaciones para producción

- **Estado:** Completo en workflow; la regla de reviewers es configuración externa de GitHub.
- **Dónde:** job `deploy-prod` de `.github/workflows/ci.yml` y Settings > Environments > production.
- **Evidencia:** environment `production` con required reviewers.
- **Cómo verificar:** `rg -n "deploy-prod|environment:|production" .github\workflows\ci.yml` y abrir Settings > Environments.
- **Resultado esperado:** job ligado a `production`; ejecución queda en espera de aprobación.

### Evidencia de ejecuciones

```powershell
gh workflow list
gh run list --limit 20
gh run view <RUN_ID>
```

Mostrar un run verde de CI, un deploy dev y el gate de producción.

---

## 5. Pruebas completas (15 %)

### 5.1 Pruebas unitarias

- **Estado:** Completo.
- **Dónde:** `services/*/src/test/java/`.
- **Evidencia:** 56 archivos `*Test.java` en los ocho servicios.
- **Cómo verificar:** `(Get-ChildItem services -Recurse -Filter '*Test.java' | Measure-Object).Count; .\gradlew test`.
- **Resultado esperado:** conteo 56 y `BUILD SUCCESSFUL`.

### 5.2 Pruebas de integración

- **Estado:** Completo.
- **Dónde:** `tests/integration-tests/`.
- **Evidencia:** siete archivos de integración para servicios y flujo cruzado, con Testcontainers.
- **Cómo verificar:** `(Get-ChildItem tests\integration-tests -Recurse -Filter '*Test.java' | Measure-Object).Count; .\gradlew :tests:integration-tests:test`.
- **Resultado esperado:** conteo 7 y `BUILD SUCCESSFUL`.

### 5.3 Pruebas E2E

- **Estado:** Completo.
- **Dónde:** `tests/e2e/cypress/cypress/e2e/`.
- **Evidencia:** cinco specs para autenticación, encuesta, acceso, flujo cruzado e identidad.
- **Cómo verificar:** `Set-Location tests\e2e\cypress; npm ci; npx cypress run`.
- **Resultado esperado:** cinco specs ejecutadas; requieren servicios levantados.

### 5.4 Pruebas de rendimiento y estrés con Locust

- **Estado:** Parcial en evidencia; scripts existen, falta adjuntar reporte actualizado.
- **Dónde:** `tests/performance/locustfile.py` y `locust_stress_test.py`.
- **Evidencia:** perfiles de carga y estrés; reportes HTML generados al ejecutar.
- **Cómo verificar:** `Set-Location tests\performance; pip install -r requirements.txt; locust -f locustfile.py --headless -u 50 -r 5 -t 5m --host http://localhost:8087 --html report.html`.
- **Resultado esperado:** `report.html` con RPS, percentiles y tasa de errores. Adjuntarlo o capturarlo para completar evidencia.

### 5.5 Pruebas de seguridad

- **Estado:** Completo.
- **Dónde:** job ZAP de `.github/workflows/ci.yml`, OWASP Dependency Check y Trivy.
- **Evidencia:** ZAP baseline contra auth, análisis de dependencias y contenedores.
- **Cómo verificar:** `rg -n "zaproxy/action-baseline|dependencyCheckAggregate|trivy-action" .github\workflows\ci.yml`.
- **Resultado esperado:** los tres tipos de escaneo aparecen automatizados.

### 5.6 Informes de cobertura y calidad

- **Estado:** Completo en pipeline.
- **Dónde:** `build.gradle.kts` y `.github/workflows/ci.yml`.
- **Evidencia:** JaCoCo HTML/XML, gate de cobertura, Codecov y SonarQube.
- **Cómo verificar:** `.\gradlew test jacocoTestReport jacocoTestCoverageVerification`.
- **Resultado esperado:** `BUILD SUCCESSFUL`; reportes en `services/*/build/reports/jacoco/`.

### 5.7 Ejecución automatizada en pipelines

- **Estado:** Completo.
- **Dónde:** `.github/workflows/ci.yml`.
- **Evidencia:** jobs de unit, integration, E2E, performance, security y quality.
- **Cómo verificar:** `rg -n "integration-test|e2e|performance|security-scan|quality-check|docker-build-scan" .github\workflows\ci.yml`.
- **Resultado esperado:** todos los niveles aparecen como jobs o pasos del CI.

### Reportes que deben mostrarse

```powershell
Get-ChildItem services -Recurse -Path *\build\reports\tests\test\index.html
Get-ChildItem services -Recurse -Path *\build\reports\jacoco\*
Get-Item tests\performance\report.html -ErrorAction SilentlyContinue
```

Falta completar resultados numéricos de performance en `docs/TESTING_STRATEGY.md`; no usar campos en blanco durante la sustentación.

---

## 6. Change Management y Release Notes (5 %)

### 6.1 Proceso formal de cambios

- **Estado:** Completo.
- **Dónde:** `docs/CHANGE_MANAGEMENT.md`.
- **Evidencia:** cambios estándar, normales y de emergencia; solicitud, evaluación, aprobación, despliegue, verificación y cierre.
- **Cómo verificar:** `rg -n "Estándar|Normal|Emergencia|Solicitud|Evaluación|Aprobación|Verificación" docs\CHANGE_MANAGEMENT.md`.
- **Resultado esperado:** proceso y responsables documentados.

### 6.2 Release Notes automáticas

- **Estado:** Completo en automatización; evidencia de releases debe consultarse en GitHub.
- **Dónde:** `.releaserc.json`, job `release`, `scripts/generate-release-notes.sh` y `RELEASE_NOTES.md`.
- **Evidencia:** semantic-release genera changelog, tag y GitHub Release; script manual como fallback.
- **Cómo verificar:** `rg -n "semantic-release|release-notes-generator|changelog" .releaserc.json .github\workflows\ci.yml; gh release list`.
- **Resultado esperado:** configuración presente y releases publicadas. Si `gh release list` está vacío, ejecutar el flujo de release antes de entregar.

### 6.3 Planes de rollback

- **Estado:** Completo.
- **Dónde:** `docs/CHANGE_MANAGEMENT.md`.
- **Evidencia:** rollback de Kubernetes, base de datos, Terraform y feature toggles.
- **Cómo verificar:** `rg -n "rollout undo|Flyway|git revert|Feature Toggle" docs\CHANGE_MANAGEMENT.md`.
- **Resultado esperado:** comandos y estrategia por capa.

### 6.4 Etiquetado de releases

- **Estado:** Parcial como evidencia: automatización existe, pero el clon local no muestra tags.
- **Dónde:** `.releaserc.json` y job `release`.
- **Evidencia:** tags `vX.Y.Z` creados desde Conventional Commits.
- **Cómo verificar:** `git tag --list "v*"; gh release list`.
- **Resultado esperado:** al menos un tag y GitHub Release. Si no aparecen, este punto todavía no tiene evidencia runtime.

---

## 7. Observabilidad y monitoreo (10 %)

### 7.1 Prometheus y Grafana

- **Estado:** Completo.
- **Dónde:** `../circleguard-infra/observability/` y `../circleguard-infra/k8s/master/observability/`.
- **Evidencia:** Prometheus, Grafana, datasources, dashboards y ServiceMonitors.
- **Cómo verificar:** `Set-Location ..\circleguard-infra\observability; docker compose -f docker-compose.observability.yml up -d; docker compose -f docker-compose.observability.yml ps`.
- **Resultado esperado:** Prometheus y Grafana en estado `Up`.

### 7.2 ELK Stack

- **Estado:** Completo.
- **Dónde:** `../circleguard-infra/observability/docker-compose.observability.yml`, `logstash/` y manifiestos master.
- **Evidencia:** Elasticsearch, Logstash, Kibana y Filebeat.
- **Cómo verificar:** `curl.exe http://localhost:9200/_cluster/health; curl.exe http://localhost:5601/api/status`.
- **Resultado esperado:** Elasticsearch responde con estado del clúster y Kibana con estado disponible.

### 7.3 Dashboards por servicio

- **Estado:** Completo.
- **Dónde:** `../circleguard-infra/observability/grafana/dashboards/services/`.
- **Evidencia:** ocho JSON, uno por microservicio, más overview y costos.
- **Cómo verificar:** `(Get-ChildItem ..\circleguard-infra\observability\grafana\dashboards\services -Filter *.json | Measure-Object).Count`.
- **Resultado esperado:** conteo 8.

### 7.4 Alertas críticas

- **Estado:** Completo.
- **Dónde:** `../circleguard-infra/observability/prometheus/alert-rules.yml`, `alertmanager/alertmanager.yml` y `prometheusrule-alertas.yaml`.
- **Evidencia:** reglas como `ServiceDown`, latencia, errores 5xx, memoria y circuit breaker.
- **Cómo verificar:** `rg -n "alert:" ..\circleguard-infra\observability\prometheus\alert-rules.yml ..\circleguard-infra\k8s\master\observability\prometheusrule-alertas.yaml`.
- **Resultado esperado:** reglas con severidad y expresiones PromQL.

### 7.5 Tracing distribuido

- **Estado:** Completo.
- **Dónde:** dependencias/configuración OTel de servicios y manifiestos Jaeger.
- **Evidencia:** Micrometer Tracing + OTLP hacia Jaeger.
- **Cómo verificar:** `rg -n "micrometer-tracing|opentelemetry-exporter|OTEL_EXPORTER" build.gradle.kts services; curl.exe http://localhost:16686/api/services`.
- **Resultado esperado:** dependencias OTel y servicios registrados en Jaeger después de generar tráfico.

### 7.6 Health checks y probes

- **Estado:** Completo.
- **Dónde:** `../circleguard-infra/k8s/dev/deployment-*.yaml`, stage/master y `application.yml`.
- **Evidencia:** startup, liveness y readiness sobre Actuator.
- **Cómo verificar:** `rg -n "startupProbe|livenessProbe|readinessProbe" ..\circleguard-infra\k8s`.
- **Resultado esperado:** probes en los deployments.

### 7.7 Métricas de negocio

- **Estado:** Completo.
- **Dónde:** clases `*Metrics.java` en servicios y dashboards Grafana.
- **Evidencia:** métricas de login, notificaciones, validaciones, encuestas y cambios de estado.
- **Cómo verificar:** `Get-ChildItem services -Recurse -Filter '*Metrics.java'; rg -n "Counter|Timer|Gauge" services --glob "*Metrics.java"`.
- **Resultado esperado:** instrumentación Micrometer de negocio en varios servicios.

### URLs para demostración local

- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`
- Alertmanager: `http://localhost:9093`
- Jaeger: `http://localhost:16686`
- Kibana: `http://localhost:5601`

---

## 8. Seguridad (5 %)

### 8.1 Escaneo continuo de vulnerabilidades

- **Estado:** Completo.
- **Dónde:** `.github/workflows/ci.yml` y `build.gradle.kts`.
- **Evidencia:** Trivy, OWASP Dependency Check y ZAP.
- **Cómo verificar:** `rg -n "dependencyCheckAggregate|zaproxy/action-baseline|trivy-action" .github\workflows\ci.yml`.
- **Resultado esperado:** tres escáneres integrados en CI.

### 8.2 Gestión segura de secretos

- **Estado:** Completo.
- **Dónde:** `../circleguard-infra/k8s/dev/sealed-secrets.yaml`, `../circleguard-infra/scripts/seal-dev-secrets.sh` y plantillas stage/prod.
- **Evidencia:** Sealed Secrets para dev; GitHub Environments y `envsubst` para stage/prod.
- **Cómo verificar:** `Get-Content ..\circleguard-infra\k8s\dev\sealed-secrets.yaml | Select-String "kind: SealedSecret|encryptedData"; rg -n "STAGE_|PROD_" .github\workflows`.
- **Resultado esperado:** secretos cifrados en dev y referencias a secrets de GitHub en otros ambientes.

### 8.3 RBAC

- **Estado:** Completo.
- **Dónde:** `../circleguard-infra/k8s/rbac.yaml` y `github-actions-rbac.yaml`.
- **Evidencia:** Roles y RoleBindings con permisos por namespace.
- **Cómo verificar:** `rg -n "kind: Role|kind: RoleBinding|verbs:|resources:" ..\circleguard-infra\k8s\rbac.yaml`.
- **Resultado esperado:** reglas RBAC explícitas y bindings separados.

### 8.4 TLS para servicios públicos

- **Estado:** Completo en manifiestos; validación pública depende de prod.
- **Dónde:** `../circleguard-infra/k8s/master/ingress/cert-manager-issuers.yaml` y `gateway-ingress.yaml`.
- **Evidencia:** cert-manager, Let's Encrypt, Ingress TLS y gateway como único punto público.
- **Cómo verificar:** `rg -n "ClusterIssuer|letsencrypt-prod|cert-manager.io/cluster-issuer|tls:" ..\circleguard-infra\k8s\master\ingress`.
- **Resultado esperado:** issuer y sección TLS. En ambiente activo, `curl.exe -I https://<dominio>` debe mostrar certificado válido.

---

## 9. Documentación y presentación (10 %)

### 9.1 Documentación completa

- **Estado:** Completo.
- **Dónde:** `docs/`, `README.md`, `BACKLOG.md`, `BRANCHING_STRATEGY.md` y documentación del repo infra.
- **Evidencia:** arquitectura, ejecución, pruebas, CI/CD, Terraform, patrones, change management, Azure y bonos.
- **Cómo verificar:** `Get-ChildItem docs -Filter *.md | Select-Object Name`.
- **Resultado esperado:** documentación temática organizada.

### 9.2 Repositorio Git organizado

- **Estado:** Completo.
- **Dónde:** `services/`, `tests/`, `docker/`, `scripts/`, `docs/`, `.github/workflows/` y repo infra separado.
- **Evidencia:** separación aplicación/infra y estructura por responsabilidad.
- **Cómo verificar:** `Get-ChildItem; Get-ChildItem ..\circleguard-infra`.
- **Resultado esperado:** directorios coherentes y sin infraestructura duplicada.

### 9.3 Costos de infraestructura

- **Estado:** Completo.
- **Dónde:** `docs/TERRAFORM.md`, `docs/BONUS_FINOPS.md` y dashboard `circleguard-costs.json`.
- **Evidencia:** estimaciones por ambiente, datos OpenCost y ahorro calculado.
- **Cómo verificar:** `rg -n "Total|mes|Ahorro|OpenCost" docs\TERRAFORM.md docs\BONUS_FINOPS.md`.
- **Resultado esperado:** tablas de costo y optimización.

### 9.4 Manual de operaciones básico

- **Estado:** Completo.
- **Dónde:** `docs/MANUAL_EJECUCION.md`, `docs/DESPLIEGUE_AZURE.md` y READMEs de infraestructura.
- **Evidencia:** arranque, despliegue, pruebas, logs, rollback y depuración.
- **Cómo verificar:** `rg -n "^## |kubectl|docker compose|gradlew|rollback" docs\MANUAL_EJECUCION.md docs\DESPLIEGUE_AZURE.md`.
- **Resultado esperado:** procedimientos reproducibles.

### 9.5 Video demostrativo

- **Estado:** Pendiente.
- **Dónde:** no existe un archivo o enlace de video en el repositorio.
- **Evidencia requerida:** video de máximo 8 minutos o enlace accesible, mostrando arquitectura, CI/CD, app, observabilidad y pruebas.
- **Cómo verificar:** `Get-ChildItem docs -Recurse -Include *.mp4,*.webm,*.mov`.
- **Resultado esperado:** archivo o documento con enlace. Sin resultado, requisito pendiente.

### 9.6 Presentación del proyecto

- **Estado:** Pendiente.
- **Dónde:** no existe presentación final en el repositorio.
- **Evidencia requerida:** PPTX, PDF o Markdown de diapositivas para 20-30 minutos.
- **Cómo verificar:** `Get-ChildItem docs -Recurse -Include *.pptx,*.pdf | Select-Object FullName`.
- **Resultado esperado:** material de presentación final.

---

## B1. Bonificación Multi-Cloud (5 %)

### B1.1 Desplegar en dos proveedores

- **Estado:** Parcial.
- **Dónde:** AKS en `../circleguard-infra/terraform/environments/dev/`; GKE en `../circleguard-infra/terraform/environments/gcp-dr/`, módulo `gke-cluster`, `.github/workflows/cd-gcp.yml` y `docs/DESPLIEGUE_GCP.md`.
- **Evidencia:** Azure dev fue desplegado; GCP tiene IaC y plan, pero debe mostrarse un clúster GKE real para cumplimiento literal.
- **Cómo verificar:** `az aks show -g rg-circle-guard-dev -n cg-aks-dev -o table; gcloud container clusters list`.
- **Resultado esperado:** AKS visible. Para completar, `gcloud` debe listar `cg-gke-dr`.

### B1.2 Estrategia de respaldo entre clouds

- **Estado:** Parcial hasta ejecutar backup/restore real.
- **Dónde:** `../circleguard-infra/k8s/dr/velero-schedule.yaml` y root `gcp-dr`.
- **Evidencia:** backup AKS hacia bucket GCS, RPO 24 h y restauración Velero.
- **Cómo verificar:** `Get-Content ..\circleguard-infra\k8s\dr\velero-schedule.yaml; velero backup get; velero restore get`.
- **Resultado esperado:** schedule en código; para evidencia total, backup `Completed` y restore probado.

### B1.3 Balanceo entre proveedores

- **Estado:** Completo como demo local; parcial como servicio cloud real.
- **Dónde:** `../circleguard-infra/multicloud/`.
- **Evidencia:** dos clusters kind simulando Azure/GCP y HAProxy con health checks/failover.
- **Cómo verificar:** seguir `../circleguard-infra/multicloud/README.md` y ejecutar `1..6 | ForEach-Object { curl.exe -s http://localhost:8090 }`.
- **Resultado esperado:** respuestas alternadas `cloud=azure` y `cloud=gcp`; al apagar Azure, 100 % GCP.

### B1.4 Comparativa de rendimiento

- **Estado:** Parcial.
- **Dónde:** `docs/BONUS_MULTICLOUD.md`.
- **Evidencia:** benchmark local documentado y comparación de costos/capacidades AKS vs GKE.
- **Cómo verificar:** `rg -n "promedio|Comparativa|AKS|GKE" docs\BONUS_MULTICLOUD.md`.
- **Resultado esperado:** métricas y tabla existentes. Para evidencia fuerte, adjuntar script/salida del benchmark real en ambos clouds.

---

## B2. Bonificación Service Mesh (5 %)

### B2.1 Implementar Istio, Linkerd o similar

- **Estado:** Completo.
- **Dónde:** `../circleguard-infra/k8s/mesh/`.
- **Evidencia:** Linkerd y namespace con inyección automática.
- **Cómo verificar:** `linkerd check; kubectl get pods -n linkerd`.
- **Resultado esperado:** checks verdes y control plane `Running`.

### B2.2 mTLS entre servicios

- **Estado:** Completo.
- **Dónde:** anotaciones `linkerd.io/inject: enabled`.
- **Evidencia:** sidecars y edges `SECURED`.
- **Cómo verificar:** `linkerd viz edges deployment -n circleguard-mesh`.
- **Resultado esperado:** columna `SECURED` activa en todas las aristas.

### B2.3 Traffic shifting canary

- **Estado:** Completo.
- **Dónde:** `../circleguard-infra/k8s/mesh/20-canary-httproute.yaml`.
- **Evidencia:** HTTPRoute 90/10 entre backend v1 y v2.
- **Cómo verificar:** `kubectl get httproute backend-canary -n circleguard-mesh -o yaml; linkerd viz stat deployment -n circleguard-mesh`.
- **Resultado esperado:** pesos 90/10 y RPS proporcional.

### B2.4 Visualizar el mesh

- **Estado:** Completo.
- **Dónde:** Linkerd Viz.
- **Evidencia:** dashboard con topología, éxito, RPS y latencias.
- **Cómo verificar:** `linkerd viz dashboard`.
- **Resultado esperado:** dashboard local en `http://localhost:50750`.

### B2.5 Circuit breakers y retry policies

- **Estado:** Completo.
- **Dónde:** `10-backend.yaml` y `30-serviceprofile-retries.yaml`.
- **Evidencia:** failure accrual, ruta retryable y retry budget.
- **Cómo verificar:** `rg -n "failure-accrual|isRetryable|retryBudget" ..\circleguard-infra\k8s\mesh`.
- **Resultado esperado:** circuit breaker y reintentos declarados.

---

## B3. Bonificación Chaos Engineering (5 %)

### B3.1 Implementar Chaos Mesh

- **Estado:** Completo.
- **Dónde:** `../circleguard-infra/chaos/`.
- **Evidencia:** cuatro CRD de Chaos Mesh y entorno objetivo.
- **Cómo verificar:** `Get-ChildItem ..\circleguard-infra\chaos\experiments`.
- **Resultado esperado:** cuatro experimentos YAML.

### B3.2 Diseñar experimentos

- **Estado:** Completo.
- **Dónde:** YAML de experimentos y `docs/BONUS_CHAOS_ENGINEERING.md`.
- **Evidencia:** hipótesis, blast radius, duración y selector por experimento.
- **Cómo verificar:** `rg -n "Hipótesis|hypothesis|duration|selector|mode:" docs\BONUS_CHAOS_ENGINEERING.md ..\circleguard-infra\chaos`.
- **Resultado esperado:** diseño explícito para pod kill, delay, pod failure y memoria.

### B3.3 Ejecutar pruebas de resiliencia

- **Estado:** Completo según resultados documentados; reproducible localmente.
- **Dónde:** `../circleguard-infra/chaos/README.md`.
- **Evidencia:** aplicar un experimento a la vez y observar métricas/health.
- **Cómo verificar:** `kubectl apply -f ..\circleguard-infra\chaos\experiments\01-pod-kill-promotion.yaml; kubectl get podchaos -A`.
- **Resultado esperado:** experimento activo y sistema degradándose de forma controlada.

### B3.4 Documentar resultados y mejoras

- **Estado:** Completo.
- **Dónde:** `docs/BONUS_CHAOS_ENGINEERING.md`.
- **Evidencia:** hipótesis confirmadas/falsada, métricas y tiempos de recuperación.
- **Cómo verificar:** `rg -n "Resultado|FALSADA|confirmada|recuperación|métricas" docs\BONUS_CHAOS_ENGINEERING.md`.
- **Resultado esperado:** resultados de cuatro experimentos.

### B3.5 Integrar aprendizajes

- **Estado:** Completo.
- **Dónde:** `PromotionClient.java` y sección de aprendizajes del documento.
- **Evidencia:** caché fallback corregida a campo `static final` tras hallar problema CGLIB.
- **Cómo verificar:** `rg -n "static final|CGLIB|lastSuccessCache" services\circleguard-dashboard-service docs\BONUS_CHAOS_ENGINEERING.md`.
- **Resultado esperado:** fix en código y explicación causal.

Después de demostrar:

```powershell
kubectl delete -f ..\circleguard-infra\chaos\experiments\01-pod-kill-promotion.yaml
```

---

## B4. Bonificación FinOps (5 %)

### B4.1 Monitoreo de costos

- **Estado:** Completo.
- **Dónde:** `scripts/install-opencost.sh` y `docs/finops/`.
- **Evidencia:** OpenCost, datos raw y asignación por namespace.
- **Cómo verificar:** `Get-Content docs\finops\opencost-allocation-sample.txt; kubectl get pods -n opencost`.
- **Resultado esperado:** datos de costo y pods OpenCost `Running`.

### B4.2 Políticas de ahorro

- **Estado:** Completo.
- **Dónde:** Terraform stage/prod, `scripts/scale-to-zero.sh` y configuraciones de retención.
- **Evidencia:** Spot, autoscaling, stop/start AKS, B-series y retención limitada.
- **Cómo verificar:** `rg -n "Spot|enable_auto_scaling|min_count|max_count" ..\circleguard-infra\terraform; Get-Content scripts\scale-to-zero.sh`.
- **Resultado esperado:** políticas codificadas.

### B4.3 Dashboards de costos y utilización

- **Estado:** Completo.
- **Dónde:** `../circleguard-infra/observability/grafana/dashboards/circleguard-costs.json`.
- **Evidencia:** costo/hora, proyección mensual, CPU/RAM por namespace y sobredimensionamiento.
- **Cómo verificar:** `Get-Item ..\circleguard-infra\observability\grafana\dashboards\circleguard-costs.json; curl.exe -u admin:admin http://localhost:3000/api/search`.
- **Resultado esperado:** dashboard `cg-costs` visible.

### B4.4 Análisis de optimización

- **Estado:** Completo.
- **Dónde:** `docs/BONUS_FINOPS.md`.
- **Evidencia:** CPU efficiency, right-sizing, apagado no-prod, Spot y DR.
- **Cómo verificar:** `rg -n "Hallazgo|Ahorro estimado|cpuEff|right-sizing" docs\BONUS_FINOPS.md`.
- **Resultado esperado:** tabla de hallazgos y acciones.

### B4.5 Estrategias y ahorros

- **Estado:** Completo como estimación.
- **Dónde:** `docs/BONUS_FINOPS.md` y `docs/TERRAFORM.md`.
- **Evidencia:** ahorro estimado de aproximadamente USD 200-250/mes y supuestos.
- **Cómo verificar:** `rg -n "200-250|158/mes|Ahorro total" docs\BONUS_FINOPS.md`.
- **Resultado esperado:** ahorros cuantificados y trazables a estrategias.

---

## 10. Entregables

### 10.1 Código fuente completo

- **Estado:** Completo.
- **Dónde:** repositorios `devops-project` y `circleguard-infra`.
- **Evidencia:** código de ocho microservicios, app móvil, pruebas e infraestructura.
- **Cómo verificar:** `git status --short; git -C ..\circleguard-infra status --short`.
- **Resultado esperado:** repositorios accesibles; antes de entregar, cambios intencionales confirmados y publicados.

### 10.2 Arquitectura detallada

- **Estado:** Completo.
- **Dónde:** `docs/PROJECT_OVERVIEW.md` y `docs/architecture/`.
- **Evidencia:** componentes, flujos, tecnologías y diagramas.
- **Cómo verificar:** `Get-Item docs\PROJECT_OVERVIEW.md, docs\architecture\circleguard-architecture.png`.
- **Resultado esperado:** documento y diagrama disponibles.

### 10.3 Metodología ágil

- **Estado:** Completo.
- **Dónde:** `docs/AGILE_METHODOLOGY.md`, `BACKLOG.md`, `BRANCHING_STRATEGY.md`.
- **Evidencia:** marco, sprints, HU y branching.
- **Cómo verificar:** `Get-Item docs\AGILE_METHODOLOGY.md, BACKLOG.md, BRANCHING_STRATEGY.md`.
- **Resultado esperado:** tres archivos presentes.

### 10.4 Patrones de diseño

- **Estado:** Completo.
- **Dónde:** `docs/DESIGN_PATTERNS.md`.
- **Evidencia:** catálogo e implementaciones adicionales.
- **Cómo verificar:** `Get-Item docs\DESIGN_PATTERNS.md`.
- **Resultado esperado:** archivo presente.

### 10.5 Guías de operación y mantenimiento

- **Estado:** Completo.
- **Dónde:** `docs/MANUAL_EJECUCION.md`, `docs/DESPLIEGUE_AZURE.md`, `docs/CHANGE_MANAGEMENT.md`.
- **Evidencia:** ejecución, operación, troubleshooting y rollback.
- **Cómo verificar:** `Get-Item docs\MANUAL_EJECUCION.md, docs\DESPLIEGUE_AZURE.md, docs\CHANGE_MANAGEMENT.md`.
- **Resultado esperado:** guías disponibles.

### 10.6 Análisis de pruebas

- **Estado:** Parcial.
- **Dónde:** `docs/TESTING_STRATEGY.md` y reportes generados.
- **Evidencia:** estrategia completa; faltan completar valores numéricos vacíos de performance.
- **Cómo verificar:** `$marcador = '_' * 3; Select-String -Path docs\TESTING_STRATEGY.md -Pattern $marcador`.
- **Resultado esperado:** actualmente aparecen campos pendientes. Deben reemplazarse con resultados del reporte Locust.

### 10.7 Documentación de IaC

- **Estado:** Completo.
- **Dónde:** `docs/TERRAFORM.md` y `../circleguard-infra/terraform/README.md`.
- **Evidencia:** módulos, ambientes, backend, operación y costos.
- **Cómo verificar:** `Get-Item docs\TERRAFORM.md, ..\circleguard-infra\terraform\README.md`.
- **Resultado esperado:** ambos documentos presentes.

### 10.8 Release Notes de cada versión

- **Estado:** Parcial.
- **Dónde:** `RELEASE_NOTES.md`, `.releaserc.json` y GitHub Releases.
- **Evidencia:** release notes iniciales y automatización; deben existir tags/releases por versión entregada.
- **Cómo verificar:** `git tag --list; gh release list`.
- **Resultado esperado:** lista de versiones. Si está vacía, generar release antes de entregar.

### 10.9 Presentación y demostración

- **Estado:** Pendiente.
- **Dónde:** material por crear en `docs/presentation/` o enlace documentado.
- **Evidencia requerida:** presentación de 20-30 minutos y video/demo.
- **Cómo verificar:** `Get-ChildItem docs -Recurse -Include *.pptx,*.pdf,*.mp4,*.webm`.
- **Resultado esperado:** archivos finales o enlaces accesibles.

---

## 11. Guion de demostración

### Bloque 1: arquitectura y repositorios

1. Abrir `docs/architecture/circleguard-architecture.png`.
2. Explicar ocho microservicios y dos repositorios.
3. Mostrar `docs/PROJECT_OVERVIEW.md`.

### Bloque 2: metodología y Git

1. Mostrar `BACKLOG.md`.
2. Mostrar GitHub Projects.
3. Ejecutar `git log --oneline --all -20` y `git branch -a`.

### Bloque 3: CI/CD y seguridad

1. Abrir GitHub Actions.
2. Mostrar run CI verde.
3. Mostrar Trivy, ZAP, Sonar y Dependency Check.
4. Mostrar deploy dev y gate production.

### Bloque 4: infraestructura y aplicación

```powershell
az aks show -g rg-circle-guard-dev -n cg-aks-dev -o table
kubectl get nodes
kubectl get pods -n circleguard-dev
kubectl get svc -n circleguard-dev
```

### Bloque 5: pruebas

```powershell
.\gradlew test
.\gradlew :tests:integration-tests:test
```

Mostrar Cypress, JaCoCo y `tests/performance/report.html`.

### Bloque 6: observabilidad

1. Grafana overview y dashboards por servicio.
2. Prometheus targets y alertas.
3. Jaeger trace.
4. Kibana logs.
5. OpenCost.

### Bloque 7: bonos

1. Linkerd: mTLS, canary y dashboard.
2. Chaos Mesh: experimento y recuperación.
3. FinOps: costos/ahorro.
4. Multi-cloud: demo HAProxy y estado real GKE si ya fue desplegado.

---

## 12. Checklist antes de entregar

- [ ] GitHub Project visible con issues e historias.
- [ ] Dos sprints y retrospectivas documentados.
- [ ] `terraform validate` verde en dev, stage y prod.
- [ ] Run CI verde con todos los jobs importantes.
- [ ] Deploy dev visible con pods `Running`.
- [ ] Environment production con required reviewers.
- [ ] Tag semántico y GitHub Release existentes.
- [ ] Reportes JaCoCo, Cypress, Locust, ZAP, Trivy y Sonar guardados.
- [ ] Resultados numéricos de performance agregados a `docs/TESTING_STRATEGY.md`.
- [ ] Dashboards Grafana y trazas Jaeger visibles.
- [ ] Video final creado.
- [ ] Presentación final creada.
- [ ] Enlaces de ambos repositorios incluidos en la entrega.
