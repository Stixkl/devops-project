# Estrategia de Branching — Circle Guard

> Documentación oficial de la estrategia de control de versiones para el proyecto final de Ingeniería de Software V.

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

| Rama | Propósito | Ambiente destino | Protegida |
|------|-----------|-----------------|-----------|
| `main` | Código estable listo para producción | `prod` | ✅ Sí |
| `develop` | Integración continua de features | `dev` | ✅ Sí |
| `staging` | Validación previa a producción | `stage` | ✅ Sí |
| `feature/*` | Desarrollo de nuevas funcionalidades | — | ❌ No |
| `hotfix/*` | Correcciones urgentes en producción | `prod` | ❌ No |
| `release/*` | Preparación de una nueva versión | — | ❌ No |

### Descripción de ramas permanentes

**`main`**
Contiene únicamente código que ha pasado todas las pruebas, revisiones y aprobaciones. Todo merge a `main` genera automáticamente un release tag y despliegue a producción (con aprobación manual requerida).

**`develop`**
Rama de integración donde convergen todas las features completadas en el sprint actual. El pipeline de CI ejecuta pruebas unitarias, de integración y análisis estático (SonarQube) en cada push.

**`staging`**
Rama intermedia para validación en un ambiente idéntico a producción. Se actualiza mediante PR desde `develop` al final de cada sprint.

---

## Convención de nombres

### Ramas de feature
```
feature/<ID-historia>-<descripcion-corta>
```
Ejemplos:
```
feature/US-01-terraform-infra-base
feature/US-05-ci-cd-github-actions
feature/US-12-monitoring-prometheus-grafana
```

### Ramas de hotfix
```
hotfix/<ID-issue>-<descripcion-corta>
```
Ejemplo:
```
hotfix/BUG-03-k8s-readiness-probe
```

### Ramas de release
```
release/v<MAJOR>.<MINOR>.<PATCH>
```
Ejemplo:
```
release/v1.2.0
```

---

## Flujo de trabajo

### 1. Inicio de una historia de usuario

```bash
# Desde la rama develop actualizada
git checkout develop
git pull origin develop

# Crear rama de feature
git checkout -b feature/US-XX-nombre-de-la-historia
```

### 2. Desarrollo y commits

```bash
# Hacer commits frecuentes y descriptivos
git add .
git commit -m "feat(US-XX): descripción del cambio realizado"

# Sincronizar con develop regularmente para evitar conflictos
git fetch origin
git rebase origin/develop
```

### 3. Apertura de Pull Request

Al finalizar el desarrollo de la historia:

1. Hacer `push` de la rama al repositorio remoto.
2. Abrir un **Pull Request** hacia `develop` en GitHub.
3. Asignar al menos un revisor.
4. Vincular el PR con la historia de usuario en GitHub Projects.
5. Esperar aprobación y que pasen todos los checks del pipeline.

### 4. Merge a develop

```bash
# Solo mediante PR aprobado — nunca push directo
# Se usa Squash Merge para mantener historial limpio
```

### 5. Promoción a staging (fin de sprint)

```bash
# Abrir PR desde develop → staging
# Requiere aprobación del equipo
# Dispara despliegue automático al ambiente stage
```

### 6. Release a producción

```bash
# Crear rama de release desde staging
git checkout -b release/v1.0.0 origin/staging

# Actualizar versión y release notes
# Abrir PR hacia main (requiere aprobación manual)
# Al hacer merge, se genera el tag automáticamente
```

### 7. Hotfix urgente

```bash
# Crear desde main
git checkout -b hotfix/BUG-XX-descripcion origin/main

# Después del fix, hacer PR hacia main Y develop
# Para no perder el fix en el próximo deploy
```

---

## Reglas de protección de ramas

Las siguientes reglas deben configurarse en **Settings → Branches** del repositorio:

### Rama `main`
- ✅ Requerir Pull Request antes de hacer merge
- ✅ Requerir al menos **1 aprobación** de revisor
- ✅ Requerir que pasen todos los status checks (CI pipeline)
- ✅ Requerir que la rama esté actualizada antes del merge
- ✅ Incluir administradores en las restricciones
- ✅ Requerir aprobación manual en GitHub Actions para despliegue a `prod`

### Rama `develop`
- ✅ Requerir Pull Request antes de hacer merge
- ✅ Requerir que pasen los status checks básicos (build + unit tests)
- ❌ No requerir aprobación (para agilizar el flujo durante el sprint)

### Rama `staging`
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

Los tags se generan **automáticamente** mediante el pipeline de CI/CD al hacer merge a `main`, basándose en los tipos de commits del PR (usando `semantic-release` o equivalente).

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

  feature/US-XX ──► develop ──► staging ──► main ──► [TAG v1.x.x]
       │               │           │          │
    (PR + CI)      (PR + CI)  (PR + CI)  (Aprobación
                                           manual)
                                                │
                                           Despliegue
                                          automático
                                            PROD


                    ┌─────────────────────────────────────────────┐
                    │               FLUJO HOTFIX                  │
                    └─────────────────────────────────────────────┘

  main ──► hotfix/BUG-XX ──► PR ──► main ──► [TAG v1.x.x+1]
                                │
                                └──► develop  (cherry-pick o PR paralelo)
```

---

## Referencias

- [GitHub Flow - GitHub Docs](https://docs.github.com/en/get-started/using-github/github-flow)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Semantic Versioning](https://semver.org/)
- [Repositorio del proyecto](https://github.com/jcmunozf/circle-guard-public)

---

*Documento mantenido por el equipo de desarrollo — IngeSoft V*
*Última actualización: Mayo 2026*
