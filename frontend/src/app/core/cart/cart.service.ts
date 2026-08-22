import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { catchError, forkJoin, map, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Product, ProductVariant } from '../../features/catalog/catalog.service';
import { AuthService } from '../auth/auth.service';

export interface CartItem { product: Product; variant: ProductVariant; quantity: number; }
export interface OrderConfirmation {
  id: number;
  status: string;
  paymentStatus: string;
  fulfillmentStatus: string;
  currency: string;
  paymentMethod: string | null;
  deliveryMethod: string | null;
  total: number;
  createdAt: string;
  reservationExpiresAt: string;
}

const GUEST_CART_KEY = 'pinatech-cart-guest';
const MAX_QUANTITY = 99;

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private activeKey = GUEST_CART_KEY;
  private readonly legacyCartDiscardedState = signal(false);
  private readonly itemsState = signal<CartItem[]>(this.restoreCart(GUEST_CART_KEY));
  private readonly confirmationState = signal<OrderConfirmation | null>(null);
  private readonly noticeState = signal('');

  readonly items = this.itemsState.asReadonly();
  readonly confirmation = this.confirmationState.asReadonly();
  readonly legacyCartDiscarded = this.legacyCartDiscardedState.asReadonly();
  readonly notice = this.noticeState.asReadonly();
  readonly count = computed(() => this.itemsState().reduce((total, item) => total + item.quantity, 0));
  readonly total = computed(() => this.itemsState().reduce((total, item) => total + item.product.price * item.quantity, 0));

  constructor() {
    effect(() => {
      const userId = this.auth.user()?.id ?? null;
      const key = this.storageKey(userId);
      if (key === this.activeKey) return;

      const current = this.itemsState();
      this.persist(this.activeKey, current);
      const next = userId !== null && this.activeKey === GUEST_CART_KEY
        ? this.merge(this.restoreCart(key), current)
        : this.restoreCart(key);
      if (userId !== null && this.activeKey === GUEST_CART_KEY) this.remove(GUEST_CART_KEY);

      this.remove(this.checkoutAttemptStorageKey());
      this.activeKey = key;
      this.remove(this.checkoutAttemptStorageKey());
      this.itemsState.set(next);
      this.persist(key, next);
      this.confirmationState.set(this.restoreConfirmation(userId));
    });
  }

  add(product: Product, variant: ProductVariant, quantity = 1): void {
    const amount = this.safeQuantity(quantity);
    const current = this.itemsState();
    const found = current.find((item) => item.variant.id === variant.id);
    this.update(found
      ? current.map((item) => item.variant.id === variant.id
        ? { product, variant, quantity: Math.min(MAX_QUANTITY, item.quantity + amount) }
        : item)
      : [...current, { product, variant, quantity: amount }]);
  }

  setQuantity(variantId: number, quantity: number): void {
    if (!Number.isFinite(quantity)) return;
    this.update(this.itemsState().flatMap((item) => {
      if (item.variant.id !== variantId) return [item];
      return quantity <= 0 ? [] : [{ ...item, quantity: this.safeQuantity(quantity) }];
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
        const reconciled = current.flatMap((item) => {
          const product = byId.get(item.product.id);
          if (!product) return [item];
          const variant = product.variants.find((candidate) => candidate.id === item.variant.id);
          return variant?.inStock ? [{ product, variant, quantity: item.quantity }] : [];
        });
        if (reconciled.length !== current.length) {
          this.noticeState.set('Quitamos del carrito los colores que ya no están disponibles.');
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

  checkout() {
    const idempotencyKey = this.checkoutAttempt();
    return this.http.post<OrderConfirmation>(`${environment.apiBaseUrl}/orders`, {
      items: this.itemsState().map((item) => ({ variantId: item.variant.id, quantity: item.quantity })),
    }, { headers: { 'Idempotency-Key': idempotencyKey } });
  }

  completeCheckout(confirmation: OrderConfirmation): void {
    this.update([]);
    this.confirmationState.set(confirmation);
    this.persistConfirmation(this.auth.user()?.id ?? null, confirmation);
  }

  dismissConfirmation(): void {
    this.confirmationState.set(null);
    this.remove(this.confirmationKey(this.auth.user()?.id ?? null));
  }

  private update(items: CartItem[]): void {
    this.itemsState.set(items);
    this.persist(this.activeKey, items);
    this.remove(this.checkoutAttemptStorageKey());
  }

  private merge(existing: CartItem[], incoming: CartItem[]): CartItem[] {
    const merged = [...existing];
    for (const item of incoming) {
      const index = merged.findIndex((candidate) => candidate.variant.id === item.variant.id);
      if (index === -1) merged.push(item);
      else merged[index] = { product: item.product, variant: item.variant, quantity: Math.min(MAX_QUANTITY, merged[index].quantity + item.quantity) };
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

  private checkoutAttempt(): string {
    const storageKey = this.checkoutAttemptStorageKey();
    try {
      const existing = localStorage.getItem(storageKey);
      if (existing) return existing;
    } catch { /* Storage can be unavailable. */ }

    const key = globalThis.crypto?.randomUUID?.()
      ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    try { localStorage.setItem(storageKey, key); } catch { /* The in-flight request remains idempotent. */ }
    return key;
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
        .map((item) => ({
          ...item,
          product: { ...item.product, images: Array.isArray(item.product.images) ? item.product.images : [], variants: Array.isArray(item.product.variants) ? item.product.variants : [] },
          quantity: this.safeQuantity(item.quantity),
        }));
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
        : Number.NaN;
      if (!Number.isFinite(reservationExpiresAt) || reservationExpiresAt <= Date.now()) {
        this.remove(storageKey);
        return null;
      }

      return typeof value['id'] === 'number' && Number.isFinite(value['id'])
        && typeof value['total'] === 'number' && Number.isFinite(value['total'])
        && typeof value['createdAt'] === 'string'
        && typeof value['status'] === 'string'
        && typeof value['paymentStatus'] === 'string'
        && typeof value['fulfillmentStatus'] === 'string'
        && typeof value['currency'] === 'string'
        && (typeof value['paymentMethod'] === 'string' || value['paymentMethod'] === null)
        && (typeof value['deliveryMethod'] === 'string' || value['deliveryMethod'] === null)
        ? {
          id: value['id'],
          status: value['status'],
          paymentStatus: value['paymentStatus'],
          fulfillmentStatus: value['fulfillmentStatus'],
          currency: value['currency'],
          paymentMethod: value['paymentMethod'] as string | null,
          deliveryMethod: value['deliveryMethod'] as string | null,
          total: value['total'],
          createdAt: value['createdAt'],
          reservationExpiresAt: value['reservationExpiresAt'] as string,
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
      && Number.isFinite(item['quantity']) && item['quantity'] > 0;
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
