#!/usr/bin/env bash
# Rolls the EC2 instance to a given commit and image. Invoked by the deploy workflow through
# SSM Run Command (as root), but can also be run by hand from an SSM session:
#   sudo /opt/keypass/deploy/deploy.sh <git-sha> <ecr-image-uri>
set -euo pipefail

SHA="${1:?usage: deploy.sh <git-sha> <image>}"
IMAGE="${2:?usage: deploy.sh <git-sha> <image>}"
APP_DIR="${APP_DIR:-/opt/keypass}"

export HOME="${HOME:-/root}"
TOKEN=$(curl -fsS -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')
AWS_REGION=$(curl -fsS -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/placement/region)
export AWS_REGION AWS_DEFAULT_REGION="$AWS_REGION"

cd "$APP_DIR"

echo "==> Checking out $SHA"
git fetch --quiet origin
git checkout --quiet --force "$SHA"

echo "==> Fetching secrets from SSM"
./deploy/fetch-secrets.sh .env

echo "==> Logging in to ECR"
aws ecr get-login-password | docker login --username AWS --password-stdin "${IMAGE%%/*}"

compose() {
  KEYPASS_IMAGE="$IMAGE" docker compose -f docker-compose.yml -f docker-compose.prod.yml "$@"
}

echo "==> Starting $IMAGE"
compose pull --quiet server
compose up -d --no-build --remove-orphans

echo "==> Waiting for the server to report healthy"
for _ in $(seq 1 30); do
  if compose exec -T caddy wget -qO- http://server:8080/actuator/health 2>/dev/null | grep -q '"UP"'; then
    echo "==> Healthy"
    docker image prune -af --filter "until=168h" >/dev/null || true
    exit 0
  fi
  sleep 5
done

echo "Server did not become healthy in time; recent logs:" >&2
compose logs --tail=100 server >&2
exit 1
