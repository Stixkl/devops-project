#!/bin/bash
# ================================================
# Deploy All Services to Minikube
# Circle Guard - Dev Environment
# ================================================

set -e

NAMESPACE="circleguard-dev"

echo "=============================================="
echo "  Deploying Circle Guard to Minikube"
echo "  Namespace: $NAMESPACE"
echo "=============================================="
echo ""

# Verificar que Minikube esta corriendo
echo "[1/7] Verificando Minikube..."
if ! minikube status | grep -q "Running"; then
    echo "     [ERROR] Minikube no esta corriendo"
    echo "     Ejecuta: minikube start --driver=docker"
    exit 1
fi
echo "     [OK] Minikube esta corriendo"
echo ""

# Configurar Docker
echo "[2/7] Configurando Docker..."
eval $(minikube -p minikube docker-env)
echo ""

# Crear namespace
echo "[3/7] Creando namespace $NAMESPACE..."
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -
echo "     [OK] Namespace creado"
echo ""

# Deploy ConfigMaps y Secrets
echo "[4/8] Desplegando ConfigMaps y Secrets..."
for f in k8s/dev/configmap-*.yaml; do
    kubectl apply -f "$f" -n $NAMESPACE
done
kubectl apply -f k8s/dev/secrets.yaml -n $NAMESPACE
echo "     [OK] ConfigMaps y Secrets desplegados"
echo ""

# Deploy Infraestructura
echo "[5/8] Desplegando infraestructura..."
kubectl apply -f k8s/dev/postgres.yaml -n $NAMESPACE
kubectl apply -f k8s/dev/redis.yaml -n $NAMESPACE
kubectl apply -f k8s/dev/zookeeper.yaml -n $NAMESPACE
kubectl apply -f k8s/dev/kafka.yaml -n $NAMESPACE
kubectl apply -f k8s/dev/neo4j.yaml -n $NAMESPACE
echo "     [OK] Infraestructura desplegada"
echo ""

# Esperar que la infraestructura este lista
echo "     Esperando que la infraestructura este lista (2 min)..."
sleep 120

# Deploy Microservicios
echo "[6/8] Desplegando microservicios..."
for service in auth identity gateway form notification promotion dashboard file; do
    echo "     Deploying ${service}-service..."
    kubectl apply -f "k8s/dev/deployment-${service}-service.yaml" -n $NAMESPACE
    kubectl apply -f "k8s/dev/service-${service}-service.yaml" -n $NAMESPACE
done
echo "     [OK] Microservicios desplegados"
echo ""

# Esperar que los pods esten listos
echo "[7/8] Esperando que los pods esten listos (3 min)..."
sleep 180

# Verificar estado
echo "[8/8] Verificando estado..."
echo ""
kubectl get pods -n $NAMESPACE

echo ""
echo "=============================================="
echo "  DEPLOY COMPLETO"
echo "=============================================="
echo ""
echo "Comandos utiles:"
echo "  Ver pods:     kubectl get pods -n $NAMESPACE"
echo "  Ver servicios: kubectl get services -n $NAMESPACE"
echo "  Ver logs:     kubectl logs -n $NAMESPACE deployment/auth-service -f"
echo "  Dashboard:    minikube dashboard"
echo "  Terminal:     kubectl exec -it -n $NAMESPACE deployment/auth-service -- /bin/sh"
echo ""
echo "IP del cluster: $(minikube ip)"
echo ""
echo "=============================================="