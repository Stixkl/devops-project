# Testing Strategy - Circle Guard

## Pirámide de Pruebas

```
                    ┌─────────────┐
                    │     E2E     │  ← 23 tests (Cypress API)
                    │   (23)      │
        ┌───────────┴─────────────┴───────────┐
        │         INTEGRATION TESTS           │  ← 8 tests (SpringBootTest)
        │              (8)                    │
    ┌───┴─────────────────────────────────────┴───┐
    │              UNIT TESTS                      │  ← 27 existing + new
    │         (27 existing + 6 new)               │
    └─────────────────────────────────────────────┘
```

## Test Suite Actual

### Unit Tests (27 existentes + nuevos del temp)

**Total esperado por servicio:**

| Servicio | Tests Existentes | Tests Nuevos | Total |
|----------|------------------|--------------|-------|
| auth-service | 1 (LoginControllerTest) | JwtTokenServiceTest, QrTokenServiceTest | 3+ |
| identity-service | 3 | IdentityVaultServiceTest | 4+ |
| gateway-service | 2 | - | 2+ |
| form-service | 4 | SymptomMapperTest, QuestionnaireControllerTest, HealthSurveyControllerTest | 7+ |
| notification-service | 7 | NotificationDispatcherTest, TemplateServiceTest | 9+ |
| promotion-service | 8 | HealthStatusServiceTest, CircleServiceTest | 10+ |

### Integration Tests (8 nuevos)

Ubicación: `tests/integration-tests/src/test/java/com/circleguard/integration/`

| Test | Servicio | Endpoint Validado |
|------|----------|-------------------|
| AuthServiceIntegrationTest | auth | /auth/login, /auth/visitor/handoff |
| FormServiceIntegrationTest | form | /surveys, /questionnaires/health |
| GatewayServiceIntegrationTest | gateway | /gate/validate, /status/{id} |
| IdentityServiceIntegrationTest | identity | /identity/create |
| NotificationServiceIntegrationTest | notification | /notifications/priority, /notifications/circle-fenced |
| PromotionServiceIntegrationTest | promotion | /health/report, /analytics/overview |
| CrossServiceIntegrationTest | all | health checks de los 6 servicios |

### E2E Tests (23 tests - Cypress API)

Ubicación: `tests/e2e/cypress/e2e/`

| Archivo | Tests | Escenario |
|---------|-------|-----------|
| 01-auth-flow.cy.js | 5 | Login, visitor handoff, JWT token |
| 02-health-survey.cy.js | 5 | Survey submission, symptom detection |
| 03-gate-access.cy.js | 5 | QR validation, visitor token validation |
| 04-cross-service.cy.js | 5 | Full cross-service flow |
| 05-identity.cy.js | 3 | Identity creation and mapping |

### Performance Tests (Locust)

Ubicación: `tests/performance/`

| User Class | Weight | Operations |
|------------|--------|------------|
| AuthServiceUser | High | Login, visitor handoff, health |
| GatewayServiceUser | High | Token validation, status lookup |
| FormServiceUser | Medium | Survey submission, questionnaires |
| NotificationServiceUser | Medium | Priority alerts, circle notifications |
| RampUpUser | Low | Mixed operations for gradual load |
| SpikeUser | Low | Burst traffic simulation |

## Análisis de Resultados de Performance

*(Llenar después de ejecutar Locust)*

### Métricas Objetivo

| Métrica | Objetivo | Umbral de Alerta |
|---------|---------|------------------|
| Response Time p95 | < 500ms | > 1000ms |
| Response Time p99 | < 1000ms | > 2000ms |
| Throughput | > 100 RPS | < 50 RPS |
| Error Rate | < 1% | > 5% |

### Resultados por Servicio

#### auth-service
- Avg Response Time: ___
- p95: ___
- p99: ___
- RPS: ___
- Error Rate: ___

#### gateway-service
- Avg Response Time: ___
- p95: ___
- p99: ___
- RPS: ___
- Error Rate: ___

#### form-service
- Avg Response Time: ___
- p95: ___
- p99: ___
- RPS: ___
- Error Rate: ___

### Recomendaciones

1. **Auth Service**: Considerar connection pooling para LDAP
2. **Gateway Service**: Redis cache hit rate debe ser > 80%
3. **Form Service**: Particionar topic Kafka si throughput < 100 RPS
4. **Notification Service**: Implementar retry backoff exponencial
