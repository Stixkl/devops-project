# Ejemplo de variables específicas por entorno
# Usar: terraform apply -var-file=envs/dev.tfvars

location = "eastus"

tags = {
  Project     = "CircleGuard"
  Environment = "dev"
  ManagedBy   = "Terraform"
}
