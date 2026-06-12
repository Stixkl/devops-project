# Estrategia de Branching — Circle Guard

> Documentación oficial de la estrategia de control de versiones para el proyecto final de Ingeniería de Software V.
> Las ramas y disparadores aquí descritos coinciden con los workflows reales (`.github/workflows/`); el mapeo operativo rama→workflow→ambiente está en `docs/PIPELINES.md`.

---

## Tabla de Contenidos

1. [Metodología adoptada](#metodología-adoptada)
2. [Estructura de ramas](#estructura-de-ramas)
3. [Convención de nombres](#convención-de-nombres)
4. [Flujo de trabajo](#flujo-de-trabajo)
5. [Reglas de protección de ramas](#reglas-de-protección-de-ramas)
6. [Política de commits](#política-de-commits)
7. [Pull Requests y Code Review](#pull-requests-y-code-review)
8. [Versionado semántico](#versionado-semántico)
9. [Diagrama de flujo](#diagrama-de-flujo)

---

## Metodología adoptada

Se utiliza **GitHub Flow** como estrategia de branching principal. Esta elección se basa en:

- **Simplicidad**: flujo lineal adecuado para equipos pequeños y proyectos académicos.
- **Integración continua**: favorece despliegues frecuentes a través de pipelines CI/CD.
- **Compatibilidad**: se integra de forma nativa con GitHub Actions, GitHub Projects y los ambientes de despliegue (dev, stage, prod).

> GitHub Flow es más liviano que GitFlow y suficiente para manejar los múltiples ambientes (dev, stage, prod) requeridos por el proyecto.

---

## Estructura de ramas

Los disparadores de despliegue corresponden a los `on: push` de los workflows reales.

| Rama | Propósito | Workflow disparado | Ambiente destino | Protegida |
|------|-----------|--------------------|------------------|-----------|
| `main` | Rama por defecto; base de los Pull Requests | `ci.yml` (en PR) | — | ✅ Sí |
| `master` | Código de producción | `ci.yml` (push) → job `deploy-prod` | `prod` (`circleguard-master`) | ✅ Sí |
| `dev` | Integración continua de features | `cd-dev.yml` (push) + `ci.yml` | `dev` (`circleguard-dev`) | ✅ Sí |
| `release/**` | Preparación / validación de una versión | `cd-stage.yml` (push) | `stage` (`circleguard-stage`) | ✅ Sí |
| `feat/*` | Desarrollo de nuevas funcionalidades | `ci.yml` (en PR) | — | ❌ No |
| `fix/*` | Correcciones de bugs | `ci.yml` (en PR) | — | ❌ No |
| `chore/*` | Mantenimiento, configs, refactors de infra | `ci.yml` (en PR) | — | ❌ No |

> **Sitio DR multi-cloud**: el segundo cloud (GCP/GKE, namespace `circleguard-dr`)
> NO se promueve por rama. Se despliega con `cd-gcp.yml` vía `workflow_dispatch`
> (manual), reutilizando las imágenes `dev-latest`. Ver `docs/DESPLIEGUE_GCP.md`.

### Descripción de ramas permanentes

**`main`**
Rama por defecto del repositorio y base de los Pull Requests. El CI (`ci.yml`) corre sobre cada PR dirigido a ella (build, tests, SonarQube, Trivy).

**`master`**
Contiene el código de producción. Un push a `master` ejecuta el CI completo y, tras el job `release` (semantic-release: tag + CHANGELOG), el job `deploy-prod` despliega a `prod` con **aprobación manual** (environment `production`, required reviewers).

**`dev`**
Rama de integración donde convergen las features del sprint. Cada push dispara `cd-dev.yml` (build+push de imágenes `dev-<sha>`/`dev-latest` y despliegue al ambiente `dev`) además del CI.

**`release/**`**
Ramas de preparación de versión. Un push dispara `cd-stage.yml`, que despliega al ambiente `stage` (`circleguard-stage`) e incluye las pruebas de integración, E2E (Cypress) y performance (Locust) vía `ci.yml`.

---

## Convención de nombres

Las ramas de trabajo usan el mismo prefijo que el tipo de Conventional Commit dominante.

### Ramas de trabajo
```
feat/<descripcion-corta>     # nueva funcionalidad
fix/<descripcion-corta>      # corrección de bug
chore/<descripcion-corta>    # mantenimiento, configs, infra
```
Ejemplos reales del proyecto:
```
feat/architecture
fix/qr-secret-256bit
fix/postgres-init-databases
chore/split-infra-repo
feat/multicloud-gcp-gke      # (repo circleguard-infra)
```

### Ramas de release
```
release/<nombre-o-version>
```
Ejemplo:
```
release/demo-satable
release/v1.2.0
```

---

## Flujo de trabajo

### 1. Inicio de una historia de usuario

```bash
# Desde la rama de integración actualizada
git checkout dev
git pull origin dev

# Crear rama de trabajo
git checkout -b feat/nombre-de-la-historia
```

### 2. Desarrollo y commits

```bash
# Hacer commits frecuentes y descriptivos (Conventional Commits)
git add .
git commit -m "feat(US-XX): descripción del cambio realizado"

# Sincronizar con dev regularmente para evitar conflictos
git fetch origin
git rebase origin/dev
```

### 3. Apertura de Pull Request

Al finalizar el desarrollo:

1. Hacer `push` de la rama al repositorio remoto.
2. Abrir un **Pull Request** en GitHub (base: `dev` para integración; `main` como base por defecto del repo).
3. Asignar al menos un revisor.
4. Vincular el PR con la historia de usuario en GitHub Projects.
5. Esperar aprobación y que pasen todos los checks del pipeline (`ci.yml`).

### 4. Merge a dev

```bash
# Solo mediante PR aprobado — nunca push directo a ramas protegidas
# Squash Merge para mantener historial limpio
# El push a dev dispara cd-dev.yml → despliegue automático al ambiente dev
```

### 5. Promoción a stage (validación de versión)

```bash
# Crear/actualizar una rama release/** desde dev
git checkout -b release/vX.Y.Z origin/dev
git push origin release/vX.Y.Z
# El push dispara cd-stage.yml → despliegue automático al ambiente stage
```

### 6. Release a producción

```bash
# Abrir PR de la rama release/** (o dev validado) hacia master
# Al hacer merge/push a master:
#   - ci.yml corre el CI completo
#   - job release (semantic-release) genera el tag vX.Y.Z y el CHANGELOG
#   - job deploy-prod despliega a prod con aprobación manual (environment production)
```

### 7. Hotfix urgente

```bash
# Crear desde master
git checkout -b fix/BUG-XX-descripcion origin/master

# Tras el fix, PR hacia master; replicar a dev para no perder el fix
```

---

## Reglas de protección de ramas

Las siguientes reglas deben configurarse en **Settings → Branches** del repositorio:

### Rama `master` (producción)
- ✅ Requerir Pull Request antes de hacer merge
- ✅ Requerir al menos **1 aprobación** de revisor
- ✅ Requerir que pasen todos los status checks (CI pipeline)
- ✅ Requerir que la rama esté actualizada antes del merge
- ✅ Incluir administradores en las restricciones
- ✅ Aprobación manual en GitHub Actions para el deploy a `prod` (environment `production`)

### Rama `main` (por defecto)
- ✅ Requerir Pull Request antes de hacer merge
- ✅ Requerir que pasen los status checks del CI

### Rama `dev`
- ✅ Requerir Pull Request antes de hacer merge
- ✅ Requerir que pasen los status checks básicos (build + unit tests)
- ❌ No requerir aprobación (para agilizar el flujo durante el sprint)

### Ramas `release/**`
- ✅ Requerir Pull Request antes de hacer merge
- ✅ Requerir al menos **1 aprobación**
- ✅ Requerir que pasen todos los status checks

---

## Política de commits

Se utiliza **Conventional Commits** para estandarizar los mensajes y permitir la generación automática de Release Notes.

### Formato
```
<tipo>(<alcance>): <descripción corta>

[cuerpo opcional]

[footer opcional: referencias a issues/US]
```

### Tipos permitidos

| Tipo | Uso |
|------|-----|
| `feat` | Nueva funcionalidad |
| `fix` | Corrección de bug |
| `docs` | Cambios en documentación |
| `chore` | Tareas de mantenimiento, configs |
| `test` | Adición o modificación de pruebas |
| `ci` | Cambios en pipelines CI/CD |
| `refactor` | Refactorización sin cambio de comportamiento |
| `perf` | Mejoras de rendimiento |
| `infra` | Cambios en infraestructura (Terraform, Kubernetes) |

### Ejemplos

```
feat(US-03): implementar circuit breaker en servicio de pagos

Se agrega Resilience4j con configuración de fallback.
El umbral de fallos se configura vía variable de entorno.

Refs: #US-03
```

```
fix(BUG-07): corregir health check en deployment de auth-service

El readinessProbe apuntaba a /health en lugar de /actuator/health.

Fixes: #BUG-07
```

```
infra(US-08): agregar módulo Terraform para RDS en ambiente prod
```

---

## Pull Requests y Code Review

### Plantilla de PR

Todo Pull Request debe completar la plantilla disponible en `.github/pull_request_template.md`:

```markdown
## Descripción
<!-- Qué hace este PR y por qué -->

## Historia de usuario relacionada
<!-- US-XX: Nombre de la historia -->

## Tipo de cambio
- [ ] Nueva funcionalidad (feat)
- [ ] Corrección de bug (fix)
- [ ] Infraestructura (infra)
- [ ] Documentación (docs)
- [ ] Otro: ___

## Checklist
- [ ] El código compila sin errores
- [ ] Las pruebas unitarias pasan
- [ ] Se agregaron/actualizaron pruebas para el cambio
- [ ] Se actualizó la documentación si aplica
- [ ] El análisis de SonarQube no reporta issues críticos
- [ ] El escaneo de Trivy no reporta vulnerabilidades HIGH/CRITICAL nuevas

## Screenshots / evidencia (si aplica)
```

### Criterios para aprobación

Un PR puede mergearse cuando:

1. Al menos **1 revisor** lo aprueba.
2. Todos los **status checks** del pipeline CI pasan (build, tests, sonar, trivy).
3. No hay **conflictos** con la rama destino.
4. Los comentarios del revisor están **resueltos**.

---

## Versionado semántico

Se usa **SemVer** (`MAJOR.MINOR.PATCH`) para etiquetar releases:

| Componente | Cuándo incrementar |
|------------|-------------------|
| `MAJOR` | Cambios que rompen compatibilidad (breaking changes) |
| `MINOR` | Nueva funcionalidad compatible con versiones anteriores |
| `PATCH` | Correcciones de bugs y hotfixes |

Los tags se generan **automáticamente** mediante el pipeline de CI/CD al hacer push a `master` (job `release` de `ci.yml`, con `semantic-release`), basándose en los tipos de commits.

Ejemplos de tags:
```
v1.0.0   ← Release inicial
v1.1.0   ← Nueva feature agregada
v1.1.1   ← Hotfix aplicado
v2.0.0   ← Cambio breaking en la API
```

---

## Diagrama de flujo

```
                    ┌─────────────────────────────────────────────┐
                    │              FLUJO PRINCIPAL                │
                    └─────────────────────────────────────────────┘

  feat/* | fix/* ──► dev ──► release/** ──► master ──► [TAG v1.x.x]
       │             │           │             │
    (PR + CI)     (cd-dev)   (cd-stage)   (ci.yml: release
                  → DEV      → STAGE       + deploy-prod,
                                            aprobación manual)
                                                │
                                           Despliegue
                                          automático PROD


                    ┌─────────────────────────────────────────────┐
                    │           MULTI-CLOUD (DR, manual)          │
                    └─────────────────────────────────────────────┘

  workflow_dispatch ──► cd-gcp.yml ──► GKE cg-gke-dr (circleguard-dr)
                         (reusa imágenes dev-latest; Azure activo + GCP pasivo)


                    ┌─────────────────────────────────────────────┐
                    │               FLUJO HOTFIX                  │
                    └─────────────────────────────────────────────┘

  master ──► fix/BUG-XX ──► PR ──► master ──► [TAG v1.x.x+1]
                              │
                              └──► dev  (cherry-pick o PR paralelo)
```

---

## Referencias

- [GitHub Flow - GitHub Docs](https://docs.github.com/en/get-started/using-github/github-flow)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Semantic Versioning](https://semver.org/)
- [Mapeo rama → ambiente (PIPELINES.md)](docs/PIPELINES.md)
- [Repositorio del proyecto](https://github.com/jcmunozf/circle-guard-public)

---

*Documento mantenido por el equipo de desarrollo — IngeSoft V*
*Última actualización: Junio 2026*
