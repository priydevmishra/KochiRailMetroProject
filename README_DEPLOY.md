# Deploy KochiRailMetro to DigitalOcean (Ubuntu)

This guide helps you deploy the Spring Boot app with MySQL using Docker and Docker Compose on a DigitalOcean Ubuntu droplet.

## Prerequisites
- Ubuntu 22.04+ droplet with at least 2GB RAM recommended
- A non-root user with sudo
- Domain (optional) if you want HTTPS via reverse proxy

## 1) Install Docker and Docker Compose

```bash
# Run on your Ubuntu server (SSH)
sudo apt-get update -y ; sudo apt-get install -y ca-certificates curl gnupg ; \
install -m 0755 -d /etc/apt/keyrings ; \
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg ; \
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo $VERSION_CODENAME) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null ; \
sudo apt-get update -y ; \
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin ; \
sudo usermod -aG docker $USER
# Log out and back in to apply group change
```

If your Ubuntu image doesn't have the Compose v2 plugin, install it or use `docker compose` available above.

## 2) Clone your repo on the server

```bash
git clone https://github.com/priydevmishra/KochiRailMetroProject.git
cd KochiRailMetroProject/KochiRailMetro
```

## 3) Prepare environment and credentials
- Copy `.env.example` to `.env` and fill values.
- Create a `credentials/` folder and place your `gmail-credentials.json` inside. Do NOT commit this file.

```bash
cp .env.example .env
mkdir -p credentials tokens logs
# securely transfer gmail-credentials.json from your local machine to server
# e.g. scp gmail-credentials.json user@server:/path/KochiRailMetro/credentials/
```

## 4) Build and run with Compose

```bash
# Build the app image and start MySQL + app
docker compose up -d --build

# Check logs
docker compose logs -f app
```

The app will be available at `http://<server-ip>:8091` unless you put it behind a reverse proxy.

Data persistence:
- MySQL data is stored in Docker volume `db_data`.
- App uploads/logs are in volumes `app_uploads` and `app_logs`. Logs are also bind-mounted to `./logs/`.
- Gmail OAuth tokens are in `./tokens/` (mounted to `/app/tokens`).

## 5) Configure firewall (optional)
```bash
sudo ufw allow 22/tcp
sudo ufw allow 8091/tcp
sudo ufw enable
```

## 6) Optional: Reverse proxy with Nginx and HTTPS
You can terminate TLS and forward to the app container.
- Install Nginx, get certificates via Certbot.
- Point your domain A-record to the droplet IP.
- Configure Nginx to proxy_pass to `http://localhost:8091`.

## 7) Maintenance
- Update images: `docker compose pull` then `docker compose up -d --build`
- Check health: `docker ps`, `docker compose ps`, `docker compose logs`
- Backups: dump MySQL with `mysqldump` or snapshot volumes.

## Environment variables reference
See `.env.example` for full list. Critical variables:
- MySQL: `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`
- App: `JWT_SECRET`, `BASE_URL`, `SERVER_PORT_HOST`
- Mail: `MAIL_USERNAME`, `MAIL_PASSWORD`
- Google OAuth (if used): `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`

## Notes on Gmail API credentials
- The application reads Gmail API OAuth credentials at `/app/credentials/gmail-credentials.json` in the container.
- Do not bake secrets into images. Mount them with the Compose `volumes` line provided.

## Production tips
- Set `BASE_URL` to your public URL (domain or IP with scheme)
- Generate a strong `JWT_SECRET`
- Consider enabling actuator health endpoints
- Use a managed DB if you prefer (DigitalOcean Managed MySQL). Update `SPRING_DATASOURCE_URL` accordingly.

---

Happy shipping!
