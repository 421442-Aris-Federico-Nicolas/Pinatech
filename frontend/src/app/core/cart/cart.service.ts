import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { catchError, forkJoin, map, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Product, ProductVariant } from '../../features/catalog/catalog.service';
import { AuthService } from '../auth/auth.service';
import { DeliveryAddress, FulfillmentMethod, PaymentMethod, PickupLocation, ShipmentSummary } from '../orders/order.service';
import { roundMoney } from '../payments/payment-pricing';

export interface CartItem { product: Product; variant: ProductVariant; quantity: number; }
export interface CartAddResult { requested: number; added: number; quantity: number; limit: number; capped: boolean; }
export interface OrderConfirmation {
  id: number;
  status: string;
  paymentStatus: string;
  fulfillmentStatus: string;
  currency: string;
  paymentMethod: PaymentMethod;
  deliveryMethod: string | null;
  fulfillmentMethod: FulfillmentMethod | null;
  pickupLocation: PickupLocation | null;
  subtotal: number;
  shippingCost: number;
  paymentDiscount: number;
  paymentSurcharge: number;
  total: number;
  createdAt: string;
  reservationExpiresAt: string | null;
  deliveryAddress: DeliveryAddress | null;
  shipment: ShipmentSummary | null;
}


export type CheckoutFulfillment =
  | { fulfillmentMethod: 'PICKUP'; pickupLocationCode: string; pickupLocationVersion: string; shippingQuoteId: null }
  | { fulfillmentMethod: 'DELIVERY'; pickupLocationCode: null; pickupLocationVersion: null; shippingQuoteId: string };

const GUEST_CART_KEY = 'pinatech-cart-guest';
const MAX_QUANTITY = 99;

