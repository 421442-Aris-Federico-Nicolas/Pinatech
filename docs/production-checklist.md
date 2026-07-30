# Production checklist

This repository is prepared for provider integration but is not ready to accept real payments until every release gate below is complete.

## Commerce

- Real catalog, prices and stock replace the development sample data.
- The first production administrator is provisioned through a controlled one-time procedure.
- Payment and delivery providers are selected and tested in sandbox.
- Legal, privacy, warranty, return and cancellation content is reviewed for Argentina.
- Fiscal data and invoicing requirements are reviewed with an accountant or invoicing provider.

## Security

- Production starts only with the explicit `prod` profile.
- Secrets come from a cloud secret manager and differ between staging and production.
- TLS terminates at the managed load balancer/CDN and HTTP redirects to HTTPS.
- CORS contains the exact HTTPS storefront origin.
- WAF and distributed rate limits protect authentication and order creation.
- Dependency, container and secret scanning run in CI.

## Data and reliability

- PostgreSQL is managed, private and configured with point-in-time recovery.
- Product images and private ticket attachments use durable object storage with encryption and lifecycle backups.
- Backup retention, RPO and RTO are documented.
- A restore has been tested in an isolated environment.
- Database migrations are rehearsed against representative data before deployment.
- CPU, memory, connection pool and request timeouts are configured from load-test results.

## Delivery

- CI builds immutable images tagged by commit and publishes them to a registry.
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
