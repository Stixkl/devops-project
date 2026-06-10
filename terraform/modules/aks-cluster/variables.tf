# Variables del módulo AKS
variable "environment" {
  description = "Entorno (dev, stage, prod)"
  type        = string
}

variable "location" {
  description = "Región de Azure"
  type        = string
}

variable "resource_group_name" {
  description = "Nombre del Resource Group"
  type        = string
}

variable "cluster_name" {
  description = "Nombre del cluster AKS"
  type        = string
}

variable "kubernetes_version" {
  description = "Versión de Kubernetes"
  type        = string
  default     = "1.29.0"
}

variable "nodepools" {
  description = "Lista de nodepools a crear (objetos con name, vm_size, node_count, min_count, max_count, enable_auto_scaling, etc.)"
  type = list(object({
    name                 = string
    vm_size              = string
    node_count           = number
    min_count            = number
    max_count            = number
    enable_auto_scaling  = bool
    os_disk_type         = string
    os_disk_size_gb      = number
    priority             = optional(string)
    eviction_policy      = optional(string)
    # FinOps: precio máximo para nodos Spot (-1 = precio de mercado, hasta -90%)
    spot_max_price       = optional(number)
  }))
}

variable "create_acr" {
  description = "Si crear Azure Container Registry"
  type        = bool
  default     = false
}

variable "tags" {
  description = "Tags para recursos"
  type        = map(string)
  default     = {}
}

# Variables de red
variable "vnet_address_space" {
  description = "CIDR para la VNet"
  type        = list(string)
  default     = ["10.0.0.0/16"]
}

variable "subnet_address_prefix" {
  description = "CIDR para la Subnet de AKS"
  type        = list(string)
  default     = ["10.0.0.0/24"]
}

variable "pod_cidr" {
  description = "CIDR para pods (usado por AKS)"
  type        = string
  default     = "10.244.0.0/16"
}

variable "service_cidr" {
  description = "CIDR para servicios K8s"
  type        = string
  default     = "10.0.1.0/24"
}
