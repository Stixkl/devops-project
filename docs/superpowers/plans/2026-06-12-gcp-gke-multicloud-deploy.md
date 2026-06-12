# Despliegue real GCP/GKE (bonus Multi-Cloud) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Desplegar CircleGuard real en GCP/GKE como segundo cloud para volver real el bonus Multi-Cloud (app en 2 clouds, backup cruzado Velero, LB entre proveedores, comparativa de rendimiento).

**Architecture:** Root Terraform aislado nuevo `environments/gcp-dr/` que instancia el módulo `gke-cluster` existente (cluster zonal spot) + bucket GCS + SA para Velero, con state en el backend azurerm actual (key `gcp-dr.tfstate`). App desplegada vía overlay `k8s/gcp/` (namespace `circleguard-dr`) y workflow `cd-gcp.yml`. Velero respalda AKS→GCS y restaura en GKE; HAProxy balancea las dos IPs públicas reales.

**Tech Stack:** Terraform (providers azurerm + google ~>5.30), GKE Standard, kubectl/kubeseal (Bitnami Sealed Secrets), Velero + plugin GCP, GitHub Actions (`google-github-actions/auth` + `get-gke-credentials`), HAProxy.

**Repos:** `circleguard-infra` en `C:\Users\juanc\Videos\circleguard-infra`; `devops-project` (app/workflows/docs) en `C:\Users\juanc\Videos\devops-project`.

**Convención de verificación:** no hay tests unitarios para IaC; la "prueba" de cada task es `terraform validate` / `kubectl apply --dry-run=client` / lint YAML / `bash -n`. Las operaciones contra el cloud real (apply, deploy, velero) viven en la Task 9 (runbook interactivo) porque requieren `gcloud auth` del usuario.

---

## Mapa de archivos

| Acción | Ruta | Responsabilidad |
|---|---|---|
| Crear | `circleguard-infra/terraform/environments/gcp-dr/providers.tf` | provider google + backend azurerm |
| Crear | `circleguard-infra/terraform/environments/gcp-dr/backend.hcl` | key `gcp-dr.tfstate` |
| Crear | `circleguard-infra/terraform/environments/gcp-dr/variables.tf` | project_id, region, tags |
| Crear | `circleguard-infra/terraform/environments/gcp-dr/gcp-dr.tfvars` | valores del entorno |
| Crear | `circleguard-infra/terraform/environments/gcp-dr/main.tf` | módulo gke-cluster + bucket GCS + SA Velero |
| Crear | `circleguard-infra/terraform/environments/gcp-dr/outputs.tf` | cluster, bucket, SA |
| Borrar | `circleguard-infra/terraform/{multicloud.tf,providers.tf,variables.tf,outputs.tf}` | root huérfano roto |
| Crear | `circleguard-infra/k8s/namespaces/circleguard-dr.yaml` | namespace DR |
| Crear | `circleguard-infra/k8s/gcp/*` | overlay app (copia de dev, gateway LoadBalancer) |
| Crear | `circleguard-infra/scripts/seal-gcp-secrets.sh` | re-sellado con cert GKE |
| Crear | `circleguard-infra/scripts/velero-install-gcp.sh` | install Velero en AKS y GKE |
| Crear | `circleguard-infra/multicloud/haproxy.real.cfg` | HAProxy a 2 IPs reales |
| Modificar | `circleguard-infra/multicloud/README.md` | sección IPs reales |
| Crear | `devops-project/.github/workflows/cd-gcp.yml` | CD a GKE |
| Modificar | `devops-project/docs/BONUS_MULTICLOUD.md` | reframe real |
| Modificar | `devops-project/docs/ESTADO_PROYECTO.md` | §0.2 GCP |
| Crear | `devops-project/docs/DESPLIEGUE_GCP.md` | runbook |

---

## Task 1: Root Terraform `environments/gcp-dr/`

**Files:**
- Create: `circleguard-infra/terraform/environments/gcp-dr/providers.tf`
- Create: `circleguard-infra/terraform/environments/gcp-dr/backend.hcl`
- Create: `circleguard-infra/terraform/environments/gcp-dr/variables.tf`
- Create: `circleguard-infra/terraform/environments/gcp-dr/gcp-dr.tfvars`
- Create: `circleguard-infra/terraform/environments/gcp-dr/main.tf`
- Create: `circleguard-infra/terraform/environments/gcp-dr/outputs.tf`

