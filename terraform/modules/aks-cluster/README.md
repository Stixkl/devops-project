# Módulo AKS (Azure Kubernetes Service)

Este módulo crea un clúster AKS completo con:
- Resource Group
- Virtual Network + Subnet delegada
- Azure Container Registry opcional
- Kubernetes Cluster con System Assigned Identity
- Nodepools definidos por variable

## Uso

```hcl
module "aks_dev" {
  source              = "./modules/aks-cluster"
  environment         = "dev"
  location            = "eastus"
  resource_group_name = "rg-circle-guard-dev"
  cluster_name        = "cg-aks-dev"
  kubernetes_version = "1.29.0"

  nodepools = [
    {
      name                = "default"
      vm_size             = "Standard_B2s"
      node_count          = 2
      min_count           = 1
      max_count           = 3
      enable_auto_scaling = false
      os_disk_type        = "Managed"
      os_disk_size_gb     = 128
    }
  ]

  create_acr = false
  tags = {
    Project = "CircleGuard"
  }
}
```

## Variables

| Nombre | Tipo | Descripción |
|--------|------|-------------|
| `environment` | string | Entorno (dev/stage/prod) |
| `location` | string | Región Azure |
| `resource_group_name` | string | Nombre del RG |
| `cluster_name` | string | Nombre del cluster (únic o global) |
| `kubernetes_version` | string | Versión K8s (default 1.29.0) |
| `nodepools` | list(object) | Lista de nodepools |
| `create_acr` | bool | Si crear ACR |
| `tags` | map(string) | Tags |

## Outputs

- `kube_config_raw`: Kubeconfig en base64 (usar `az aks get-credentials` o `kubectl` con decodificación)
- `aks_name`, `aks_fqdn`: identificadores del cluster
- `resource_group_name`, `vnet_name`, `subnet_name`: red
- `acr_login_server`: si se creó ACR

## Costos aproximados (precios 2026)

- Dev (2x B2s): ~$50/mes
- Stage (3x B2ms): ~$100/mes
- Prod (3x B4ms + 5x B2ms): ~$350/mes
- Load Balancer: ~$25/mes por cluster

## Requisitos

- Provider Azure >= 3.100
- Permisos Contributor en la suscripción

## Notas

- El default_node_pool se crea con valores base; se recomienda añadir nodepools adicionales vía `nodepools`.
- Auto-scaling requiere nodepools con `enable_auto_scaling=true`.
- Para usar Spot VMs, establecer `priority="Spot"` y `eviction_policy="Delete"`.
