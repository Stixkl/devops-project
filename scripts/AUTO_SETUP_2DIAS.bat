@echo off
REM ================================================
REM AUTO SETUP - Circle Guard CI/CD (2 Días)
REM Solo lo esencial para completar el taller
REM ================================================

color 0A
cd /d "%~dp0"

echo.
echo ################################################################
echo #         CIRCLE GUARD - AUTO SETUP (2 DÍAS)                  #
echo ################################################################
echo.

REM ==========================================
REM VERIFICAR EJECUCION COMO ADMINISTRADOR
REM ==========================================
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Este script debe ejecutarse como ADMINISTRADOR
    echo.
    echo Haz clic derecho en este archivo y selecciona:
    echo "Ejecutar como administrador"
    echo.
    pause
    exit /b 1
)

echo [OK] Ejecutando como Administrador
echo.

REM ==========================================
REM PASO 1: INSTALAR CHOCO SI NO EXISTE
REM ==========================================
echo =============================================
echo PASO 1: Verificando Chocolatey...
echo =============================================

where choco >nul 2>&1
if %errorlevel% neq 0 (
    echo     Chocolatey no encontrado. Instalando...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "iex ((New-Object System.Net.WebClient).DownloadString('https://chocolatey.org/install.ps1'))"
    echo     [OK] Chocolatey instalado
) else (
    echo     [OK] Chocolatey ya instalado
)
echo.

REM ==========================================
REM PASO 2: INSTALAR TODAS LAS HERRAMIENTAS
REM ==========================================
echo =============================================
echo PASO 2: Instalando herramientas...
echo =============================================

echo     - Minikube...
choco install minikube -y --force >nul 2>&1

echo     - Kubernetes CLI...
choco install kubernetes-cli -y --force >nul 2>&1

echo     - Java JDK 21...
choco install microsoft-openjdk21 -y --force >nul 2>&1

echo     - Gradle...
choco install gradle -y --force >nul 2>&1

echo     - Git...
choco install git -y --force >nul 2>&1

echo     - Docker CLI...
choco install docker-cli -y --force >nul 2>&1

echo.
echo     Refrescando entorno...
refreshenv >nul 2>&1
echo.

REM ==========================================
REM PASO 3: CONFIGURAR JAVA Y GRADLE
REM ==========================================
echo =============================================
echo PASO 3: Verificando Java y Gradle...
echo =============================================

setx JAVA_HOME "C:\Program Files\Microsoft\jdk-21.0.1" >nul 2>&1
setx PATH "%PATH%;C:\Program Files\Microsoft\jdk-21.0.1\bin" >nul 2>&1

java -version 2>&1 | findstr "version"
echo.

REM ==========================================
REM PASO 4: INICIAR MINIKUBE
REM ==========================================
echo =============================================
echo PASO 4: Iniciando Minikube...
echo =============================================
echo.

echo     Configurando recursos...
call minikube config set memory 4096 >nul 2>&1
call minikube config set cpus 4 >nul 2>&1

echo     Iniciando cluster (esto toma 3-5 minutos)...
call minikube start --driver=docker

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Minikube no pudo iniciar
    echo.
    echo Posibles soluciones:
    echo 1. Verifica que Hyper-V esta habilitado
    echo 2. Reinicia el equipo e intenta de nuevo
    echo 3. Ejecuta: minikube delete ^&^& minikube start --driver=docker
    echo.
    pause
    exit /b 1
)

echo.
echo     [OK] Minikube iniciado
echo.

REM ==========================================
REM PASO 5: CONFIGURAR DOCKER EN MINIKUBE
REM ==========================================
echo =============================================
echo PASO 5: Configurando Docker en Minikube...
echo =============================================

call minikube docker-env > set_env.bat
call set_env.bat
del set_env.bat

docker ps >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARN] Docker puede no estar accesible
    echo        Ejecuta en cada terminal: minikube docker-env ^|^| Invoke-Expression
) else (
    echo     [OK] Docker configurado
)
echo.

REM ==========================================
REM PASO 6: HABILITAR ADDONS K8S
REM ==========================================
echo =============================================
echo PASO 6: Habilitando addons de Kubernetes...
echo =============================================

call minikube addons enable ingress >nul 2>&1
call minikube addons enable storage-provisioner >nul 2>&1
call minikube addons enable default-storageclass >nul 2>&1

echo     [OK] Addons habilitados
echo.

REM ==========================================
REM PASO 7: VERIFICAR TODO
REM ==========================================
echo =============================================
echo PASO 7: Verificacion final...
echo =============================================

echo.
echo     Minikube status:
call minikube status

echo.
echo     Kubernetes nodes:
kubectl get nodes

echo.
echo     Cluster info:
call minikube ip

REM ==========================================
REM PASO 8: CREAR NAMESPACES
REM ==========================================
echo.
echo =============================================
echo PASO 8: Creando namespaces K8s...
echo =============================================

if exist "k8s\namespaces" (
    kubectl apply -f k8s\namespaces\namespace-dev.yaml
    kubectl apply -f k8s\namespaces\namespace-stage.yaml
    kubectl apply -f k8s\namespaces\namespace-master.yaml
    echo     [OK] Namespaces creados
) else (
    echo     [WARN] k8s\namespaces no encontrado
    echo            Ejecuta desde la carpeta del proyecto
)
echo.

REM ==========================================
REM FIN
REM ==========================================
echo.
echo ################################################################
echo #              INSTALACION COMPLETA!
echo ################################################################
echo.
echo LO SIGUIENTE:
echo.
echo 1. CI/CD en GitHub Actions (.github/workflows): ci.yml, cd-dev.yml, cd-stage.yml
echo    Configura Environments (dev/stage/production) y Secrets en GitHub.
echo.
echo 2. VER EL DASHBOARD K8S:
echo    minikube dashboard
echo.
echo 3. VER ESTADO:
echo    kubectl get pods -n circleguard-dev
echo.
echo 4. PARA CONSTRUIR DOCKER:
echo    minikube docker-env ^|^| Invoke-Expression
echo    docker build -t circleguard/auth-service:latest .
echo.
echo ################################################################
echo.

pause