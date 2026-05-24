#!/bin/bash
# ================================================
# Build All Docker Images
# Circle Guard - Para Minikube
# ================================================

set -e

echo "=============================================="
echo "  Building Circle Guard Docker Images"
echo "=============================================="
echo ""

# Configurar Docker para Minikube
echo "[1/6] Configurando Docker en Minikube..."
eval $(minikube -p minikube docker-env)
echo "     [OK] Docker configurado"
echo ""

SERVICES=(
    "auth-service"
    "identity-service"
    "gateway-service"
    "form-service"
    "notification-service"
    "promotion-service"
)

echo "[2/6] Construyendo imagen base (Gradle + JDK)..."
# Verificar que Gradle esta disponible
if ! command -v gradle &> /dev/null; then
    echo "     [INFO] Gradle no encontrado, descargando wrapper..."
fi
echo ""

echo "[3/6] Building services..."
for service in "${SERVICES[@]}"; do
    echo "     Building $service..."
    docker build -f docker/Dockerfile.$service -t circleguard/$service:latest .
    minikube image load circleguard/$service:latest
    echo "     [OK] $service built and loaded"
done

echo ""
echo "[4/6] Loading images to Minikube..."
for service in "${SERVICES[@]}"; do
    echo "     Loading circleguard/$service:latest..."
    minikube image load circleguard/$service:latest
done

echo ""
echo "[5/6] Verificando imagenes..."
docker images | grep circleguard

echo ""
echo "[6/6] Generando reporte..."
echo ""
echo "=============================================="
echo "  BUILD COMPLETO"
echo "=============================================="
echo "Imagenes disponibles:"
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" | grep circleguard
echo ""
echo "Para usar estas imagenes en Kubernetes, estan"
echo "disponibles directamente en el cluster Minikube."
echo ""
echo "=============================================="