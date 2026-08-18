# Payment and shipping integration

Mercado Pago Checkout Pro is implemented as a hosted checkout. Delivery quoting is not implemented yet.

## Runtime switch

`GET /api/checkout/capabilities` reports Mercado Pago only when `MP_ENABLED=true` and the backend starts only if all required payment settings are present.

```json
{
  "currency": "ARS",
  "orderRequestsEnabled": true,
  "onlinePaymentsEnabled": true,
  "deliveryQuotesEnabled": false,
  "paymentMethods": ["MERCADO_PAGO"],
  "deliveryMethods": []
}
```

## Configuration

Required when enabled:

- `MP_ENVIRONMENT`: `sandbox` or `production`.
- `MP_ACCESS_TOKEN`: private server credential.
- `MP_WEBHOOK_SECRET`: Webhooks signature secret.
- `MP_COLLECTOR_ID`: expected seller user ID.
- `PUBLIC_BASE_URL`: public HTTPS storefront URL; `/api` must reach the backend.

Optional timeout and reconciliation settings are listed in `.env.example`. Credentials belong only in `.env`, deployment secrets, or an explicit `.env.local`. They must never be placed in Angular or committed.

Docker Compose reads `.env` automatically. An alternative file requires `docker compose --env-file .env.local ...`.

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

## Persistence and privacy

Migration V14 adds `payment_attempts` and `payment_events`. Attempts contain the IDs and state needed for idempotency and reconciliation. Events retain hashes of notification/provider payloads rather than complete payloads, avoiding unnecessary personal data storage. Card data is never handled or stored by this application.

## Expiration and refunds

The preference expires with the stock reservation. Cash, ATM and bank-transfer payment types are excluded and binary mode is enabled for immediate outcomes.

The webhook and reservation worker lock the same order. If stock was already released when an approval arrives, the order is not reopened: it becomes `REFUND_PENDING`, a full refund is requested with a persisted idempotency key, and the reconciliation worker retries until it becomes `REFUNDED`.

Administrative cancellation of paid orders is blocked until a separate audited return/refund workflow is implemented.

## Sandbox setup

1. Create a Mercado Pago application and select test credentials.
2. Expose the Docker storefront through one HTTPS tunnel to local port 80.
3. Put the test access token, Webhooks secret, test seller user ID and tunnel URL in `.env`.
4. Register `${PUBLIC_BASE_URL}/api/payments/webhooks/mercado-pago` as the payment notification URL.
5. Set `CORS_ALLOWED_ORIGIN` to the same public origin and `MP_ENABLED=true`.
6. Rebuild the stack and verify `/api/checkout/capabilities` reports Mercado Pago.
7. Complete a purchase with a test buyer and test payment method.
8. Verify approval, rejection, duplicate notification, expiration and late-refund scenarios before changing to production credentials.

Return URLs improve the user experience but are not proof of payment. A valid webhook plus an authoritative API lookup remains the financial source of truth.