interface StoredCheckoutAttempt {
  key: string;
  requestHash: string;
}

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private activeKey = GUEST_CART_KEY;
  private readonly checkoutAttempts = new Map<string, string>();
  private readonly legacyCartDiscardedState = signal(false);
  private readonly itemsState = signal<CartItem[]>(this.restoreCart(GUEST_CART_KEY));
  private readonly confirmationState = signal<OrderConfirmation | null>(null);
  private readonly noticeState = signal('');

  readonly items = this.itemsState.asReadonly();
  readonly confirmation = this.confirmationState.asReadonly();
  readonly legacyCartDiscarded = this.legacyCartDiscardedState.asReadonly();
  readonly notice = this.noticeState.asReadonly();
  readonly count = computed(() => this.itemsState().reduce((total, item) => total + item.quantity, 0));
  readonly total = computed(() => roundMoney(this.itemsState().reduce((total, item) => total + item.product.price * item.quantity, 0)));

  constructor() {
    effect(() => {
      const userId = this.auth.isAuthenticated() ? this.auth.user()?.id ?? null : null;
      const key = this.storageKey(userId);
      if (key === this.activeKey) return;

      const current = this.itemsState();
      this.persist(this.activeKey, current);
      const next = userId !== null && this.activeKey === GUEST_CART_KEY
        ? this.merge(this.restoreCart(key), current)
        : this.restoreCart(key);
      if (userId !== null && this.activeKey === GUEST_CART_KEY) this.remove(GUEST_CART_KEY);

      this.remove(this.checkoutAttemptStorageKey());
      this.checkoutAttempts.clear();
      this.activeKey = key;
      this.remove(this.checkoutAttemptStorageKey());
      this.itemsState.set(next);
      this.persist(key, next);
      this.confirmationState.set(this.restoreConfirmation(userId));
    });
  }

  add(product: Product, variant: ProductVariant, quantity = 1): CartAddResult {
    const requested = this.safeQuantity(quantity);
    const current = this.itemsState();
    const found = current.find((item) => item.variant.id === variant.id);
    const previousQuantity = found?.quantity ?? 0;
    const limit = this.stockLimit(variant);
    const nextQuantity = Math.min(limit, previousQuantity + requested);
    const added = Math.max(0, nextQuantity - previousQuantity);
    if (added > 0) {
      this.update(found
        ? current.map((item) => item.variant.id === variant.id
          ? { product, variant, quantity: nextQuantity }
          : item)
        : [...current, { product, variant, quantity: nextQuantity }]);
    }
    return { requested, added, quantity: added > 0 ? nextQuantity : previousQuantity, limit, capped: added < requested };
  }

  setQuantity(variantId: number, quantity: number): void {
    if (!Number.isFinite(quantity)) return;
    this.update(this.itemsState().flatMap((item) => {
      if (item.variant.id !== variantId) return [item];
      if (quantity <= 0) return [];
      const bounded = Math.min(this.safeQuantity(quantity), this.stockLimit(item.variant));
      return bounded > 0 ? [{ ...item, quantity: bounded }] : [];
    }));
  }

  removeItem(variantId: number): void { this.update(this.itemsState().filter((item) => item.variant.id !== variantId)); }
  clear(): void { this.update([]); }
  dismissLegacyCartWarning(): void { this.legacyCartDiscardedState.set(false); }
  dismissNotice(): void { this.noticeState.set(''); }

  reconcile() {
    const items = this.itemsState();
    const storageKey = this.activeKey;
    const productIds = [...new Set(items.map((item) => item.product.id))];
    if (!productIds.length) return of(true);

    return forkJoin(productIds.map((id) => this.http.get<Product>(`${environment.apiBaseUrl}/products/${id}`))).pipe(
      map((products) => {
        if (storageKey !== this.activeKey) return true;
        const byId = new Map(products.map((product) => [product.id, product]));
        const current = this.itemsState();
        let removed = false;
        let adjusted = false;
        const reconciled = current.flatMap((item) => {
          const product = byId.get(item.product.id);
          if (!product) return [item];
          const variant = product.variants.find((candidate) => candidate.id === item.variant.id);
          const limit = variant ? this.stockLimit(variant) : 0;
          if (!variant || limit === 0) { removed = true; return []; }
          const quantity = Math.min(item.quantity, limit);
          if (quantity !== item.quantity) adjusted = true;
          return [{ product, variant, quantity }];
        });
        if (removed || adjusted) {
          this.noticeState.set(removed && adjusted
            ? 'Quitamos los colores sin stock y ajustamos otras cantidades a la disponibilidad actual.'
            : removed
              ? 'Quitamos del carrito los colores que ya no están disponibles.'
              : 'Ajustamos las cantidades del carrito al stock disponible actualmente.');
          this.update(reconciled);
        } else {
          this.itemsState.set(reconciled);
          this.persist(this.activeKey, reconciled);
        }
        return true;
      }),
      catchError(() => of(false)),
    );
  }

  checkout(paymentMethod: PaymentMethod, fulfillment: CheckoutFulfillment) {
    const items = this.itemsState().map((item) => ({ variantId: item.variant.id, quantity: item.quantity }));
    const requestHash = `${paymentMethod}|${fulfillment.fulfillmentMethod}|${fulfillment.pickupLocationCode ?? ''}|${fulfillment.pickupLocationVersion ?? ''}|${fulfillment.shippingQuoteId ?? ''}|${[...items]
      .sort((left, right) => left.variantId - right.variantId || left.quantity - right.quantity)
      .map((item) => `${item.variantId}:${item.quantity}`).join(',')}`;
    const idempotencyKey = this.checkoutAttempt(requestHash);
    return this.http.post<OrderConfirmation>(`${environment.apiBaseUrl}/orders`, {
      items,
      paymentMethod,
      ...fulfillment,
    }, { headers: { 'Idempotency-Key': idempotencyKey } });
  }

  completeCheckout(confirmation: OrderConfirmation): void {
    this.update([]);
    this.confirmationState.set(confirmation);
    this.persistConfirmation(this.auth.isAuthenticated() ? this.auth.user()?.id ?? null : null, confirmation);
  }

  dismissConfirmation(): void {
    this.confirmationState.set(null);
    this.remove(this.confirmationKey(this.auth.isAuthenticated() ? this.auth.user()?.id ?? null : null));
  }

  stockLimit(variant: ProductVariant): number {
    const available = Number.isInteger(variant.availableQuantity)
      ? variant.availableQuantity
      : variant.inStock ? MAX_QUANTITY : 0;
    return Math.min(MAX_QUANTITY, Math.max(0, available));
  }

  private update(items: CartItem[]): void {
    this.itemsState.set(items);
    this.persist(this.activeKey, items);
    this.remove(this.checkoutAttemptStorageKey());
    this.checkoutAttempts.clear();
  }

  private merge(existing: CartItem[], incoming: CartItem[]): CartItem[] {
    const merged = [...existing];
    for (const item of incoming) {
      const index = merged.findIndex((candidate) => candidate.variant.id === item.variant.id);
      if (index === -1) merged.push(item);
      else merged[index] = { product: item.product, variant: item.variant, quantity: Math.min(this.stockLimit(item.variant), merged[index].quantity + item.quantity) };
    }
    return merged;
  }

  private storageKey(userId: number | null): string {
    return userId === null ? GUEST_CART_KEY : `pinatech-cart-user-${userId}`;
  }

  private confirmationKey(userId: number | null): string {
    return userId === null ? 'pinatech-order-guest' : `pinatech-order-user-${userId}`;
  }

  private checkoutAttemptStorageKey(): string {
    return `pinatech-checkout-${this.activeKey}`;
  }

  private checkoutAttempt(requestHash: string): string {
    const inMemory = this.checkoutAttempts.get(requestHash);
    if (inMemory) return inMemory;

    const storageKey = this.checkoutAttemptStorageKey();
    try {
      const existing: unknown = JSON.parse(localStorage.getItem(storageKey) ?? 'null');
      if (this.isStoredCheckoutAttempt(existing) && existing.requestHash === requestHash) {
        this.checkoutAttempts.set(requestHash, existing.key);
        return existing.key;
      }
    } catch { /* Storage can be unavailable. */ }

    const attempt: StoredCheckoutAttempt = {
      key: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      requestHash,
    };
    this.checkoutAttempts.set(requestHash, attempt.key);
    try { localStorage.setItem(storageKey, JSON.stringify(attempt)); } catch { /* The in-flight request remains idempotent. */ }
    return attempt.key;
  }

  private isStoredCheckoutAttempt(value: unknown): value is StoredCheckoutAttempt {
    if (!value || typeof value !== 'object') return false;
    const attempt = value as Record<string, unknown>;
    return typeof attempt['key'] === 'string' && !!attempt['key']
      && typeof attempt['requestHash'] === 'string';
  }

  private persist(key: string, items: CartItem[]): void {
    try { localStorage.setItem(key, JSON.stringify(items)); } catch { /* Storage can be unavailable or full. */ }
  }

  private persistConfirmation(userId: number | null, confirmation: OrderConfirmation): void {
    try { localStorage.setItem(this.confirmationKey(userId), JSON.stringify(confirmation)); } catch { /* Keep in-memory confirmation. */ }
  }

  private remove(key: string): void {
    try { localStorage.removeItem(key); } catch { /* Storage can be unavailable. */ }
  }

  private restoreCart(key: string): CartItem[] {
    try {
      const parsed: unknown = JSON.parse(localStorage.getItem(key) ?? '[]');
      if (!Array.isArray(parsed)) return [];
      if (parsed.some((item) => this.isLegacyCartItem(item))) {
        this.legacyCartDiscardedState.set(true);
        this.remove(key);
      }
      return parsed.filter((item): item is CartItem => this.isCartItem(item))
        .flatMap((item) => {
          const availableQuantity = this.stockLimit(item.variant);
          if (availableQuantity === 0) return [];
          return [{
            ...item,
            product: { ...item.product, images: Array.isArray(item.product.images) ? item.product.images : [], variants: Array.isArray(item.product.variants) ? item.product.variants : [] },
            variant: { ...item.variant, availableQuantity },
            quantity: Math.min(this.safeQuantity(item.quantity), availableQuantity),
          }];
        });
    } catch {
      return [];
    }
  }

  private restoreConfirmation(userId: number | null): OrderConfirmation | null {
    const storageKey = this.confirmationKey(userId);
    try {
      const parsed: unknown = JSON.parse(localStorage.getItem(storageKey) ?? 'null');
      if (!parsed || typeof parsed !== 'object') return null;
      const value = parsed as Record<string, unknown>;
      const reservationExpiresAt = typeof value['reservationExpiresAt'] === 'string'
        ? Date.parse(value['reservationExpiresAt'])
        : value['reservationExpiresAt'] === null ? Number.POSITIVE_INFINITY : Number.NaN;
      if (!Number.isFinite(reservationExpiresAt) && reservationExpiresAt !== Number.POSITIVE_INFINITY || reservationExpiresAt <= Date.now()) {
        this.remove(storageKey);
        return null;
      }

      return typeof value['id'] === 'number' && Number.isFinite(value['id'])
        && typeof value['subtotal'] === 'number' && Number.isFinite(value['subtotal'])
        && (value['shippingCost'] === undefined || typeof value['shippingCost'] === 'number' && Number.isFinite(value['shippingCost']))
        && (value['paymentDiscount'] === undefined || typeof value['paymentDiscount'] === 'number' && Number.isFinite(value['paymentDiscount']))
        && (value['paymentSurcharge'] === undefined || typeof value['paymentSurcharge'] === 'number' && Number.isFinite(value['paymentSurcharge']))
        && typeof value['total'] === 'number' && Number.isFinite(value['total'])
        && typeof value['createdAt'] === 'string'
        && typeof value['status'] === 'string'
        && typeof value['paymentStatus'] === 'string'
        && typeof value['fulfillmentStatus'] === 'string'
        && typeof value['currency'] === 'string'
        && (value['paymentMethod'] === 'BANK_TRANSFER' || value['paymentMethod'] === 'MERCADO_PAGO')
        && (typeof value['deliveryMethod'] === 'string' || value['deliveryMethod'] === null)
        && (value['fulfillmentMethod'] === 'PICKUP' || value['fulfillmentMethod'] === 'DELIVERY' || value['fulfillmentMethod'] == null)
        && (value['pickupLocation'] == null || this.isPickupLocation(value['pickupLocation']))
        && (value['deliveryAddress'] == null || this.isDeliveryAddress(value['deliveryAddress']))
        && (value['shipment'] == null || this.isShipmentSummary(value['shipment']))
        ? {
          id: value['id'],
          status: value['status'],
          paymentStatus: value['paymentStatus'],
          fulfillmentStatus: value['fulfillmentStatus'],
          currency: value['currency'],
          paymentMethod: value['paymentMethod'],
          deliveryMethod: value['deliveryMethod'] as string | null,
          fulfillmentMethod: value['fulfillmentMethod'] === 'PICKUP' || value['fulfillmentMethod'] === 'DELIVERY' ? value['fulfillmentMethod'] : null,
          pickupLocation: this.isPickupLocation(value['pickupLocation']) ? value['pickupLocation'] : null,
          subtotal: value['subtotal'],
          shippingCost: typeof value['shippingCost'] === 'number' ? value['shippingCost'] : 0,
          paymentDiscount: typeof value['paymentDiscount'] === 'number' ? value['paymentDiscount'] : 0,
          paymentSurcharge: typeof value['paymentSurcharge'] === 'number' ? value['paymentSurcharge'] : 0,
          total: value['total'],
          createdAt: value['createdAt'],
          reservationExpiresAt: value['reservationExpiresAt'] as string | null,
          deliveryAddress: this.isDeliveryAddress(value['deliveryAddress']) ? value['deliveryAddress'] : null,
          shipment: this.isShipmentSummary(value['shipment']) ? value['shipment'] : null,
        }
        : null;
    } catch {
      return null;
    }
  }

  private isCartItem(value: unknown): value is CartItem {
    if (!value || typeof value !== 'object') return false;
    const item = value as Record<string, unknown>;
    if (!item['product'] || typeof item['product'] !== 'object' || !item['variant'] || typeof item['variant'] !== 'object' || typeof item['quantity'] !== 'number') return false;
    const product = item['product'] as Record<string, unknown>;
    const variant = item['variant'] as Record<string, unknown>;
    return typeof product['id'] === 'number' && Number.isInteger(product['id']) && product['id'] > 0
      && typeof product['name'] === 'string' && typeof product['slug'] === 'string'
      && typeof product['description'] === 'string'
      && typeof product['price'] === 'number' && Number.isFinite(product['price']) && product['price'] >= 0
      && typeof product['categoryId'] === 'number' && typeof product['categoryName'] === 'string'
      && typeof product['brandId'] === 'number' && typeof product['brandName'] === 'string'
      && typeof variant['id'] === 'number' && Number.isInteger(variant['id']) && variant['id'] > 0
      && typeof variant['colorName'] === 'string'
      && typeof variant['inStock'] === 'boolean'
      && (variant['availableQuantity'] === undefined || typeof variant['availableQuantity'] === 'number' && Number.isInteger(variant['availableQuantity']) && variant['availableQuantity'] >= 0)
      && Number.isFinite(item['quantity']) && item['quantity'] > 0;
  }

  private isPickupLocation(value: unknown): value is PickupLocation {
    if (!value || typeof value !== 'object') return false;
    const location = value as Record<string, unknown>;
    return typeof location['code'] === 'string'
      && typeof location['version'] === 'string'
      && typeof location['name'] === 'string'
      && Array.isArray(location['addressLines'])
      && location['addressLines'].every((line) => typeof line === 'string')
      && typeof location['locality'] === 'string'
      && typeof location['provinceCode'] === 'string'
      && typeof location['postalCode'] === 'string'
      && typeof location['instructions'] === 'string'
      && typeof location['hours'] === 'string';
  }

  private isDeliveryAddress(value: unknown): value is DeliveryAddress {
    if (!value || typeof value !== 'object') return false;
    const address = value as Record<string, unknown>;
    return ['recipientName', 'street', 'streetNumber', 'locality', 'province', 'provinceCode', 'postalCode', 'countryCode']
      .every((field) => typeof address[field] === 'string')
      && (address['floorApartment'] === null || typeof address['floorApartment'] === 'string')
      && (address['reference'] === null || typeof address['reference'] === 'string');
  }

  private isShipmentSummary(value: unknown): value is ShipmentSummary {
    if (!value || typeof value !== 'object') return false;
    const shipment = value as Record<string, unknown>;
    return typeof shipment['status'] === 'string'
      && ['providerStatus', 'providerSubstatus', 'carrier', 'trackingCode', 'trackingUrl', 'estimatedDeliveryAt']
        .every((field) => shipment[field] === null || typeof shipment[field] === 'string')
      && typeof shipment['incident'] === 'boolean';
  }

  private isLegacyCartItem(value: unknown): boolean {
    if (!value || typeof value !== 'object') return false;
    const item = value as Record<string, unknown>;
    return !!item['product'] && typeof item['product'] === 'object' && !item['variant'];
  }

  private safeQuantity(quantity: number): number {
    return Math.min(MAX_QUANTITY, Math.max(1, Math.floor(quantity)));
  }
}
