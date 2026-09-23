# Deployment: AWS (single EC2 instance)

The AWS side is defined in Terraform (`deploy/terraform/`), and deploys run from a manual GitHub
Actions workflow (`.github/workflows/deploy.yml`). It is sized to stay inside the AWS free tier
(one t3.micro, 20 GB of EBS, one public IPv4 address, at most 3 images in ECR). AWS still needs a
payment card on the account, and anything beyond the free-tier allowances is billed.

## Shape

```
                     ┌────────────────────── EC2 (Amazon Linux 2023) ──────────────────────┐
Internet ──80/443──▶ │ Caddy (auto HTTPS) ──▶ server:8080 ──▶ Postgres (compose network)   │
                     └──────────────────────────────────────────────────────────────────────┘
GitHub Actions ──OIDC──▶ build ▶ Trivy ▶ push to ECR ▶ SSM Run Command ▶ deploy/deploy.sh
```

- Neither Postgres nor the application port is published to the host; Caddy is the only listener.
  `/actuator/*` is answered with a 404 at the proxy.
- There is no SSH key and no port 22. Shell access is via SSM Session Manager.
- Secrets live only in SSM Parameter Store as `SecureString`s and are written to a mode-600
  `.env` on the instance at deploy time. They are never in the repository, the image, Terraform
  state or GitHub.
- GitHub gets no long-lived AWS keys: the deploy job assumes an IAM role via OIDC, and that role
  trusts only jobs running in the `production` GitHub environment.
- IMDSv2 is enforced with a hop limit of 1, so containers cannot reach the instance credentials.

## What Terraform creates

| Resource | Purpose |
| --- | --- |
| ECR repository `keypass-server` | Immutable tags, scan on push, keeps the last 3 images |
| EC2 instance + Elastic IP | t3.micro (free tier) with 2 GB swap, encrypted 20 GB gp3 root volume |
| Security group | 80 and 443 in from anywhere; nothing else |
| Instance role | Read `/keypass/*` from SSM, pull from ECR, Session Manager |
| GitHub OIDC provider + deploy role | Push to ECR, `ssm:SendCommand` to this one instance |

## One-off setup

Prerequisites: Terraform ≥ 1.6, the AWS CLI with the Session Manager plugin, and credentials for
the target account.

1. **Provision.**

   ```
   cd deploy/terraform
   cp terraform.tfvars.example terraform.tfvars   # set the region; see variables.tf for the rest
   terraform init
   terraform apply
   ```

   If the account already has a GitHub OIDC provider, set `create_github_oidc_provider = false`.
   State is local by default; `versions.tf` shows how to switch to an S3 backend.

2. **DNS.** Point an `A` record for your hostname at the `public_ip` output.

3. **Secrets.** Generate a signing key pair with `java scripts/GenerateSigningKey.java`, then:

   ```
   R=<region>
   aws ssm put-parameter --region $R --type SecureString --name /keypass/KEYPASS_SIGNING_KEY        --value '<private key>'
   aws ssm put-parameter --region $R --type SecureString --name /keypass/KEYPASS_SIGNING_PUBLIC_KEY --value '<public key>'
   aws ssm put-parameter --region $R --type SecureString --name /keypass/DB_PASSWORD                --value "$(openssl rand -base64 32)"
   aws ssm put-parameter --region $R --type SecureString --name /keypass/KEYPASS_DOMAIN             --value 'keypass.example.com'
   ```

   `DB_PASSWORD` is only applied when Postgres initialises its volume. Changing it later means
   changing it inside the database too.

4. **GitHub.** In the repository settings, create an environment named `production` (optionally
   with required reviewers, which then gate every deploy) and add these environment *variables*
   from `terraform output`:

   | Variable | Terraform output |
   | --- | --- |
   | `AWS_REGION` | `region` |
   | `AWS_DEPLOY_ROLE_ARN` | `github_deploy_role_arn` |
   | `ECR_REPOSITORY` | `ecr_repository` |
   | `EC2_INSTANCE_ID` | `instance_id` |

## Deploy

Run the **deploy** workflow from the Actions tab (it deploys the commit it is run on). It:

1. builds the image and fails on any fixable HIGH/CRITICAL Trivy finding,
2. pushes it to ECR tagged with the commit SHA,
3. runs `deploy/deploy.sh <sha> <image>` on the instance through SSM, which checks out that
   commit, refreshes `.env` from SSM, pulls the image, runs `docker compose up`, and waits for
   `/actuator/health` to report `UP`. The job fails, with the server logs, if it doesn't.

Then check it from outside:

```
curl https://$KEYPASS_DOMAIN/api/v1/.well-known/keypass-keys   # public signing keys; /actuator/* is 404 by design
```

The prod profile refuses to start without `KEYPASS_SIGNING_KEY` (an ephemeral key would silently
invalidate every issued credential on restart) and disables Swagger UI and the OpenAPI endpoint.

**Rollback:** run the workflow again on the earlier commit, or from a session on the instance:
`sudo /opt/keypass/deploy/deploy.sh <old-sha> <registry>/keypass-server:<old-sha>`.

**Shell access:** `terraform output ssm_session_command`.

## Without the pipeline

The instance can also build and run everything itself, using the same overlay:

```
cd /opt/keypass
./deploy/fetch-secrets.sh
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

## Not covered

Backups (snapshot the EBS volume with AWS Backup / Data Lifecycle Manager, or move to RDS), log
shipping, and a multi-instance setup, which would additionally need the shared rate limiter
described in the README. The instance clones the repository over HTTPS without credentials, so
it must stay public or `repo_url` must point at something the instance can read.
