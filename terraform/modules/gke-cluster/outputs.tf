output "cluster_name" {
  description = "Nombre del cluster GKE"
  value       = google_container_cluster.gke.name
}

output "endpoint" {
  description = "Endpoint del API server"
  value       = google_container_cluster.gke.endpoint
  sensitive   = true
}

output "ca_certificate" {
  description = "CA del cluster (para kubeconfig)"
  value       = google_container_cluster.gke.master_auth[0].cluster_ca_certificate
  sensitive   = true
}

output "vpc_name" {
  description = "Nombre de la VPC creada"
  value       = google_compute_network.vpc.name
}
