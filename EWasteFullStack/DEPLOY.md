# Deployment guide

The system has two kinds of parts, and they are deployed differently:

| Part | Where it runs | How people get it |
|---|---|---|
| **Server** (REST API + database) | one always-on machine: a VPS, a cloud VM, a school server, or a PC on your LAN | you deploy it once (below) |
| **Clients** (Swing or JavaFX desktop apps) | every user's / admin's own computer | they get a jar or an installer, then type your server's address on the sign-in window |

So "deploying" = put the server somewhere reachable, protect it with HTTPS, then hand out the client.

---------------------------------------------------------------------------------------------------

## A. Easiest: one computer / local network (no HTTPS)
Good for a classroom, a demo or an office LAN.
```bash
./scripts/setup-sqlite.sh && ./build.sh
EWASTE_ADMIN_PASSWORD='pick-a-password' ./run-server.sh --host=0.0.0.0 --port=8080
```
Find that machine's IP (`ip a`, `ipconfig`), allow port 8080 in its firewall, and on the other computers start the client with
`http://<that-ip>:8080` in the *Server* box. Traffic is unencrypted, so use this only on a network you trust.

To use a local PostgreSQL database instead of SQLite, create the database and application user first (for example, on Ubuntu):
```bash
sudo -u postgres createuser --pwprompt ewaste
sudo -u postgres createdb --owner=ewaste ewaste
```
Then install the JDBC driver:
```bash
bash ./scripts/setup-postgres.sh
EWASTE_STORE=postgres \
EWASTE_DB_URL=jdbc:postgresql://localhost:5432/ewaste \
EWASTE_DB_USER=ewaste EWASTE_DB_PASSWORD='your-secret' \
EWASTE_ADMIN_PASSWORD='your-admin-secret' \
./run-server.sh --host=0.0.0.0 --port=8080
```
On Windows, run `.\scripts\setup-postgres.bat`, set the same `EWASTE_*` values in PowerShell, then run `.\run-server.bat`.

## B. Public server with Docker + automatic HTTPS (recommended)
You need: a Linux VPS (1 vCPU / 1 GB RAM is plenty), a domain name, Docker with the compose plugin.

1. **DNS:** create an `A` record `ewaste.example.com → <server IP>`.
2. **Firewall:** allow inbound 22 (SSH), 80 and 443 only, e.g. `sudo ufw allow 22 && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable`.
3. **Copy the project** to the server (`git clone`, or `scp -r EWasteFullStack user@server:~/ewaste`) and `cd ewaste`.
4. **Configure:** `cp deploy/.env.example .env` and edit it:
   * `EWASTE_DOMAIN=ewaste.example.com`
   * `EWASTE_ADMIN_PASSWORD=` a long random password (only used the first time the database is created)
   * optional `EWASTE_MAIL_*` for email notifications (see README section 5)
5. **Start:** `docker compose up -d --build`. By default this downloads the JDK image and SQLite driver (checksum verified).
6. **Check:** `docker compose ps` (app should become *healthy*), `docker compose logs -f app`, then open `https://ewaste.example.com/api/health` - you should see `{"status":"ok","store":"sqlite ..."}`.
   Caddy fetches and renews the HTTPS certificate by itself; this needs ports 80/443 reachable and the DNS record in place.
7. **Use:** in the client's *Server* box enter `https://ewaste.example.com`. Sign in as `admin` with your password.

> The compose file also publishes the app on `127.0.0.1:8080` (reachable only from the server itself) for debugging; Caddy reaches it over the internal Docker network.
> For a local trial with `EWASTE_DOMAIN=localhost` Caddy uses a self-signed certificate that Java does not trust - for local tests just use `http://localhost:8080`.

**Update to a new version:** copy the new files over the old ones, then `docker compose up -d --build`. Data lives in the `ewaste-data` volume and is kept; database changes are migrated automatically.

**Backup** (stop the app for a consistent copy; takes a second):
```bash
docker compose stop app
docker run --rm -v "$(docker volume ls -q | grep ewaste-data)":/data -v "$PWD":/backup alpine tar czf /backup/ewaste-$(date +%F).tgz -C /data .
docker compose start app
```
Restore by extracting the archive back into the volume the same way. Keep copies off the server. Try a restore once before you need it.

