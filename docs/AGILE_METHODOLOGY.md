# Metodología Ágil — CircleGuard

## Marco elegido: Scrum (adaptado a equipo de 2)

- **Sprints** de 2 semanas, 2 iteraciones completas ejecutadas.
- **Roles**: Product Owner rotativo (define prioridad contra la rúbrica),
  ambos integrantes como Development Team; sin Scrum Master dedicado
  (impedimentos se tratan en la daily asíncrona).
- **Ceremonias**:
  - *Sprint Planning*: selección de HUs del backlog (`BACKLOG.md`) según el
    peso en la rúbrica.
  - *Daily asíncrona* por chat (qué hice / qué haré / bloqueos).
  - *Sprint Review*: demo de los incrementos (pipelines corriendo,
    dashboards, clusters).
  - *Retrospectiva*: acciones de mejora registradas abajo.
- **Herramienta de gestión**: GitHub Projects (tablero
  Backlog / Sprint / En progreso / Revisión / Done) + Issues vinculados a
  PRs. Las HUs con criterios de aceptación viven en `BACKLOG.md` (9 épicas
  E1–E9 mapeadas 1:1 a la rúbrica).
- **Definition of Done**: criterios de aceptación de la HU cumplidos + tests
  verdes en CI + revisión en PR + desplegable (imagen Docker construible).

## Estrategia de branching

**GitHub Flow** con ramas de ambiente — documentada en
`BRANCHING_STRATEGY.md`: `main` (prod) ← `release/*` (stage) ← `dev` ←
`feat/*`, Conventional Commits, PRs obligatorios con revisión cruzada,
versionado semántico automatizado (semantic-release en Actions, tags
`vX.Y.Z` generados por el job `release` de `ci.yml`).

## Iteración 1 — Fundamentos (Sprint 1)

**Objetivo**: proyecto corriendo + base de automatización.

| HU | Entregable verificable |
|----|------------------------|
| HU-01 Setup local | `docker-compose.dev.yml` con los 12 contenedores (8 servicios + infra) |
| HU-03 Branching | `BRANCHING_STRATEGY.md`, ramas dev/release/main activas |
| HU-04 CI base | `.github/workflows/ci.yml` (build + unit tests por servicio) |
| HU-05 Terraform base | `circleguard-infra/terraform/` con módulo `aks-cluster`, 3 ambientes, backend azurerm |
| HU-06 Pipelines despliegue | `.github/workflows/cd-dev.yml`, `cd-stage.yml` y job `deploy-prod` de `ci.yml` |

**Review**: demo de pipeline dev desplegando a Kubernetes local.
**Retrospectiva** (acciones):
- *Mejorar*: los tests dependían de Docker/BD locales → se introdujeron H2 +
  WireMock + Testcontainers para aislarlos (aplicado en Sprint 2).
- *Mantener*: convención de commits + revisión cruzada de PRs.

## Iteración 2 — Calidad, observabilidad y resiliencia (Sprint 2)

**Objetivo**: cobertura de las épicas de mayor peso restante.

| HU | Entregable verificable |
|----|------------------------|
| HU-07 Pruebas completas | Integración (`tests/integration-tests`), E2E Cypress (5 specs), Locust, ZAP baseline en CI |
| HU-08 Calidad/seguridad CI | SonarQube + JaCoCo + OWASP dependency-check + Trivy en `ci.yml` |
| HU-09 Observabilidad | kube-prometheus-stack + ServiceMonitors ×8 + Jaeger + ELK + alertas (`circleguard-infra/k8s/master/observability/`) |
| HU-10 Patrones | Circuit Breaker + External Config + Feature Toggle (`docs/DESIGN_PATTERNS.md`) |
| HU-11 Bonus | Service Mesh, Chaos, FinOps, Multi-Cloud (`docs/BONUS_*.md`) |

**Review**: demo en vivo — mesh con mTLS, experimento de caos abriendo el
circuit breaker, dashboard de costos.
**Retrospectiva** (acciones):
- *Mejorar*: se mantenían dos motores CI/CD (Actions + Jenkins) en paralelo
  → se consolidó todo en GitHub Actions (CI + CD con environments para los
  gates de promoción), simplificando el mantenimiento (`docs/PIPELINES.md`).
- *Aprendizaje*: el experimento de caos 1 falsó la hipótesis del fallback y
  destapó un bug real → "test in production-like" se adopta como práctica.

## Trazabilidad

- Backlog completo con HUs, criterios y tasks: `BACKLOG.md`.
- Evidencia de las iteraciones: historial de commits por épica
  (`git log --oneline`), PRs y tags del repositorio.
