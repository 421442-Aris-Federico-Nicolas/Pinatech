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
  const isAuthenticated = signal(false);
  const variant = { id: 11, colorName: 'Negro', colorHex: '#000000', inStock: true, availableQuantity: 99 };
  const product: Product = { id: 1, name: 'Teclado', slug: 'teclado', description: 'Mecánico', price: 1000, categoryId: 2, categoryName: 'Periféricos', brandId: 3, brandName: 'Marca', images: [], specifications: [], variants: [variant] };
  const pickupLocation = { code: 'CORDOBA_CENTRO', version: 'v1', name: 'Pinatech Centro', addressLines: ['Av. Colón 123'], locality: 'Córdoba', provinceCode: 'X', postalCode: '5000', instructions: 'Presentá tu DNI.', hours: 'Lunes a viernes de 9 a 18.' };

  beforeEach(() => {
    localStorage.clear();
    user.set(null);
    isAuthenticated.set(false);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { user, isAuthenticated } },
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

  it('keeps carts saved before exact stock quantities were introduced', () => {
    const { availableQuantity: _availableQuantity, ...previousVariant } = variant;
    localStorage.setItem('pinatech-cart-guest', JSON.stringify([{ product, variant: previousVariant, quantity: 2 }]));

    const cart = TestBed.inject(CartService);

    expect(cart.items()[0].quantity).toBe(2);
    expect(cart.items()[0].variant.availableQuantity).toBe(99);
  });

  it('merges the guest cart into the customer cart after login', () => {
    localStorage.setItem('pinatech-cart-guest', JSON.stringify([{ product, variant, quantity: 2 }]));
    localStorage.setItem('pinatech-cart-user-7', JSON.stringify([{ product, variant, quantity: 3 }]));
    const cart = TestBed.inject(CartService);

    user.set({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, emailVerified: true, roles: ['CUSTOMER'] });
    isAuthenticated.set(true);
    TestBed.tick();

    expect(cart.items()).toEqual([{ product, variant, quantity: 5 }]);
    expect(localStorage.getItem('pinatech-cart-guest')).toBeNull();
  });

  it('keeps colors of the same product as independent cart lines', () => {
    const cart = TestBed.inject(CartService);
    const blue = { id: 12, colorName: 'Azul', colorHex: '#0000FF', inStock: true, availableQuantity: 99 };

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
      .flush({ ...product, variants: [{ ...variant, inStock: false, availableQuantity: 0 }] });

    expect(cart.items()).toEqual([]);
    expect(cart.notice()).toContain('ya no están disponibles');
  });

  it('reduces a cart line when current stock drops below its quantity', () => {
    const cart = TestBed.inject(CartService);
    cart.add(product, variant, 5);

    cart.reconcile().subscribe();
    TestBed.inject(HttpTestingController).expectOne(`${environment.apiBaseUrl}/products/1`)
      .flush({ ...product, variants: [{ ...variant, availableQuantity: 3 }] });

    expect(cart.items()[0].quantity).toBe(3);
    expect(cart.items()[0].variant.availableQuantity).toBe(3);
    expect(cart.notice()).toContain('Ajustamos las cantidades');
  });

  it('does not restore an expired confirmation and removes it from storage', () => {
    user.set({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, emailVerified: true, roles: ['CUSTOMER'] });
    isAuthenticated.set(true);
    localStorage.setItem('pinatech-order-user-7', JSON.stringify({
      id: 42,
      status: 'PENDING_PAYMENT',
      paymentStatus: 'PENDING',
      fulfillmentStatus: 'PENDING',
      currency: 'ARS',
      paymentMethod: null,
      deliveryMethod: null,
      fulfillmentMethod: 'PICKUP',
      pickupLocation,
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
    user.set({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, emailVerified: true, roles: ['CUSTOMER'] });
    isAuthenticated.set(true);
    localStorage.setItem('pinatech-order-user-7', JSON.stringify({
      id: 42,
      status: 'PENDING_PAYMENT',
      paymentStatus: 'PENDING',
      fulfillmentStatus: 'PENDING',
      currency: 'ARS',
      paymentMethod: null,
      deliveryMethod: null,
      fulfillmentMethod: 'PICKUP',
      pickupLocation,
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

    const first = cart.add(product, variant, 120);
    const capped = cart.add(product, variant, 2);

    expect(cart.count()).toBe(99);
    expect(cart.total()).toBe(99000);
    expect(first).toEqual({ requested: 99, added: 99, quantity: 99, limit: 99, capped: false });
    expect(capped).toEqual({ requested: 2, added: 0, quantity: 99, limit: 99, capped: true });
  });

  it('reports the actual amount added when a request reaches the quantity cap', () => {
    const cart = TestBed.inject(CartService);
    cart.add(product, variant, 98);

    const result = cart.add(product, variant, 5);

    expect(result).toEqual({ requested: 5, added: 1, quantity: 99, limit: 99, capped: true });
    expect(cart.count()).toBe(99);
  });

  it('never adds more units than the variant stock', () => {
    const cart = TestBed.inject(CartService);
    const limited = { ...variant, availableQuantity: 5 };

    const first = cart.add({ ...product, variants: [limited] }, limited, 4);
    const second = cart.add({ ...product, variants: [limited] }, limited, 3);

    expect(first.added).toBe(4);
    expect(second).toEqual({ requested: 3, added: 1, quantity: 5, limit: 5, capped: true });
    expect(cart.count()).toBe(5);

    cart.setQuantity(limited.id, 20);
    expect(cart.items()[0].quantity).toBe(5);
  });

  it('keeps the cart after creating an order and clears it only when checkout is completed', () => {
    const cart = TestBed.inject(CartService);
    cart.add(product, variant, 2);
    let createdOrder: Parameters<typeof cart.completeCheckout>[0] | undefined;
    cart.checkout('PICKUP', pickupLocation.code, pickupLocation.version).subscribe((order) => createdOrder = order);

    const request = TestBed.inject(HttpTestingController).expectOne(`${environment.apiBaseUrl}/orders`);
    expect(request.request.body).toEqual({
      items: [{ variantId: 11, quantity: 2 }],
      fulfillmentMethod: 'PICKUP',
      pickupLocationCode: pickupLocation.code,
      pickupLocationVersion: pickupLocation.version,
    });
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush({
      id: 42,
      status: 'PENDING_PAYMENT',
      paymentStatus: 'PENDING',
      fulfillmentStatus: 'PENDING',
      currency: 'ARS',
      paymentMethod: null,
      deliveryMethod: null,
      fulfillmentMethod: 'PICKUP',
      pickupLocation,
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

    cart.checkout('PICKUP', pickupLocation.code, pickupLocation.version).subscribe({ error: () => undefined });
    const first = http.expectOne(`${environment.apiBaseUrl}/orders`);
    const firstKey = first.request.headers.get('Idempotency-Key');
    first.flush({ detail: 'Temporary failure' }, { status: 503, statusText: 'Unavailable' });

    cart.checkout('PICKUP', pickupLocation.code, pickupLocation.version).subscribe();
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
      fulfillmentMethod: 'PICKUP',
      pickupLocation,
      total: 1000,
      createdAt: '2026-07-28T20:00:00Z',
      reservationExpiresAt: '2026-07-29T20:00:00Z',
    });
  });

  it('regenerates the checkout idempotency key when the pickup selection changes', () => {
    const cart = TestBed.inject(CartService);
    const http = TestBed.inject(HttpTestingController);
    cart.add(product, variant);

    cart.checkout('PICKUP', 'CORDOBA_CENTRO', 'v1').subscribe({ error: () => undefined });
    const first = http.expectOne(`${environment.apiBaseUrl}/orders`);
    const firstKey = first.request.headers.get('Idempotency-Key');
    first.flush({ detail: 'Temporary failure' }, { status: 503, statusText: 'Unavailable' });

    cart.checkout('PICKUP', 'CORDOBA_NORTE', 'v1').subscribe({ error: () => undefined });
    const changed = http.expectOne(`${environment.apiBaseUrl}/orders`);
    expect(changed.request.headers.get('Idempotency-Key')).not.toBe(firstKey);
    expect(changed.request.body.pickupLocationCode).toBe('CORDOBA_NORTE');
    changed.flush({ detail: 'Temporary failure' }, { status: 503, statusText: 'Unavailable' });
  });

  it('reuses an in-memory idempotency key when storage throws and clears it after a cart mutation', () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
    const cart = TestBed.inject(CartService);
    const http = TestBed.inject(HttpTestingController);
    cart.add(product, variant);

    cart.checkout('PICKUP', pickupLocation.code, pickupLocation.version).subscribe({ error: () => undefined });
    const first = http.expectOne(`${environment.apiBaseUrl}/orders`);
    const firstKey = first.request.headers.get('Idempotency-Key');
    first.flush({}, { status: 503, statusText: 'Unavailable' });
    cart.checkout('PICKUP', pickupLocation.code, pickupLocation.version).subscribe({ error: () => undefined });
    const retry = http.expectOne(`${environment.apiBaseUrl}/orders`);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(firstKey);
    retry.flush({}, { status: 503, statusText: 'Unavailable' });

    cart.setQuantity(variant.id, 2);
    cart.checkout('PICKUP', pickupLocation.code, pickupLocation.version).subscribe({ error: () => undefined });
    const changed = http.expectOne(`${environment.apiBaseUrl}/orders`);
    expect(changed.request.headers.get('Idempotency-Key')).not.toBe(firstKey);
    changed.flush({}, { status: 503, statusText: 'Unavailable' });
    getItem.mockRestore();
    setItem.mockRestore();
  });

  it('does not activate a residual user cart without an authenticated session', () => {
    localStorage.setItem('pinatech-cart-guest', JSON.stringify([{ product, variant, quantity: 1 }]));
    localStorage.setItem('pinatech-cart-user-7', JSON.stringify([{ product, variant, quantity: 4 }]));
    user.set({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, emailVerified: true, roles: ['CUSTOMER'] });

    const cart = TestBed.inject(CartService);
    TestBed.tick();

    expect(cart.items()[0].quantity).toBe(1);
  });
});
