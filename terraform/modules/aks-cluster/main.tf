# Módulo AKS: crea RG, VNet, Subnet, AKS y nodepools

# Resource Group
resource "azurerm_resource_group" "rg" {
  name     = var.resource_group_name
  location = var.location
  tags     = var.tags
}

# Virtual Network
resource "azurerm_virtual_network" "vnet" {
  name                = "${var.cluster_name}-vnet"
  address_space       = var.vnet_address_space
  location            = var.location
  resource_group_name = azurerm_resource_group.rg.name
  tags                = var.tags
}

# Subnet delegada para AKS
resource "azurerm_subnet" "aks" {
  name                 = "${var.cluster_name}-subnet"
  resource_group_name  = azurerm_resource_group.rg.name
  virtual_network_name = azurerm_virtual_network.vnet.name
  address_prefixes     = var.subnet_address_prefix

  delegation {
    name = "aks-delegation"
    service_delegation {
      name = "Microsoft.ContainerService/managedClusters"
      actions = [
        "Microsoft.Network/virtualNetworks/subnets/action"
      ]
    }
  }
}

# Azure Container Registry (opcional)
resource "azurerm_container_registry" "acr" {
  count               = var.create_acr ? 1 : 0
  name                = "${var.cluster_name}acr"
  resource_group_name = azurerm_resource_group.rg.name
  location            = var.location
  sku                 = "Standard"
  admin_enabled       = true
  tags                = var.tags
}

# locals: primer nodepool para default_node_pool
locals {
  first_pool = var.nodepools[0]
  additional_pools = slice(var.nodepools, 1, length(var.nodepools))
}

# Kubernetes Cluster
resource "azurerm_kubernetes_cluster" "aks" {
  name                = var.cluster_name
  location            = var.location
  resource_group_name = azurerm_resource_group.rg.name
  kubernetes_version  = var.kubernetes_version
  dns_prefix          = var.cluster_name

  # Habilitar RBAC
  azure_active_directory_role_based_access_control {
    managed = true
  }

  # Identity: System Assigned
  identity {
    type = "SystemAssigned"
  }

  # Red: usar la subnet creada
  default_node_pool {
    name                = local.first_pool.name
    vm_size             = local.first_pool.vm_size
    node_count          = local.first_pool.node_count
    vnet_subnet_id      = azurerm_subnet.aks.id
    os_disk_type        = local.first_pool.os_disk_type
    os_disk_size_gb     = local.first_pool.os_disk_size_gb
    enable_auto_scaling = local.first_pool.enable_auto_scaling
    min_count           = local.first_pool.min_count
    max_count           = local.first_pool.max_count
  }

  network_profile {
    network_plugin = "azure"
    dns_service_ip = cidrhost(var.service_cidr, 10)
    service_cidr   = var.service_cidr
  }

  tags = var.tags
}

# Nodepools adicionales (segundo en adelante)
resource "azurerm_kubernetes_cluster_node_pool" "additional" {
  for_each = { for pool in local.additional_pools : pool.name => pool }

  kubernetes_cluster_id = azurerm_kubernetes_cluster.aks.id
  name                  = each.value.name
  vm_size               = each.value.vm_size
  node_count            = each.value.node_count
  min_count             = try(each.value.min_count, null)
  max_count             = try(each.value.max_count, null)
  enable_auto_scaling   = try(each.value.enable_auto_scaling, false)
  os_disk_type          = each.value.os_disk_type
  os_disk_size_gb       = each.value.os_disk_size_gb
  vnet_subnet_id        = azurerm_subnet.aks.id
  priority              = try(each.value.priority, null)
  eviction_policy       = try(each.value.eviction_policy, null)
  spot_max_price        = try(each.value.spot_max_price, null)
  # Los nodos Spot reciben automáticamente el taint
  # kubernetes.azure.com/scalesetpriority=spot:NoSchedule; las cargas
  # tolerantes a interrupciones deben declarar la toleration.

  depends_on = [
    azurerm_kubernetes_cluster.aks
  ]
}
