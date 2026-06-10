# Multi-Cloud (bonus): sitio de respaldo pasivo en GCP (GKE) espejo del
# ambiente productivo en Azure (AKS). Estrategia activo-pasivo:
#   - AKS (módulo aks_prod en main.tf) sirve el tráfico.
#   - GKE (gke_dr) se mantiene con capacidad mínima + autoscaling; Velero
#     restaura los namespaces desde el bucket de respaldo (k8s/dr/).
#   - El balanceo/failover entre clouds se hace en el DNS/LB externo
#     (demo local con HAProxy en multicloud/).
#
# Se habilita con -var enable_gke_dr=true (por defecto NO se crea nada,
# así `terraform plan` no exige credenciales de GCP en los pipelines).

variable "enable_gke_dr" {
  description = "Crear el sitio de respaldo GKE en GCP"
  type        = bool
  default     = false
}

variable "gcp_project_id" {
  description = "Proyecto de GCP para el sitio de respaldo"
  type        = string
  default     = ""
}

variable "gcp_region" {
  description = "Región de GCP para el sitio de respaldo"
  type        = string
  default     = "us-central1"
}

module "gke_dr" {
  source = "./modules/gke-cluster"
  count  = var.enable_gke_dr ? 1 : 0

  environment  = "dr"
  project_id   = var.gcp_project_id
  region       = var.gcp_region
  cluster_name = "cg-gke-dr"

  nodepools = [
    {
      name                = "default"
      machine_type        = "e2-medium" # equivalente a Standard_B2s
      node_count          = 1
      min_count           = 1
      max_count           = 3
      enable_auto_scaling = true
      disk_size_gb        = 100
      spot                = true # FinOps: el sitio pasivo corre en spot
    }
  ]

  tags = var.tags
}
