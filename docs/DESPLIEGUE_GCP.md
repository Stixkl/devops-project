# Despliegue en GCP (GKE) — Circle Guard

> Guía operativa del despliegue real del **sitio DR multi-cloud** en Google Kubernetes Engine (GKE), segundo proveedor del bonus Multi-Cloud (Azure/AKS activo + GCP/GKE).
> Estado: **artefactos IaC/CD listos y validados**; la ejecución contra el cloud real requiere credenciales GCP (sección 2) y se corre con el runbook de la sección 8.
> Última actualización: 2026-06-12.

---

## 1. Arquitectura del despliegue

- **Cluster**: `cg-gke-dr`, GKE Standard **zonal** en `us-central1-a`, 2× `e2-medium` **spot** con autoscaling 1→3 (FinOps: spot GCP ~ −80/91 %).
- **Estado de Terraform**: reusa el backend `azurerm` existente — Storage Account `cgtf816751`, container `tfstate`, key **`gcp-dr.tfstate`** (state aislado del de Azure).
- **Root Terraform**: `circleguard-infra/terraform/environments/gcp-dr/` (instancia el módulo `terraform/modules/gke-cluster`, espejo del de AKS).
- **Namespace de la app**: `circleguard-dr`.
- **Workloads**: mismos 8 microservicios + 5 datastores que dev (manifiestos en `k8s/gcp/`, copia de `k8s/dev/` con namespace reapuntado).
- **Imágenes**: `stixk/circleguard-<servicio>:dev-latest` (DockerHub público) — no se re-construye en el sitio DR.
- **Secretos**: Bitnami Sealed Secrets re-sellado con la clave del cluster GKE (`scripts/seal-gcp-secrets.sh`). El SealedSecret de dev NO sirve aquí (atado a la clave de cg-aks-dev).
- **Backup cruzado**: Velero en AKS respalda a un bucket **GCS** `cg-velero-dr-<project>`; Velero en GKE restaura desde ese bucket.
- **Balanceo entre clouds**: HAProxy (`multicloud/haproxy.real.cfg`) sobre las IPs públicas reales de los gateways AKS + GKE.

### Por qué zonal y no regional

Un cluster **zonal** (location = una zona, `us-central1-a`) usa una sola zona → evita triplicar los nodos de un cluster regional y el primer cluster zonal tiene el control-plane cubierto por el crédito de GKE. Suficiente para el sitio DR/demo.

---

## 2. Prerrequisitos

| Tool | Uso |
|---|---|
| `gcloud` | provisión / credenciales del cluster |
| `terraform` | aplicar el root `environments/gcp-dr/` |
| `kubectl` + `kubeseal` | aplicar manifests y sellar secretos |
| `velero` CLI | backup/restore cruzado |
| `gh` | secrets y workflows de GitHub |

Login y proyecto (interactivo):

```bash
gcloud auth login
gcloud auth application-default login          # ADC para Terraform
gcloud config set project <PROJECT_ID>
gcloud services enable container.googleapis.com compute.googleapis.com
```

Requiere un proyecto GCP con **billing habilitado** (free trial $300 / 90 días o crédito education).

---

## 3. Provisión con Terraform

```bash
cd circleguard-infra/terraform/environments/gcp-dr
# completar project_id en gcp-dr.tfvars
terraform init -backend-config=backend.hcl
terraform apply -var-file=gcp-dr.tfvars
```

Crea: cluster `cg-gke-dr`, nodepool spot, bucket `cg-velero-dr-<project>`, service account `velero-backup` con `roles/storage.admin` y su key.

Exportar la key de la SA de Velero a archivo:

```bash
terraform output -raw velero_sa_key_base64 | base64 -d > ../../../gcp-velero-sa.json
```

---

## 4. Credenciales kubectl + Sealed Secrets

```bash
gcloud container clusters get-credentials cg-gke-dr --zone us-central1-a
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm install sealed-secrets sealed-secrets/sealed-secrets -n kube-system
cd circleguard-infra && ./scripts/seal-gcp-secrets.sh
git add k8s/gcp/sealed-secrets.yaml k8s/gcp/sealed-secrets-cert.pem
git commit -m "chore(multicloud): sealed secrets for cg-gke-dr"
```

---

## 5. Desplegar la app

Configurar en el **GitHub environment `gcp`** (repo `devops-project`) los secrets:

- `GCP_SA_KEY`: JSON de una SA con `roles/container.developer` + `roles/container.clusterViewer`.
- `GCP_PROJECT`: id del proyecto.

Lanzar el workflow:

```bash
gh workflow run cd-gcp.yml
```

Resultado esperado: 8 deployments `Running` en `circleguard-dr`, smoke `/actuator/health` 200.

> Alternativa manual sin CI: `kubectl apply -f k8s/namespaces/namespace-dr.yaml -f k8s/gcp/`.

---

## 6. Backup cruzado con Velero

```bash
# En AKS (cluster activo):
az aks get-credentials --resource-group rg-circle-guard-dev --name cg-aks-dev --admin
BUCKET=$(cd circleguard-infra/terraform/environments/gcp-dr && terraform output -raw velero_bucket) \
  SA_KEY=$PWD/gcp-velero-sa.json ./scripts/velero-install-gcp.sh
kubectl apply -f k8s/dr/velero-schedule.yaml
velero backup create circleguard-ondemand --include-namespaces circleguard-dev --wait

# En GKE (sitio pasivo):
gcloud container clusters get-credentials cg-gke-dr --zone us-central1-a
BUCKET=<mismo> SA_KEY=$PWD/gcp-velero-sa.json ./scripts/velero-install-gcp.sh
velero restore create --from-backup circleguard-ondemand --wait
velero restore describe <id>
```

Objetivos: **RPO 24 h** (schedule diario), **RTO < 1 h** (GKE ya en caliente).

---

## 7. Balanceo entre clouds + comparativa

IPs públicas y HAProxy real: ver `circleguard-infra/multicloud/README.md` (sección "Balanceo entre clouds REALES"). Benchmark: loop `curl` (N=20) contra cada IP, anotar latencia media/p95 en `docs/BONUS_MULTICLOUD.md` §4.

---

## 8. FinOps — apagar entre demos

```bash
gcloud container clusters resize cg-gke-dr --num-nodes=0 --zone us-central1-a --quiet
# o destruir por completo:
cd circleguard-infra/terraform/environments/gcp-dr && terraform destroy -var-file=gcp-dr.tfvars
```

---

## 9. Fallos anticipados

- **kubectl en CI sin auth-plugin**: `get-gke-credentials` instala `gke-gcloud-auth-plugin` automáticamente — por eso CI usa esa action y no un kubeconfig estático.
- **SealedSecret no descifra**: re-sellar con el cert de GKE (sección 4); no copiar el de dev.
- **Spot eviction durante demo**: autoscaling 1→3 amortigua; reescalar manualmente si hace falta.
- **postgres `emptyDir`**: en reschedule se pierden datos pero initdb recrea las 5 DBs y Flyway re-migra (igual que en AKS).
