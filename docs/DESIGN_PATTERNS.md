# Patrones de Diseño — CircleGuard

Documento de los patrones de diseño implementados en la plataforma de
microservicios CircleGuard: su **propósito**, **beneficio**, **dónde** están y
**cómo verificarlos**. Se divide en patrones **nuevos** (añadidos para el taller
de patrones) y patrones **ya existentes** catalogados en el código.

---

## 1. Patrones nuevos

### 1.1 Circuit Breaker (Resiliencia)

- **Propósito**: evitar fallos en cascada cuando un servicio aguas abajo está
  caído o lento. Tras un umbral de fallos el circuito se **abre** y las llamadas
  fallan rápido (fail-fast) hacia un fallback, sin agotar hilos ni timeouts.
- **Implementación**: `spring-cloud-starter-circuitbreaker-resilience4j` con la
  anotación `@CircuitBreaker(name=..., fallbackMethod=...)` + eventos del
  breaker logueados por `CircuitBreakerEventLogger` (auth/dashboard/notification).
- **Dónde** (las 3 llamadas REST entre servicios):
  - `dashboard → promotion`: `PromotionClient` (`getHealthStats`,
    `getHealthStatsByDepartment`), instancia `promotionService`. El fallback
    devuelve el **último resultado bueno cacheado** (Caffeine, 30 min) marcado
    con `cached: true`, o un error map si no hay caché.
  - `auth → identity`: `IdentityClient.mapIdentity`, instancia
    `identityService`. Fallback: `Optional.empty()` → login degradado con UUID
    determinístico (`LoginController`).
  - `notification → auth`: `AuthServiceClient.getUsersByPermission`, instancia
    `authServicePermissions`. Fallback: `BROADCAST_ALL` → el listener difunde
    por todos los canales en vez de perder la alerta.
- **Config** (`application.yml` de cada servicio, bloque
  `resilience4j.circuitbreaker.configs.default` + instancia con overrides):
  `slidingWindowSize=10`, `minimumNumberOfCalls=5`, `failureRateThreshold=50%`,
  `waitDurationInOpenState=10-30s`, `record/ignore-exceptions` (5xx/timeout
  cuentan, 4xx no).
- **Beneficio**: degradación controlada, recuperación automática (estado
  half-open) y aislamiento de fallos.

### 1.2 Retry (Resiliencia)

- **Propósito**: reintentar automáticamente fallos **transitorios** (timeouts,
  errores de red puntuales) antes de dar el envío por perdido.
- **Implementación**: Spring Retry (`@EnableRetry` en `NotificationApplication`,
  `@Retryable` con backoff) en los canales de notificación:
  `EmailServiceImpl`, `SmsServiceImpl` (Twilio) y `PushServiceImpl` (Gotify).
- **Beneficio**: absorbe inestabilidad puntual de proveedores externos sin
  propagar el error; se complementa con el Circuit Breaker de las llamadas REST
  internas (retry para canales de salida, breaker para dependencias internas).

### 1.3 External Configuration (Configuración)

- **Propósito**: sacar la configuración (URLs de servicios, flags) del código y
  centralizarla en propiedades **tipadas** y validadas, sobreescribibles por
  variable de entorno o perfil sin recompilar.
- **Implementación**: dos mecanismos complementarios.
  - **Claves `circleguard.client.*` + env vars en todos los clientes REST**:
    URL, connect/read/write timeouts inyectados por constructor
    (`PromotionClient`, `IdentityClient`, `AuthServiceClient`), p.ej.
    `CIRCLEGUARD_CLIENT_PROMOTION_URL`,
    `CIRCLEGUARD_CLIENT_IDENTITY_READ_TIMEOUT`. Todo `application.yml` usa
    defaults sobreescribibles (`${VAR:default}`): datasource, Kafka, LDAP, JWT,
    Twilio, Gotify, sampling de tracing, etc.
  - **Clase tipada `@ConfigurationProperties`**: `DashboardProperties`
    (`@Validated`, habilitada con `@ConfigurationPropertiesScan` en
    `DashboardApplication`) que agrupa los feature flags del dashboard.
