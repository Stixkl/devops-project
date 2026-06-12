# Infraestructura como Código con Terraform - CircleGuard

## Visión general

CircleGuard utiliza **Terraform** para provisionar la infraestructura base en Azure: específicamente, los clústeres AKS (Azure Kubernetes Service) para los entornos de desarrollo, staging y producción.

> **Nota**: el código Terraform y los manifiestos Kubernetes viven en el repo separado [circleguard-infra](https://github.com/JuanAmor8/circleguard-infra). Clónalo junto a este repo: `git clone https://github.com/JuanAmor8/circleguard-infra.git`.

**Objetivo**: Mantener los manifiestos Kubernetes (`circleguard-infra/k8s/*`) como la fuente de verdad para desplegar los microservicios, mientras que Terraform gestiona únicamente la infraestructura del clúster (red, nodos, etc.).

## Arquitectura Azure

Cada entorno (dev, stage, prod) despliega un **clúster AKS independiente** con su propio:

- Resource Group (`rg-circle-guard-<entorno>`)
- Virtual Network (`<clustername>-vnet`)
- Subnet dedicada al clúster (`<clustername>-subnet`), sin `delegation` explícita
- Nodepools configurados según necesidades de carga
- (Opcional) Azure Container Registry (solo prod)

```
Entorno DEV
├── RG: rg-circle-guard-dev
├── VNet: cg-aks-dev-vnet /16
├── Subnet: cg-aks-dev-subnet /24
├── AKS: cg-aks-dev (1 nodepool: 2× Standard_B2s)
└── ACR: no

Entorno STAGE
├── RG: rg-circle-guard-stage
├── VNet: cg-aks-stage-vnet /16
├── Subnet: cg-aks-stage-subnet /24
├── AKS: cg-aks-stage (1 nodepool: 3× Standard_B2ms, auto-scaling 3-6)
└── ACR: no

Entorno PROD
├── RG: rg-circle-guard-prod
├── VNet: cg-aks-prod-vnet /16
├── Subnet: cg-aks-prod-subnet /24
├── AKS: cg-aks-prod
│   ├── Nodepool system: 3× Standard_B4ms (Spot)
│   └── Nodepool user: 5× Standard_B2ms (auto-scaling 5-10)
└── ACR: cg-aks-prodacr (Standard)
```

## Estructura de módulos y entornos

```
circleguard-infra/terraform/
├── environments/
│   ├── dev/                # Root aislado + backend dev.tfstate
│   ├── stage/              # Root aislado + backend stage.tfstate
│   └── prod/               # Root aislado + backend prod.tfstate
├── modules/
    └── aks-cluster/
        ├── main.tf
        ├── variables.tf
        ├── outputs.tf
        └── README.md
└── scripts/
    └── init-backend.sh
```

Cada root instancia únicamente su clúster. Esto evita que un `terraform apply` o `destroy` de dev modifique stage/prod.

### Módulo `aks-cluster`

Este módulo reutilizable crea:

1. **Resource Group**
2. **Virtual Network** con espacio de direcciones configurable
3. **Subnet** dedicada, sin delegación a `Microsoft.ContainerService/managedClusters`
4. **Azure Container Registry** (opcional)
5. **AKS cluster** con System Assigned Identity
6. **Nodepools** (uno como default_node_pool, los adicionales como recursos aparte)

**Variables de entrada** (completas en `modules/aks-cluster/variables.tf`):

| Variable | Tipo | Descripción |
|----------|------|-------------|
| `environment` | string | Entorno (dev/stage/prod) |
| `location` | string | Región Azure |
| `resource_group_name` | string | Nombre del RG |
| `cluster_name` | string | Nombre único global del cluster |
| `kubernetes_version` | string | Versión de Kubernetes soportada por la región (dev real: 1.33) |
| `nodepools` | list(object) | Lista de nodepools a crear |
| `create_acr` | bool | Si crear Azure Container Registry |

**Outputs**:

| Output | Descripción |
|--------|-------------|
| `resource_group_name` | Nombre del RG creado |
| `vnet_name` | Nombre de la VNet |
| `subnet_name` | Nombre de la Subnet |
| `aks_name` | Nombre del cluster AKS |
| `aks_fqdn` | FQDN del API server |
| `kube_config_raw` | Kubeconfig en base64 (sensible) |
| `node_resource_group` | RG de los nodos del cluster |
| `acr_login_server` | URL del ACR (si se creó) |

## Backend remoto (Azure Storage)

El estado de Terraform (`terraform.tfstate`) se almacena en **Azure Storage Account** para permitir trabajo en equipo y locking.

### Inicialización

Ejecutar `scripts/init-backend.sh` una sola vez:

```bash
cd circleguard-infra/terraform
./scripts/init-backend.sh
```

Esto crea:
- Resource Group: `rg-terraform-state`
- Storage Account: `cgterraformXXXX` (nombre único)
- Container: `tfstate`

Luego, inicializar el root del entorno:

```bash
cd environments/dev
terraform init -backend-config=backend.hcl
```

El archivo de estado se guardará como `dev.tfstate`, `stage.tfstate`, `prod.tfstate` en el contenedor.

## Flujo de trabajo

### 1. Crear un nuevo entorno

```bash
cd circleguard-infra/terraform/environments/dev
terraform init -backend-config=backend.hcl
terraform apply -var-file=dev.tfvars
```

Terraform:
- Crea RG, VNet, Subnet
- Crea el cluster AKS con los nodepools especificados
- Genera outputs (incluido kubeconfig raw)

### 2. Obtener kubeconfig para kubectl

```bash
# Opción A: Usar Azure CLI
az aks get-credentials --admin \
  --resource-group rg-circle-guard-dev \
  --name cg-aks-dev \
  --file kubeconfig-dev.yaml
```

El clúster dev está integrado con AAD. El kubeconfig admin evita depender de `kubelogin` en CI, pero concede acceso total y debe tratarse como secreto.

Configurar `KUBECONFIG`:

```bash
export KUBECONFIG=$(pwd)/kubeconfig-dev.yaml
kubectl cluster-info
```

### 3. Desplegar aplicaciones

Los manifests Kubernetes están en `circleguard-infra/k8s/dev`, `k8s/stage`, `k8s/master`. Ya no se ejecutan contra Minikube, sino contra el AKS:

```bash
cd circleguard-infra
kubectl apply -f k8s/dev/ -n circleguard-dev
```

Los workflows de CD clonan `circleguard-infra` en `infra/` (paso "Checkout infra repo") y ejecutan `kubectl apply -f infra/k8s/...`; solo necesitan tener el `KUBECONFIG` apuntando al cluster correcto.

### 4. Actualizar escalado

Si necesitas cambiar el número de nodos, modifica `environments/dev/dev.tfvars` y ejecuta desde ese root:

```bash
terraform apply -var-file=dev.tfvars
```

Terraform detectará cambios y actualizará los nodepools.

### 5. Destruir entorno

```bash
cd circleguard-infra/terraform/environments/dev
terraform destroy -var-file=dev.tfvars
```

**Cuidado**: elimina todos los recursos en ese RG, incluyendo datos de PVCs.

## Consideraciones de costos (Azure 2026)

| Recurso | DEV | STAGE | PROD |
|--------|-----|-------|------|
| VM (B2s) | 2× $17.60/mo | - | - |
| VM (B2ms) | - | 3× $52.80/mo | 5× $52.80/mo |
| VM (B4ms) | - | - | 3× $158.40/mo |
| Load Balancer | $25/mo | $25/mo | $25/mo |
| ACR Storage (prod) | - | - | ~$5/mo |
| **Total aprox.** | **~$60/mo** | **~$133/mo** | **~$550/mo** |

*Precios orientativos; consulta Azure Pricing Calculator para valores exactos.*

## Integración con GitHub Actions

Los workflows de CD (`.github/workflows/cd-dev.yml`, `cd-stage.yml` y el job `deploy-prod` de `ci.yml`) ya ejecutan `kubectl apply`. Para que apunten a AKS:

1. Obtén el kubeconfig de cada cluster (como se describió arriba).
2. Codifícalo en base64 y guárdalo como GitHub Secret por ambiente: `KUBE_CONFIG_DEV`, `KUBE_CONFIG_STAGE`, `KUBE_CONFIG_PROD`.
   ```bash
   base64 -w0 kubeconfig-dev.yaml   # pega el resultado en el secret KUBE_CONFIG_DEV
   ```
3. Los workflows decodifican el secret a `~/.kube/config` y ejecutan, p. ej.:
   ```yaml
   - run: |
       echo "${{ secrets.KUBE_CONFIG_DEV }}" | base64 -d > ~/.kube/config
       kubectl apply -f infra/k8s/dev/
   ```
   (la ruta `infra/` proviene del paso "Checkout infra repo", que clona `circleguard-infra` dentro del workspace.)
4. Si el secret `KUBE_CONFIG_*` no está configurado, el job omite el despliegue al cluster con un warning (las imágenes se publican igualmente), útil para validar el pipeline sin un AKS activo.

La ejecución real de dev, restricciones de Azure for Students y troubleshooting están documentados en `docs/DESPLIEGUE_AZURE.md`.

## Troubleshooting

- **Backend no inicializado**: Ejecuta `terraform init` con los parámetros `-backend-config`.
- **Error de cuota en Azure**: Si te faltan vCPUs en la región, solicita aumento de cuota o usa tamaños más pequeños.
- **kubectl no puede conectarse**: Verifica que `az aks get-credentials` haya sobrescrito el contexto correcto. Usa `kubectl config get-contexts`.
- **Terraform tarda mucho**: AKS tarda 5-10 minutos en crearse. Espera.

## Próximos pasos (opcional)

- Migrar también bases de datos a Azure Database for PostgreSQL y Neo4j Aura (actualmente corren en K8s).
- Configurar Azure Monitor para AKS y Log Analytics (actualmente no se usa).
- Habilitar auto-scaling de nodos basado en métricas personalizadas.
- Integrar Azure Key Vault para secretos (en lugar de Kubernetes Secrets).

## Referencias

- [Provider Azure Terraform](https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs)
- [AKS Terraform resource](https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs/resources/kubernetes_cluster)
- [Documentación oficial AKS](https://learn.microsoft.com/azure/aks/)
