# Runbook de deploy — ribero-be

Sigue el mismo patrón que `darmoz-auth` (mismo servidor `darmoz@192.168.1.23`,
mismo Postgres compartido `nexora`, mismas redes Traefik `proxy`/`data`). Este
servicio no emite tokens, solo los verifica: no necesita llave privada, solo
la llave pública RSA que ya usa `darmoz-auth`.

## Primer deploy

1. `mkdir -p /opt/infra/services/ribero-be/keys`
2. Copiar la llave pública de `darmoz-auth` (la misma que firma sus tokens)
   a `/opt/infra/services/ribero-be/keys/public_key.pem` (`scp`, nunca
   por git). Es la misma llave pública que ya está desplegada para
   `darmoz-auth`, no se genera una nueva.
3. Copiar `deploy/compose.yaml` → `/opt/infra/services/ribero-be/compose.yaml`.
4. Copiar `deploy/.env.example` → `/opt/infra/services/ribero-be/.env`,
   completar y `chmod 600`.
5. Registrar el runner self-hosted para este repo (si no existe ya uno
   reutilizable), igual que se hizo para `darmoz-auth`:
   ```bash
   mkdir -p ~/actions-runner-ribero-be && cd ~/actions-runner-ribero-be
   curl -o runner.tar.gz -L https://github.com/actions/runner/releases/download/v<version>/actions-runner-linux-x64-<version>.tar.gz
   tar xzf runner.tar.gz
   ./config.sh --url https://github.com/darmozcs/ribero-be --token <TOKEN> \
     --unattended --name darmoz-server-ribero-be --labels self-hosted,linux,x64 --work _work
   sudo ./svc.sh install darmoz
   sudo ./svc.sh start
   ```
6. `git push` a `main` → dispara CI: test → build & push a GHCR → deploy
   (`docker compose pull && up -d` en el server vía el runner).
7. Si `docker compose pull` da `denied`: hacer público el paquete en
   `github.com/darmozcs?tab=packages` → `ribero-be` → Package settings →
   Public (o `docker login ghcr.io` en el server con un token
   `read:packages`).
8. Verificar: `curl -sk https://darmozsc.duckdns.org/ribero-api/actuator/health`
   y `docker logs ribero-be --tail 50`.

## Rollback

Fijar `APP_IMAGE` en el `.env` del server a un tag `:<sha>` anterior (cada
build de CI publica `latest` y `${{ github.sha }}`), luego:
```bash
docker compose pull && docker compose up -d
```

## Logs

```bash
docker compose logs -f
```

## Notas

- Las tablas propias (`rb_users`, `rb_locales`, `rb_ventas`, ...) y la tabla de historial de
  Flyway (`rb_flyway_schema_history`) están prefijadas para convivir en la
  misma base `nexora` que usa `darmoz-auth` sin colisionar con sus `auth_*`.
- `app.expected-application-id` queda sin configurar hasta que se
  provisione un UUID de Application dedicado en darmoz-auth para este
  servicio; mientras tanto el filtro solo valida firma RS256 + issuer.
