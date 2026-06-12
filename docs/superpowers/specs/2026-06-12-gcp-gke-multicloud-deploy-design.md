# Diseño — Despliegue real en GCP/GKE (bonus Multi-Cloud)

> Fecha: 2026-06-12 · Branch base: `chore/split-infra-repo`
> Repos afectados: `devops-project` (app, workflows, docs) y `circleguard-infra` (terraform, k8s, multicloud, dr).

## 1. Objetivo

Volver **real** el bonus Multi-Cloud (5 %) desplegando CircleGuard en un segundo
proveedor (GCP/GKE), de modo que los 4 requisitos del enunciado pasen de
"IaC + demo local" a evidencia real en dos clouds:

| Requisito del enunciado | Estado actual | Meta de este diseño |
|---|---|---|
| Desplegar la app en ≥2 proveedores | solo AKS real | app corriendo en GKE real |
| Estrategia de respaldo entre clouds | `velero-schedule.yaml` sin ejecutar | Velero AKS→bucket GCS + restore real en GKE |
| Balanceo de carga entre proveedores | HAProxy → 2 clusters kind | HAProxy → 2 IPs públicas reales (AKS + GKE) |
| Comparativa de rendimiento | números simulados | latencias reales AKS vs GKE |

**Enfoque elegido (A)**: espejo completo de AKS reutilizando el módulo
`gke-cluster` existente. **Backend de estado (elegido)**: reusar el backend
`azurerm` actual con un `key` nuevo (`gcp-dr.tfstate`).

## 2. Contexto del repo (verificado)

- Módulo `circleguard-infra/terraform/modules/gke-cluster/` ya existe: red + VPC +
  `google_container_cluster` (Standard, `remove_default_node_pool`, release channel
  REGULAR, workload identity) + node pools con spot/autoscaling. Interfaz espejo
  del módulo `aks-cluster`. Se reutiliza sin cambios de módulo.
- `terraform/multicloud.tf` quedó **huérfano**: instancia `gke_dr` y comenta
  "módulo `aks_prod` en `main.tf`", pero el refactor de estados aislados
  (`docs/ESTADO_PROYECTO.md` §0.1) eliminó ese `main.tf`. El patrón vigente son
  roots aislados `terraform/environments/{dev,stage,prod}/` con `backend.hcl` +
  `*.tfvars` propios y state separado.
- Manifiestos `k8s/dev/` ya incorporan los fixes obtenidos contra AKS:
  `enableServiceLinks: false`, initdb que crea las 5 DBs
  (`circleguard_{auth,identity,promotion,dashboard,form}`), `replicas: 1`,
  requests bajos, `QR_SECRET` ≥ 32 bytes. Reutilizables.
