import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
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
    fulfillmentMethod: 'PICKUP',
    pickupLocation: { code: 'CORDOBA_CENTRO', version: 'v1', name: 'Pinatech Centro', addressLines: ['Av. Colón 123'], locality: 'Córdoba', provinceCode: 'X', postalCode: '5000', instructions: 'Presentá tu DNI.', hours: 'Lunes a viernes de 9 a 18.' },
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
    expect(fixture.nativeElement.querySelector('app-pinatech-sale-success')).toBeNull();
    expect(fixture.nativeElement.querySelector('.result-card')?.classList).toContain('app-card');
    expect(fixture.nativeElement.querySelector('.actions a')?.classList).toContain('app-button');
    expect(fixture.nativeElement.querySelector('.state .app-feedback__body[role="status"]')?.querySelector('button')).toBeNull();
    expect(fixture.nativeElement.querySelector('[translate="no"]')?.textContent).toBe('Mercado Pago');
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
    expect(fixture.nativeElement.querySelector('app-pinatech-sale-success')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Todavía no recibimos una confirmación definitiva');
    const retry = [...fixture.nativeElement.querySelectorAll('button')]
      .find((button: HTMLButtonElement) => button.textContent?.includes('Volver a verificar el pago'));
    expect(retry).toBeTruthy();
    const announcement = fixture.nativeElement.querySelector('.state .app-feedback__body[role="status"]') as HTMLElement;
    expect(announcement).toBeTruthy();
    expect(announcement.contains(retry as Node)).toBe(false);
    expect(announcement.closest('.state')?.contains(retry as Node)).toBe(true);

    (retry as HTMLButtonElement).click();
    fixture.detectChanges();
    expect((retry as HTMLButtonElement).isConnected).toBe(true);
    expect((retry as HTMLButtonElement).disabled).toBe(true);
    expect((retry as HTMLButtonElement).getAttribute('aria-busy')).toBe('true');
    fixture.destroy();
  });

  it('keeps the error retry control mounted while verifying again', async () => {
    const retry = new Subject<Order>();
    const get = vi.fn()
      .mockReturnValueOnce(throwError(() => new Error('network')))
      .mockReturnValueOnce(retry);
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
    vi.advanceTimersByTime(0);
    fixture.detectChanges();
    const retryButton = fixture.nativeElement.querySelector('.result-card app-feedback button') as HTMLButtonElement;

    retryButton.click();
    vi.advanceTimersByTime(0);
    fixture.detectChanges();

    expect(retryButton.isConnected).toBe(true);
    expect(retryButton.disabled).toBe(true);
    expect(retryButton.getAttribute('aria-busy')).toBe('true');
    expect(fixture.nativeElement.querySelector('.result-card > app-feedback .app-feedback__body[role="status"]')?.textContent).toContain('Volviendo a verificar');
    fixture.destroy();
  });

  it.each(['REFUND_PENDING', 'REFUNDED'] as const)('does not render the sale mascot for %s', async (paymentStatus) => {
    const get = vi.fn(() => of({ ...order, paymentStatus }));
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
    vi.advanceTimersByTime(0);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-pinatech-sale-success')).toBeNull();
    fixture.destroy();
  });

  it('renders the sale mascot only for the server-approved payment status', async () => {
    const get = vi.fn(() => of({ ...order, paymentStatus: 'APPROVED' as const }));
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
    vi.advanceTimersByTime(0);
    fixture.detectChanges();

    const mascot = fixture.nativeElement.querySelector('app-pinatech-sale-success') as HTMLElement;
    expect(mascot).toBeTruthy();
    expect(mascot.querySelector('[role="status"]')?.textContent).toContain('Pago aprobado');
    expect(mascot.querySelector('.order-number')?.textContent).toContain('Pedido #42');
    expect(fixture.nativeElement.querySelector('dl dd')?.textContent).toContain('3');
    expect(fixture.nativeElement.querySelectorAll('dl dt')).toHaveLength(1);
    fixture.destroy();
  });
});
