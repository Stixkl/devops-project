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
- **Implementación**: `resilience4j-spring-boot3` con la anotación
  `@CircuitBreaker(name=..., fallbackMethod=...)`.
- **Dónde** (las 3 llamadas REST entre servicios):
  - `dashboard → promotion`: `PromotionClient` (`getHealthStats`,
    `getHealthStatsByDepartment`), instancia `promotion`.
  - `auth → identity`: `IdentityClient.getAnonymousId`, instancia `identity`.
  - `notification → auth`: `AuthAdminClient.getPriorityAlertAdmins`, instancia
    `auth`.
- **Config** (`application.yml` de cada servicio): `slidingWindowSize=10`,
  `minimumNumberOfCalls=5`, `failureRateThreshold=50%`,
  `waitDurationInOpenState=10s`, `registerHealthIndicator=true`.
- **Beneficio**: degradación controlada, recuperación automática (estado
  half-open) y aislamiento de fallos.

### 1.2 Retry (Resiliencia)

- **Propósito**: reintentar automáticamente fallos **transitorios** (timeouts,
  errores de red puntuales) antes de abrir el circuito o dar fallback.
- **Implementación**: anotación `@Retry(name=...)` de resilience4j sobre los
  mismos métodos cliente. `maxAttempts=3`, `waitDuration=500ms`.
- **Beneficio**: absorbe inestabilidad puntual sin propagar el error; se combina
  con Circuit Breaker (retry primero, breaker si los reintentos también fallan).

### 1.3 External Configuration (Configuración)

- **Propósito**: sacar la configuración (URLs de servicios, flags) del código y
  centralizarla en propiedades **tipadas** y validadas, sobreescribibles por
  variable de entorno o perfil sin recompilar.
- **Implementación**: clases `@ConfigurationProperties` + `@Validated`,
  habilitadas con `@ConfigurationPropertiesScan` en cada `*Application`.
  - `dashboard`: `DashboardProperties` (`circleguard.promotion-service.url`).
  - `auth`: `AuthClientProperties` (`circleguard.identity-service.url`) —
    elimina la URL hardcodeada `http://localhost:8083/...` de `IdentityClient`.
  - `notification`: `NotificationProperties` (`auth.api.url`).
- **Beneficio**: configuración 12-factor; un mismo artefacto se promueve entre
  dev/stage/prod cambiando solo variables de entorno (p.ej.
  `CIRCLEGUARD_PROMOTIONSERVICE_URL`). Los `RestTemplate` además se crean como
  beans con timeouts (`RestClientConfig`), antes eran `new RestTemplate()` sin
  timeout.

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
| Circuit Breaker + Retry | `DashboardIntegrationTest`: con WireMock devolviendo `503`, la llamada cae al **fallback** (`error map`) en vez de propagar la excepción — el test arranca el contexto resilience4j real. |
| Feature Toggle (property) | `AnalyticsServiceTest.getDepartmentStats_WhenFeatureDisabled_*`: con el flag en `false`, devuelve respuesta degradada y `promotionClient` **nunca** se invoca. |
| Métricas en vivo | `curl localhost:8084/actuator/prometheus \| grep resilience4j_circuitbreaker_state` y estado del breaker en `/actuator/health` (`registerHealthIndicator=true`). Prometheus ya scrapea estas métricas. |
| External Config | Arrancar con `CIRCLEGUARD_PROMOTIONSERVICE_URL=...` y confirmar que el cliente usa esa URL. |

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
