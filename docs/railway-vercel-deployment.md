# Railway backend and Vercel frontend

The production topology uses these public origins:

- Storefront: `https://pinatech.com.ar` on Vercel.
- API: `https://api.pinatech.com.ar` on Railway.
- PostgreSQL: Supabase Session Pooler over TLS.
- Uploaded images: a Railway Volume mounted at `/app/uploads`.

For additive API changes, deploy Railway first because the Vercel build embeds the final API
origin. For removed or incompatible API behavior, deploy a frontend that no longer depends on
the old contract first and keep the deprecated backend contract during a bounded compatibility
window. The version-update notice helps users refresh open tabs, but it is not proof that every
old client has been retired. Contract the backend only in a later release after the compatibility
window and server-side usage checks are complete.

## Stage 1: Railway backend

### Create the service

1. Push the repository without `.env`, database dumps, or media backups.
2. Create a Railway project from the Git repository.
3. Set the service root directory to `/backend`.
4. Set the Railway config file path to `/backend/railway.json`.
5. Import the variables from `backend/railway.env.example` and replace every placeholder or empty secret.
6. Do not define `PORT`; Railway injects it and Spring reads it automatically.

Keep `MP_ENABLED=false` until all Mercado Pago production values are ready. Never combine a production `APP_USR-` token with `MP_ENVIRONMENT=sandbox`.

Keep `BANK_TRANSFER_ENABLED=false` until every `BANK_TRANSFER_*` value has been reviewed, the volume is attached and an administrator is assigned to the proof queue. The CBU must contain exactly 22 digits. Bank data are returned only from authenticated order endpoints and are never included in email.

Keep `RESEND_ENABLED=false` until `RESEND_API_KEY`, `RESEND_FROM`, `STOREFRONT_BASE_URL`, `EMAIL_LOGO_URL` and the single `SELLER_NOTIFICATION_EMAIL` recipient are correct and the sender domain is verified. `EMAIL_LOGO_URL` must be a public HTTPS image URL without credentials, query parameters or a fragment; it is independent of the storefront URL and is loaded directly by email clients rather than attached as CID content. Enable pickup only after every `PICKUP_*` public field has been reviewed.

Keep `ZIPNOVA_ENABLED=false` until the production account, origin and credentials are verified. Use an environment-specific `ZIPNOVA_SOURCE`, register `https://api.pinatech.com.ar/api/shipping/webhooks/zipnova/{ZIPNOVA_WEBHOOK_SECRET}` in Zipnova, and never point the callback at the Vercel storefront. Railway edge and application logs must be checked to confirm that the secret-bearing path is not retained before setting both `ZIPNOVA_ENABLED=true` and `ZIPNOVA_PRODUCTION_CONFIRMATION=true`.

### Attach persistent storage

1. Attach one Railway Volume to the backend service.
2. Set its mount path to `/app/uploads`.
3. Keep a single backend replica while files use this local volume.

Private transfer originals are stored under `PRIVATE_DOCUMENTS_SUBROOT` in this same volume. They have no download endpoint; administrators receive only sanitized PNG previews. Approved files are retained for five years and rejected files for 90 days, so database and volume backups must use coordinated restore points.

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
MP_STOREFRONT_BASE_URL=https://pinatech.com.ar
MP_WEBHOOK_BASE_URL=https://api.pinatech.com.ar
REFRESH_COOKIE_SECURE=true
```

`MP_STOREFRONT_BASE_URL` is used only for the browser return after checkout. Mercado Pago sends
`POST` notifications to `${MP_WEBHOOK_BASE_URL}/api/payments/webhooks/mercado-pago`; pointing that
URL at Vercel returns `405 Method Not Allowed` and leaves paid orders pending in the application.

The storefront and API are different origins but the same `pinatech.com.ar` site. This allows the existing secure `SameSite=Strict` refresh cookie to work with credentialed API requests without weakening it to `SameSite=None`.

Vercel preview domains are intentionally not accepted by the production CORS policy. Use the canonical production domain for authentication tests.

## Release checks

Run these checks after both DNS records have valid TLS certificates:

1. `GET https://api.pinatech.com.ar/actuator/health/readiness` returns `UP`.
2. `GET https://api.pinatech.com.ar/api/products` returns the catalog.
3. Product images return `200` from `/api/products/images/{id}/content`.
4. Registration, login, page reload, token refresh, and logout work from `https://pinatech.com.ar`.
5. An authenticated image upload remains available after restarting the Railway service.
6. Flyway reports schema version 27 in Railway logs.
7. No secret appears in Vercel variables, frontend bundles, Git history, or deployment logs.
8. A Mercado Pago webhook test returns `200` from
   `https://api.pinatech.com.ar/api/payments/webhooks/mercado-pago`.
9. Registration sends a verification email whose link returns to `https://pinatech.com.ar/verify-email`.
10. `GET https://pinatech.com.ar/pinatech-favicon.png` returns `200` with an image content type, and the verification email displays that public logo.
11. Password recovery and email change complete successfully and invalidate prior sessions.
12. `GET https://api.pinatech.com.ar/api/checkout/capabilities` exposes only the reviewed pickup location.
13. `GET https://pinatech.com.ar/version.json` returns a nonempty version with `Cache-Control: no-store`.
14. `index.html` and application routes are not cached, while generated JS/CSS bundles with a content hash are immutable.
15. A nonexistent old chunk returns `404` instead of the Angular HTML shell.
16. An open tab detects a later frontend deployment and keeps the `Actualizar` action visible until the user activates it.
17. Creating an order and approving Mercado Pago or bank-transfer payment sends the expected seller notifications to `SELLER_NOTIFICATION_EMAIL` with a working admin order link.
18. A delivery quote returns only persisted Zipnova options and charges the selected `price_incl_tax` without applying the bank-transfer discount to shipping.
19. Payment approval creates one Zipnova shipment, exposes its tracking and documents, and duplicate worker/webhook execution does not create another shipment or email.
20. Reconciliation covers delivery, damage and cancellation states; disabling Zipnova with pending work stops provider calls without losing queued records.
21. Railway request logs, application logs and APM traces do not contain `ZIPNOVA_WEBHOOK_SECRET`; rotate it immediately if any layer retained the callback path.
