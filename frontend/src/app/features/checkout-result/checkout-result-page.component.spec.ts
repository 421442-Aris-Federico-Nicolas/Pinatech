import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { GuestCheckoutService } from '../../core/guest/guest-checkout.service';
import { OrderService } from '../../core/orders/order.service';
import { CheckoutResultPageComponent } from './checkout-result-page.component';

describe('CheckoutResultPageComponent', () => {
  afterEach(() => vi.useRealTimers());

  it('prioritizes the correlated guest order even when an account session exists', async () => {
    const publicId = '123e4567-e89b-42d3-a456-426614174000';
    const order = {
      id: 42, status: 'PAID', paymentStatus: 'APPROVED', fulfillmentStatus: 'PENDING', currency: 'ARS',
      paymentMethod: 'MERCADO_PAGO', deliveryMethod: null, fulfillmentMethod: 'PICKUP', pickupLocation: null,
      subtotal: 3000, shippingCost: 0, paymentDiscount: 0, paymentSurcharge: 0, total: 3000,
      createdAt: '2026-09-08T10:00:00Z', reservationExpiresAt: null, cancellationReason: null, cancelledAt: null,
      customerName: 'Ada Lovelace', customerEmail: 'ada@example.com', items: [], deliveryAddress: null, shipment: null,
    };
    const returnPublicId = vi.fn((orderId: number | null, supplied: string | null) => supplied ?? (orderId === 42 ? publicId : null));
    const accountGet = vi.fn(() => of(order));
    await TestBed.configureTestingModule({ imports: [CheckoutResultPageComponent], providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ orderId: '42' }) } } },
      { provide: AuthService, useValue: { user: signal({ id: 7, roles: ['CUSTOMER'] }) } },
      { provide: GuestCheckoutService, useValue: { returnPublicId, getOrder: () => of({ publicId, accessToken: null, order }) } },
      { provide: OrderService, useValue: { get: accountGet } },
    ] }).compileComponents();
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(CheckoutResultPageComponent);
    vi.advanceTimersByTime(0);
    fixture.detectChanges();

    expect(returnPublicId).toHaveBeenCalledWith(42, null);
    expect(fixture.nativeElement.querySelector('app-guest-checkout-result')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-checkout-result')).toBeNull();
    expect(accountGet).not.toHaveBeenCalled();
    fixture.destroy();
  });

  it('uses the public guest reference supplied by the Mercado Pago return', async () => {
    const publicId = '123e4567-e89b-42d3-a456-426614174000';
    const returnPublicId = vi.fn((_orderId: number | null, supplied: string | null) => supplied);
    await TestBed.configureTestingModule({ imports: [CheckoutResultPageComponent], providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ orderId: '42', guestOrder: publicId }) } } },
      { provide: AuthService, useValue: { user: signal({ id: 7, roles: ['CUSTOMER'] }) } },
      { provide: GuestCheckoutService, useValue: { returnPublicId, getOrder: () => of({ publicId, accessToken: null, order: { paymentStatus: 'PENDING' } }) } },
      { provide: OrderService, useValue: { get: vi.fn() } },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(CheckoutResultPageComponent);
    fixture.detectChanges();

    expect(returnPublicId).toHaveBeenCalledWith(42, publicId);
    expect(fixture.nativeElement.querySelector('app-guest-checkout-result')).toBeTruthy();
    fixture.destroy();
  });
});
