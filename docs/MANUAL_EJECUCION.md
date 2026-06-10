# MANUAL DE EJECUCIÓN - Taller 2 CI/CD (Circle Guard)

> Este documento contiene los pasos que DEBES ejecutar manualmente. OpenCode ya preparó todos los artefactos.

---

## VERIFICACIÓN INICIAL

Antes de comenzar, verifica que todo está en su lugar:

```powershell
# Verificar estructura
Get-ChildItem C:\Users\juanc\Videos\devops-project\docker
Get-ChildItem C:\Users\juanc\Videos\devops-project\jenkins
Get-ChildItem C:\Users\juanc\Videos\devops-project\k8s
Get-ChildItem C:\Users\juanc\Videos\devops-project\tests
Get-ChildItem C:\Users\juanc\Videos\devops-project\scripts
Get-ChildItem C:\Users\juanc\Videos\devops-project\docs
```

Debes ver:
- `docker/`: 6 Dockerfiles + docker-compose.yml
- `jenkins/`: 3 Jenkinsfiles
- `k8s/`: namespaces, dev, stage, master
- `tests/`: integration-tests, e2e, performance
- `scripts/`: 8 scripts
- `docs/`: 4 docs nuevos (TALLER2_README, PIPELINES, TESTING_STRATEGY, VIDEO_SCRIPT)

---

## B1. INSTALAR JENKINS + DOCKER + KUBERNETES

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

### 3. Instalar Jenkins en contenedor

```powershell
docker run -d --name jenkins `
  -p 8080:8080 -p 50000:50000 `
  -v jenkins_home:/var/jenkins_home `
  -v //var/run/docker.sock:/var/run/docker.sock `
  jenkins/jenkins:lts

# Esperar a que esté listo
docker logs jenkins
# Busca el password inicial de administrador

# Acceder a http://localhost:8080
```

---

## B2. CONFIGURAR JENKINS

### 1. Acceder a Jenkins
Abre http://localhost:8080 y completa el setup inicial con el password del contenedor.

### 2. Instalar plugins
Ve a **Manage Jenkins → Manage Plugins → Available**:
- Docker Pipeline
- Kubernetes CLI
- JUnit
- HTML Publisher
- Pipeline: Stage View
- Git

### 3. Agregar Credentials
Ve a **Manage Jenkins → Manage Credentials → Add Credentials**:

| ID | Tipo | Valor |
|----|------|-------|
| `dockerhub-credentials` | Username/Password | Tu usuario y password de Docker Hub |
| `kubeconfig-dev` | Secret File | Tu kubeconfig del cluster dev |
| `kubeconfig-stage` | Secret File | Tu kubeconfig del cluster stage |
| `kubeconfig-master` | Secret File | Tu kubeconfig del cluster master |
| `DOCKER_USERNAME` | Secret Text | Tu usuario de Docker Hub |
| `DOCKER_PASSWORD` | Secret Text | Tu password de Docker Hub |

### 4. Crear Jobs Multibranch Pipeline

Para **cada servicio**, crea un nuevo item:

1. **New Item** → nombre: `circleguard-auth-service-dev`
2. Tipo: **Multibranch Pipeline**
3. Branch Sources → **Add source** → Git:
    - Repository URL: `https://github.com/JuanAmor8/devops-project.git`
    - Credentials: (tu token GitHub o anonymous)
4. Build Configuration → Mode: **by Jenkinsfile**
   - Script Path: `jenkins/Jenkinsfile-dev`
5. **Add Property**: `SERVICE_NAME` = `auth`
6. Repetir para los otros 5 servicios:
   - identity → `SERVICE_NAME` = `identity`
   - form → `SERVICE_NAME` = `form`
   - gateway → `SERVICE_NAME` = `gateway`
   - notification → `SERVICE_NAME` = `notification`
   - promotion → `SERVICE_NAME` = `promotion`

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

```powershell
cd C:\Users\Administrator\Videos\devops-project

# 1. Crear namespaces
kubectl apply -f k8s/namespaces/

# 2. Verificar namespaces
kubectl get namespaces

# 3. Desplegar a DEV
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
# 1. Jenkins - Jobs Multibranch Pipeline
# 2. Jenkins - Pipeline DEV ejecutándose (console output)
# 3. Jenkins - Pipeline STAGE ejecutándose
# 4. Jenkins - Pipeline MASTER ejecutándose
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
- 0:30-1:30 → Setup Jenkins/Docker/K8s
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
Compress-Archive -Path docker,jenkins,k8s,tests,scripts,docs -DestinationPath circleguard-taller2-entrega.zip -Force

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
| 1 | 10% | Jenkins instalado, Docker funcionando, K8s corriendo |
| 2 | 15% | 6 jobs Multibranch Pipeline creados en Jenkins |
| 3 | 30% | Unit tests pasan, 8 integration tests, 23 E2E tests, Locust report |
| 4 | 15% | Pipeline STAGE ejecuta en K8s stage |
| 5 | 15% | Pipeline MASTER ejecuta, Release Notes generadas, git tag creado |
| 6 | 15% | 4 docs en docs/, video ≤8 min, ZIP entregado |

---

## COMANDOS ÚTILES DE DEBUG

```powershell
# Ver logs de Jenkins
docker logs jenkins -f

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

4. **Para el video**, puedes usar builds anteriores de Jenkins como evidencia en lugar de ejecutar todo en vivo (si el tiempo es corto).

---

**Generado:** 2026-05-09
**Versión:** 1.0
**Para:** Circle Guard Taller 2 - CI/CD con Jenkins, Docker y Kubernetes