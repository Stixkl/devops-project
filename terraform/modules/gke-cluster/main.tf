# Módulo GKE: red + cluster + nodepools. Espejo del módulo aks-cluster para
# la estrategia multi-cloud (AKS activo / GKE pasivo de respaldo).

# Red dedicada (equivalente a la VNet del módulo AKS)
resource "google_compute_network" "vpc" {
  name                    = "${var.cluster_name}-vpc"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "nodes" {
  name          = "${var.cluster_name}-subnet"
  network       = google_compute_network.vpc.id
  region        = var.region
  ip_cidr_range = var.vpc_cidr
}

# Cluster GKE (sin nodepool por defecto: se gestionan aparte, igual que en AKS
# se separan default_node_pool y pools adicionales)
resource "google_container_cluster" "gke" {
  name     = var.cluster_name
  location = var.region
  project  = var.project_id

  network    = google_compute_network.vpc.id
  subnetwork = google_compute_subnetwork.nodes.id

  remove_default_node_pool = true
  initial_node_count       = 1

  min_master_version = var.kubernetes_version

  release_channel {
    channel = "REGULAR"
  }

  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  resource_labels = var.tags
}

# Nodepools (forma equivalente a azurerm_kubernetes_cluster_node_pool)
resource "google_container_node_pool" "pools" {
  for_each = { for pool in var.nodepools : pool.name => pool }

  name     = each.value.name
  cluster  = google_container_cluster.gke.id
  location = var.region
  project  = var.project_id

  node_count = each.value.enable_auto_scaling ? null : each.value.node_count

  dynamic "autoscaling" {
    for_each = each.value.enable_auto_scaling ? [1] : []
    content {
      min_node_count = each.value.min_count
      max_node_count = each.value.max_count
    }
  }

  node_config {
    machine_type = each.value.machine_type
    disk_size_gb = each.value.disk_size_gb
    # FinOps: nodos spot (hasta -91% vs precio on-demand en GCP)
    spot   = each.value.spot
    labels = var.tags

    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform"
    ]
  }
}
