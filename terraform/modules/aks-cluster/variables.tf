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

# Variables de red (podemos generarlas automáticamente basadas en environment)
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

output "resource_group_name" {
  description = "Nombre del Resource Group creado"
  value       = azurerm_resource_group.rg.name
}

output "vnet_name" {
  description = "Nombre de la VNet creada"
  value       = azurerm_virtual_network.vnet.name
}

output "subnet_name" {
  description = "Nombre de la Subnet para AKS"
  value       = azurerm_subnet.aks.name
}

output "aks_name" {
  description = "Nombre del cluster AKS"
  value       = azurerm_kubernetes_cluster.aks.name
}

output "aks_fqdn" {
  description = "FQDN del API server"
  value       = azurerm_kubernetes_cluster.aks.fqdn
}

output "kube_config_raw" {
  description = "Kubeconfig en base64 (para Jenkins o kubectl)"
  value       = azurerm_kubernetes_cluster.aks.kube_config_raw
  sensitive   = true
}

output "node_resource_group" {
  description = "Resource Group de los nodos (auto-generado por AKS)"
  value       = azurerm_kubernetes_cluster.aks.node_resource_group
}

output "acr_login_server" {
  description = "Login server del ACR (si se creó)"
  value       = var.create_acr ? azurerm_container_registry.acr[0].login_server : ""
}

output "acr_name" {
  description = "Nombre del ACR"
  value       = var.create_acr ? azurerm_container_registry.acr[0].name : ""
}
