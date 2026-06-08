#!/bin/bash
# Script para inicializar el backend remoto de Terraform en Azure Storage
# Ejecutar UNA VEZ antes del primer terraform init
# Requiere: Azure CLI instalado y autenticado (az login)

set -e

# Obtener la suscripción activa, limpiando posibles CR (Windows CRLF)
echo "Obteniendo la suscripción de Azure..."
SUBSCRIPTION_ID=$(az account show --query id -o tsv 2>/dev/null | tr -d '\r')

if [ -z "$SUBSCRIPTION_ID" ]; then
  echo "ERROR: No se pudo obtener la suscripción."
  echo "Asegúrate de haber ejecutado 'az login' y 'az account set' correctamente."
  echo "En Git Bash, puedes necesitar ejecutar 'az login' dentro del mismo shell."
  exit 1
fi

echo "Suscripción detectada: $SUBSCRIPTION_ID"
echo ""

# Configuración
RESOURCE_GROUP="rg-terraform-state"
LOCATION="eastus"
STORAGE_ACCOUNT_PREFIX="cgterraform"
CONTAINER_NAME="tfstate"

# 1) Crear Resource Group para el backend (si no existe)
echo "Creando Resource Group '$RESOURCE_GROUP' en $LOCATION..."
az group create --name "$RESOURCE_GROUP" --location "$LOCATION" --subscription "$SUBSCRIPTION_ID" --output none

# 2) Crear Storage Account (nombre único global)
RANDOM_SUFFIX=$((RANDOM % 10000))
STORAGE_ACCOUNT="${STORAGE_ACCOUNT_PREFIX}${RANDOM_SUFFIX}"
echo "Creando Storage Account '$STORAGE_ACCOUNT'..."
az storage account create \
  --name "$STORAGE_ACCOUNT" \
  --resource-group "$RESOURCE_GROUP" \
  --location "$LOCATION" \
  --sku "Standard_LRS" \
  --kind "StorageV2" \
  --subscription "$SUBSCRIPTION_ID" \
  --output none

# 3) Obtener clave de storage (limpiar CR)
STORAGE_KEY=$(az storage account keys list \
  --resource-group "$RESOURCE_GROUP" \
  --account-name "$STORAGE_ACCOUNT" \
  --subscription "$SUBSCRIPTION_ID" \
  --query "[0].value" -o tsv | tr -d '\r')

# 4) Crear container para tfstate
echo "Creando container '$CONTAINER_NAME'..."
az storage container create \
  --account-name "$STORAGE_ACCOUNT" \
  --account-key "$STORAGE_KEY" \
  --name "$CONTAINER_NAME" \
  --subscription "$SUBSCRIPTION_ID" \
  --output none

# 5) Mostrar instrucciones de terraform init
echo ""
echo "============================================"
echo "Backend Azure Storage creado con éxito!"
echo "============================================"
echo ""
echo "Configuración del backend:"
echo "  Resource Group: $RESOURCE_GROUP"
echo "  Storage Account: $STORAGE_ACCOUNT"
echo "  Container: $CONTAINER_NAME"
echo "  Subscription: $SUBSCRIPTION_ID"
echo ""
echo "Para inicializar Terraform, ejecuta en cada entorno:"
echo ""
echo "  terraform init \\"
echo "    -backend-config=\"resource_group_name=$RESOURCE_GROUP\" \\"
echo "    -backend-config=\"storage_account_name=$STORAGE_ACCOUNT\" \\"
echo "    -backend-config=\"container_name=$CONTAINER_NAME\" \\"
echo "    -backend-config=\"key=dev.tfstate\""
echo ""
echo "Luego aplica:"
echo "  terraform apply -var-file=envs/dev.tfvars"
echo ""
echo "IMPORTANTE: Guarda estas credenciales en un lugar seguro."
echo "El Storage Account name es único y no se puede recuperar si se pierde."
echo ""
