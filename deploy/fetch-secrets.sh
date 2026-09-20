#!/usr/bin/env bash
# Writes .env for docker compose from AWS SSM Parameter Store (SecureString parameters under
# /keypass/). Run on the EC2 instance; its instance role needs ssm:GetParametersByPath and
# kms:Decrypt. Nothing secret is stored in the repository or the AMI.
set -euo pipefail

PREFIX="${SSM_PREFIX:-/keypass}"
OUT="${1:-.env}"

umask 077
aws ssm get-parameters-by-path --path "$PREFIX" --with-decryption --query 'Parameters[].[Name,Value]' --output text |
  while IFS=$'\t' read -r name value; do
    printf '%s=%s\n' "${name##*/}" "$value"
  done >"$OUT"

echo "Wrote $(wc -l <"$OUT" | tr -d ' ') variables to $OUT"
