@echo off
REM ================================================
REM Quick Start Script - Circle Guard CI/CD
REM Sin Docker Desktop - Con Minikube
REM ================================================

echo.
echo ##############################################
echo #   Circle Guard - Setup Automatizado         #
echo #   Taller CI/CD sin Docker Desktop           #
echo ##############################################
echo.

REM Verificar si es Admin
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Ejecuta este script como Administrador!
    pause
    exit /b 1
)

echo [1/8] Verificando Chocolatey...
where choco >nul 2>&1
if %errorlevel% neq 0 (
    echo     Chocolatey no encontrado. Instalando...
    powershell -Command "Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; irm https://chocolatey.org/install.ps1 | iex"
) else (
    echo     [OK] Chocolatey ya instalado
)

echo.
echo [2/8] Instalando herramientas base...
choco install minikube kubernetes-cli gradle docker-cli microsoft-openjdk21 -y --force

echo.
echo [3/8] Verificando Java...
refreshenv >nul 2>&1
java -version

echo.
echo [4/8] Verificando Minikube...
minikube version

echo.
echo [5/8] Iniciando Minikube (esto puede tomar 3-5 minutos)...
minikube config set memory 4096
minikube config set cpus 4
minikube start --driver=docker

echo.
echo [6/8] Habilitando addons de Kubernetes...
minikube addons enable ingress
minikube addons enable storage-provisioner
minikube addons enable default-storageclass

echo.
echo [7/8] Configurando Docker en Minikube...
call minikube docker-env > set_env.bat
call set_env.bat
del set_env.bat
echo     [OK] Docker configurado en Minikube

echo.
echo [8/8] Creando namespaces en Kubernetes...
kubectl apply -f k8s\namespaces\namespace-dev.yaml
kubectl apply -f k8s\namespaces\namespace-stage.yaml
kubectl apply -f k8s\namespaces\namespace-master.yaml

echo.
echo ==============================================
echo              INSTALACION COMPLETA
echo ==============================================
echo.
echo IMPORTANTE: Antes de usar Docker, ejecuta:
echo.
echo   minikube docker-env ^> set_env.bat
echo   call set_env.bat
echo.
echo Luego puedes hacer:
echo   docker build -t mi-imagen .
echo.
echo Para ver el dashboard de Kubernetes:
echo   minikube dashboard
echo.
echo ==============================================
echo.

pause