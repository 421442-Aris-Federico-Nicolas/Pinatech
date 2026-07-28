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
  const product: Product = { id: 1, name: 'Teclado', slug: 'teclado', description: 'Mecánico', price: 1000, categoryId: 2, categoryName: 'Periféricos', brandId: 3, brandName: 'Marca' };

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
      { product, quantity: 2 },
      { product: { ...product, price: 'free' }, quantity: 1 },
      { product, quantity: -3 },
    ]));

    const cart = TestBed.inject(CartService);

    expect(cart.items()).toEqual([{ product, quantity: 2 }]);
    expect(cart.total()).toBe(2000);
  });

  it('merges the guest cart into the customer cart after login', () => {
    localStorage.setItem('pinatech-cart-guest', JSON.stringify([{ product, quantity: 2 }]));
    localStorage.setItem('pinatech-cart-user-7', JSON.stringify([{ product, quantity: 3 }]));
    const cart = TestBed.inject(CartService);

    user.set({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, roles: ['CUSTOMER'] });
    TestBed.tick();

    expect(cart.items()).toEqual([{ product, quantity: 5 }]);
    expect(localStorage.getItem('pinatech-cart-guest')).toBeNull();
  });

  it('caps quantities and computes item count and total', () => {
    const cart = TestBed.inject(CartService);

    cart.add(product, 120);

    expect(cart.count()).toBe(99);
    expect(cart.total()).toBe(99000);
  });

  it('clears the cart and persists the order confirmation after checkout', () => {
    const cart = TestBed.inject(CartService);
    cart.add(product, 2);
    cart.checkout().subscribe();

    const request = TestBed.inject(HttpTestingController).expectOne(`${environment.apiBaseUrl}/orders`);
    expect(request.request.body).toEqual({ items: [{ productId: 1, quantity: 2 }] });
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush({ id: 42, total: 2000, createdAt: '2026-07-28T20:00:00Z' });

    expect(cart.items()).toEqual([]);
    expect(cart.confirmation()?.id).toBe(42);
    expect(JSON.parse(localStorage.getItem('pinatech-order-guest') ?? 'null').id).toBe(42);
  });

  it('reuses the idempotency key when a checkout request is retried', () => {
    const cart = TestBed.inject(CartService);
    const http = TestBed.inject(HttpTestingController);
    cart.add(product);

    cart.checkout().subscribe({ error: () => undefined });
    const first = http.expectOne(`${environment.apiBaseUrl}/orders`);
    const firstKey = first.request.headers.get('Idempotency-Key');
    first.flush({ detail: 'Temporary failure' }, { status: 503, statusText: 'Unavailable' });

    cart.checkout().subscribe();
    const retry = http.expectOne(`${environment.apiBaseUrl}/orders`);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(firstKey);
    retry.flush({ id: 42, total: 1000, createdAt: '2026-07-28T20:00:00Z' });
  });
});