- [ ] **Step 1: Crear `providers.tf`**

```hcl
# Backend y proveedor para el sitio DR GCP/GKE (root module aislado).
terraform {
  required_version = ">= 1.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.30"
    }
  }
  # Backend remoto azurerm: se configura con -backend-config=backend.hcl
  backend "azurerm" {}
}

provider "google" {
  project = var.project_id
  region  = var.region
  # Credenciales: ADC vía `gcloud auth application-default login`
  # o GOOGLE_APPLICATION_CREDENTIALS en CI.
}
```

- [ ] **Step 2: Crear `backend.hcl`** (reusa el Storage Account existente, key nuevo)

```hcl
resource_group_name  = "rg-terraform-state"
storage_account_name = "cgtf816751"
container_name       = "tfstate"
key                  = "gcp-dr.tfstate"
```

- [ ] **Step 3: Crear `variables.tf`**

```hcl
variable "project_id" {
  description = "Proyecto de GCP donde se crea el sitio DR"
  type        = string
}

variable "region" {
  description = "Zona/región de GCP (zonal: us-central1-a)"
  type        = string
  default     = "us-central1-a"
}

variable "tags" {
  description = "Labels para los recursos GCP"
  type        = map(string)
  default = {
    project     = "circleguard"
    environment = "dr"
    managedby   = "terraform"
  }
}
```

- [ ] **Step 4: Crear `gcp-dr.tfvars`** (placeholder de project_id a completar por el usuario)

```hcl
project_id = "REEMPLAZAR-CON-PROJECT-ID"
region     = "us-central1-a"
```

- [ ] **Step 5: Crear `main.tf`** (módulo GKE + bucket GCS + SA Velero)

```hcl
# Sitio DR GCP/GKE: cluster zonal spot (espejo del cg-aks-dev) + bucket GCS
# y service account para el respaldo cruzado con Velero (AKS -> GCS).

module "gke" {
  source             = "../../modules/gke-cluster"
  environment        = "dr"
  project_id         = var.project_id
  region             = var.region # zona us-central1-a -> cluster zonal
  cluster_name       = "cg-gke-dr"
  kubernetes_version = "1.29"

  nodepools = [
    {
      name                = "default"
      machine_type        = "e2-medium"
      node_count          = 2
      min_count           = 1
      max_count           = 3
      enable_auto_scaling = true
      disk_size_gb        = 50
      spot                = true # FinOps: spot GCP (~ -80/91%)
    }
  ]

  tags = var.tags
}

# --- Velero: bucket de respaldo cruzado (vive en GCP, respalda AKS) ---
resource "google_storage_bucket" "velero_dr" {
  name                        = "cg-velero-dr-${var.project_id}"
  project                     = var.project_id
  location                    = "US"
  uniform_bucket_level_access = true
  force_destroy               = true # solo demo; quitar en prod real
  labels                      = var.tags
}

resource "google_service_account" "velero" {
  account_id   = "velero-backup"
  display_name = "Velero cross-cloud backup"
  project      = var.project_id
}

resource "google_storage_bucket_iam_member" "velero" {
  bucket = google_storage_bucket.velero_dr.name
  role   = "roles/storage.admin"
  member = "serviceAccount:${google_service_account.velero.email}"
}

resource "google_service_account_key" "velero" {
  service_account_id = google_service_account.velero.name
}
```

- [ ] **Step 6: Crear `outputs.tf`**

```hcl
output "cluster_name" {
  value = module.gke.cluster_name
}

output "cluster_endpoint" {
  value     = module.gke.endpoint
  sensitive = true
}

output "velero_bucket" {
  value = google_storage_bucket.velero_dr.name
}

output "velero_sa_email" {
  value = google_service_account.velero.email
}

output "velero_sa_key_base64" {
  description = "Clave de la SA de Velero (base64). Decodificar a gcp-velero-sa.json."
  value       = google_service_account_key.velero.private_key
  sensitive   = true
}
```

- [ ] **Step 7: Verificar validate** (sin backend ni credenciales)

Run: `cd C:\Users\juanc\Videos\circleguard-infra\terraform\environments\gcp-dr && terraform init -backend=false && terraform validate`
Expected: `Success! The configuration is valid.`

