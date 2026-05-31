# ================================================
# Check Status Script
# Circle Guard - Verificar estado del sistema
# ================================================

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "  Circle Guard - Status del Sistema" -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host ""

# Minikube Status
Write-Host "[1/5] Estado de Minikube..." -ForegroundColor Yellow
$minikubeStatus = minikube status 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host $minikubeStatus -ForegroundColor Green
} else {
    Write-Host "     [ERROR] Minikube no esta corriendo" -ForegroundColor Red
    Write-Host "     Ejecuta: minikube start --driver=docker" -ForegroundColor Yellow
}
Write-Host ""

# Docker Status
Write-Host "[2/5] Estado de Docker..." -ForegroundColor Yellow
$env:DOCKER_TLS_VERIFY = ""
$env:DOCKER_HOST = ""
minikube docker-env | Invoke-Expression | Out-Null
$dockerStatus = docker ps 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "     [OK] Docker esta funcionando" -ForegroundColor Green
    Write-Host "     Contenedores activos: $((docker ps -q | Measure-Object).Count)" -ForegroundColor Green
} else {
    Write-Host "     [WARN] Docker puede no estar configurado" -ForegroundColor Yellow
    Write-Host "     Ejecuta: minikube docker-env | Invoke-Expression" -ForegroundColor Yellow
}
Write-Host ""

# Kubernetes Status
Write-Host "[3/5] Estado de Kubernetes..." -ForegroundColor Yellow
$k8sStatus = kubectl get nodes 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host $k8sStatus -ForegroundColor Green
} else {
    Write-Host "     [ERROR] Kubernetes no esta accesible" -ForegroundColor Red
}
Write-Host ""

# Pods en Namespace dev
Write-Host "[4/5] Pods en circleguard-dev..." -ForegroundColor Yellow
$pods = kubectl get pods -n circleguard-dev 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host $pods -ForegroundColor Green
} else {
    Write-Host "     [INFO] Namespace aun no tiene pods desplegados" -ForegroundColor Yellow
}
Write-Host ""

# Servicios
Write-Host "[5/5] Servicios en circleguard-dev..." -ForegroundColor Yellow
$services = kubectl get services -n circleguard-dev 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host $services -ForegroundColor Green
} else {
    Write-Host "     [INFO] No hay servicios desplegados aun" -ForegroundColor Yellow
}
Write-Host ""

# Resumen
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host "  Comandos Rapidos" -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  minikube start                    - Iniciar cluster" -ForegroundColor White
Write-Host "  minikube dashboard                - Abrir dashboard K8s" -ForegroundColor White
Write-Host "  kubectl get pods -n circleguard-dev - Ver pods" -ForegroundColor White
Write-Host "  minikube ip                      - Ver IP del cluster" -ForegroundColor White
Write-Host "  kubectl logs -f <pod> -n circleguard-dev - Ver logs" -ForegroundColor White
Write-Host ""
Write-Host "==============================================" -ForegroundColor Cyan
Write-Host ""