### Use PostgreSQL with Docker Compose

The regular compose setup keeps SQLite as its default. To run PostgreSQL in a separate container instead:

1. Copy `deploy/.env.example` to `.env`. Set a long, unique `EWASTE_DB_PASSWORD` and your admin password.
2. Start the optional PostgreSQL overlay:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.postgres.yml up -d --build
   ```
   The database is on the private Compose network only; the app waits for PostgreSQL to become healthy.
3. Check `docker compose -f docker-compose.yml -f docker-compose.postgres.yml logs -f app postgres`, then open
   `https://ewaste.example.com/api/health`. Its `store` value should say `PostgreSQL database`.
4. Back up PostgreSQL with `pg_dump`:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.postgres.yml exec -T postgres \
     sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > ewaste-$(date +%F).sql
   ```
   Restore into the database with:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.postgres.yml exec -T postgres \
     sh -c 'psql -U "$POSTGRES_USER" "$POSTGRES_DB"' < ewaste-YYYY-MM-DD.sql
   ```

PostgreSQL is a separate store; switching from SQLite does **not** copy existing records. Keep the SQLite database as a backup and plan/import
any data migration separately before switching production traffic. The application creates and additively updates its PostgreSQL tables on startup.
The overlay stores database files in the `ewaste-postgres` Docker volume; do not remove that volume when updating the app.

## C. Server on a VPS without Docker (systemd)
1. On the server: install a JRE 11+ (`sudo apt install openjdk-21-jre-headless`).
2. On your machine: install the driver you plan to use (`bash ./scripts/setup-sqlite.sh` or `bash ./scripts/setup-postgres.sh`), run `bash ./scripts/package.sh`,
   then upload `dist/ewaste-server.jar` and `dist/lib/` to `/opt/ewaste/` on the server.
3. Create a user and folders. For PostgreSQL, also set `EWASTE_STORE=postgres`, `EWASTE_DB_URL`, `EWASTE_DB_USER`, and `EWASTE_DB_PASSWORD`
   in the protected `/etc/ewaste.env`; the database must be reachable and created in advance:
   ```bash
   sudo useradd --system --home /opt/ewaste --shell /usr/sbin/nologin ewaste
   sudo mkdir -p /var/lib/ewaste && sudo chown ewaste /var/lib/ewaste
   echo 'EWASTE_ADMIN_PASSWORD=pick-a-long-password' | sudo tee /etc/ewaste.env && sudo chmod 600 /etc/ewaste.env
   ```
4. Install the service: `sudo cp deploy/ewaste.service /etc/systemd/system/ && sudo systemctl daemon-reload && sudo systemctl enable --now ewaste`.
   Logs: `journalctl -u ewaste -f`. It listens on `127.0.0.1:8080` only.
5. Put HTTPS in front: either install **Caddy** (`reverse_proxy 127.0.0.1:8080` in its Caddyfile - see `deploy/Caddyfile`) or **nginx + certbot** (`deploy/nginx.conf`, then `sudo certbot --nginx -d ewaste.example.com`).
6. Backups: stop the service, copy `/var/lib/ewaste`, start it again (`sudo systemctl stop ewaste && sudo tar czf ... && sudo systemctl start ewaste`).

## D. Container platforms (Render, Fly.io, Railway, Azure/AWS/GCP container services ...)
The `Dockerfile` works anywhere that runs a container. What matters:
* expose port **8080** (or set `EWASTE_PORT`) and let the platform terminate HTTPS;
* with SQLite, attach a **persistent volume mounted at `/data`**; with PostgreSQL, configure an external PostgreSQL service and persistent storage for it;
* set `EWASTE_ADMIN_PASSWORD` (and optional `EWASTE_MAIL_*`) as secrets/environment variables;
* run **exactly one instance** (sessions and the global lock are per process; the app is not built for horizontal scaling).
Platform menus change often, so follow that platform's own "deploy a Dockerfile with a volume" guide.

