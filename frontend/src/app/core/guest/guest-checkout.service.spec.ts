import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { GUEST_REQUEST } from '../interceptors/auth.interceptor';
import { GuestCheckoutService, GuestCreateOrderRequest } from './guest-checkout.service';

describe('GuestCheckoutService', () => {
  let service: GuestCheckoutService;
  let http: HttpTestingController;
  const session = { csrfToken: 'c'.repeat(43), expiresAt: '2099-01-01T00:00:00Z', emailVerified: false, verifiedEmail: null };
  const request: GuestCreateOrderRequest = {
    items: [{ variantId: 11, quantity: 2 }], paymentMethod: 'MERCADO_PAGO', fulfillmentMethod: 'PICKUP',
    pickupLocationCode: 'CORDOBA_CENTRO', pickupLocationVersion: 'v1', shippingQuoteId: null,
    customer: { firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: '3515551234', documentNumber: '12345678' },
    deliveryAddress: null,
  };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(GuestCheckoutService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    history.replaceState({}, '', '/');
  });

  it('restores a cookie session or creates one when it is absent', () => {
    service.initialize().subscribe((value) => expect(value).toEqual(session));
    const restore = http.expectOne(`${environment.apiBaseUrl}/guest-checkout/session`);
    expect(restore.request.context.get(GUEST_REQUEST)).toBe(true);
    restore.flush(null, { status: 404, statusText: 'Not Found' });

    const create = http.expectOne(`${environment.apiBaseUrl}/guest-checkout/sessions`);
    expect(create.request.method).toBe('POST');
    create.flush(session);
    expect(service.session()).toEqual(session);
  });

  it('adds guest CSRF and persists order access only in session storage', () => {
    service.session.set(session);
    service.createOrder(request).subscribe();

    const create = http.expectOne(`${environment.apiBaseUrl}/guest-checkout/orders`);
    expect(create.request.headers.get('X-Guest-CSRF')).toBe(session.csrfToken);
    expect(create.request.headers.get('Idempotency-Key')).toBeTruthy();
    create.flush({ publicId: '123e4567-e89b-42d3-a456-426614174000', accessToken: 'a'.repeat(43), order: { id: 42 } });

    expect(service.currentPublicId(42)).toBe('123e4567-e89b-42d3-a456-426614174000');
    expect(sessionStorage.getItem('pinatech-guest-order-public-id-42')).toBe('123e4567-e89b-42d3-a456-426614174000');
    expect(sessionStorage.getItem('pinatech-guest-order-token-123e4567-e89b-42d3-a456-426614174000')).toBe('a'.repeat(43));
    expect(localStorage.getItem('pinatech-guest-order-token-123e4567-e89b-42d3-a456-426614174000')).toBeNull();
  });

  it('checks email eligibility with the guest CSRF token', () => {
    service.session.set(session);
    service.checkEmailEligibility('ada@example.com').subscribe();

    const eligibility = http.expectOne(`${environment.apiBaseUrl}/guest-checkout/email-eligibility`);
    expect(eligibility.request.method).toBe('POST');
    expect(eligibility.request.body).toEqual({ email: 'ada@example.com' });
    expect(eligibility.request.headers.get('X-Guest-CSRF')).toBe(session.csrfToken);
    expect(eligibility.request.context.get(GUEST_REQUEST)).toBe(true);
    eligibility.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('sends the temporary access token when reading an order', () => {
    const publicId = '123e4567-e89b-42d3-a456-426614174000';
    sessionStorage.setItem(`pinatech-guest-order-token-${publicId}`, 'a'.repeat(43));
    service.getOrder(publicId).subscribe();

    const get = http.expectOne(`${environment.apiBaseUrl}/guest-orders/${publicId}`);
    expect(get.request.headers.get('X-Order-Access-Token')).toBe('a'.repeat(43));
    expect(get.request.context.get(GUEST_REQUEST)).toBe(true);
    get.flush({ publicId, accessToken: null, order: { id: 42 } });
  });

  it('keeps independent order correlations for multiple guest purchases', () => {
    const first = '123e4567-e89b-42d3-a456-426614174000';
    const second = '223e4567-e89b-42d3-a456-426614174000';
    service.getOrder(first).subscribe();
    http.expectOne(`${environment.apiBaseUrl}/guest-orders/${first}`)
      .flush({ publicId: first, accessToken: null, order: { id: 42 } });
    service.getOrder(second).subscribe();
    http.expectOne(`${environment.apiBaseUrl}/guest-orders/${second}`)
      .flush({ publicId: second, accessToken: null, order: { id: 43 } });

    expect(service.currentPublicId(42)).toBe(first);
    expect(service.currentPublicId(43)).toBe(second);
    expect(service.currentPublicId(44)).toBeNull();
  });

  it('uses and remembers the public reference returned by Mercado Pago', () => {
    const publicId = '123e4567-e89b-42d3-a456-426614174000';

    expect(service.returnPublicId(42, publicId)).toBe(publicId);
    expect(service.currentPublicId(42)).toBe(publicId);
    expect(service.returnPublicId(43, 'invalid')).toBeNull();
  });

  it('captures an email access token in session storage and immediately scrubs the fragment', () => {
    const publicId = '123e4567-e89b-42d3-a456-426614174000';
    const token = 'a'.repeat(43);
    history.replaceState({}, '', `/pedido/${publicId}?source=email#token=${token}`);

    service.captureAccessTokenFromFragment(publicId);

    expect(sessionStorage.getItem(`pinatech-guest-order-token-${publicId}`)).toBe(token);
    expect(location.hash).toBe('');
    expect(location.pathname + location.search).toBe(`/pedido/${publicId}?source=email`);
  });

  it('refreshes CSRF once after a guest mutation 404 and preserves its idempotency key', () => {
    const publicId = '123e4567-e89b-42d3-a456-426614174000';
    service.session.set(session);
    service.mercadoPago(publicId, 'PENDING').subscribe();

    const first = http.expectOne(`${environment.apiBaseUrl}/guest-orders/${publicId}/payments/mercado-pago`);
    const key = first.request.headers.get('Idempotency-Key');
    expect(first.request.headers.get('X-Guest-CSRF')).toBe(session.csrfToken);
    first.flush(null, { status: 404, statusText: 'Not Found' });

    const refresh = http.expectOne(`${environment.apiBaseUrl}/guest-checkout/session`);
    const refreshed = { ...session, csrfToken: 'd'.repeat(43) };
    refresh.flush(refreshed);
    const retry = http.expectOne(`${environment.apiBaseUrl}/guest-orders/${publicId}/payments/mercado-pago`);
    expect(retry.request.headers.get('X-Guest-CSRF')).toBe(refreshed.csrfToken);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush({ attemptId: 'attempt', orderId: 42, status: 'PENDING', checkoutUrl: 'https://mp.example/checkout', expiresAt: '2099-01-01T00:00:00Z' });
  });
});
