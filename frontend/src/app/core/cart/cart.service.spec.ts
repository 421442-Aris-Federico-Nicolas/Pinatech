import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthenticatedUser } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import { Product } from '../../features/catalog/catalog.service';
import { CartService } from './cart.service';
import { environment } from '../../../environments/environment';

describe('CartService', () => {
  const user = signal<AuthenticatedUser | null>(null);
  const variant = { id: 11, colorName: 'Negro', colorHex: '#000000', inStock: true };
  const product: Product = { id: 1, name: 'Teclado', slug: 'teclado', description: 'Mecánico', price: 1000, categoryId: 2, categoryName: 'Periféricos', brandId: 3, brandName: 'Marca', images: [], specifications: [], variants: [variant] };

  beforeEach(() => {
    localStorage.clear();
    user.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { user } },
      ],
    });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('ignores invalid persisted values and restores only safe cart items', () => {
    localStorage.setItem('pinatech-cart-guest', JSON.stringify([
      { product, variant, quantity: 2 },
      { product: { ...product, price: 'free' }, variant, quantity: 1 },
      { product, variant, quantity: -3 },
    ]));

    const cart = TestBed.inject(CartService);

    expect(cart.items()).toEqual([{ product, variant, quantity: 2 }]);
    expect(cart.total()).toBe(2000);
  });

  it('merges the guest cart into the customer cart after login', () => {
    localStorage.setItem('pinatech-cart-guest', JSON.stringify([{ product, variant, quantity: 2 }]));
    localStorage.setItem('pinatech-cart-user-7', JSON.stringify([{ product, variant, quantity: 3 }]));
    const cart = TestBed.inject(CartService);

    user.set({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, roles: ['CUSTOMER'] });
    TestBed.tick();

    expect(cart.items()).toEqual([{ product, variant, quantity: 5 }]);
    expect(localStorage.getItem('pinatech-cart-guest')).toBeNull();
  });

  it('keeps colors of the same product as independent cart lines', () => {
    const cart = TestBed.inject(CartService);
    const blue = { id: 12, colorName: 'Azul', colorHex: '#0000FF', inStock: true };

    cart.add(product, variant);
    cart.add({ ...product, variants: [variant, blue] }, blue, 2);

    expect(cart.items().map((item) => [item.variant.id, item.quantity])).toEqual([[11, 1], [12, 2]]);
  });

  it('discards legacy lines that cannot be assigned to a color safely', () => {
    localStorage.setItem('pinatech-cart-guest', JSON.stringify([{ product, quantity: 2 }]));

    const cart = TestBed.inject(CartService);

    expect(cart.items()).toEqual([]);
    expect(cart.legacyCartDiscarded()).toBe(true);
  });

  it('removes a persisted color that is no longer available', () => {
    const cart = TestBed.inject(CartService);
    cart.add(product, variant);

    cart.reconcile().subscribe();
    TestBed.inject(HttpTestingController).expectOne(`${environment.apiBaseUrl}/products/1`)
      .flush({ ...product, variants: [{ ...variant, inStock: false }] });

    expect(cart.items()).toEqual([]);
    expect(cart.notice()).toContain('ya no están disponibles');
  });

  it('does not restore an expired confirmation and removes it from storage', () => {
    user.set({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, roles: ['CUSTOMER'] });
    localStorage.setItem('pinatech-order-user-7', JSON.stringify({
      id: 42,
      status: 'PENDING_PAYMENT',
      paymentStatus: 'PENDING',
      fulfillmentStatus: 'PENDING',
      currency: 'ARS',
      paymentMethod: null,
      deliveryMethod: null,
      total: 2000,
      createdAt: '1999-12-31T20:00:00Z',
      reservationExpiresAt: '2000-01-01T20:00:00Z',
    }));

    const cart = TestBed.inject(CartService);
    TestBed.tick();

    expect(cart.confirmation()).toBeNull();
    expect(localStorage.getItem('pinatech-order-user-7')).toBeNull();
  });

  it('rejects an invalid confirmation expiration date', () => {
    user.set({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, roles: ['CUSTOMER'] });
    localStorage.setItem('pinatech-order-user-7', JSON.stringify({
      id: 42,
      status: 'PENDING_PAYMENT',
      paymentStatus: 'PENDING',
      fulfillmentStatus: 'PENDING',
      currency: 'ARS',
      paymentMethod: null,
      deliveryMethod: null,
      total: 2000,
      createdAt: '2026-07-28T20:00:00Z',
      reservationExpiresAt: 'not-a-date',
    }));

    const cart = TestBed.inject(CartService);
    TestBed.tick();

    expect(cart.confirmation()).toBeNull();
    expect(localStorage.getItem('pinatech-order-user-7')).toBeNull();
  });

  it('caps quantities and computes item count and total', () => {
    const cart = TestBed.inject(CartService);

    cart.add(product, variant, 120);

    expect(cart.count()).toBe(99);
    expect(cart.total()).toBe(99000);
  });

  it('keeps the cart after creating an order and clears it only when checkout is completed', () => {
    const cart = TestBed.inject(CartService);
    cart.add(product, variant, 2);
    let createdOrder: Parameters<typeof cart.completeCheckout>[0] | undefined;
    cart.checkout().subscribe((order) => createdOrder = order);

    const request = TestBed.inject(HttpTestingController).expectOne(`${environment.apiBaseUrl}/orders`);
    expect(request.request.body).toEqual({ items: [{ variantId: 11, quantity: 2 }] });
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush({
      id: 42,
      status: 'PENDING_PAYMENT',
      paymentStatus: 'PENDING',
      fulfillmentStatus: 'PENDING',
      currency: 'ARS',
      paymentMethod: null,
      deliveryMethod: null,
      total: 2000,
      createdAt: '2026-07-28T20:00:00Z',
      reservationExpiresAt: '2026-07-29T20:00:00Z',
    });

    expect(cart.items()).toEqual([{ product, variant, quantity: 2 }]);
    expect(cart.confirmation()).toBeNull();
    expect(localStorage.getItem('pinatech-order-guest')).toBeNull();

    cart.completeCheckout(createdOrder!);

    expect(cart.items()).toEqual([]);
    expect(cart.confirmation()?.id).toBe(42);
    expect(JSON.parse(localStorage.getItem('pinatech-order-guest') ?? 'null').id).toBe(42);
  });

  it('reuses the idempotency key when a checkout request is retried', () => {
    const cart = TestBed.inject(CartService);
    const http = TestBed.inject(HttpTestingController);
    cart.add(product, variant);

    cart.checkout().subscribe({ error: () => undefined });
    const first = http.expectOne(`${environment.apiBaseUrl}/orders`);
    const firstKey = first.request.headers.get('Idempotency-Key');
    first.flush({ detail: 'Temporary failure' }, { status: 503, statusText: 'Unavailable' });

    cart.checkout().subscribe();
    const retry = http.expectOne(`${environment.apiBaseUrl}/orders`);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(firstKey);
    retry.flush({
      id: 42,
      status: 'PENDING_PAYMENT',
      paymentStatus: 'PENDING',
      fulfillmentStatus: 'PENDING',
      currency: 'ARS',
      paymentMethod: null,
      deliveryMethod: null,
      total: 1000,
      createdAt: '2026-07-28T20:00:00Z',
      reservationExpiresAt: '2026-07-29T20:00:00Z',
    });
  });
});
