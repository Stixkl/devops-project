# Bonus — Chaos Engineering (Chaos Mesh)

**Chaos Mesh 2.x** instalado vía Helm en el cluster kind, con 4 experimentos
diseñados, ejecutados y documentados contra un entorno objetivo real: el
`dashboard-service` (Java + resilience4j) con PostgreSQL y un stub de
`promotion-service`. Manifests en `chaos/`.

El resultado más valioso: **el experimento 1 falsó la hipótesis** y destapó un
bug real en el fallback de caché, que se corrigió e integró a la arquitectura
(ver §Aprendizajes).

## Método

Cada experimento declara en su YAML: **hipótesis → blast radius → ejecución →
verificación**. Se ejecutan de a uno, con observación vía métricas Prometheus
del propio servicio (`/actuator/prometheus`) y curl desde un pod cliente.

## Experimento 1 — Pod kill de la dependencia (`PodChaos`)

**Hipótesis**: al morir promotion-service, el circuit breaker
`promotionService` abre y el fallback sirve el último dato bueno cacheado
(Caffeine) con `cached: true`.

**Resultado 1ª ejecución — hipótesis FALSADA**:

```
req 1: {"cached":false,"error":"Service unavailable"}
...
req 7: {"activeCount":5000,...,"source":"promotion-stub"}   <- pod volvió, cache repoblada
req 9: {"cached":false,"error":"Service unavailable"}        <- ¡breaker abierto pero SIN caché!
```

El fallback degradaba bien (nunca 5xx al usuario) pero **jamás devolvía datos
cacheados**, incluso tras llamadas exitosas previas.

**Causa raíz**: resilience4j invoca el método fallback sobre el **proxy CGLIB**,
cuyos campos de instancia no se inicializan (Spring usa objenesis: no corre
constructores ni inicializadores). La caché Caffeine era un campo de instancia
⇒ `null` dentro del fallback. El guard `if (lastSuccessCache != null)` que
había en el código enmascaraba el síntoma (evitaba el NPE) sin curar la causa.

**Fix integrado** (`PromotionClient.java`): la caché pasó a campo `static
final` (nivel de clase, compartido por proxy y target). Re-ejecución:

```
req 1: {"probableCount":15,"activeCount":5000,"cached":true,
        "cached_at":"2026-06-10T19:18:54Z","source":"promotion-stub",...}
```

Ciclo del breaker capturado en métricas durante el experimento:

```
resilience4j_circuitbreaker_calls_seconds_count{kind="failed"}      6.0
resilience4j_circuitbreaker_calls_seconds_count{kind="successful"}  4.0
resilience4j_circuitbreaker_not_permitted_calls_total               4.0   <- cortocircuitos en OPEN
resilience4j_circuitbreaker_state{state="half_open"}                1.0   <- recuperándose
```

## Experimento 2 — Latencia de red 5s en la dependencia (`NetworkChaos`)

**Hipótesis**: con delay 5s (≥ read-timeout), el breaker abre por
slow-calls/timeouts y la latencia NO se propaga al usuario.

**Resultado — confirmada**: bajo el delay, el dashboard respondió en **0 ms**
con `cached: true` (fallback instantáneo); el breaker cortocircuitó las
llamadas lentas (`not_permitted_calls_total = 6`) y pasó a `half_open` al
expirar el caos (duration 2m, auto-limpieza).

## Experimento 3 — Fallo del propio servicio (`PodChaos pod-failure`, 60s)

**Hipótesis**: liveness/readiness probes detectan el fallo, Kubernetes
reinicia el contenedor y lo saca del Service mientras tanto.

**Resultado — confirmada** (observación cada 15s):

```
t=15s  dashboard 0/1  RESTARTS=0   endpoints: <vacío>   <- readiness lo sacó
t=45s  dashboard 0/1  RESTARTS=1   endpoints: <vacío>   <- k8s reinició
t=75s  dashboard 1/1  RESTARTS=1   endpoints: 10.244.0.29  <- recuperado
```

Auto-recuperación total en ~75s sin intervención humana.

## Experimento 4 — Presión de memoria (`StressChaos`, 256MB, 2m)

**Hipótesis**: el límite del Deployment (768Mi) contiene la presión; el
servicio sigue respondiendo y no hay OOMKill.

**Resultado — confirmada**: memoria del contenedor 483 MiB / 768 Mi durante el
estrés, `/actuator/health` → HTTP 200 en 36 ms, RESTARTS sin incremento. La
métrica `jvm_memory_used_bytes` queda disponible para la alerta
`HighJVMMemory` (Prometheus rules).

## Aprendizajes integrados a la arquitectura

1. **Bug real corregido**: caché de fallback inservible por campos de instancia
   en proxies CGLIB → campo `static` + comentario explicando la restricción
   (`PromotionClient.java`). Sin el experimento, este bug habría llegado a
   producción "pasando los tests" (los unit tests solo cubrían el caso
   sin caché).
2. **Guards defensivos pueden ocultar bugs**: el `!= null` del fallback
   convertía un defecto funcional en degradación silenciosa. Ahora está
   documentado el porqué del static.
3. **Límites de recursos validados**: requests/limits del Deployment de
   chaos (`chaos/00-target-env.yaml`) demostraron contener presión de memoria
   sin afectar disponibilidad — patrón replicado en los deployments de
   `k8s/{dev,stage,master}`.
4. **Las probes correctas importan**: la recuperación de 75s del exp. 3
   depende de los thresholds de liveness (3×10s); valores mayores alargarían
   el downtime sin beneficio.
