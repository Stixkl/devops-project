# Ejemplo de variables específicas por entorno
# Usar: terraform apply -var-file=envs/stage.tfvars

location = "eastus"

tags = {
  Project     = "CircleGuard"
  Environment = "stage"
  ManagedBy   = "Terraform"
}
