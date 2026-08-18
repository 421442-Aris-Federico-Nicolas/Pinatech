# Railway backend and Vercel frontend

The production topology uses these public origins:

- Storefront: `https://pinatech.com.ar` on Vercel.
- API: `https://api.pinatech.com.ar` on Railway.
- PostgreSQL: Supabase Session Pooler over TLS.
- Uploaded images: a Railway Volume mounted at `/app/uploads`.

Deploy Railway first. The Vercel build embeds the final API origin.

## Stage 1: Railway backend

### Create the service

1. Push the repository without `.env`, database dumps, or media backups.
2. Create a Railway project from the Git repository.
3. Set the service root directory to `/backend`.
4. Set the Railway config file path to `/backend/railway.json`.
5. Import the variables from `backend/railway.env.example` and replace every placeholder or empty secret.
6. Do not define `PORT`; Railway injects it and Spring reads it automatically.

Keep `MP_ENABLED=false` until all Mercado Pago production values are ready. Never combine a production `APP_USR-` token with `MP_ENVIRONMENT=sandbox`.

### Attach persistent storage

1. Attach one Railway Volume to the backend service.
2. Set its mount path to `/app/uploads`.
3. Keep a single backend replica while files use this local volume.

The image entrypoint starts as root only to set volume ownership, then runs Java as the unprivileged `spring` user. Do not set `RAILWAY_RUN_UID=0`.

### Copy the existing images

Export the current Docker volume contents on the local machine:

```powershell
New-Item -ItemType Directory -Force -Path ".\railway-uploads"
docker cp computer-store-backend:/app/uploads/. ".\railway-uploads"
```

After installing and linking the Railway CLI, open the volume browser and upload the files into its root. File names must remain unchanged because PostgreSQL stores them as UUID keys.

```powershell
railway login
railway link
railway volume browse /
```

There should be 9 image files after the initial migration. Keep the Docker volume and local backup until every image responds from Railway.

### Configure networking

1. Generate a temporary Railway domain.
2. Verify `https://RAILWAY_DOMAIN/actuator/health/readiness` returns `UP`.
3. Add `api.pinatech.com.ar` as a custom domain.
4. Create the CNAME and TXT records Railway provides.
5. Verify `https://api.pinatech.com.ar/api/health` before deploying Vercel.

The service configuration already uses `/actuator/health/readiness`, a 180-second startup timeout, graceful draining, and no deployment overlap because the service owns a single writable volume.

## Stage 2: Vercel frontend

### Create the project

1. Import the same Git repository into Vercel.
2. Set the project root directory to `frontend`.
3. Keep the Angular framework preset.
4. Vercel reads `frontend/vercel.json`, runs `npm run build:vercel`, and publishes `dist/frontend/browser`.
5. Assign `pinatech.com.ar` as the production domain.
6. Redirect `www.pinatech.com.ar` to `pinatech.com.ar` in Vercel so there is one canonical browser origin.

The Vercel build uses `frontend/src/environments/environment.vercel.ts` and calls `https://api.pinatech.com.ar/api`. Docker builds continue to use the relative `/api` proxy.

### Browser security

Railway must contain exactly:

```text
CORS_ALLOWED_ORIGIN=https://pinatech.com.ar
PUBLIC_BASE_URL=https://pinatech.com.ar
REFRESH_COOKIE_SECURE=true
```

The storefront and API are different origins but the same `pinatech.com.ar` site. This allows the existing secure `SameSite=Strict` refresh cookie to work with credentialed API requests without weakening it to `SameSite=None`.

Vercel preview domains are intentionally not accepted by the production CORS policy. Use the canonical production domain for authentication tests.

## Release checks

Run these checks after both DNS records have valid TLS certificates:

1. `GET https://api.pinatech.com.ar/actuator/health/readiness` returns `UP`.
2. `GET https://api.pinatech.com.ar/api/products` returns the catalog.
3. Product images return `200` from `/api/products/images/{id}/content`.
4. Registration, login, page reload, token refresh, and logout work from `https://pinatech.com.ar`.
5. An authenticated image upload remains available after restarting the Railway service.
6. Flyway reports schema version `15` in Railway logs.
7. No secret appears in Vercel variables, frontend bundles, Git history, or deployment logs.
