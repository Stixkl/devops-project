# Variables globales del proyecto
variable "location" {
  description = "Región de Azure para todos los recursos"
  type        = string
  default     = "eastus"
}

variable "tags" {
  description = "Tags aplicados a todos los recursos"
  type        = map(string)
  default = {
    Project     = "CircleGuard"
    Environment = "dev"
    ManagedBy   = "Terraform"
  }
}

variable "subscription_id" {
  description = "Azure Subscription ID (se puede pasar como env var ARM_SUBSCRIPTION_ID)"
  type        = string
  sensitive   = true
}

# Estas variables se usan en cada módulo, pero las definimos aquí para referencia
# Los valores específicos por entorno se pasan en los tfvars
