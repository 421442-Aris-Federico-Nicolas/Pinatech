# Production checklist

Mercado Pago Checkout Pro and Zipnova delivery are implemented but must remain disabled or on test credentials where available
until every applicable release gate below is complete. Passing this list is a manual release
decision, not an automatic claim that the application is production-ready.

## Commerce

- Real catalog, prices and stock replace the development sample data.
- The first production administrator is provisioned through a controlled one-time procedure.
- Payment and delivery providers are selected and tested in sandbox.
- Every active delivery product has reviewed weight, dimensions, classification and vertical-handling data.
- Legal, privacy, warranty, return and cancellation content is reviewed for Argentina.
- Fiscal data and invoicing requirements are reviewed with an accountant or invoicing provider.

## Security

- Production starts only with the explicit `prod` profile.
- Secrets live outside the repository in a root-owned `0600` file or a suitable secret
  manager, and differ between staging and production.
- Caddy is the only service publishing host ports; HTTP redirects to valid automatic HTTPS.
- CORS contains the exact HTTPS storefront origin.
- WAF and distributed rate limits protect authentication and order creation.
- Dependency, container and secret scanning run in CI.
- SSH is key-only, root login is disabled and the firewall exposes only approved SSH, 80/443.
- Resend uses a verified sending domain and account-action links point only to the canonical storefront.
- `SELLER_NOTIFICATION_EMAIL` contains exactly one monitored, valid mailbox; order-created and payment-approved notifications were tested for Mercado Pago and bank transfer.
- `EMAIL_LOGO_URL` is a public HTTPS image without credentials, query parameters or fragments, and returns `200` with an image content type.
- Email verification, password reset and email change are tested without leaking account existence.
- Password reset and email change invalidate existing sessions.

## Data and reliability

- PostgreSQL is private and has encrypted offsite backups plus tested point-in-time recovery.
- Product images and private ticket attachments have encrypted offsite, versioned backups.
- Backup retention, RPO and RTO are documented.
- A restore has been tested in an isolated environment.
- Database migrations are rehearsed against representative data before deployment.
- CPU, memory, connection pool and request timeouts are configured from load-test results.

## Delivery

- CI builds immutable images tagged by commit and publishes them to a registry.
- Production Compose contains no `build` and deploys registry references pinned by digest.
- Staging deploys the same image digests that production will receive.
- Production requires approval, smoke tests and a documented rollback.
- Backend readiness and liveness probes control traffic.
- The application shell and deployment marker are not cached; hashed bundles remain immutable.
- Missing asset paths return `404` instead of the Angular shell.
- Incompatible frontend/backend changes use a frontend-first expand/contract rollout.
- Logs, metrics, traces and alerts cover latency, errors, database saturation and order failures.

## Release gate

- Backend tests pass.
- Frontend tests and production build pass.
- Container images build successfully.
- Desktop and mobile checkout tests pass.
- Duplicate order and duplicate webhook scenarios are safe.
- Zipnova quote expiry, empty quotes, provider outage, ambiguous create, reconciliation and damaged delivery are verified.
- Zipnova account/origin IDs, exact webhook URL, URL-safe webhook secret and production confirmation are independently reviewed.
- Label and dispatch-document downloads, tracking links and customer ownership checks pass end-to-end.
- Customer and seller email outbox events retry independently without duplicate notifications.
- Payment rejection, expiration, cancellation and refund scenarios are verified.
- Registration, email verification, password recovery and email change pass end-to-end.
- An open tab detects a new frontend version and offers an explicit update without discarding unsaved work automatically.
- Verified customers can create pickup orders and the selected pickup snapshot remains immutable.
- A backup restore and deployment rollback have been demonstrated.
- The exact production payment and shipping webhook URLs, Mercado Pago collector ID and Zipnova account/origin IDs were independently verified.
- On-call ownership, monitoring, alerts, incident response and secret rotation were tested.

Operational procedure: [`production-deployment.md`](production-deployment.md).
