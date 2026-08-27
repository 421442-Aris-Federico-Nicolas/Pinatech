import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { OrderService } from './order.service';

describe('OrderService', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('gets an order by ID from the backend', () => {
    let fulfillmentMethod: string | null = null;
    let pickupName = '';
    TestBed.inject(OrderService).get(42).subscribe((order) => {
      fulfillmentMethod = order.fulfillmentMethod;
      pickupName = order.pickupLocation?.name ?? '';
    });

    const request = TestBed.inject(HttpTestingController).expectOne(`${environment.apiBaseUrl}/orders/42`);
    expect(request.request.method).toBe('GET');
    request.flush({
      id: 42,
      fulfillmentMethod: 'PICKUP',
      pickupLocation: { code: 'CORDOBA_CENTRO', version: 'v1', name: 'Pinatech Centro', addressLines: ['Av. Colón 123'], locality: 'Córdoba', provinceCode: 'X', postalCode: '5000', instructions: 'Presentá tu DNI.', hours: 'Lunes a viernes de 9 a 18.' },
    });
    expect(fulfillmentMethod).toBe('PICKUP');
    expect(pickupName).toBe('Pinatech Centro');
  });

  it('gets the current customer orders', () => {
    TestBed.inject(OrderService).mine().subscribe();

    const request = TestBed.inject(HttpTestingController).expectOne(`${environment.apiBaseUrl}/orders/me`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });
});