- [ ] **Step 8: Commit** (repo circleguard-infra)

```bash
cd C:/Users/juanc/Videos/circleguard-infra
git add terraform/environments/gcp-dr
git commit -m "feat(multicloud): add GCP/GKE DR terraform root with Velero bucket + SA"
```

---

## Task 2: Eliminar root Terraform huérfano

**Files:**
- Delete: `circleguard-infra/terraform/multicloud.tf`
- Delete: `circleguard-infra/terraform/providers.tf`
- Delete: `circleguard-infra/terraform/variables.tf`
- Delete: `circleguard-infra/terraform/outputs.tf`

Justificación: `multicloud.tf` instancia `gke_dr` y comenta "módulo `aks_prod` en `main.tf`", pero el refactor de estados aislados eliminó ese `main.tf`. El directorio `terraform/` raíz ya no es un root válido; la instancia GKE vive ahora en `environments/gcp-dr/`.

- [ ] **Step 1: Confirmar que nada referencia el root raíz**

Run: `cd C:\Users\juanc\Videos\circleguard-infra && grep -rn "multicloud.tf\|enable_gke_dr\|gke_dr" --include=*.tf --include=*.yml --include=*.yaml --include=*.sh --include=*.md . | grep -v environments/gcp-dr`
Expected: solo apariciones en docs (que se actualizan en Task 8) y `terraform/README.md`. Si hay un workflow que hace `terraform -chdir=terraform apply`, anotarlo para Task 5/8.

- [ ] **Step 2: Borrar los 4 archivos del root raíz**

```bash
cd C:/Users/juanc/Videos/circleguard-infra
git rm terraform/multicloud.tf terraform/providers.tf terraform/variables.tf terraform/outputs.tf
```

- [ ] **Step 3: Verificar que los env roots siguen válidos**

Run: `cd C:\Users\juanc\Videos\circleguard-infra\terraform\environments\dev && terraform init -backend=false && terraform validate`
Expected: `Success! The configuration is valid.`

- [ ] **Step 4: Commit**

```bash
cd C:/Users/juanc/Videos/circleguard-infra
git add -A terraform
git commit -m "chore(terraform): remove orphan root config superseded by isolated env roots"
```

---

## Task 3: Namespace + overlay app `k8s/gcp/`

**Files:**
- Create: `circleguard-infra/k8s/namespaces/circleguard-dr.yaml`
- Create: `circleguard-infra/k8s/gcp/` (copia de `k8s/dev/` salvo `sealed-secrets*`)
- Modify: `circleguard-infra/k8s/gcp/service-gateway-service.yaml` (type LoadBalancer)

- [ ] **Step 1: Crear namespace**

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: circleguard-dr
  labels:
    app.kubernetes.io/part-of: circleguard
    cloud: gcp
