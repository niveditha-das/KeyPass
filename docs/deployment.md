# Deployment: a single EC2 instance

This is a runbook, not a deployed environment: standing it up needs a real AWS account and costs
real money. Everything below is prepared and checked in (`docker-compose.prod.yml`,
`deploy/Caddyfile`, `deploy/fetch-secrets.sh`) so that going live is a handful of commands.

## Shape

```
Internet ──443──▶ Caddy (auto HTTPS) ──▶ server:8080 ──▶ Postgres (compose network only)
```

Neither Postgres nor the application port is published to the host; Caddy is the only listener.
`/actuator/*` is answered with a 404 at the proxy, so Prometheus (if enabled) scrapes over the
internal compose network only.

## One-off setup

1. Launch a small instance (t3.small is plenty for one server) running Amazon Linux 2023 or
   Ubuntu, with Docker and the Compose plugin installed. Open only ports 80 and 443 (plus 22
   from your own IP).
2. Point a DNS `A` record for your hostname at the instance's Elastic IP.
3. Generate a signing key pair locally with `java scripts/GenerateSigningKey.java`, then store the
   values as `SecureString` parameters in SSM Parameter Store:
   `/keypass/KEYPASS_SIGNING_KEY`, `/keypass/KEYPASS_SIGNING_PUBLIC_KEY`,
   `/keypass/DB_PASSWORD`, `/keypass/KEYPASS_DOMAIN`.
4. Attach an instance role allowing `ssm:GetParametersByPath` on `/keypass` and `kms:Decrypt`.

## Deploy

```
git clone <repo> && cd Keypass
./deploy/fetch-secrets.sh            # writes .env (mode 600) from SSM
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
curl https://$KEYPASS_DOMAIN/api/v1/.well-known/keypass-keys   # public signing keys; /actuator/* is 404 by design
```

The prod profile refuses to start without `KEYPASS_SIGNING_KEY` (an ephemeral key would silently
invalidate every issued credential on restart) and disables Swagger UI and the OpenAPI endpoint.

## Not covered

Backups (enable RDS or snapshot the `keypass-db` volume), log shipping, and a multi-instance
setup, which would additionally need the shared rate limiter described in the README.
