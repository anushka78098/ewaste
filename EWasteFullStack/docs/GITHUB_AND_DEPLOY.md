# Beginner guide: GitHub and deployment

This project has a **desktop frontend** (Swing or JavaFX) and a **server** (API + database). GitHub stores the source code; the server runs on a VPS; each user runs the desktop client on their own computer. GitHub Pages is not a host for this Java desktop application.

## 1. Run and test on Windows

Open PowerShell in the project folder (the folder containing `build.bat` and `run-server.bat`):

```powershell
cd "$HOME\Downloads\EWasteFullStack\EWasteFullStack"
java -version
javac -version
.\scripts\setup-sqlite.bat
.\test.bat
```

The SQLite setup is needed once. `test.bat` compiles the code and runs the existing API and service tests. To start the app locally, open **two** PowerShell windows in the same folder:

```powershell
# Window 1: start the backend
.\run-server.bat
```

```powershell
# Window 2: start the Swing desktop client
.\run-client.bat
```

In the sign-in window, use `http://localhost:8080`. For the JavaFX client, install JavaFX once and run:

```powershell
.\scripts\setup-javafx.bat
.\build-fx.bat
.\run-client-fx.bat
```

## 2. Connect the local database

By default the application uses SQLite; you do not need to create tables manually. The setup script installs the JDBC driver, and the server creates
`data\ewaste.db` the first time it starts. Database schema updates are additive, so normal upgrades keep existing records.

With the server running, check which store it selected:

```powershell
Invoke-RestMethod http://localhost:8080/api/health | Format-List
```

With the default setup, the `store` value should say SQLite. If it says file-backed snapshot, check that `lib\sqlite-jdbc-3.36.0.3.jar` exists and restart the server. PostgreSQL is also supported: install `.\scripts\setup-postgres.bat`, configure `EWASTE_STORE`, `EWASTE_DB_URL`, `EWASTE_DB_USER`, and `EWASTE_DB_PASSWORD`, and restart. The app creates its schema in the selected database, but switching stores does not copy existing records. Stop the local server with **Ctrl+C** before making a simple SQLite backup:

```powershell
Copy-Item .\data\ewaste.db .\data\ewaste.db.bak
```

The local database is ignored by Git. Never upload it as source code; it contains your application data.

## 3. Review and push your changes to GitHub

The configured repository is `anushka78098/ewaste`, and its default branch is `main`. In PowerShell, go to the repository root (the folder containing `.git`):

```powershell
cd "$HOME\Downloads\EWasteFullStack"
git status
git branch --show-current
git remote -v
```

Before staging, check the status for local passwords, `.env` files, database files, or other private data. The real deployment `.env` and local `config/mail.properties` / `config/decision.properties` files are ignored by Git; keep passwords out of source code and chat.

Stage, inspect, commit, and push:

```powershell
git add -A
git diff --cached --check
git diff --cached --stat
git status
git commit -m "Improve the E-Waste Manager"
git push -u origin main
```

Review the staged file list before committing. If a private file appears there, unstage only that file with `git restore --staged path\to\file`, then check `git status` again. If you are working on a branch other than `main`, replace `main` in the push command with the branch shown by `git branch --show-current`.

This repository currently uses an SSH remote (`git@github.com:...`). If Git reports `Permission denied (publickey)` and you have not configured an SSH key, switch the remote to HTTPS and retry:

```powershell
git remote set-url origin https://github.com/anushka78098/ewaste.git
git push -u origin main
```

Sign in through the Git Credential Manager prompt if GitHub asks. Do not put a GitHub password or access token in a command or source file.

## 4. Deploy the server and database on an Ubuntu VPS

You need an Ubuntu VPS that stays online, a domain name, and access to change the domain's DNS. The example uses Docker Compose and Caddy to provide HTTPS automatically.

1. In your domain provider's DNS settings, point an `A` record (for example `ewaste.example.com`) to the VPS's public IP address.
2. Connect to the VPS using SSH. Install Git, Docker Engine, and the Docker Compose plugin. On Ubuntu releases with these packages:

   ```bash
   sudo apt update
   sudo apt install -y git docker.io docker-compose-v2
   sudo systemctl enable --now docker
   docker --version
   docker compose version
   ```

   If `docker-compose-v2` is not available for your Ubuntu release, install Docker Engine and its Compose plugin using Docker's official Ubuntu instructions.
3. Allow SSH before enabling the firewall, then allow HTTPS traffic:

   ```bash
   sudo ufw allow OpenSSH
   sudo ufw allow 80/tcp
   sudo ufw allow 443/tcp
   sudo ufw enable
   ```

   If your cloud provider has a separate firewall, allow ports 80 and 443 there too.
4. Clone the repository and move into the application folder. The project is in the `EWasteFullStack` subfolder:

   ```bash
   cd ~
   git clone https://github.com/anushka78098/ewaste.git ewaste
   cd ~/ewaste/EWasteFullStack
   ```

5. Create the private environment file and edit it:

   ```bash
   cp deploy/.env.example .env
   nano .env
   ```

   Set `EWASTE_DOMAIN` to the real domain from step 1. Replace `EWASTE_ADMIN_PASSWORD` with a strong unique password; `openssl rand -hex 24` can generate one. Save the file in the editor. Do not add this real `.env` file to GitHub.
6. Build and start the application:

   ```bash
   sudo docker compose up -d --build
   sudo docker compose ps
   sudo docker compose logs -f app
   ```

   Press **Ctrl+C** to exit the live log view; the server continues running. Wait for the app to become healthy, then check:

   ```bash
   curl -fsS https://ewaste.example.com/api/health
   ```

   Replace the example hostname with yours. Caddy requests the HTTPS certificate after DNS points to this VPS and ports 80/443 are reachable.
7. On your Windows computer, start `.\run-client.bat` from the project folder and enter `https://ewaste.example.com` in the **Server** box. The JavaFX client can use the same server address.

The default container setup includes the SQLite JDBC driver and stores the database at `/data/ewaste.db`. Docker Compose mounts a persistent `ewaste-data` volume at `/data`, so the database survives container rebuilds and restarts. To use PostgreSQL, copy `deploy/.env.example` to `.env`, set a unique `EWASTE_DB_PASSWORD`, then start with `docker compose -f docker-compose.yml -f docker-compose.postgres.yml up -d --build`. It uses a separate `ewaste-postgres` volume. Confirm `/api/health` reports the selected store. Keep backups before server maintenance; see [DEPLOY.md](../DEPLOY.md) for SQLite and PostgreSQL backup procedures.

## 5. Publish an update later

After you commit and push changes to the `main` branch, connect to the VPS and run:

```bash
cd ~/ewaste/EWasteFullStack
git pull --ff-only origin main
sudo docker compose up -d --build
sudo docker compose ps
```

The server image is rebuilt and restarted; the persistent database volume is retained. To inspect errors, run `sudo docker compose logs -f app`.

## Common beginner errors

| Message | What to check |
|---|---|
| `git push` says `Permission denied (publickey)` | Set up an SSH key or switch `origin` to the HTTPS URL above. |
| `docker compose` is not recognized | Install the Docker Compose plugin and confirm `docker compose version`. |
| HTTPS check fails | Verify the DNS `A` record, cloud firewall, and VPS firewall; ports 80 and 443 must be reachable. |
| Client cannot connect | Enter the full URL including `https://` and check `https://your-domain/api/health` in a browser. |
| Data disappears after redeployment | Confirm the Compose file has the persistent `ewaste-data` volume; do not remove volumes with `docker compose down -v`. |
