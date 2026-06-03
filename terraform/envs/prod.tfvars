# Ejemplo de variables específicas por entorno
# Usar: terraform apply -var-file=envs/prod.tfvars

location = "eastus"

tags = {
  Project     = "CircleGuard"
  Environment = "prod"
  ManagedBy   = "Terraform"
}