---------------------------------------------------------------------------------------------------

## Environment variables
| Variable | Default | Meaning |
|---|---|---|
| `EWASTE_PORT` | 8080 | listening port |
| `EWASTE_HOST` | 127.0.0.1 (image: 0.0.0.0) | listening address |
| `EWASTE_DATA` | data (image: /data) | folder for the database |
| `EWASTE_STORE` | auto | `auto` = SQLite if its driver is present (or PostgreSQL when `EWASTE_DB_URL` is set), `sqlite`, `postgres`, or `file` |
| `EWASTE_DB_URL` | unset | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/ewaste` |
| `EWASTE_DB_USER` | unset | PostgreSQL application user |
| `EWASTE_DB_PASSWORD` | unset | PostgreSQL password; provide through a secret/environment variable |
| `EWASTE_ADMIN_PASSWORD` | admin123 | password of the first admin; only used while no admin exists yet |
| `EWASTE_MAIL_ENABLED/HOST/PORT/SECURITY/USERNAME/PASSWORD/FROM` | off | email notifications (`SECURITY` = NONE, STARTTLS or SSL) |
| `EWASTE_MAIL_FILE` | config/mail.properties | optional properties file (variables above override it) |

## Distributing the client
* **Jar:** `./scripts/package.sh` → send `dist/ewaste-client.jar`. Users need a JRE 11+ and run `java -jar ewaste-client.jar https://ewaste.example.com` (or double-click and type the address once).
* **Installer:** `./scripts/make-installer.sh deb|rpm|dmg|pkg` (Linux/macOS) or `scripts\make-installer.bat` (Windows, needs the WiX toolset). `jpackage` bundles its own Java runtime, so users install nothing else. Build on the OS you are targeting.
  Unsigned installers trigger "unknown publisher" warnings on Windows and macOS; code signing is a separate paid step.
* **JavaFX client:** `package.sh` also builds `ewaste-client-fx.jar` plus `lib-fx/` when JavaFX is installed; run it with
  `java --module-path lib-fx --add-modules javafx.controls -jar ewaste-client-fx.jar`.
  Remember it has not been compiled by the author - build and try it (README section 3) before handing it to users.

## Security checklist before going public
- [ ] HTTPS in front of the server (sections B / C); never expose plain `:8080` to the internet.
- [ ] Admin password changed (the dashboard warns while the default is in use).
- [ ] Firewall allows only SSH, 80 and 443.
- [ ] Backups scheduled (cron) and a restore tested.
- [ ] Email password supplied through an environment variable / secret, not committed to a repository.
- [ ] PostgreSQL password supplied through a secret/environment variable when PostgreSQL is enabled.
- [ ] You understand the limits: sessions are in memory (restart = everyone signs in again), no rate limit on registration or on the public tracker,
      logins are locked after 5 failures per username for 5 minutes, there is no password-reset-by-email flow (an admin can only deactivate users; change a forgotten
      password by editing the database or recreating the account).

## Troubleshooting
| Symptom | Fix |
|---|---|
| Client says "Server not reachable" | check the URL (https vs http, port), `curl https://host/api/health`, firewall, `docker compose ps` |
| Caddy cannot get a certificate | DNS record not pointing at the server yet, or ports 80/443 blocked; see `docker compose logs caddy` |
| `store` in `/api/health` says "snapshot file" | the SQLite driver is missing: re-run `scripts/setup-sqlite.sh` (or rebuild the image) |
| PostgreSQL driver missing | run `scripts/setup-postgres.sh` and rebuild/restart, or rebuild the Docker image |
| PostgreSQL connection refused | check `EWASTE_DB_URL`, credentials, firewall/network access, and that PostgreSQL is accepting connections |
| Data disappeared after redeploy | no persistent volume on `/data` (container platforms) |
| "Too many failed sign-ins" (429) | wait 5 minutes or restart the server |
| Emails not arriving | Announcements & email → *Send test email*; read "Last error"; Gmail needs an App password |
| `build-fx` fails | see README section 3; JavaFX jars must match your OS, and the JavaFX client is the least-tested part |
