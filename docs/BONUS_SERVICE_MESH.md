# Bonus — Service Mesh (Linkerd)

Service mesh **Linkerd** (edge-26.5.5) desplegado y verificado en vivo sobre un
cluster kind (`circleguard-mesh`), cubriendo los 5 requisitos del bonus:
mTLS, traffic shifting canary, visualización, circuit breakers y retry policies.
Manifests en `k8s/mesh/`; los namespaces de la aplicación
(`k8s/namespaces/*.yaml`) ya están anotados con `linkerd.io/inject: enabled`
para enrolar los 8 microservicios al desplegarse.

## Arquitectura

```
┌────────────────────── kind: circleguard-mesh ──────────────────────┐
│  linkerd (control plane)      linkerd-viz (prometheus, web, tap)   │
│                                                                    │
│  ns circleguard-mesh (linkerd.io/inject: enabled)                  │
│   ┌────────┐   HTTPRoute backend-canary (Gateway API)              │
│   │ client │ ──► 90% ──► backend-v1 (+ linkerd-proxy sidecar)      │
│   │ (curl) │ ──► 10% ──► backend-v2 (+ linkerd-proxy sidecar)      │
│   └────────┘                                                       │
│        Service backend: failure-accrual (circuit breaker)          │
│        ServiceProfile: retries con retryBudget                     │
└────────────────────────────────────────────────────────────────────┘
```

Cada pod lleva el sidecar `linkerd-proxy` (READY 2/2) inyectado
automáticamente por el `proxy-injector` gracias a la anotación del namespace.

## Instalación (reproducible)

Ver `k8s/mesh/README.md`. Resumen:

```bash
kind create cluster --name circleguard-mesh
kubectl apply -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.2.1/standard-install.yaml
linkerd install --crds | kubectl apply -f -
linkerd install | kubectl apply -f -
linkerd viz install | kubectl apply -f -
kubectl apply -f k8s/mesh/
```

## Evidencia (capturada en vivo)

### 1. mTLS entre todos los servicios

`linkerd viz edges deployment -n circleguard-mesh` — toda arista SECURED:

```
SRC          DST          SRC_NS             DST_NS             SECURED
client       backend-v1   circleguard-mesh   circleguard-mesh   √
client       backend-v2   circleguard-mesh   circleguard-mesh   √
prometheus   backend-v1   linkerd-viz        circleguard-mesh   √
prometheus   backend-v2   linkerd-viz        circleguard-mesh   √
prometheus   client       linkerd-viz        circleguard-mesh   √
```

### 2. Traffic shifting para canary (Gateway API HTTPRoute)

Pesos declarados en `k8s/mesh/20-canary-httproute.yaml`: `backend-v1=90,
backend-v2=10`. Se cambió en caliente a 50/50 con `kubectl patch` y el tráfico
real siguió los pesos (ventana de 1 min, tráfico generado por `client`):

```
# pesos 90/10                          # pesos 50/50 (tras patch)
NAME         SUCCESS      RPS          NAME         SUCCESS      RPS
backend-v1   100.00%   0.9rps         backend-v1   100.00%   0.4rps
backend-v2   100.00%   0.1rps         backend-v2   100.00%   0.6rps
```

Esto es el mecanismo de despliegue canary: subir peso de v2 gradualmente
mientras se observa su tasa de éxito en linkerd-viz.

### 3. Retry policies (ServiceProfile)

`k8s/mesh/30-serviceprofile-retries.yaml` define la ruta `GET /` con
`isRetryable: true` y presupuesto de reintentos (`retryBudget: ratio 0.2,
minRetriesPerSecond 10`). Verificación con éxito efectivo vs real:

```
linkerd viz routes deploy/client --to svc/backend -n circleguard-mesh -o wide
ROUTE     SERVICE   EFFECTIVE_SUCCESS   EFFECTIVE_RPS   ACTUAL_SUCCESS   ACTUAL_RPS
GET /     backend             100.00%          1.0rps          100.00%       1.0rps
```

(EFFECTIVE = lo que ve el cliente tras retries; ACTUAL = lo que respondió el
backend. Ante fallos transitorios EFFECTIVE > ACTUAL.)

### 4. Circuit breaker (failure accrual)

Anotaciones activas en el Service apex `backend`
(`k8s/mesh/10-backend.yaml`), capturadas del cluster:

```
balancer.linkerd.io/failure-accrual: consecutive
balancer.linkerd.io/failure-accrual-consecutive-max-failures: "5"
balancer.linkerd.io/failure-accrual-consecutive-min-penalty: 1s
balancer.linkerd.io/failure-accrual-consecutive-max-penalty: 60s
```

Tras 5 fallos consecutivos de un endpoint, Linkerd lo expulsa del balanceo
(backoff exponencial 1s→60s) y lo reintroduce con requests de prueba
(half-open). Complementa los circuit breakers de aplicación (resilience4j)
operando en la capa de red, sin tocar código.

### 5. Visualización del mesh

`linkerd viz dashboard` → http://localhost:50750 (topología, tasas de éxito,
latencias p50/p95/p99 en vivo, tap de requests). El Prometheus de linkerd-viz
scrapea todos los sidecars; estado del plano: `linkerd check` OK.

## Integración con CircleGuard

- `k8s/namespaces/namespace-{dev,stage,master}.yaml`: `linkerd.io/inject:
  enabled` — al aplicar los deployments de `k8s/{dev,stage,master}/` en un
  cluster con Linkerd, los 8 servicios quedan en el mesh con mTLS sin cambios
  de código.
- El patrón canary del HTTPRoute es directamente aplicable a los servicios
  (ej. `promotion-service` v1/v2 en un release).
