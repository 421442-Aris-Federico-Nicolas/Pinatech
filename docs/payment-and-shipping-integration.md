# Payment and shipping integration

Mercado Pago Checkout Pro, manually reviewed bank transfers and Zipnova home delivery are implemented.

## Runtime switch

`GET /api/checkout/capabilities` reports each method only when its configuration is enabled and valid. Bank account data are never returned by this public endpoint.

```json
{
  "currency": "ARS",
  "orderRequestsEnabled": true,
  "onlinePaymentsEnabled": true,
  "deliveryQuotesEnabled": true,
  "mercadoPagoSurchargeRate": 0,
  "bankTransferDiscountRate": 0.10,
  "paymentMethods": ["MERCADO_PAGO", "BANK_TRANSFER"],
  "deliveryMethods": ["ZIPNOVA"],
  "fulfillmentMethods": ["PICKUP", "DELIVERY"]
}
```

## Configuration

Required when enabled:

- `MP_ENVIRONMENT`: `sandbox` or `production`.
- `MP_ACCESS_TOKEN`: private server credential.
- `MP_WEBHOOK_SECRET`: Webhooks signature secret.
- `MP_COLLECTOR_ID`: expected seller user ID.
- `MP_STOREFRONT_BASE_URL`: public HTTPS storefront URL used by browser return links.
- `MP_WEBHOOK_BASE_URL`: public HTTPS API origin used by Mercado Pago `POST` notifications.

`docker-compose.prod.yml` derives both Mercado Pago public URLs and the exact CORS origin as
`https://${APP_DOMAIN}` so they cannot drift. The local Compose file reads them separately.

Optional timeout and reconciliation settings are listed in `.env.example` and
`.env.production.example`. Production credentials belong only in the root-owned deployment
file or a secret manager. They must never be placed in Angular or committed.
Production also requires the explicit `MP_PRODUCTION_CONFIRMATION=true` safety gate.

Zipnova requires `ZIPNOVA_TOKEN`, `ZIPNOVA_SECRET`, `ZIPNOVA_ACCOUNT_ID`,
`ZIPNOVA_ORIGIN_ID` and a URL-safe `ZIPNOVA_WEBHOOK_SECRET`. Credentials are used only by the
backend with HTTP Basic authentication against `https://api.zipnova.com.ar/v2`; they must never
be exposed to Angular. Keep `ZIPNOVA_ENABLED=false` until catalog logistics data, origin, account,
webhook and a minimum-value real shipment are verified. Production activation additionally
requires `ZIPNOVA_PRODUCTION_CONFIRMATION=true`.

Bank transfer requires `BANK_TRANSFER_ENABLED=true` plus holder, tax ID, bank name, alias,
22-digit CBU and currency. Those values are snapshotted into each transfer order. The default
proof deadline is 24 hours. Files use the private subdirectory configured by
`PRIVATE_DOCUMENTS_SUBROOT`; while this is a local volume, run exactly one backend replica.

Local Docker Compose reads `.env` automatically. Production always uses the explicit
root-owned environment file described in the deployment runbook.

`paymentMethod` is mandatory on every new order so the backend can apply the corresponding price
authoritatively. Deploy the backend before clients that consume `bankTransferDiscountRate`; V21
includes an insert trigger only to keep the previous backend binary able to drain safely while the
rolling deployment replaces it. `mercadoPagoSurchargeRate` remains in capabilities with value zero
for contract compatibility.

## Checkout flow

1. Angular creates an idempotent order with `POST /api/orders`.
2. The backend recalculates prices and reserves stock.
3. Angular calls `POST /api/orders/{orderId}/payments/mercado-pago` with a separate idempotency key.
4. The backend creates an expiring preference using the saved ARS total and a non-sequential external reference.
5. Angular redirects to the provider-owned `sandbox_init_point` or `init_point`.
6. Mercado Pago calls `POST /api/payments/webhooks/mercado-pago`.
7. The endpoint acknowledges non-payment topics such as `merchant_order` without treating their IDs as payments.
8. For `type=payment`, the backend verifies `x-signature`, queries the payment and validates seller, preference, reference, amount and currency.
9. A database transaction records the event once and updates the order.
10. `/checkout/result` polls `GET /api/orders/{orderId}` and ignores payment statuses from browser query parameters.

Only a verified provider payment can set an order to paid. The administration UI cannot mark it paid manually.

`Product.price` is the list and Mercado Pago price; existing product values are not migrated. New
Mercado Pago orders persist zero surcharge and zero discount. Historical orders are not repriced,
and a historical Mercado Pago preference still includes its persisted positive surcharge as a
separate item so its original total remains payable.

## Bank transfer flow

1. Angular creates an idempotent order with immutable `paymentMethod=BANK_TRANSFER`.
2. The backend applies a 10% discount once to the complete product subtotal, rounds it to two
   decimals with `HALF_UP`, reserves stock and exposes the snapshotted account only to the owner.
3. The owner has 24 hours to upload one JPEG, PNG or PDF proof of at most 5 MiB.
4. Images are regenerated; PDF active content is rejected and each page is rendered to a sanitized PNG preview.
5. A successful upload changes payment status to `UNDER_REVIEW` and pauses automatic expiration.
6. An administrator reviews only the sanitized previews and records the exact amount and a unique bank reference.
7. Approval changes the order to `PAID`; rejection requires a reason, cancels the order and releases stock.
8. Late or incorrect payments never reopen cancelled orders and require a manual refund process.

