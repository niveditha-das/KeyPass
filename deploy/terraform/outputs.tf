output "public_ip" {
  description = "Elastic IP. Point the DNS A record for KEYPASS_DOMAIN here."
  value       = aws_eip.server.public_ip
}

output "instance_id" {
  description = "Set as the EC2_INSTANCE_ID variable on the GitHub 'production' environment."
  value       = aws_instance.server.id
}

output "ecr_repository" {
  description = "Set as the ECR_REPOSITORY variable on the GitHub 'production' environment."
  value       = aws_ecr_repository.server.name
}

output "github_deploy_role_arn" {
  description = "Set as the AWS_DEPLOY_ROLE_ARN variable on the GitHub 'production' environment."
  value       = aws_iam_role.github_deploy.arn
}

output "region" {
  description = "Set as the AWS_REGION variable on the GitHub 'production' environment."
  value       = var.region
}

output "ssm_session_command" {
  description = "Open a shell on the instance without SSH."
  value       = "aws ssm start-session --region ${var.region} --target ${aws_instance.server.id}"
}
