# Variables del módulo GKE (interfaz espejo del módulo aks-cluster
# para poder promover ambientes entre clouds con la misma forma de datos)
variable "environment" {
  description = "Entorno (dev, stage, prod, dr)"
  type        = string
}

variable "project_id" {
  description = "Proyecto de GCP"
  type        = string
}

variable "region" {
  description = "Región de GCP (equivalente a location en AKS)"
  type        = string
  default     = "us-central1"
}

variable "cluster_name" {
  description = "Nombre del cluster GKE"
  type        = string
}

variable "kubernetes_version" {
  description = "Versión mínima del master (GKE usa canales de release)"
  type        = string
  default     = "1.29"
}

variable "nodepools" {
  description = "Lista de nodepools (misma forma que el módulo aks-cluster)"
  type = list(object({
    name                = string
    machine_type        = string
    node_count          = number
    min_count           = number
    max_count           = number
    enable_auto_scaling = bool
    disk_size_gb        = number
    spot                = optional(bool, false)
  }))
}

variable "vpc_cidr" {
  description = "CIDR de la subred de nodos"
  type        = string
  default     = "10.10.0.0/16"
}

variable "tags" {
  description = "Labels para los recursos (equivalente a tags en Azure)"
  type        = map(string)
  default     = {}
}
