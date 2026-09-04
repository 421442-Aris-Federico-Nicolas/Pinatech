import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { CheckoutService } from './checkout.service';

describe('CheckoutService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('loads fulfillment methods and pickup locations from capabilities', () => {
    const service = TestBed.inject(CheckoutService);
    const http = TestBed.inject(HttpTestingController);
    let pickupCode = '';

    service.capabilities().subscribe((capabilities) => pickupCode = capabilities.pickupLocations[0].code);
    const request = http.expectOne(`${environment.apiBaseUrl}/checkout/capabilities`);
    expect(request.request.method).toBe('GET');
    request.flush({
      currency: 'ARS', orderRequestsEnabled: true, onlinePaymentsEnabled: true,
      deliveryQuotesEnabled: false, paymentMethods: ['MERCADO_PAGO'], bankTransferDiscountRate: 0.1, deliveryMethods: [],
      fulfillmentMethods: ['PICKUP'],
      pickupLocations: [{ code: 'CORDOBA_CENTRO', version: 'v1', name: 'Pinatech Centro', addressLines: ['Av. Colón 123'], locality: 'Córdoba', provinceCode: 'X', postalCode: '5000', instructions: 'Presentá tu DNI.', hours: 'Lunes a viernes de 9 a 18.' }],
    });

    expect(pickupCode).toBe('CORDOBA_CENTRO');
  });

  it('uses a stable payment idempotency key separate from order creation', () => {
    const service = TestBed.inject(CheckoutService);
    const http = TestBed.inject(HttpTestingController);
    const url = `${environment.apiBaseUrl}/orders/42/payments/mercado-pago`;

    service.mercadoPago(42, 'PENDING').subscribe({ error: () => undefined });
    const first = http.expectOne(url);
    const firstKey = first.request.headers.get('Idempotency-Key');
    expect(first.request.method).toBe('POST');
    expect(first.request.body).toEqual({});
    expect(firstKey).toBeTruthy();
    first.flush({ detail: 'Temporary failure' }, { status: 503, statusText: 'Unavailable' });

    service.mercadoPago(42, 'PENDING').subscribe();
    const retry = http.expectOne(url);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(firstKey);
    retry.flush({ attemptId: 'a-1', orderId: 42, status: 'PENDING', checkoutUrl: 'https://example.com', expiresAt: '2099-01-01T00:00:00Z' });

    service.mercadoPago(42, 'REJECTED').subscribe();
    const newAttempt = http.expectOne(url);
    expect(newAttempt.request.headers.get('Idempotency-Key')).not.toBe(firstKey);
    newAttempt.flush({ attemptId: 'a-2', orderId: 42, status: 'PENDING', checkoutUrl: 'https://example.com', expiresAt: '2099-01-01T00:00:00Z' });
  });

  it('posts typed cart lines to the Zipnova quote endpoint', () => {
    const service = TestBed.inject(CheckoutService);
    const http = TestBed.inject(HttpTestingController);
    let carrier = '';

    service.shippingQuotes([{ variantId: 11, quantity: 2 }]).subscribe((response) => carrier = response.options[0].carrier);
    const request = http.expectOne(`${environment.apiBaseUrl}/shipping/quotes`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ items: [{ variantId: 11, quantity: 2 }] });
    request.flush({ options: [{
      shippingQuoteId: '0b47f21d-a03a-4bc6-a59a-a761a31bd68d', carrier: 'Andreani',
      serviceCode: 'standard', service: 'Estándar', logisticType: 'carrier_pickup', amount: 850,
      currency: 'ARS', estimatedDeliveryAt: '2099-08-03T20:00:00Z', expiresAt: '2099-08-01T20:10:00Z', tags: [],
    }] });

    expect(carrier).toBe('Andreani');
  });
});