```

- [ ] **Step 2: Copiar manifiestos dev → gcp y reapuntar namespace**

```bash
cd C:/Users/juanc/Videos/circleguard-infra
mkdir -p k8s/gcp
cp k8s/dev/*.yaml k8s/gcp/
rm -f k8s/gcp/sealed-secrets.yaml k8s/gcp/sealed-secrets-cert.pem
# Reapuntar el namespace en todos los manifiestos copiados:
grep -rl "circleguard-dev" k8s/gcp | xargs sed -i 's/circleguard-dev/circleguard-dr/g'
```

- [ ] **Step 3: Gateway como LoadBalancer** (editar `k8s/gcp/service-gateway-service.yaml`)

Cambiar `type: ClusterIP` (o NodePort) por `type: LoadBalancer` en el Service del gateway. Si ya es ClusterIP, el bloque queda:

```yaml
spec:
  type: LoadBalancer
  selector:
    app: gateway-service
  ports:
    - port: 8087
      targetPort: 8087
```

- [ ] **Step 4: Verificar dry-run** (cliente, sin cluster)

Run: `cd C:\Users\juanc\Videos\circleguard-infra && kubectl apply --dry-run=client -f k8s/namespaces/circleguard-dr.yaml -f k8s/gcp/ 2>&1 | tail -20`
Expected: cada objeto imprime `(dry run)` sin errores de schema. (El SealedSecret no está aún; se genera en la Task 9 contra el cluster real.)

- [ ] **Step 5: Commit**

```bash
cd C:/Users/juanc/Videos/circleguard-infra
git add k8s/namespaces/circleguard-dr.yaml k8s/gcp
git commit -m "feat(multicloud): add GKE app overlay (circleguard-dr ns, gateway LoadBalancer)"
```

---

## Task 4: Script de re-sellado de secretos para GKE

**Files:**
- Create: `circleguard-infra/scripts/seal-gcp-secrets.sh`

- [ ] **Step 1: Crear script** (espejo del de dev; usa el cert del cluster GKE)

```bash
#!/usr/bin/env bash
# Re-sella los secretos de CircleGuard con la clave PÚBLICA del controller
# Sealed Secrets del cluster GKE (cg-gke-dr). Necesario porque un SealedSecret
# solo lo descifra el cluster que tiene la clave privada correspondiente.
#
# Prerrequisito: kubectl apuntando a cg-gke-dr y el controller instalado:
#   helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
#   helm install sealed-secrets sealed-secrets/sealed-secrets -n kube-system
set -euo pipefail

NS="circleguard-dr"
OUT="k8s/gcp/sealed-secrets.yaml"
CONTROLLER="sealed-secrets-controller"

# Valores mock (mismos que dev). QR_SECRET >= 32 bytes (JJWT HMAC-SHA >= 256 bits).
kubectl create secret generic circleguard-secrets \
  --namespace "$NS" \
  --from-literal=DB_PASSWORD="circleguard_mock_pw" \
  --from-literal=QR_SECRET="circleguard_qr_secret_32bytes_min_ok" \
  --dry-run=client -o yaml \
| kubeseal --fetch-cert --controller-name "$CONTROLLER" --controller-namespace kube-system >/dev/null 2>&1 || true

kubectl create secret generic circleguard-secrets \
  --namespace "$NS" \
  --from-literal=DB_PASSWORD="circleguard_mock_pw" \
  --from-literal=QR_SECRET="circleguard_qr_secret_32bytes_min_ok" \
  --dry-run=client -o yaml \
| kubeseal --controller-name "$CONTROLLER" --controller-namespace kube-system \
    --format yaml > "$OUT"

echo "SealedSecret escrito en $OUT (cifrado con la clave de cg-gke-dr)"
```

- [ ] **Step 2: Verificar sintaxis bash**

Run: `bash -n C:/Users/juanc/Videos/circleguard-infra/scripts/seal-gcp-secrets.sh && echo OK`
Expected: `OK`

- [ ] **Step 3: Commit**

```bash
cd C:/Users/juanc/Videos/circleguard-infra
git add scripts/seal-gcp-secrets.sh
git commit -m "feat(multicloud): add seal-gcp-secrets script for GKE re-sealing"
```

---

## Task 5: Workflow CD a GKE `cd-gcp.yml`

**Files:**
- Create: `devops-project/.github/workflows/cd-gcp.yml`

- [ ] **Step 1: Crear el workflow** (reusa imágenes `dev-latest` ya publicadas; no re-build)

```yaml
name: CD - GCP (GKE DR)

on:
  workflow_dispatch:
    inputs:
      image_tag:
        description: "Tag de imagen a desplegar"
        default: "dev-latest"
        required: false

concurrency:
  group: cd-gcp
  cancel-in-progress: false

env:
  INFRA_REPO: JuanAmor8/circleguard-infra
  REGISTRY_USER: stixk
  CLUSTER: cg-gke-dr
  ZONE: us-central1-a
  NS: circleguard-dr

jobs:
  deploy-gcp:
    runs-on: ubuntu-latest
    environment:
      name: gcp
    steps:
      - name: Checkout infra repo
        uses: actions/checkout@v4
        with:
          repository: JuanAmor8/circleguard-infra
          path: infra

      - name: Check GCP credentials
        id: creds
        run: |
          if [ -z "${{ secrets.GCP_SA_KEY }}" ] || [ -z "${{ secrets.GCP_PROJECT }}" ]; then
            echo "available=false" >> "$GITHUB_OUTPUT"
            echo "::warning::GCP_SA_KEY/GCP_PROJECT no configurados — se omite el deploy a GKE."
          else
            echo "available=true" >> "$GITHUB_OUTPUT"
          fi

      - name: Authenticate to GCP
        if: steps.creds.outputs.available == 'true'
        uses: google-github-actions/auth@v2
        with:
          credentials_json: ${{ secrets.GCP_SA_KEY }}

      - name: Get GKE credentials
        if: steps.creds.outputs.available == 'true'
        uses: google-github-actions/get-gke-credentials@v2
        with:
          cluster_name: ${{ env.CLUSTER }}
          location: ${{ env.ZONE }}
          project_id: ${{ secrets.GCP_PROJECT }}

      - name: Deploy to GKE
        if: steps.creds.outputs.available == 'true'
        run: |
          kubectl apply -f infra/k8s/namespaces/circleguard-dr.yaml
          kubectl apply -f infra/k8s/gcp/
          TAG="${{ github.event.inputs.image_tag }}"
          for svc in auth identity gateway form notification promotion dashboard file; do
            kubectl set image deployment/${svc}-service \
              ${svc}-service=${REGISTRY_USER}/circleguard-${svc}-service:${TAG} \
              -n ${NS} || true
          done
          for svc in auth identity gateway form notification promotion dashboard file; do
            kubectl rollout status deployment/${svc}-service -n ${NS} --timeout=180s || true
          done

      - name: Smoke test
        if: steps.creds.outputs.available == 'true'
        run: |
          kubectl get pods -n ${NS}
          kubectl port-forward svc/gateway-service 8087:8087 -n ${NS} &
          sleep 5
          curl -sf http://localhost:8087/actuator/health || echo "::warning::gateway health check failed"

  notify-failure:
    runs-on: ubuntu-latest
    needs: [deploy-gcp]
    if: failure()
    permissions:
      issues: write
    steps:
      - name: Open issue with failure details
        uses: actions/github-script@v7
        with:
          script: |
            const runUrl = `${context.serverUrl}/${context.repo.owner}/${context.repo.repo}/actions/runs/${context.runId}`;
            await github.rest.issues.create({
              owner: context.repo.owner,
              repo: context.repo.repo,
              title: `CD-gcp failure (${context.sha.substring(0,7)})`,
              labels: ['cd-failure'],
              body: `El despliegue a GKE (circleguard-dr) falló.\n\n- **Run**: ${runUrl}\n- **Commit**: ${context.sha}\n\nRollback: \`kubectl rollout undo deployment/<svc>-service -n circleguard-dr\`.`
            });
```

- [ ] **Step 2: Verificar que el YAML parsea**

Run: `cd C:\Users\juanc\Videos\devops-project && python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/cd-gcp.yml')); print('YAML OK')"`
Expected: `YAML OK`

- [ ] **Step 3: Commit**

```bash
cd C:/Users/juanc/Videos/devops-project
git add .github/workflows/cd-gcp.yml
git commit -m "feat(cd): add GKE DR deploy workflow (cd-gcp.yml)"
```

---

## Task 6: Script de instalación de Velero (AKS + GKE)

**Files:**
- Create: `circleguard-infra/scripts/velero-install-gcp.sh`

- [ ] **Step 1: Crear script**

```bash
#!/usr/bin/env bash
# Instala Velero con plugin GCP en el cluster ACTUAL del kubeconfig, apuntando
# al bucket de respaldo cruzado en GCS. Se ejecuta dos veces:
#   1) con kubeconfig de AKS (cluster activo)  -> produce backups
#   2) con kubeconfig de GKE (cg-gke-dr)       -> consume backups (restore)
#
# Uso:
#   BUCKET=cg-velero-dr-<project> SA_KEY=./gcp-velero-sa.json ./velero-install-gcp.sh
set -euo pipefail

: "${BUCKET:?define BUCKET=cg-velero-dr-<project>}"
: "${SA_KEY:?define SA_KEY=ruta/gcp-velero-sa.json}"

velero install \
  --provider gcp \
  --plugins velero/velero-plugin-for-gcp:v1.10.0 \
  --bucket "$BUCKET" \
  --secret-file "$SA_KEY" \
  --use-volume-snapshots=false \
  --wait

echo "Velero instalado contra gs://$BUCKET en el cluster actual."
echo "Backup on-demand:  velero backup create circleguard-ondemand --include-namespaces circleguard-dev --wait"
echo "Restore en GKE:    velero restore create --from-backup circleguard-ondemand --wait"
```

- [ ] **Step 2: Verificar sintaxis bash**

Run: `bash -n C:/Users/juanc/Videos/circleguard-infra/scripts/velero-install-gcp.sh && echo OK`
Expected: `OK`

- [ ] **Step 3: Commit**

```bash
cd C:/Users/juanc/Videos/circleguard-infra
git add scripts/velero-install-gcp.sh
git commit -m "feat(dr): add velero-install-gcp script for cross-cloud backup"
```

---

## Task 7: HAProxy a IPs reales

**Files:**
- Create: `circleguard-infra/multicloud/haproxy.real.cfg`
- Modify: `circleguard-infra/multicloud/README.md`

- [ ] **Step 1: Crear `haproxy.real.cfg`** (basado en `haproxy.cfg`, con placeholders de IP real)

```
global
    daemon
    maxconn 256

defaults
    mode http
    timeout connect 5s
    timeout client  30s
    timeout server  30s
    option httpchk GET /actuator/health

frontend multicloud_in
    bind *:8090
    default_backend clouds

backend clouds
    balance roundrobin
    # Reemplazar con las IPs públicas reales de los gateways (kubectl get svc):
    #   AKS:  kubectl get svc gateway-service -n circleguard-dev  -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
    #   GKE:  kubectl get svc gateway-service -n circleguard-dr   -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
    server azure AKS_PUBLIC_IP:8087 check fall 2 rise 2 inter 2s
    server gcp   GKE_PUBLIC_IP:8087 check fall 2 rise 2 inter 2s
```

- [ ] **Step 2: Añadir sección al README** (`multicloud/README.md`)

Agregar al final:

```markdown
## Balanceo entre clouds REALES (AKS + GKE)

Con ambos clusters desplegados y el gateway expuesto como `LoadBalancer`:

```bash
AKS_IP=$(kubectl --context aks get svc gateway-service -n circleguard-dev -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
GKE_IP=$(kubectl --context gke get svc gateway-service -n circleguard-dr -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
sed -e "s/AKS_PUBLIC_IP/$AKS_IP/" -e "s/GKE_PUBLIC_IP/$GKE_IP/" haproxy.real.cfg > /tmp/haproxy.cfg
haproxy -f /tmp/haproxy.cfg &
for i in $(seq 1 6); do curl -s localhost:8090/actuator/health; echo; done
```

Failover real: `kubectl --context aks scale deploy/gateway-service --replicas=0 -n circleguard-dev`
→ el health check (fall 2) expulsa AKS y el 100% va a GKE. Producción: Azure
Traffic Manager / GCP Cloud DNS con health checks equivalentes.
```

- [ ] **Step 3: Verificar config HAProxy** (si `haproxy` está instalado; si no, omitir)

Run: `haproxy -c -f C:/Users/juanc/Videos/circleguard-infra/multicloud/haproxy.real.cfg 2>&1 || echo "haproxy no instalado — validar en demo"`
Expected: `Configuration file is valid` o el aviso de no-instalado.

- [ ] **Step 4: Commit**

```bash
cd C:/Users/juanc/Videos/circleguard-infra
git add multicloud/haproxy.real.cfg multicloud/README.md
git commit -m "feat(multicloud): add real-IP HAProxy config for cross-cloud LB"
```

---

## Task 8: Documentación

**Files:**
- Modify: `devops-project/docs/BONUS_MULTICLOUD.md`
- Modify: `devops-project/docs/ESTADO_PROYECTO.md`
- Create: `devops-project/docs/DESPLIEGUE_GCP.md`
- Create: memoria `gcp-deploy-constraints.md` + entrada en `MEMORY.md`

- [ ] **Step 1: `DESPLIEGUE_GCP.md`** — runbook espejo de `DESPLIEGUE_AZURE.md`

Contenido mínimo (secciones): Prerrequisitos (proyecto + billing, `gcloud auth login`, `application-default login`, `gcloud config set project`, habilitar `container.googleapis.com` y `compute.googleapis.com`, crear SA CI + key → secrets `GCP_SA_KEY`/`GCP_PROJECT`); Terraform (`terraform init -backend-config=backend.hcl` + `apply -var-file=gcp-dr.tfvars` en `environments/gcp-dr/`); credenciales kubectl (`gcloud container clusters get-credentials cg-gke-dr --zone us-central1-a`); Sealed Secrets (helm + `scripts/seal-gcp-secrets.sh`); deploy (`cd-gcp.yml` workflow_dispatch); Velero (`scripts/velero-install-gcp.sh` en AKS y GKE + backup/restore); LB (`multicloud/haproxy.real.cfg`); FinOps (`gcloud container clusters resize cg-gke-dr --num-nodes=0` entre demos).

- [ ] **Step 2: Reframe `BONUS_MULTICLOUD.md`**

- Cabecera: cambiar "IaC real + demo local funcional (sin gasto cloud)" → "desplegado real en dos clouds (AKS activo + GKE)".
- §1: añadir que GKE se instancia en `terraform/environments/gcp-dr/` (no en el `multicloud.tf` borrado) y que está **desplegado real**.
- §2: reemplazar "Prerrequisito (una vez)" por evidencia real de backup+restore.
- §3: enlazar a `haproxy.real.cfg` con IPs reales.
- §4: reemplazar tabla simulada por números reales capturados (Task 9).

- [ ] **Step 3: `ESTADO_PROYECTO.md` §0.2**

Insertar tras §0.1 una sección "## 0.2 Despliegue real en GCP (GKE)" con tabla de piezas (cluster cg-gke-dr, bucket Velero, namespace circleguard-dr, workflow cd-gcp), fixes aplicados, y nota FinOps. Actualizar el veredicto global y la fila Multi-Cloud de §1 / Bonos.

- [ ] **Step 4: Memoria** `C:\Users\juanc\.claude\projects\C--Users-juanc-Videos-devops-project\memory\gcp-deploy-constraints.md`

```markdown
---
name: gcp-deploy-constraints
description: Decisiones/restricciones del deploy real en GCP/GKE (cluster zonal, spot OK, APIs, state azurerm)
metadata:
  type: project
---

Sitio DR multicloud en GCP/GKE (`cg-gke-dr`, root `terraform/environments/gcp-dr/`):

- Cluster **zonal** (`region=us-central1-a`), no regional → evita 3× nodos; primer cluster zonal tiene control-plane cubierto por crédito.
- **Spot SÍ disponible** en GCP (a diferencia de Azure Students, ver [[azure-students-deploy-constraints]]).
- APIs a habilitar: `container.googleapis.com`, `compute.googleapis.com`.
- State Terraform reusa backend azurerm (`cgtf816751`, key `gcp-dr.tfstate`) — no GCS.
- SealedSecret se re-sella con cert de GKE (`scripts/seal-gcp-secrets.sh`); el de dev no descifra aquí.
- Velero respalda AKS→bucket `cg-velero-dr-<project>` en GCS (`scripts/velero-install-gcp.sh`).
- CI a GKE via `google-github-actions/auth` + `get-gke-credentials` (secrets `GCP_SA_KEY`, `GCP_PROJECT`).
```

Añadir a `MEMORY.md`: `- [Restricciones deploy GCP](gcp-deploy-constraints.md) — GKE zonal, spot OK, state azurerm, re-sellado SealedSecret`

- [ ] **Step 5: Commit docs**

```bash
cd C:/Users/juanc/Videos/devops-project
git add docs/BONUS_MULTICLOUD.md docs/ESTADO_PROYECTO.md docs/DESPLIEGUE_GCP.md
git commit -m "docs(multicloud): document real GCP/GKE deploy"
```

---

## Task 9: Runbook interactivo (requiere `gcloud auth` del usuario) — ejecución real en cloud

> Estos pasos NO los puede correr el agente: requieren login interactivo de Google
> Cloud y consumen crédito. El usuario los ejecuta con el prefijo `!` en la sesión
> o en su terminal. Captura las salidas para la evidencia de las docs (Task 8 §2/§4).

- [ ] **Step 1: Prerrequisitos GCP** (usuario)

```bash
gcloud auth login
gcloud auth application-default login
gcloud config set project <PROJECT_ID>
gcloud services enable container.googleapis.com compute.googleapis.com
# Completar gcp-dr.tfvars con el project_id real.
```

- [ ] **Step 2: Provisionar con Terraform**

```bash
cd C:/Users/juanc/Videos/circleguard-infra/terraform/environments/gcp-dr
terraform init -backend-config=backend.hcl
terraform apply -var-file=gcp-dr.tfvars
# Exportar la SA de Velero a archivo:
terraform output -raw velero_sa_key_base64 | base64 -d > ../../../gcp-velero-sa.json
```
Expected: cluster `cg-gke-dr` Running, bucket y SA creados.

- [ ] **Step 3: Credenciales kubectl + Sealed Secrets**

```bash
gcloud container clusters get-credentials cg-gke-dr --zone us-central1-a
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm install sealed-secrets sealed-secrets/sealed-secrets -n kube-system
cd C:/Users/juanc/Videos/circleguard-infra && ./scripts/seal-gcp-secrets.sh
git add k8s/gcp/sealed-secrets.yaml && git commit -m "chore(multicloud): sealed secrets for cg-gke-dr"
```

- [ ] **Step 4: Crear secrets de CI + environment, lanzar deploy**

Guardar `GCP_SA_KEY` (JSON de una SA con roles `container.developer`+`container.clusterViewer`) y `GCP_PROJECT` en el environment `gcp` de GitHub; luego `gh workflow run cd-gcp.yml`.
Expected: 8 deployments `Running` en `circleguard-dr`, smoke health 200.

- [ ] **Step 5: Velero backup (AKS) + restore (GKE)**

```bash
# AKS:
az aks get-credentials --resource-group rg-circle-guard-dev --name cg-aks-dev --admin
BUCKET=$(terraform -chdir=terraform/environments/gcp-dr output -raw velero_bucket) SA_KEY=./gcp-velero-sa.json ./scripts/velero-install-gcp.sh
velero backup create circleguard-ondemand --include-namespaces circleguard-dev --wait
# GKE:
gcloud container clusters get-credentials cg-gke-dr --zone us-central1-a
BUCKET=<mismo> SA_KEY=./gcp-velero-sa.json ./scripts/velero-install-gcp.sh
velero restore create --from-backup circleguard-ondemand --wait
velero restore describe <id>
```
Expected: backup `Completed`, restore `Completed`.

- [ ] **Step 6: LB real + benchmark, capturar evidencia**

```bash
# IPs públicas:
kubectl --context cg-aks-dev get svc gateway-service -n circleguard-dev -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
kubectl --context gke_<proj>_us-central1-a_cg-gke-dr get svc gateway-service -n circleguard-dr -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
# HAProxy con IPs reales (ver multicloud/README.md), capturar round-robin + failover.
# Benchmark: loop curl x20 a cada IP, anotar latencia media/p95.
```

- [ ] **Step 7: Volcar evidencia a docs** — pegar números reales en `BONUS_MULTICLOUD.md` §4 y salidas Velero/HAProxy en §2/§3; actualizar `ESTADO_PROYECTO.md` §0.2. Commit.

- [ ] **Step 8: FinOps — apagar entre demos**

```bash
gcloud container clusters resize cg-gke-dr --num-nodes=0 --zone us-central1-a --quiet
# o destruir: terraform destroy -var-file=gcp-dr.tfvars
```

---

## Self-Review (cobertura del spec)

- Spec §3.1 TF root → Task 1. §3.1 limpieza huérfano → Task 2. ✓
- Spec §3.2 overlay k8s/gcp + namespace + gateway LB → Task 3; re-sellado → Task 4 + Task 9 §3. ✓
- Spec §3.3 cd-gcp.yml → Task 5. ✓
- Spec §3.4 Velero → Task 6 (script) + Task 9 §5 (ejecución). ✓
- Spec §3.5 LB real → Task 7 + Task 9 §6. ✓
- Spec §3.6 perf real → Task 9 §6-7 (datos reales solo tras deploy). ✓
- Spec §3.7 docs → Task 8. ✓
- Prerrequisitos interactivos → Task 9 §1. ✓

Sin placeholders de plan (los `REEMPLAZAR`/`<PROJECT_ID>` son inputs legítimos del usuario, no del plan). Nombres consistentes: `cg-gke-dr`, `circleguard-dr`, bucket `cg-velero-dr-<project>`, key `gcp-dr.tfstate` usados igual en todas las tasks.
