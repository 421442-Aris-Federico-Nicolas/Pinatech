# Production checklist

Mercado Pago Checkout Pro is implemented but must remain disabled or on sandbox credentials
until every applicable release gate below is complete. Passing this list is a manual release
decision, not an automatic claim that the application is production-ready.

## Commerce

- Real catalog, prices and stock replace the development sample data.
- The first production administrator is provisioned through a controlled one-time procedure.
- Payment and delivery providers are selected and tested in sandbox.
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
- Logs, metrics, traces and alerts cover latency, errors, database saturation and order failures.

## Release gate

- Backend tests pass.
- Frontend tests and production build pass.
- Container images build successfully.
- Desktop and mobile checkout tests pass.
- Duplicate order and duplicate webhook scenarios are safe.
- Payment rejection, expiration, cancellation and refund scenarios are verified.
- A backup restore and deployment rollback have been demonstrated.
- The exact production webhook URL and production collector ID were independently verified.
- On-call ownership, monitoring, alerts, incident response and secret rotation were tested.

Operational procedure: [`production-deployment.md`](production-deployment.md).