- **Beneficio**: configuración 12-factor; un mismo artefacto se promueve entre
  dev/stage/prod cambiando solo variables de entorno. Los `RestTemplate` se
  construyen con timeouts explícitos vía `RestTemplateBuilder` — nunca
  `new RestTemplate()` sin timeout.

### 1.4 Feature Toggle (Configuración)

- **Propósito**: activar/desactivar funcionalidad en runtime o por despliegue,
  desacoplando "deploy" de "release".
- **Dos variantes**:
  1. **Property-based (nuevo)**: `circleguard.features.department-stats-enabled`
     (en `DashboardProperties`) controla
     `AnalyticsService.getDepartmentStats`. Si está en `false`, devuelve una
     respuesta degradada y **no** llama a promotion. Conmutable por env var
     `CIRCLEGUARD_FEATURES_DEPARTMENTSTATSENABLED`.
  2. **DB-backed (existente, mejorado/documentado)**:
     `SystemSettings.unconfirmedFencingEnabled` en `promotion`, conmutable en
     caliente vía `AdminController` (`POST /api/v1/admin/settings/
     toggle-unconfirmed-fencing`), con caché (`@Cacheable`/`@CacheEvict`).
- **Beneficio**: releases graduales, kill-switch operativo y A/B sin redeploy.

---

## 2. Verificación

| Patrón | Cómo se comprueba |
|--------|-------------------|
| Circuit Breaker | `PromotionClientTest` / `IdentityClientTest` / `IdentityClientIntegrationTest`: con el breaker abierto (URL inalcanzable, ventana mínima), la llamada cae al **fallback** (error map / `Optional.empty()`) y se recupera tras half-open — contexto resilience4j real. |
| Feature Toggle (property) | `AnalyticsServiceTest.getDepartmentStats_WhenFeatureDisabled_*`: con el flag en `false`, devuelve respuesta degradada y `promotionClient` **nunca** se invoca. |
| Métricas en vivo | `curl localhost:8084/actuator/prometheus \| grep resilience4j_circuitbreaker` — Prometheus scrapea el estado del breaker; eventos en logs vía `CircuitBreakerEventLogger`. |
| External Config | Arrancar con `CIRCLEGUARD_CLIENT_PROMOTION_URL=...` y confirmar que el cliente usa esa URL. |

---

## 3. Patrones ya existentes (catálogo)

| Patrón | Dónde (ejemplo) | Propósito |
|--------|-----------------|-----------|
| **Builder** | `SystemSettings` (`@Builder`), DTOs | Construcción fluida e inmutable de objetos. |
| **Repository** | `*Repository` JPA (p.ej. `SystemSettingsRepository`) | Abstracción del acceso a datos. |
| **Observer / Pub-Sub** | Listeners Kafka (`ExposureNotificationListener`, `PriorityAlertListener`, `CircleFencedListener`) | Desacople por eventos entre servicios. |
| **Chain of Responsibility** | Cadena de filtros de Spring Security (`SecurityConfig`) | Procesamiento secuencial de la petición. |
| **Facade** | `NotificationDispatcher` | Interfaz única sobre email/SMS/push. |
| **Adapter** | `EmailServiceImpl`, `SmsServiceImpl` (Twilio), `PushServiceImpl` (Gotify) | Adaptan APIs externas a interfaces internas. |
| **Strategy** | Implementaciones intercambiables de `EmailService`/`SmsService`/`PushService` | Selección de algoritmo/proveedor. |
| **DTO** | Objetos de request/response en controllers | Separar el modelo de dominio del contrato API. |
| **Caching** | `@Cacheable`/`@CacheEvict` en `AdminController` | Reducir lecturas repetidas de settings. |
| **Retry (previo)** | `@Retryable` en `PushServiceImpl` (Gotify) | Reintento de envíos push transitorios. |
