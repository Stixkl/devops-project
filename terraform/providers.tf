# Configuración del proveedor Azure
terraform {
  required_version = ">= 1.0"
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.100"
    }
  }

  # Backend remoto: se configura con -backend-config en terraform init
  # Ver scripts/init-backend.sh para inicialización
  backend "azurerm" {}
}

provider "azurerm" {
  features {}

  # Opcional: se puede pasar subscription_id via env var o -var
  # subscription_id = var.subscription_id
}