Only one pending transfer order is allowed per customer. Originals have no HTTP download route.
Approved files are retained for five years and rejected files for 90 days; minimal hashes and audit
metadata remain after deletion. Historical transfers with a persisted zero discount remain valid;
new transfers persist zero surcharge and their calculated discount.

## Zipnova delivery flow

1. The customer saves phone, DNI/CUIT and one complete Argentine address in the profile.
2. Every product must have weight, height, width, length, classification ID and vertical-handling data.
3. Angular sends the current cart to `POST /api/shipping/quotes`; the backend requests Zipnova home-delivery options and persists each tax-inclusive `price_incl_tax` for 15 minutes.
4. Angular creates the order with `fulfillmentMethod=DELIVERY` and one `shippingQuoteId`. The backend locks and consumes that quote, verifies the user/cart/profile hashes and snapshots the destination and merchandise logistics.
5. Shipping is added after payment pricing, so the bank-transfer discount applies only to products.
6. Payment approval enqueues shipment creation. A worker first searches the stable external ID `PIN-{orderId}` and then creates the shipment, preventing duplicates after ambiguous network responses.
7. Zipnova calls `POST /api/shipping/webhooks/zipnova/{ZIPNOVA_WEBHOOK_SECRET}`. The inbox deduplicates notifications and retrieves shipment/tracking data authoritatively from Zipnova.
8. Reconciliation remains active as a fallback. Customers see tracking in My orders; administrators can retry, download PDFs and cancel eligible pre-dispatch shipments.

The tracking email is enqueued once after `documentation_ready` when a tracking code or HTTPS URL
exists. The delivery email is enqueued once only for an undamaged `delivered` shipment;
`delivered_with_damage` remains an incident and does not send thanks.

## Order email outbox

Order creation, payment approval, transfer rejection, shipment tracking availability and physical delivery enqueue email in the
same database transaction as the business event. A scheduled worker retries temporary Resend
failures with a stable idempotency key and moves an event to `FAILED` after ten attempts. Transfer
emails link to `/orders?order={id}` and never contain alias or CBU.

## Persistence and privacy

Migrations V14 and V15 add payment attempts, hashed events and a provider-payment ledger.
V22 adds the persisted `payment_discount` with a zero default for historical orders and does not
rewrite product prices, order totals, surcharges or discounts.
The ledger preserves every payment ID generated by one preference so duplicate approvals and
refunds are handled independently. Events retain hashes of notification/provider payloads
rather than complete payloads, avoiding unnecessary personal data storage. Card data is never
handled or stored by this application.

V25 adds product logistics, V26 adds the normalized customer document and V27 adds delivery
snapshots, persisted quotes, shipment work state, tracking events and the webhook inbox. Provider
payloads are not retained; only bounded operational fields and hashes required for idempotency are stored.

## Expiration and refunds

The preference expires with the stock reservation. Cash, ATM and bank-transfer payment types are excluded and binary mode is enabled for immediate outcomes.

The webhook and reservation worker lock the same order. If stock was already released when an approval arrives, the order is not reopened: it becomes `REFUND_PENDING`, a full refund is requested with a persisted idempotency key, and the reconciliation worker retries until it becomes `REFUNDED`.

Lost-payment discovery is bounded by `MP_RECONCILIATION_LOOKBACK` (30 days by default after
preference expiry). Persisted pending refunds use their own retry queue and are not discarded
when that discovery window closes.

Administrative cancellation of paid orders is blocked until a separate audited return/refund workflow is implemented.

## Sandbox setup

1. Create a Mercado Pago application and select test credentials.
2. Expose the Docker storefront through one HTTPS tunnel to local port 80.
3. Put the test access token, Webhooks secret, test seller user ID and tunnel URL in `.env`.
4. Register `${MP_WEBHOOK_BASE_URL}/api/payments/webhooks/mercado-pago` as the payment notification URL.
5. Set `CORS_ALLOWED_ORIGIN` to the same public origin and `MP_ENABLED=true`.
6. Rebuild the stack and verify `/api/checkout/capabilities` reports Mercado Pago.
7. Complete a purchase with a test buyer and test payment method.
8. Verify approval, rejection, duplicate notification, expiration and late-refund scenarios before changing to production credentials.

Return URLs improve the user experience but are not proof of payment. A valid webhook plus an authoritative API lookup remains the financial source of truth.

## Production activation

Implementing Checkout Pro does not make the integration production-ready automatically.
Keep `MP_ENABLED=false` until the production checklist, restore test, monitoring, incident
ownership and provider verification are complete. Then use `MP_ENVIRONMENT=production`,
production credentials and the seller's real collector ID.
Set `MP_PRODUCTION_CONFIRMATION=true` only during the reviewed production activation.

For a deployment whose `APP_DOMAIN` is `store.example.com`, register exactly this payment
webhook URL, without a trailing slash:

```text
https://store.example.com/api/payments/webhooks/mercado-pago
```

Follow the controlled activation and rotation procedure in
[`production-deployment.md`](production-deployment.md). Browser return URLs remain
non-authoritative in production.

For Zipnova, register the exact public webhook URL with the URL-safe secret generated for that
environment. Rotate the URL and secret together, and ensure access logs/APM redact the secret path.
