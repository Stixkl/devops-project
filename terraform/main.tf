# Main Terraform configuration: instancia el módulo AKS para cada entorno
# Cada bloque module despliega un cluster AKS independiente con su propio resource group, VNet, subnet

# Módulo DEV
module "aks_dev" {
  source              = "./modules/aks-cluster"
  environment         = "dev"
  location            = var.location
  resource_group_name = "rg-circle-guard-dev"
  cluster_name        = "cg-aks-dev"
  kubernetes_version = "1.29.0"

  nodepools = [
    {
      name               = "default"
      vm_size            = "Standard_B2s"
      node_count         = 2
      min_count          = 1
      max_count          = 3
      enable_auto_scaling = false
      os_disk_type       = "Managed"
      os_disk_size_gb    = 128
    }
  ]

  create_acr = false
  tags       = var.tags
}

# Módulo STAGE
module "aks_stage" {
  source              = "./modules/aks-cluster"
  environment         = "stage"
  location            = var.location
  resource_group_name = "rg-circle-guard-stage"
  cluster_name        = "cg-aks-stage"
  kubernetes_version = "1.29.0"

  nodepools = [
    {
      name               = "default"
      vm_size            = "Standard_B2ms"
      node_count         = 3
      min_count          = 3
      max_count          = 6
      enable_auto_scaling = true
      os_disk_type       = "Managed"
      os_disk_size_gb    = 128
    },
    {
      # FinOps: capacidad de ráfaga en nodos Spot (hasta -90% de costo).
      # min 0 = scale-to-zero cuando no hay carga; las cargas que corren aquí
      # toleran evicción (taint scalesetpriority=spot:NoSchedule).
      name               = "burst"
      vm_size            = "Standard_B2ms"
      node_count         = 0
      min_count          = 0
      max_count          = 3
      enable_auto_scaling = true
      priority           = "Spot"
      eviction_policy    = "Delete"
      spot_max_price     = -1
      os_disk_type       = "Managed"
      os_disk_size_gb    = 128
    }
  ]

  create_acr = false
  tags       = var.tags
}

# Módulo PROD (master)
module "aks_prod" {
  source              = "./modules/aks-cluster"
  environment         = "prod"
  location            = var.location
  resource_group_name = "rg-circle-guard-prod"
  cluster_name        = "cg-aks-prod"
  kubernetes_version = "1.29.0"

  nodepools = [
    {
      # System pool SIEMPRE on-demand: alojar el plano de sistema en Spot
      # arriesga evicciones de CoreDNS/metrics; el ahorro Spot va en pools
      # de usuario tolerantes (ver pool burst de stage).
      name               = "system"
      vm_size            = "Standard_B4ms"
      node_count         = 3
      min_count          = 3
      max_count          = 5
      enable_auto_scaling = true
      os_disk_type       = "Managed"
      os_disk_size_gb    = 128
    },
    {
      name               = "user"
      vm_size            = "Standard_B2ms"
      node_count         = 5
      min_count          = 5
      max_count          = 10
      enable_auto_scaling = true
      os_disk_type       = "Managed"
      os_disk_size_gb    = 128
    }
  ]

  create_acr = true
  tags       = var.tags
}
