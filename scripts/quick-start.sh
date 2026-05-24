#!/bin/bash
# ================================================
# Quick Start Script - Circle Guard CI/CD
# Sin Docker Desktop - Con Minikube
# ================================================

set -e

echo ""
echo "##############################################"
echo "#   Circle Guard - Setup Automatizado         #"
echo "#   Taller CI/CD sin Docker Desktop           #"
echo "##############################################"
echo ""

# Verificar permisos de administrador
if [ "$EUID" -ne 0 ]; then
    echo "[ERROR] Ejecuta este script como root (sudo)"
    exit 1
fi

echo "[1/7] Verificando Chocolatey..."
if ! command -v choco &> /dev/null; then
    echo "     Chocolatey no encontrado. Instalando..."
    powershell -Command "Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; irm https://chocolatey.org/install.ps1 | iex"
else
    echo "     [OK] Chocolatey ya instalado"
fi

echo ""
echo "[2/7] Instalando herramientas base..."
choco install minikube kubernetes-cli gradle docker-cli microsoft-openjdk21 -y --force

echo ""
echo "[3/7] Verificando Java..."
export PATH="/c/Program Files/Microsoft/jdk-21.0.1/bin:$PATH"
java -version

echo ""
echo "[4/7] Verificando Minikube..."
minikube version

echo ""
echo "[5/7] Iniciando Minikube (esto puede tomar 3-5 minutos)..."
minikube config set memory 4096
minikube config set cpus 4
minikube start --driver=docker

echo ""
echo "[6/7] Habilitando addons de Kubernetes..."
minikube addons enable ingress
minikube addons enable storage-provisioner
minikube addons enable default-storageclass

echo ""
echo "[7/7] Configurando Docker en Minikube..."
eval $(minikube -p minikube docker-env)

echo ""
echo "=============================================="
echo "             INSTALACION COMPLETA"
echo "=============================================="
echo ""
echo "Docker esta configurado para usar Minikube."
echo "Ahora puedes hacer:"
echo ""
echo "  docker build -t mi-imagen ."
echo ""
echo "Para ver el dashboard de Kubernetes:"
echo "  minikube dashboard"
echo ""
echo "=============================================="
echo ""