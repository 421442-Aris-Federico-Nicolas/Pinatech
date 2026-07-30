# Payment and shipping integration

The application deliberately does not process payments or quote deliveries yet. The current checkout registers a stock reservation and clearly reports that no payment or delivery was selected.

## Current contract

`GET /api/checkout/capabilities` is the feature switch consumed by Angular. It currently returns:

```json
{
  "currency": "ARS",
  "orderRequestsEnabled": true,
  "onlinePaymentsEnabled": false,
  "deliveryQuotesEnabled": false,
  "paymentMethods": [],
  "deliveryMethods": []
}
```

Each order already stores these provider-neutral fields:

- `currency`: monetary currency, currently `ARS`.
- `payment_status`: `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`, `CANCELLED` or `REFUNDED`.
- `fulfillment_status`: `PENDING`, `PREPARING`, `READY`, `SHIPPED`, `DELIVERED` or `CANCELLED`.
- `payment_method`: future business method such as bank transfer or an online gateway.
- `delivery_method`: future method such as pickup or carrier delivery.

The legacy `status` remains temporarily for compatibility with the existing administration flow. New payment code must use `payment_status` as the financial source of truth and `fulfillment_status` for preparation and delivery.

## How an online payment works

The browser must never decide that an order is paid and must never send trusted totals. A normal hosted-checkout integration works as follows:

1. The backend recalculates prices and reserves stock.
2. The backend creates a payment attempt with the provider using the order ID as the external reference.
3. The provider returns a hosted checkout URL or public preference identifier.
4. Angular redirects the customer to that provider-owned checkout.
5. The provider calls a backend webhook when payment changes state.
6. The backend verifies the webhook signature and queries the provider when required.
7. A database transaction records the provider event exactly once and changes `payment_status`.
8. Only an authenticated provider event may set `payment_status = APPROVED`.

The redirect back to Angular is only a user experience signal. It is not proof of payment because users can forge or revisit redirect URLs.

## Provider-neutral backend boundary

When a provider is selected, add a payment module with these responsibilities:

```text
PaymentApplicationService
  -> PaymentGateway (interface)
       -> MercadoPagoPaymentGateway / chosen provider adapter
  -> PaymentAttemptRepository
  -> PaymentWebhookController
```

Persist a `payment_attempts` table instead of placing provider payloads in `customer_orders`. At minimum it should contain order ID, provider, external ID, status, amount, currency, idempotency key, timestamps and the last verified event ID. Store only the fields needed for reconciliation; never store card numbers or security codes.

Required controls:

- Provider credentials loaded from a secret manager, never Angular or Git.
- Idempotency when creating attempts and processing webhook events.
- Signature validation and replay protection on webhooks.
- Amount, currency and order ownership verified server-side.
- Structured audit trail for approval, rejection and refunds.
- Retry-safe processing because providers deliver webhooks more than once.
- Separate sandbox and production accounts, credentials and webhook URLs.

## Bank transfer

Bank transfer is not the same as an online gateway. A first implementation normally needs:

- A payment method selected before creating the order.
- Configurable account instructions displayed after reservation.
- A longer, explicit transfer expiration policy.
- Optional receipt upload to private object storage with size and MIME validation.
- An audited administrator action or bank reconciliation integration to approve it.

Do not mark a transfer as approved merely because the customer uploaded an image.

## Shipping decision

Before implementing delivery, define:

- Store pickup availability and business hours.
- Carriers and covered postal codes.
- Whether price depends on postal code, weight, dimensions or order total.
- Who creates labels and tracking numbers.
- Delivery promises and what happens when quoting fails.

After that decision, add immutable order snapshots for recipient, address, postal code, quoted cost, carrier/service and tracking. Product weight and dimensions are required for most carrier quotations.

## Implementation order

1. Choose payment methods and delivery modes.
2. Define sandbox accounts and business rules.
3. Add payment attempts and delivery quotes as separate tables.
4. Implement backend adapters and webhook verification.
5. Enable methods through checkout capabilities.
6. Add Angular selection and redirect screens.
7. Cover approval, rejection, expiration, duplicate webhook and refund with integration tests.
8. Run a complete sandbox purchase before enabling production credentials.
