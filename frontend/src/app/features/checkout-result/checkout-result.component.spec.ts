import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Order, OrderService } from '../../core/orders/order.service';
import { CheckoutResultComponent } from './checkout-result.component';

describe('CheckoutResultComponent', () => {
  const order: Order = {
    id: 42,
    status: 'PENDING_PAYMENT',
    paymentStatus: 'REJECTED',
    fulfillmentStatus: 'PENDING',
    currency: 'ARS',
    paymentMethod: 'MERCADO_PAGO',
    deliveryMethod: null,
    total: 3000,
    createdAt: '2026-07-28T20:00:00Z',
    reservationExpiresAt: '2099-07-29T20:00:00Z',
    customerName: 'Ada Lovelace',
    customerEmail: 'ada@example.com',
    items: [],
  };

  afterEach(() => vi.useRealTimers());

  it('ignores Mercado Pago status query params and renders the backend status', async () => {
    const get = vi.fn(() => of(order));
    await TestBed.configureTestingModule({
      imports: [CheckoutResultComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ orderId: '42', status: 'approved', collection_status: 'approved' }) } } },
        { provide: OrderService, useValue: { get } },
      ],
    }).compileComponents();
    vi.useFakeTimers();

    const fixture = TestBed.createComponent(CheckoutResultComponent);
    vi.advanceTimersByTime(0);
    fixture.detectChanges();

    expect(get).toHaveBeenCalledWith(42);
    expect(fixture.nativeElement.textContent).toContain('Pago rechazado');
    expect(fixture.nativeElement.textContent).not.toContain('Pago aprobado');
    fixture.destroy();
  });

  it('bounds polling while the backend keeps the payment pending', async () => {
    const get = vi.fn(() => of({ ...order, paymentStatus: 'PENDING' }));
    await TestBed.configureTestingModule({
      imports: [CheckoutResultComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ orderId: '42' }) } } },
        { provide: OrderService, useValue: { get } },
      ],
    }).compileComponents();
    vi.useFakeTimers();

    const fixture = TestBed.createComponent(CheckoutResultComponent);
    vi.advanceTimersByTime(10000);
    fixture.detectChanges();

    expect(get).toHaveBeenCalledTimes(6);
    expect(fixture.nativeElement.textContent).toContain('Pago pendiente');
    expect(fixture.nativeElement.textContent).toContain('Todavía no recibimos una confirmación definitiva');
    fixture.destroy();
  });
});
