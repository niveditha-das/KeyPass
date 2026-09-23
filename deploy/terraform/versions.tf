terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # State is kept locally by default. For a shared setup, switch to an S3 backend:
  # backend "s3" {
  #   bucket       = "<your-state-bucket>"
  #   key          = "keypass/terraform.tfstate"
  #   region       = "eu-west-1"
  #   use_lockfile = true
  # }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "keypass"
      ManagedBy = "terraform"
    }
  }
}
