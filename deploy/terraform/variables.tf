variable "region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "eu-west-1"
}

variable "instance_type" {
  description = "EC2 instance type. t3.small (2 GiB) comfortably fits the server, Postgres and Caddy."
  type        = string
  default     = "t3.small"
}

variable "root_volume_gb" {
  description = "Size of the encrypted root volume, which also holds the Postgres data volume."
  type        = number
  default     = 20
}

variable "repo_url" {
  description = "Git URL the instance clones for the compose files, Caddyfile and deploy scripts. Must be readable without credentials."
  type        = string
  default     = "https://github.com/niveditha-das/KeyPass.git"
}

variable "github_repository" {
  description = "GitHub repository (owner/name) allowed to deploy through OIDC."
  type        = string
  default     = "niveditha-das/KeyPass"
}

variable "github_environment" {
  description = "GitHub Actions environment the deploy job runs in. Only that environment can assume the deploy role."
  type        = string
  default     = "production"
}

variable "create_github_oidc_provider" {
  description = "Create the GitHub OIDC identity provider. Set to false if the account already has one."
  type        = bool
  default     = true
}

variable "ssm_prefix" {
  description = "SSM Parameter Store path holding the runtime secrets (see docs/deployment.md)."
  type        = string
  default     = "/keypass"
}
