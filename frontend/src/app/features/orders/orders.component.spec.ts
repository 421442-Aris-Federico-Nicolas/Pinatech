import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { Order, OrderService } from '../../core/orders/order.service';
import { CHECKOUT_WINDOW, CheckoutService } from '../checkout/checkout.service';
import { OrdersComponent } from './orders.component';

describe('OrdersComponent', () => {
  const order: Order = {
    id: 42,
    status: 'PENDING_PAYMENT',
    paymentStatus: 'PENDING',
    fulfillmentStatus: 'PENDING',
    currency: 'ARS',
    paymentMethod: null,
    deliveryMethod: null,
    total: 3000,
    createdAt: '2026-07-28T20:00:00Z',
    reservationExpiresAt: '2099-07-29T20:00:00Z',
    customerName: 'Ada Lovelace',
    customerEmail: 'ada@example.com',
    items: [{ productId: 1, variantId: 11, productName: 'Teclado', colorName: 'Negro', colorHex: '#000000', unitPrice: 1500, quantity: 2, subtotal: 3000 }],
  };

  it('renders Spanish statuses and the expiration for pending payment', async () => {
    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: { mine: () => of([order]) } },
        { provide: CheckoutService, useValue: { capabilities: () => of({ onlinePaymentsEnabled: false, paymentMethods: [] }) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Pendiente de pago');
    expect(fixture.nativeElement.textContent).toContain('Preparación pendiente');
    expect(fixture.nativeElement.textContent).toContain('Reserva vigente hasta');
    expect(fixture.nativeElement.textContent).toContain('A definir');
    expect(fixture.nativeElement.querySelector('.orders')?.tagName).toBe('OL');
    expect(fixture.nativeElement.querySelector('.items')?.tagName).toBe('UL');
    expect(fixture.nativeElement.querySelector('.badges')?.tagName).toBe('UL');
  });

  it('shows a recoverable error when loading fails', async () => {
    const retry = new Subject<Order[]>();
    const mine = vi.fn()
      .mockReturnValueOnce(throwError(() => new Error('network')))
      .mockReturnValueOnce(retry);
    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: { mine } },
        { provide: CheckoutService, useValue: { capabilities: () => of({ onlinePaymentsEnabled: false, paymentMethods: [] }) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No pudimos mostrar tus pedidos');
    const retryButton = fixture.nativeElement.querySelector('.state button') as HTMLButtonElement;
    expect(retryButton.textContent).toContain('Reintentar');

    retryButton.click();
    fixture.detectChanges();

    expect(retryButton.isConnected).toBe(true);
    expect(retryButton.disabled).toBe(true);
    expect(retryButton.getAttribute('aria-busy')).toBe('true');
    expect(fixture.nativeElement.querySelector('.state [role="status"]')?.textContent).toContain('Volviendo a cargar');
  });

  it('explains when an expired reservation can no longer be paid', async () => {
    const expired = { ...order, reservationExpiresAt: '2000-07-29T20:00:00Z' };
    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: { mine: () => of([expired]) } },
        { provide: CheckoutService, useValue: { capabilities: () => of({ onlinePaymentsEnabled: true, paymentMethods: ['MERCADO_PAGO'] }) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Reserva vencida');
    expect(fixture.nativeElement.textContent).toContain('ya no admite un nuevo intento de pago');
    expect(fixture.nativeElement.textContent).not.toContain('Continuar pago');
  });

  it('shows a recoverable capability error and announces its retry', async () => {
    const payable = { ...order, reservationExpiresAt: '2099-07-29T20:00:00Z' };
    const retry = new Subject<{ onlinePaymentsEnabled: boolean; paymentMethods: string[] }>();
    const capabilitiesRequest = vi.fn()
      .mockReturnValueOnce(throwError(() => new Error('network')))
      .mockReturnValueOnce(retry);
    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: { mine: () => of([payable]) } },
        { provide: CheckoutService, useValue: { capabilities: capabilitiesRequest } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();
    const retryButton = fixture.nativeElement.querySelector('.capability-error button') as HTMLButtonElement;
    expect(fixture.nativeElement.textContent).toContain('No pudimos consultar si el pago online está disponible.');

    retryButton.click();
    fixture.detectChanges();

    expect(retryButton.isConnected).toBe(true);
    expect(retryButton.disabled).toBe(true);
    expect(retryButton.getAttribute('aria-busy')).toBe('true');
    expect(fixture.nativeElement.querySelector('.capability-error [role="status"]')?.textContent).toContain('Volviendo a consultar');

    retry.next({ onlinePaymentsEnabled: true, paymentMethods: ['MERCADO_PAGO'] });
    retry.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Continuar pago');
  });

  it('continues a payable order through the backend-provided checkout URL', async () => {
    const payable = { ...order, reservationExpiresAt: '2099-07-29T20:00:00Z' };
    const assign = vi.fn();
    const mercadoPago = vi.fn(() => of({
      attemptId: 'attempt-1', orderId: 42, status: 'PENDING',
      checkoutUrl: 'https://www.mercadopago.com.ar/checkout', expiresAt: '2099-07-29T20:00:00Z',
    }));
    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: { mine: () => of([payable]) } },
        { provide: CheckoutService, useValue: {
          capabilities: () => of({ onlinePaymentsEnabled: true, paymentMethods: ['MERCADO_PAGO'] }),
          mercadoPago,
        } },
        { provide: CHECKOUT_WINDOW, useValue: { location: { assign } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();
    const button = [...fixture.nativeElement.querySelectorAll('button')]
      .find((candidate: HTMLButtonElement) => candidate.textContent?.includes('Continuar pago')) as HTMLButtonElement;
    expect(button.getAttribute('aria-label')).toBe('Continuar el pago del pedido 42');
    button.click();

    expect(mercadoPago).toHaveBeenCalledWith(42, 'PENDING');
    expect(assign).toHaveBeenCalledWith('https://www.mercadopago.com.ar/checkout');
  });
});
