# Chaos Engineering — CircleGuard

Experimentos de caos con **Chaos Mesh** sobre un entorno objetivo real
(dashboard-service con su circuit breaker resilience4j + PostgreSQL + stub de
promotion-service). Resultados y aprendizajes en
`docs/BONUS_CHAOS_ENGINEERING.md`.

## Estructura

- `00-target-env.yaml` — namespace `circleguard-chaos` con el entorno objetivo.
- `experiments/01-pod-kill-promotion.yaml` — muerte de la dependencia.
- `experiments/02-network-delay-promotion.yaml` — latencia de red 5s.
- `experiments/03-pod-failure-dashboard.yaml` — fallo del propio servicio.
- `experiments/04-stress-memory-dashboard.yaml` — presión de memoria.

## Ejecución

```bash
# 1. Chaos Mesh en el cluster (kind usa containerd)
helm repo add chaos-mesh https://charts.chaos-mesh.org
helm install chaos-mesh chaos-mesh/chaos-mesh -n chaos-mesh --create-namespace \
  --set chaosDaemon.runtime=containerd \
  --set chaosDaemon.socketPath=/run/containerd/containerd.sock

# 2. Imagen del dashboard y entorno objetivo
./gradlew :services:circleguard-dashboard-service:bootJar
docker build -t circleguard/dashboard-service:chaos <dir-con-jar>
kind load docker-image circleguard/dashboard-service:chaos --name <cluster>
kubectl apply -f chaos/00-target-env.yaml

# 3. Un experimento a la vez (hipótesis en el YAML)
kubectl apply -f chaos/experiments/01-pod-kill-promotion.yaml
# ... observar ... y limpiar:
kubectl delete -f chaos/experiments/01-pod-kill-promotion.yaml
```

Dashboard de Chaos Mesh: `kubectl port-forward -n chaos-mesh svc/chaos-dashboard 2333:2333`.