- **SealedSecret está cifrado con la clave del clúster AKS** → no descifra en GKE
  (mismo fallo que el PR #3 de Azure). Requiere re-sellado con el cert de GKE.
- Imágenes en DockerHub `stixk/circleguard-*` son **públicas** → GKE las baja sin
  credenciales nuevas.
- `k8s/dr/velero-schedule.yaml` está documentado pero nunca ejecutado.
- `multicloud/` corre la demo con dos clusters kind + HAProxy + números simulados.
- `cd-dev.yml`: build-push (matriz 8 servicios → DockerHub) + deploy-dev
  (kubeconfig desde secret base64, `kubectl apply`, `set image` a `dev-<sha>`,
  rollout status, smoke `/actuator/health`).

## 3. Componentes y cambios

### 3.1 Terraform — root aislado `terraform/environments/gcp-dr/`

Espejo de `environments/dev/`. Archivos:

- `providers.tf`: `required_providers.google ~> 5.30` + `backend "azurerm" {}`.
- `backend.hcl`: `resource_group_name=rg-terraform-state`,
  `storage_account_name=cgtf816751`, `container_name=tfstate`,
  **`key=gcp-dr.tfstate`**.
- `variables.tf`: `project_id`, `region`, `tags`.
- `gcp-dr.tfvars`: `project_id=<proyecto>`, `region="us-central1-a"`, tags.
- `main.tf`:
  - módulo `gke-cluster` con:
    - `region = "us-central1-a"` → cluster **zonal** (1 zona; el primer cluster
      zonal tiene el control-plane cubierto por el crédito de GKE y evita el
      triple de nodos de un cluster regional).
    - `cluster_name = "cg-gke-dr"`, `environment = "dr"`, `kubernetes_version`
      según canal REGULAR.
    - nodepool único `default`: `machine_type=e2-medium`, `node_count=2`,
      `enable_auto_scaling=true`, `min_count=1`, `max_count=3`,
      `disk_size_gb=50`, `spot=true` (FinOps: spot GCP ~ −80/91 %).
  - **Bucket GCS de Velero** `google_storage_bucket "cg-velero-dr"`
    (`location=US`, `uniform_bucket_level_access=true`, `force_destroy=true`).
  - **Service Account de Velero** `google_service_account "velero-backup"` +
    `google_storage_bucket_iam_member` con `roles/storage.admin` sobre el bucket
    + `google_service_account_key` (output sensible → archivo `gcp-velero-sa.json`
    para `velero install`).
- `outputs.tf`: `cluster_name`, `endpoint` (sensible), bucket name, SA email.

**Limpieza (parte del trabajo)**: eliminar el root huérfano del directorio
`terraform/` raíz — `multicloud.tf`, `providers.tf`, `variables.tf`, `outputs.tf` —
que referencian `aks_prod`/`main.tf` ya inexistente. La instancia GKE pasa a vivir
en el env root nuevo. (Si se prefiere conservar histórico, reemplazar
`multicloud.tf` por un comentario-puntero al nuevo root en lugar de borrarlo.)

### 3.2 App en GKE — overlay `k8s/gcp/`

- Copia de `k8s/dev/` con namespace **`circleguard-dr`** (nuevo manifest en
  `k8s/namespaces/`).
- **Secretos**: instalar el controller Bitnami Sealed Secrets en GKE
  (`kube-system`), obtener el cert (`kubeseal --fetch-cert --controller-name
  sealed-secrets-controller`) y re-sellar los valores mock → `k8s/gcp/sealed-secrets.yaml`.
  Script `scripts/seal-gcp-secrets.sh` espejo del de dev.
- Hereda los fixes de AKS (service links, initdb 5 DBs, replicas:1, QR_SECRET).
- **Gateway** `service-gateway-service.yaml` con `type: LoadBalancer` → IP pública
  (necesaria para el LB cruzado y el benchmark).
- Imágenes: `stixk/circleguard-*:dev-latest` (públicas).

### 3.3 CD — workflow nuevo `cd-gcp.yml` (repo `devops-project`)

Mirror de `cd-dev.yml`, con auth idiomático de GKE:

- Trigger: `workflow_dispatch` (el sitio DR no se redepliega en cada push).
- Reusa el job `build-push` existente o referencia las imágenes ya publicadas
  (no re-build: el sitio DR consume las mismas imágenes que dev).
- Job `deploy-gcp`:
  - `google-github-actions/auth@v2` con secret **`GCP_SA_KEY`**.
  - `google-github-actions/get-gke-credentials@v2` (`cluster_name=cg-gke-dr`,
    `location=us-central1-a`, `project_id` desde secret **`GCP_PROJECT`**).
  - `kubectl apply -f infra/k8s/namespaces/` + `-f infra/k8s/gcp/`.
  - rollout status de los 8 deployments en `circleguard-dr`.
  - smoke `/actuator/health` vía port-forward.
- Job `notify-failure` análogo (issue `cd-failure`).

GitHub environment nuevo `gcp` con los secrets `GCP_SA_KEY`, `GCP_PROJECT`.

### 3.4 Velero — respaldo cruzado real

- **En AKS** (cluster activo): `velero install --provider gcp
  --plugins velero/velero-plugin-for-gcp:v1.10.0 --bucket cg-velero-dr
  --secret-file ./gcp-velero-sa.json` (SA del paso 3.1).
- Aplicar `k8s/dr/velero-schedule.yaml` + un `velero backup create
  circleguard-ondemand --include-namespaces circleguard-dev` para evidencia
  inmediata (sin esperar al cron 02:00).
- **En GKE**: `velero install` (mismo bucket, mismo SA) + `velero restore create
  --from-backup circleguard-ondemand`. Verificar objetos restaurados en
  `circleguard-dr`.
- Documentar RPO (24 h, frecuencia del schedule) y RTO (< 1 h, cluster ya en
  caliente). Datos PostgreSQL: backup lógico al mismo bucket (reconstruible);
  Kafka/Redis reconstruibles (event replay/cache).

### 3.5 Balanceo entre proveedores — real

- Exponer el gateway en ambos clusters como `LoadBalancer` → 2 IPs públicas
  (AKS y GKE).
- Repuntar `multicloud/haproxy.cfg` a esas 2 IPs reales (en lugar de los
  NodePort de kind). Capturar round-robin + failover (`scale --replicas=0` del
  lado AKS) contra clusters reales.
- Documentar que en producción este rol lo cumple Azure Traffic Manager / GCP
  Cloud DNS con health checks equivalentes.

### 3.6 Comparativa de rendimiento — real

- Loop `curl` (N=20–50 req) + subset de Locust contra la IP pública del gateway
  en AKS y en GKE. Capturar latencia media/p95.
- Reemplazar la tabla simulada de `docs/BONUS_MULTICLOUD.md` §4 con números
  reales, anotando que la geografía (centralus vs us-central1) influye.

### 3.7 Documentación

- `docs/BONUS_MULTICLOUD.md`: reframe de "IaC + demo local" a "real en dos
  clouds"; números reales; evidencia del restore Velero; HAProxy a IPs reales.
- `docs/ESTADO_PROYECTO.md`: nueva sección **§0.2 Despliegue real en GCP**
  (espejo de §0.1 Azure: tabla de piezas, fixes aplicados, operación/FinOps).
- `docs/DESPLIEGUE_GCP.md`: nuevo, espejo de `DESPLIEGUE_AZURE.md`
  (prerrequisitos, `gcloud auth`, APIs, `terraform init/apply`, deploy, Velero).
- Memoria: crear nota de restricciones/decisiones GCP (zonal, spot OK, APIs a
  habilitar), enlazada desde `[[azure-students-deploy-constraints]]`.

## 4. Flujo de datos / despliegue (orden de ejecución)

1. Prerrequisitos GCP (usuario, interactivo): proyecto + billing; `gcloud auth
   login` + `application-default login`; `gcloud config set project`; habilitar
   `container.googleapis.com` y `compute.googleapis.com`; crear SA de CI + key →
   secrets `GCP_SA_KEY`, `GCP_PROJECT`.
2. `terraform init -backend-config=backend.hcl` + `apply` en
   `environments/gcp-dr/` → cluster `cg-gke-dr`, bucket `cg-velero-dr`, SA Velero.
3. `gcloud container clusters get-credentials cg-gke-dr --zone us-central1-a`.
4. Instalar Sealed Secrets en GKE + re-sellar → `k8s/gcp/sealed-secrets.yaml`.
5. `cd-gcp.yml` (`workflow_dispatch`) → app corriendo en `circleguard-dr`.
6. Velero install en AKS + backup on-demand; Velero install en GKE + restore.
7. Exponer gateways LoadBalancer; repuntar HAProxy a IPs reales; capturar
   round-robin/failover.
8. Benchmark AKS vs GKE; actualizar docs.

## 5. Manejo de errores y riesgos anticipados

- **GKE auth plugin en CI**: usar `get-gke-credentials` (instala
  `gke-gcloud-auth-plugin`) en vez de kubeconfig estático para evitar exec-plugin
  ausente.
- **SealedSecret no descifra en GKE**: re-sellar con el cert del cluster nuevo
  (paso 4) — no copiar el de dev.
- **Sobre-suscripción de nodos**: 8 servicios + datastores sobre 2× e2-medium;
  reusar `replicas:1` + requests bajos de `k8s/dev` (ya validados en B2s). Spot
  puede ser desalojado → autoscaling 1→3 amortigua.
- **Spot eviction durante demo**: tener `az/gcloud` para reescalar; documentar.
- **`force_destroy` del bucket**: solo para entorno de demo; documentar que en
  prod se quita.
- **Costo**: cluster zonal + 2 nodos spot + bucket pequeño → bajo; apagar entre
  demos (`gcloud container clusters resize cg-gke-dr --num-nodes=0` o destroy).
- **Persistencia**: postgres en `emptyDir` (igual que AKS) → initdb + Flyway
  auto-sanan; PVC solo si se requiere persistencia real.

## 6. Estrategia de pruebas / verificación

- `terraform init -backend=false && terraform validate` en `environments/gcp-dr/`
  → `Success! The configuration is valid`.
- `terraform plan` con creds GCP → recursos esperados (cluster, pools, bucket, SA).
- Tras deploy: `kubectl get pods -n circleguard-dr` → todos `Running`; smoke
  `/actuator/health` 200.
- Velero: `velero backup describe` Completed; `velero restore describe` Completed;
  objetos presentes en `circleguard-dr`.
- LB: salida del loop `curl` alternando `cloud:azure`/`cloud:gcp`; failover al
  tumbar un lado.
- Perf: tabla con latencias reales capturadas.

## 7. Fuera de alcance (YAGNI)

- HPA / multi-zona / regional en GKE (zonal basta para el bonus).
- ACR/Artifact Registry propio (se reutiliza DockerHub público).
- Stage/prod en GCP (solo el sitio DR `cg-gke-dr`).
- Global LB gestionado real (Traffic Manager/Cloud DNS) — documentado como
  equivalente prod, no provisionado.
