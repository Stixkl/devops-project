# Diseño: guía de evidencias de la rúbrica

## Objetivo

Crear `docs/EVIDENCIAS_RUBRICA.md` como guía verificable para demostrar el cumplimiento del proyecto frente a `docs/Proyecto Final IngeSoft V (1).md`.

## Alcance

El documento cubrirá:

- Los 9 rubros principales.
- Cada subrequisito individual de cada rubro.
- Los 4 bonos.
- Los entregables finales.

Cada subrequisito tendrá:

1. Estado: `Completo`, `Parcial` o `Pendiente`.
2. Dónde se encuentra: ruta exacta en `devops-project` o `circleguard-infra`.
3. Cómo se evidencia: artefacto, configuración, código, reporte o salida que demuestra el punto.
4. Cómo se ejecuta o verifica: comandos reproducibles.
5. Resultado esperado: salida mínima que debe observarse.

## Fuentes

- Rúbrica: `docs/Proyecto Final IngeSoft V (1).md`.
- Auditoría: `docs/ESTADO_PROYECTO.md`.
- Documentación temática en `docs/`.
- Código, pruebas, scripts y workflows de `devops-project`.
- Terraform, Kubernetes, observabilidad, service mesh, chaos y multicloud de `../circleguard-infra`.

La guía priorizará artefactos reales sobre afirmaciones documentales. No marcará video, presentación ni ejecuciones no comprobadas como completas.

## Estructura

1. Propósito y convenciones.
2. Prerrequisitos y ubicación de ambos repositorios.
3. Resumen ejecutivo por rubro.
4. Sección por rubro con todos sus subrequisitos.
5. Sección por bono con todos sus subrequisitos.
6. Entregables.
7. Checklist de demostración.

Los comandos usarán PowerShell cuando dependan de Windows. Se conservarán comandos estándar de Gradle, Docker, Terraform, Kubernetes, npm, Locust, GitHub CLI y Azure CLI cuando corresponda.

## Validación

Antes de finalizar:

- Confirmar que todos los puntos de la rúbrica estén representados.
- Confirmar existencia de cada ruta local referenciada.
- Contrastar comandos con scripts, workflows y documentación existentes.
- Buscar marcadores vagos como `TODO` o `TBD`.
- Revisar que estados parciales y pendientes sean explícitos.

## Fuera de alcance

- Crear video o diapositivas.
- Ejecutar despliegues cloud con costo.
- Modificar infraestructura, aplicación, pipelines o pruebas.
- Afirmar resultados de ambientes que no puedan verificarse desde los artefactos disponibles.
