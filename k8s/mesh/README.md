# Service Mesh — Linkerd (CircleGuard)

Service mesh para CircleGuard implementado con **Linkerd**. Cubre las cuatro
capacidades requeridas del bonus, instaladas y verificadas en vivo sobre un
cluster local (`kind`):

1. **mTLS** automático entre servicios.
2. **Traffic shifting / canary** por pesos.
3. **Circuit breaker + retries**.
4. **Visualización del mesh** (Linkerd Viz).

## Arquitectura de la demo

Namespace `circleguard-mesh` anotado con `linkerd.io/inject: enabled`, de modo
que el *proxy-injector* añade un sidecar `linkerd2-proxy` a cada pod. Eso es lo
que habilita mTLS, retries y métricas sin tocar el código de la aplicación.

| Componente   | Rol |
|--------------|-----|
| `backend-v1` | Versión estable del backend (`http-echo`). |
| `backend-v2` | Versión canary del backend. |
| `backend`    | Service apex que el cliente invoca; reparte vía HTTPRoute. |
| `client`     | Generador de tráfico (curl en bucle) para alimentar el mesh. |

Los mismos patrones (anotación de inyección, HTTPRoute, ServiceProfile,
`failure-accrual`) se aplican tal cual a los Deployments reales de
`k8s/dev`, `k8s/stage` y `k8s/master`.

## Archivos

| Archivo | Capacidad |
|---------|-----------|
| `00-namespace.yaml`              | Inyección del proxy (base de mTLS). |
| `10-backend.yaml`                | v1/v2 + apex Service + **circuit breaker** (`failure-accrual`). |
| `20-canary-httproute.yaml`       | **Traffic shifting / canary** 90/10 vía Gateway API HTTPRoute. |
| `30-serviceprofile-retries.yaml` | **Retries** + retry budget vía ServiceProfile. |
| `40-client.yaml`                 | Generador de tráfico. |

## Instalación (reproducible)

```bash
# 1. Cluster local
kind create cluster --name circleguard-mesh

# 2. Gateway API CRDs (requisito de Linkerd para traffic shifting)
kubectl apply --server-side -f \
  https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.2.1/standard-install.yaml

# 3. Control plane de Linkerd
linkerd install --crds | kubectl apply -f -
linkerd install        | kubectl apply -f -
linkerd check

# 4. Linkerd Viz (dashboard + métricas)
linkerd viz install | kubectl apply -f -

# 5. Workloads de la demo (ya inyectados por el namespace)
kubectl apply -f k8s/mesh/
```

## Verificación de cada capacidad

### 1. mTLS automático
```bash
linkerd viz edges deployment -n circleguard-mesh
```
La columna `SECURED` muestra `√` en todos los edges (p. ej. `client → backend-v1`),
confirmando que el tráfico entre pods va cifrado con mTLS.

### 2. Traffic shifting / canary
```bash
linkerd viz stat deployment -n circleguard-mesh
```
Con pesos 90/10, `backend-v1` recibe la mayor parte del RPS y `backend-v2` una
fracción. Cambiar los pesos del HTTPRoute desplaza el tráfico en caliente, sin
reiniciar nada ni tocar al cliente:
```bash
# Promueve el canary a 50/50
kubectl patch httproute backend-canary -n circleguard-mesh --type=json \
  -p='[{"op":"replace","path":"/spec/rules/0/backendRefs/0/weight","value":50},
       {"op":"replace","path":"/spec/rules/0/backendRefs/1/weight","value":50}]'
```

### 3. Circuit breaker + retries
- **Retries**: el ServiceProfile marca la ruta `GET /` como `isRetryable` con un
  `retryBudget` que limita los reintentos al 20% del tráfico. Verificación:
  ```bash
  linkerd viz routes deploy/client -n circleguard-mesh --to svc/backend
  ```
- **Circuit breaker**: el Service apex está anotado con
  `balancer.linkerd.io/failure-accrual: consecutive`. Tras N fallos
  consecutivos, el endpoint se saca del balanceo y se reintroduce con backoff.

### 4. Visualización del mesh
```bash
linkerd viz dashboard
```
Abre el dashboard de Linkerd Viz (topología, éxito, RPS, latencias p50/p95/p99,
estado de mTLS por edge).

## Limpieza
```bash
kind delete cluster --name circleguard-mesh
```
