import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { GuestCheckoutService } from '../../core/guest/guest-checkout.service';
import { Order } from '../../core/orders/order.service';
import { GuestCheckoutResultComponent } from './guest-checkout-result.component';

describe('GuestCheckoutResultComponent', () => {
  afterEach(() => vi.useRealTimers());

  it('uses the temporary public id and renders only the authoritative guest order status', async () => {
    const publicId = '123e4567-e89b-42d3-a456-426614174000';
    const order: Order = { id: 42, status: 'PAID', paymentStatus: 'APPROVED', fulfillmentStatus: 'PENDING', currency: 'ARS',
      paymentMethod: 'MERCADO_PAGO', deliveryMethod: null, fulfillmentMethod: 'PICKUP', pickupLocation: null, subtotal: 3000,
      shippingCost: 0, paymentDiscount: 0, paymentSurcharge: 0, total: 3000, createdAt: '2026-09-08T10:00:00Z',
      reservationExpiresAt: null, cancellationReason: null, cancelledAt: null, customerName: 'Ada Lovelace', customerEmail: 'ada@example.com',
      items: [], deliveryAddress: null, shipment: null };
    const getOrder = vi.fn(() => of({ publicId, accessToken: null, order }));
    await TestBed.configureTestingModule({ imports: [GuestCheckoutResultComponent], providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ orderId: '42', status: 'rejected' }) } } },
      { provide: GuestCheckoutService, useValue: { returnPublicId: () => publicId, getOrder } },
    ] }).compileComponents();
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(GuestCheckoutResultComponent);
    vi.advanceTimersByTime(0);
    fixture.detectChanges();

    expect(getOrder).toHaveBeenCalledWith(publicId);
    expect(fixture.nativeElement.textContent).toContain('Pago aprobado');
    expect(fixture.nativeElement.textContent).not.toContain('Pago rechazado');
    expect((fixture.nativeElement.querySelector('a[href^="/pedido/"]') as HTMLAnchorElement).getAttribute('href')).toBe(`/pedido/${publicId}`);
    fixture.destroy();
  });

  it.each([
    ['IN_MEDIATION', 'Pago en mediación'],
    ['CHARGEBACK', 'Pago contracargado'],
  ] as const)('renders guest %s as a terminal dispute state', async (paymentStatus, message) => {
    const publicId = '123e4567-e89b-42d3-a456-426614174000';
    const order: Order = { id: 42, status: 'PAID', paymentStatus, fulfillmentStatus: 'PENDING', currency: 'ARS',
      paymentMethod: 'MERCADO_PAGO', deliveryMethod: null, fulfillmentMethod: 'PICKUP', pickupLocation: null, subtotal: 3000,
      shippingCost: 0, paymentDiscount: 0, paymentSurcharge: 0, total: 3000, createdAt: '2026-09-08T10:00:00Z',
      reservationExpiresAt: null, cancellationReason: null, cancelledAt: null, customerName: 'Ada Lovelace', customerEmail: 'ada@example.com',
      items: [], deliveryAddress: null, shipment: null };
    const getOrder = vi.fn(() => of({ publicId, accessToken: null, order }));
    await TestBed.configureTestingModule({ imports: [GuestCheckoutResultComponent], providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ orderId: '42' }) } } },
      { provide: GuestCheckoutService, useValue: { returnPublicId: () => publicId, getOrder } },
    ] }).compileComponents();
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(GuestCheckoutResultComponent);
    vi.advanceTimersByTime(10000);
    fixture.detectChanges();

    expect(getOrder).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain(message);
    fixture.destroy();
  });
});
