# Rubric Evidence Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Crear una guía Markdown que indique, para cada subrequisito de la rúbrica, su estado, ubicación, evidencia, comando de verificación y resultado esperado.

**Architecture:** Un único documento `docs/EVIDENCIAS_RUBRICA.md` organizado por rubros, bonos y entregables. Las referencias apuntan a los repositorios hermanos `devops-project` y `circleguard-infra`; los estados distinguen implementación existente de evidencia externa pendiente.

**Tech Stack:** Markdown, PowerShell, Git, Gradle, npm/Cypress, Python/Locust, Docker, Terraform, kubectl, Helm, GitHub CLI y Azure CLI.

---

### Task 1: Redactar la guía trazable

**Files:**
- Create: `docs/EVIDENCIAS_RUBRICA.md`
- Reference: `docs/Proyecto Final IngeSoft V (1).md`
- Reference: `docs/ESTADO_PROYECTO.md`
- Reference: `docs/*.md`
- Reference: `.github/workflows/*.yml`
- Reference: `../circleguard-infra/**`

- [x] **Step 1: Crear estructura base**

Crear propósito, convenciones, prerrequisitos, resumen, nueve rubros, cuatro bonos, entregables y checklist de demostración.

- [x] **Step 2: Documentar cada subrequisito**

Usar este formato:

```markdown
#### N.M Nombre del subrequisito

- **Estado:** Completo | Parcial | Pendiente
- **Dónde:** `ruta`
- **Evidencia:** descripción comprobable
- **Cómo verificar:**
  ```powershell
  comando
  ```
- **Resultado esperado:** salida observable
```

- [x] **Step 3: Marcar brechas sin ocultarlas**

Video, presentación y resultados no capturados deben quedar como `Pendiente` o `Parcial`, con acción exacta para completar evidencia.

- [x] **Step 4: Revisar el archivo**

Run:

```powershell
rg -n "^## |^#### |Estado:|Dónde:|Evidencia:|Cómo verificar:|Resultado esperado:" docs/EVIDENCIAS_RUBRICA.md
```

Expected: aparecen todos los rubros y cada subrequisito contiene los cinco campos.

### Task 2: Validar trazabilidad y calidad

**Files:**
- Verify: `docs/EVIDENCIAS_RUBRICA.md`

- [x] **Step 1: Buscar placeholders**

Run:

```powershell
rg -n "TBD|TODO|___|Llenar después" docs/EVIDENCIAS_RUBRICA.md
```

Expected: sin coincidencias.

- [x] **Step 2: Validar rutas literales**

Extraer rutas entre backticks que empiecen por `docs/`, `.github/`, `services/`, `tests/`, `scripts/`, `mobile/`, `docker/` o `../circleguard-infra/`; comprobar existencia cuando no sean patrones.

Expected: cero rutas inexistentes, salvo rutas marcadas explícitamente como artefactos externos o resultados generados.

- [x] **Step 3: Validar formato Git**

Run:

```powershell
git diff --check
git status --short
```

Expected: `git diff --check` exit 0; solo plan y guía esperados aparecen modificados.

- [x] **Step 4: Commit**

```powershell
git add docs/superpowers/plans/2026-06-12-evidencias-rubrica.md docs/EVIDENCIAS_RUBRICA.md
git commit -m "docs: add rubric evidence guide"
```
