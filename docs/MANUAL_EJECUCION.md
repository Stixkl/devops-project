# MANUAL DE EJECUCIÓN - Taller 2 CI/CD (Circle Guard)

> Este documento contiene los pasos que DEBES ejecutar manualmente. OpenCode ya preparó todos los artefactos.

---

## VERIFICACIÓN INICIAL

Antes de comenzar, verifica que todo está en su lugar:

> **Nota — dos repositorios**: la infraestructura (`terraform/`, `k8s/`, `observability/`, `chaos/`, `multicloud/`) vive ahora en un repo separado: [circleguard-infra](https://github.com/JuanAmor8/circleguard-infra). Clónalo junto a este repo antes de cualquier despliegue:
>
> ```powershell
> git clone https://github.com/JuanAmor8/circleguard-infra.git
> ```

```powershell
# Verificar estructura (repo de aplicación)
Get-ChildItem C:\Users\juanc\Videos\devops-project\docker
Get-ChildItem C:\Users\juanc\Videos\devops-project\.github\workflows
Get-ChildItem C:\Users\juanc\Videos\devops-project\tests
Get-ChildItem C:\Users\juanc\Videos\devops-project\scripts
Get-ChildItem C:\Users\juanc\Videos\devops-project\docs

# Verificar estructura (repo de infraestructura, clonado al lado)
Get-ChildItem C:\Users\juanc\Videos\circleguard-infra\k8s
```

Debes ver:
- `docker/`: 6 Dockerfiles + docker-compose.yml
- `.github/workflows/`: `ci.yml` (CI + deploy a prod), `cd-dev.yml`, `cd-stage.yml`
- `tests/`: integration-tests, e2e, performance
- `scripts/`: 8 scripts
- `docs/`: PIPELINES, TESTING_STRATEGY, PROJECT_OVERVIEW, architecture/
- En `circleguard-infra/`: `k8s/` (namespaces, dev, stage, master), `terraform/`, `observability/`, `chaos/`, `multicloud/`

---

## B1. INSTALAR DOCKER + KUBERNETES

### Opción recomendada (sin Docker Desktop - usa Minikube)

Ejecuta como **Administrador** en PowerShell:

```powershell
# 1. Instalar Chocolatey (si no tienes)
Set-ExecutionPolicy Bypass -Scope Process -Force
iex ((New-Object System.Net.WebClient).DownloadString('https://chocolatey.org/install.ps1'))

# 2. Instalar herramientas
choco install docker-engine docker-cli -y
choco install minikube kubernetes-cli -y

# 3. Reiniciar terminal y empezar Minikube
minikube start --driver=docker --cpus=4 --memory=8g

# 4. Verificar
kubectl get nodes
minikube status
```

---

## B2. CONFIGURAR GITHUB ACTIONS (CD)

El CD corre completamente en GitHub Actions (no hay que instalar nada localmente). Solo hay que configurar el repositorio una vez:

### 1. Crear Environments
Ve a **Settings → Environments** del repo y crea:

| Environment | Protección |
|-------------|------------|
| `dev` | — |
| `stage` | — |
| `production` | **Required reviewers** (gate de aprobación manual al deploy de prod) |

### 2. Agregar Secrets
Ve a **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Valor |
|--------|-------|
| `DOCKERHUB_USERNAME` | Tu usuario de Docker Hub |
| `DOCKERHUB_TOKEN` | Token de acceso de Docker Hub |
| `KUBE_CONFIG_DEV` | Kubeconfig del cluster dev en **base64** |
| `KUBE_CONFIG_STAGE` | Kubeconfig del cluster stage en base64 |
| `KUBE_CONFIG_PROD` | Kubeconfig del cluster prod en base64 |
| `STAGE_*` | Valores de los secretos de stage: `DB_PASSWORD`, `JWT_SECRET`, `QR_SECRET`, `LDAP_BIND_PASSWORD`, `NEO4J_PASSWORD`, `VAULT_SECRET`, `VAULT_SALT`, `VAULT_HASH_SALT`, `TWILIO_SID/TOKEN/FROM`, `GOTIFY_TOKEN` |
| `PROD_*` | Igual que stage, más `DB_URL`, `DB_USERNAME`, `LDAP_URL` (= `ldap://openldap-prod:389`), `LDAP_BIND_DN`, `NEO4J_USERNAME` |

> Si algún `KUBE_CONFIG_*` no está definido, el workflow publica las imágenes pero omite el deploy al cluster con un warning.

### 3. Disparar los pipelines

Cada despliegue se dispara con un push a la rama correspondiente:

```powershell
git push origin dev          # → cd-dev.yml: build+push (dev-<sha>) y deploy a circleguard-dev
git push origin release/1.2  # → cd-stage.yml: build+push (stage-<run>) y deploy a circleguard-stage
git push origin master       # → ci.yml: CI completo + semantic-release + deploy-prod
                             #   (queda en espera hasta aprobar en el environment `production`)
```

El avance se sigue en la pestaña **Actions** del repo; los fallos de CD abren un issue con label `cd-failure`.

---

## B3. CONSTRUIR IMÁGENES DOCKER LOCALMENTE

```powershell
cd C:\Users\Administrator\Videos\devops-project

# Verificar docker-compose
docker compose -f docker/docker-compose.yml config

# Construir todas las imágenes (esto tarda ~20-30 minutos)
docker compose -f docker/docker-compose.yml build

# Verificar imágenes creadas
docker images | Select-String circleguard
```

**Resultado esperado:** 6 imágenes `circleguard/<service>:latest`

---

## B4. DESPLEGAR A KUBERNETES

Los manifiestos viven en el repo [circleguard-infra](https://github.com/JuanAmor8/circleguard-infra). Clónalo primero si no lo has hecho.

### Secretos de DEV (Sealed Secrets)

El antiguo `k8s/dev/secrets.yaml` (base64 en git) fue reemplazado por **Bitnami Sealed Secrets**:

```powershell
# 1. Instalar el controller (una sola vez por cluster)
helm install sealed-secrets sealed-secrets/sealed-secrets -n kube-system

# 2. Generar los secretos sellados (desde el repo circleguard-infra)
cd C:\Users\Administrator\Videos\circleguard-infra
./scripts/seal-dev-secrets.sh
# Produce k8s/dev/sealed-secrets.yaml (cifrado, seguro de commitear)
```

Stage y master siguen usando plantillas `envsubst` alimentadas por los secretos `STAGE_*`/`PROD_*` de GitHub Actions. Nota: la plantilla de secretos de master ahora incluye la clave `NEO4J_AUTH`, y el secret `PROD_LDAP_URL` debe ser `ldap://openldap-prod:389`.

### Despliegue

```powershell
cd C:\Users\Administrator\Videos\circleguard-infra

# 1. Crear namespaces
kubectl apply -f k8s/namespaces/

# 2. Verificar namespaces
kubectl get namespaces

# 3. Desplegar a DEV (incluye sealed-secrets.yaml)
kubectl apply -f k8s/dev/ -n circleguard-dev

# 4. Ver pods
kubectl get pods -n circleguard-dev

# 5. Ver servicios
kubectl get svc -n circleguard-dev

# 6. Esperar a que estén ready
kubectl rollout status deployment/auth-service -n circleguard-dev --timeout=120s

# 7. Ver logs de un pod
kubectl logs -n circleguard-dev deployment/auth-service --tail=50
```

### Datastores en stage y master

Ya no es solo dev: los manifiestos de `circleguard-infra` despliegan también los datastores en stage y master:

- **stage**: `kafka`, `zookeeper`, `neo4j`, `redis`, `openldap` (almacenamiento `emptyDir`).
- **master (prod)**: `kafka-prod`, `zookeeper-prod`, `neo4j-prod`, `redis-prod`, `openldap-prod` (con PVCs).

---

## B5. EJECUTAR PRUEBAS

### Unit Tests (desde la raíz del proyecto)

```powershell
cd C:\Users\Administrator\Videos\devops-project

# Ejecutar tests de un servicio específico
.\gradlew :services:circleguard-auth-service:test
.\gradlew :services:circleguard-identity-service:test
.\gradlew :services:circleguard-gateway-service:test
.\gradlew :services:circleguard-form-service:test
.\gradlew :services:circleguard-notification-service:test
.\gradlew :services:circleguard-promotion-service:test

# O ejecutar TODOS los tests de una vez
.\gradlew test
```

### Integration Tests

```powershell
cd C:\Users\Administrator\Videos\devops-project

# Ejecutar módulo de integración
.\gradlew :tests:integration-tests:test

# Ver reportes
# Los resultados estarán en tests/integration-tests/build/test-results/
```

### E2E Tests (requiere que los servicios estén corriendo)

```powershell
cd C:\Users\Administrator\Videos\devops-project\tests\e2e

# Instalar Cypress
npm install

# Ejecutar tests (headless)
npx cypress run

# O abrir la UI para ver los tests
npx cypress open
```

### Performance Tests (requiere que los servicios estén corriendo)

```powershell
cd C:\Users\Administrator\Videos\devops-project\tests\performance

# Instalar dependencias
pip install -r requirements.txt

# Test básico de carga (50 usuarios, 5 minutos)
locust -f locustfile.py --headless -u 50 -r 5 -t 5m --host http://localhost:8087 --html report.html

# Test de stress (500 usuarios)
locust -f locust_stress_test.py --headless -u 500 -r 50 -t 5m --host http://localhost:8087 --html stress_report.html
```

---

## B6. CAPTURAR EVIDENCIAS

```powershell
# Crear carpeta para screenshots
New-Item -ItemType Directory -Force -Path C:\Users\Administrator\Videos\devops-project\docs\screenshots

# Capturas necesarias:
# 1. GitHub Actions - pestaña Actions con los workflows (ci.yml, cd-dev.yml, cd-stage.yml)
# 2. GitHub Actions - run de cd-dev.yml ejecutándose (logs del job deploy-dev)
# 3. GitHub Actions - run de cd-stage.yml ejecutándose
# 4. GitHub Actions - job deploy-prod con el gate de aprobación del environment production
# 5. K8s - kubectl get pods -n circleguard-dev
# 6. K8s - kubectl get pods -n circleguard-stage
# 7. K8s - kubectl get pods -n circleguard-master
# 8. Tests - Reporte JUnit HTML
# 9. Tests - Reporte Cypress (si usas dashboard)
# 10. Tests - Reporte Locust HTML
# 11. Git - git tag -l (tags de release)
# 12. Git - RELEASE_NOTES.md
```

---

## B7. GRABAR VIDEO

Sigue el guion en `docs/VIDEO_SCRIPT.md`. Herramientas recomendadas:
- **OBS Studio** (gratis): https://obsproject.com/
- **ShareX** (Windows, gratis): para screenshots rápidos

Estructura del video (≤8 minutos):
- 0:00-0:30 → Intro + arquitectura
- 0:30-1:30 → Setup Docker/K8s + configuración GitHub Actions
- 1:30-3:00 → Pipeline DEV
- 3:00-4:30 → Tests (unit + integration + E2E + performance)
- 4:30-6:00 → Pipeline STAGE
- 6:00-7:30 → Pipeline MASTER + Release Notes
- 7:30-8:00 → Locust report + cierre

---

## B8. EMPAQUETAR ENTREGA

```powershell
cd C:\Users\Administrator\Videos\devops-project

# Crear ZIP con todos los artefactos
Compress-Archive -Path docker,.github,tests,scripts,docs -DestinationPath circleguard-taller2-entrega.zip -Force
# (los manifiestos k8s viven en el repo circleguard-infra)

# Verificar tamaño
Get-Item circleguard-taller2-entrega.zip | Select-Object Name, Length

# Verificar contenido del ZIP
Expand-Archive -Path circleguard-taller2-entrega.zip -DestinationPath verify_temp
Get-ChildItem verify_temp
Remove-Item -Recurse -Force verify_temp
```

---

## CHECKLIST FINAL

Antes de entregar, verifica cada punto de la rúbrica:

| Punto | % | Verificación |
|-------|---|--------------|
| 1 | 10% | Docker funcionando, K8s corriendo, workflows de Actions configurados |
| 2 | 15% | Workflows CD (cd-dev, cd-stage, deploy-prod) ejecutando en Actions |
| 3 | 30% | Unit tests pasan, 8 integration tests, 23 E2E tests, Locust report |
| 4 | 15% | Pipeline STAGE ejecuta en K8s stage |
| 5 | 15% | Pipeline MASTER ejecuta, Release Notes generadas, git tag creado |
| 6 | 15% | 4 docs en docs/, video ≤8 min, ZIP entregado |

---

## COMANDOS ÚTILES DE DEBUG

```powershell
# Ver logs de un run de GitHub Actions (requiere gh CLI)
gh run list --limit 10
gh run view --log

# Ver pods con más detalle
kubectl describe pods -n circleguard-dev

# Entrar a un pod
kubectl exec -it -n circleguard-dev deployment/auth-service -- sh

# Ver eventos del namespace
kubectl get events -n circleguard-dev --sort-by='.lastTimestamp'

# Reiniciar un deployment
kubectl rollout restart deployment/auth-service -n circleguard-dev

# Ver uso de recursos
kubectl top pods -n circleguard-dev

# Port-forward para probar localmente
kubectl port-forward svc/auth-service 8180:8180 -n circleguard-dev
```

---

## NOTAS IMPORTANTES

1. **El temp directory** (`circ le-guard-test`) ya no se necesita. Puedes borrarlo después de verificar que todo está en el proyecto real.

2. **Si Minikube no inicia**, verifica que Hyper-V o VirtualBox estén habilitados:
```powershell
# En Windows con Hyper-V
minikube start --driver=hyperv --cpus=4 --memory=8g

# En Windows con VirtualBox
minikube start --driver=virtualbox --cpus=4 --memory=8g
```

3. **Si los tests de integración fallan** porque los servicios no están corriendo, es esperado. El objetivo es que compilen y la estructura esté correcta.

4. **Para el video**, puedes usar runs anteriores de GitHub Actions como evidencia en lugar de ejecutar todo en vivo (si el tiempo es corto).

---

**Generado:** 2026-05-09
**Versión:** 1.0
**Para:** Circle Guard Taller 2 - CI/CD con GitHub Actions, Docker y Kubernetes