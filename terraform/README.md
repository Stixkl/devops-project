# Terraform Infrastructure - CircleGuard

Este directorio contiene la infraestructura como código (IaC) para provisionar el clúster AKS (Azure Kubernetes Service) donde se desplegarán los microservicios de CircleGuard.

## Estructura

```
terraform/
├── main.tf                 # Instanciación de módulos por entorno
├── variables.tf            # Variables globales
├── outputs.tf              # Outputs globales (vacío)
├── providers.tf            # Provider Azure y backend
├── .gitignore              # Archivos a ignorar
├── envs/
│   ├── dev.tfvars          # Valores para entorno DEV
│   ├── stage.tfvars        # Valores para entorno STAGE
│   └── prod.tfvars         # Valores para entorno PROD
├── modules/
│   └── aks-cluster/        # Módulo que crea AKS + red + RG
│       ├── main.tf
│       ├── variables.tf
│       ├── outputs.tf
│       └── README.md
└── scripts/
    └── init-backend.sh     # Inicializa backend remoto (Azure Storage)
```

## Requisitos previos

1. **Azure CLI** instalado y autenticado:
   ```bash
   az login
   ```
2. **Terraform** >= 1.0 instalado.
3. Permisos de **Contributor** en la suscripción de Azure.
4. (Opcional) Acceso a Azure desde Jenkins si se automatiza.

## Pasos de configuración (una sola vez)

### 1. Inicializar el backend remoto

Ejecuta el script que crea el Storage Account para guardar el estado Terraform:

```bash
cd terraform
chmod +x scripts/init-backend.sh
./scripts/init-backend.sh
```

Anota las credenciales mostradas (Storage Account name y resource group).

### 2. Inicializar Terraform con el backend

Para cada entorno (dev, stage, prod) ejecuta:

```bash
# DEV
terraform init \
  -backend-config="resource_group_name=rg-terraform-state" \
  -backend-config="storage_account_name=<tu-storage-account>" \
  -backend-config="container_name=tfstate" \
  -backend-config="key=dev.tfstate"

# STAGE
terraform init \
  -backend-config="resource_group_name=rg-terraform-state" \
  -backend-config="storage_account_name=<tu-storage-account>" \
  -backend-config="container_name=tfstate" \
  -backend-config="key=stage.tfstate"

# PROD
terraform init \
  -backend-config="resource_group_name=rg-terraform-state" \
  -backend-config="storage_account_name=<tu-storage-account>" \
  -backend-config="container_name=tfstate" \
  -backend-config="key=prod.tfstate"
```

### 3. Aplicar (crear recursos)

```bash
# Crear entorno DEV
terraform apply -var-file=envs/dev.tfvars

# Crear entorno STAGE
terraform apply -var-file=envs/stage.tfvars

# Crear entorno PROD
terraform apply -var-file=envs/prod.tfvars
```

## Obtener kubeconfig para kubectl/Jenkins

Después de crear un cluster, obtén sus credenciales:

```bash
# Para DEV
az aks get-credentials \
  --resource-group $(terraform output -raw dev_resource_group_name 2>/dev/null || echo "rg-circle-guard-dev") \
  --name cg-aks-dev \
  --file kubeconfig-dev.yaml

# Para STAGE
az aks get-credentials \
  --resource-group $(terraform output -raw stage_resource_group_name 2>/dev/null || echo "rg-circle-guard-stage") \
  --name cg-aks-stage \
  --file kubeconfig-stage.yaml

# Para PROD
az aks get-credentials \
  --resource-group $(terraform output -raw prod_resource_group_name 2>/dev/null || echo "rg-circle-guard-prod") \
  --name cg-aks-prod \
  --file kubeconfig-prod.yaml
```

Configura `KUBECONFIG` para usar el archivo:

```bash
export KUBECONFIG=$(pwd)/kubeconfig-dev.yaml
kubectl get nodes
```

**Para Jenkins**: guarda los archivos `kubeconfig-*.yaml` como credenciales secretas y configura los pipelines para usar el archivo correspondiente mediante la variable `KUBECONFIG`.

## Destruir recursos

Si necesitas eliminar un cluster y toda su infraestructura:

```bash
terraform destroy -var-file=envs/dev.tfvars
```

**Advertencia**: Esto elimina el cluster, VNet, nodos y datos persistente en los PVCs. Asegúrate de hacer backup si es necesario.

## Variables disponibles

Revisa `variables.tf` y los archivos `envs/*.tfvars` para ver valores por entorno. Puedes modificar:
- Tamaños de VM (vm_size)
- Número de nodos
- Auto-scaling
- Región
- Versión Kubernetes

## Costos aproximados (precios 2026)

- **DEV**: 2× B2s → ~$50/mes
- **STAGE**: 3× B2ms → ~$100/mes
- **PROD**: 3× B4ms + 5× B2ms → ~$350/mes + Load Balancer ($25/mes por cluster)

## Notas

- El backend remoto usa Azure Storage. No compartas las credenciales.
- Cada entorno tiene su propio `tfstate` (dev.tfstate, stage.tfstate, prod.tfstate).
- Los namespaces y deployments de la aplicación se gestionan con `kubectl apply -f k8s/*` (no con Terraform).
- Si no tienes permisos para crear recursos en Azure, pide acceso al administrador.

## Solución de problemas

- `Error: storage account name must be between 3 and 24 characters` → El storage account debe ser único globalmente. Reejecuta `init-backend.sh`.
- `az: command not found` → Instala Azure CLI.
- `Error: AuthorizationFailed` →Tu cuenta no tiene permisos Contributor en la suscripción.
- `kubectl get nodes` falla → Verifica que el comando `az aks get-credentials` haya terminado correctamente y que el archivo kubeconfig exista.
