# Módulo AKS: outputs clave para usar desde root

output "resource_group_name" {
  description = "Nombre del RG"
  value       = azurerm_resource_group.rg.name
}

output "vnet_name" {
  description = "Nombre de VNet"
  value       = azurerm_virtual_network.vnet.name
}

output "subnet_name" {
  description = "Nombre de la Subnet"
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
  description = "Kubeconfig en base64"
  value       = azurerm_kubernetes_cluster.aks.kube_config_raw
  sensitive   = true
}

output "node_resource_group" {
  description = "RG de los nodos ( AKs lo crea automáticamente )"
  value       = azurerm_kubernetes_cluster.aks.node_resource_group
}

output "acr_login_server" {
  description = "Login server ACR"
  value       = var.create_acr ? azurerm_container_registry.acr[0].login_server : ""
}

output "acr_name" {
  description = "Nombre ACR"
  value       = var.create_acr ? azurerm_container_registry.acr[0].name : ""
}

output "subnet_id" {
  description = "ID de la subnet (para integrar otros servicios)"
  value       = azurerm_subnet.aks.id
}

output "identity_principal_id" {
  description = "Object ID de la identidad del cluster (para RBAC)"
  value       = azurerm_kubernetes_cluster.aks.identity[0].principal_id
}
