# Observabilidad — CircleGuard

Stack completo de observabilidad para los 8 microservicios, **instalado y
verificado en vivo**. Cubre las tres señales (métricas, trazas, logs) más
dashboards, alertas, health checks y métricas de negocio.

## Componentes

| Pilar    | Herramienta            | Puerto | Rol |
|----------|------------------------|--------|-----|
| Métricas | Prometheus             | 9090   | Scrape de `/actuator/prometheus`, reglas de alerta |
| Métricas | Grafana                | 3000   | Dashboards (admin/admin) |
| Alertas  | Alertmanager           | 9093   | Enrutado/agrupado de alertas |
| Trazas   | Jaeger (all-in-one)    | 16686  | Ingesta OTLP (4317/4318), UI |
| Logs     | Elasticsearch          | 9200   | Almacén de logs (`logstash-*`) |
| Logs     | Logstash               | 5000   | Ingesta TCP `json_lines` desde Logback |
| Logs     | Kibana                 | 5601   | Exploración de logs |

## Instrumentación de los servicios

Aplicada a los 8 servicios (deps en `build.gradle.kts` raíz, config en cada
`application.yml`, `logback-spring.xml` por servicio):

- **Métricas**: `micrometer-registry-prometheus` → `/actuator/prometheus`.
- **Trazas**: `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`
  (sampling 1.0) → Jaeger vía OTLP `http://localhost:4318/v1/traces`.
- **Logs**: `logstash-logback-encoder`, appender TCP reconectante → Logstash:5000.
  Si ELK está caído, el servicio igual arranca y bufferiza.
- **Health/probes**: `/actuator/health` con `liveness`/`readiness` (k8s).
- **Métrica de negocio**: `circleguard_health_status_changes_total{status}` en
  `promotion-service` (transiciones de estado de salud por tipo).

## Levantar el stack

```bash
cd observability
docker compose -f docker-compose.observability.yml up -d
```

Los servicios Spring Boot corren en el host; los contenedores los alcanzan vía
`host.docker.internal`. Arrancar un servicio con la instrumentación activa:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces \
LOGSTASH_HOST=localhost LOGSTASH_PORT=5000 \
java -jar services/circleguard-gateway-service/build/libs/*.jar --server.port=8087
```

## Verificación (ejecutada en vivo)

| Señal | Comando | Resultado |
|-------|---------|-----------|
| Scrape | `curl 'localhost:9090/api/v1/query?query=up{service="gateway"}'` | `up=1` |
| Alertas | `curl localhost:9090/api/v1/rules` | 4 reglas en 3 grupos |
| Trazas | `curl localhost:16686/api/services` | `circleguard-gateway-service` |
| Logs | `curl 'localhost:9200/logstash-*/_count'` | `count > 0` |
| Dashboards | `curl -u admin:admin localhost:3000/api/search` | `CircleGuard - Service Overview` |
| Datasources | `curl -u admin:admin localhost:3000/api/datasources` | Prometheus, Jaeger, Elasticsearch |

## Dashboards y alertas

- **Dashboard** `grafana/dashboards/circleguard-overview.json` (uid `cg-overview`):
  request rate, p95 latency, 5xx rate, JVM heap y la métrica de negocio, con
  variable `$service`.
- **Reglas** `prometheus/alert-rules.yml`: `ServiceDown` (crítica),
  `HighRequestLatencyP95`, `HighHttp5xxErrorRate` (crítica), `HighJvmHeapUsage`.
- **Kibana**: crear data view `logstash-*` (campo tiempo `@timestamp`) para
  explorar logs.

## URLs

- Grafana: http://localhost:3000 (admin/admin)
- Prometheus: http://localhost:9090
- Alertmanager: http://localhost:9093
- Jaeger: http://localhost:16686
- Kibana: http://localhost:5601

## Limpieza

```bash
docker compose -f docker-compose.observability.yml down -v
```
