import { HttpClient } from '@angular/common/http';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { FulfillmentMethod, PaymentMethod, PickupLocation } from '../../core/orders/order.service';

export const CHECKOUT_WINDOW = new InjectionToken<Pick<Window, 'location'>>('Checkout window', {
  providedIn: 'root',
  factory: () => window,
});

export interface CheckoutCapabilities {
  currency: string;
  orderRequestsEnabled: boolean;
  onlinePaymentsEnabled: boolean;
  deliveryQuotesEnabled: boolean;
  paymentMethods: PaymentMethod[];
  bankTransferDiscountRate: number;
  deliveryMethods: string[];
  fulfillmentMethods: FulfillmentMethod[];
  pickupLocations: PickupLocation[];
}

export interface ShippingQuoteItem {
  variantId: number;
  quantity: number;
}

export interface ShippingQuoteOption {
  shippingQuoteId: string;
  carrier: string;
  serviceCode: string;
  service: string;
  logisticType: string;
  amount: number;
  currency: string;
  estimatedDeliveryAt: string | null;
  expiresAt: string;
  tags: string[];
}

export interface ShippingQuoteResponse {
  options: ShippingQuoteOption[];
}

export interface MercadoPagoCheckout {
  attemptId: string;
  orderId: number;
  status: string;
  checkoutUrl: string;
  expiresAt: string;
}

interface StoredPaymentAttempt {
  key: string;
  paymentStatus: string;
}

@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly http = inject(HttpClient);

  capabilities() {
    return this.http.get<CheckoutCapabilities>(`${environment.apiBaseUrl}/checkout/capabilities`);
  }

  shippingQuotes(items: ShippingQuoteItem[]) {
    return this.http.post<ShippingQuoteResponse>(`${environment.apiBaseUrl}/shipping/quotes`, { items });
  }

  mercadoPago(orderId: number, paymentStatus: string) {
    return this.http.post<MercadoPagoCheckout>(
      `${environment.apiBaseUrl}/orders/${orderId}/payments/mercado-pago`,
      {},
      { headers: { 'Idempotency-Key': this.paymentAttempt(orderId, paymentStatus) } },
    );
  }

  private paymentAttempt(orderId: number, paymentStatus: string): string {
    const storageKey = `pinatech-mercado-pago-attempt-${orderId}`;
    try {
      const parsed: unknown = JSON.parse(localStorage.getItem(storageKey) ?? 'null');
      if (this.isStoredAttempt(parsed) && parsed.paymentStatus === paymentStatus) return parsed.key;
    } catch { /* A malformed or unavailable entry is replaced below. */ }

    const attempt: StoredPaymentAttempt = {
      key: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      paymentStatus,
    };
    try { localStorage.setItem(storageKey, JSON.stringify(attempt)); } catch { /* The in-flight request remains idempotent. */ }
    return attempt.key;
  }

  private isStoredAttempt(value: unknown): value is StoredPaymentAttempt {
    if (!value || typeof value !== 'object') return false;
    const attempt = value as Record<string, unknown>;
    return typeof attempt['key'] === 'string' && !!attempt['key']
      && typeof attempt['paymentStatus'] === 'string';
  }
}
