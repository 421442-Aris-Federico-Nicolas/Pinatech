import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
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
    reservationExpiresAt: '2026-07-29T20:00:00Z',
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
  });

  it('shows a recoverable error when loading fails', async () => {
    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: { mine: () => throwError(() => new Error('network')) } },
        { provide: CheckoutService, useValue: { capabilities: () => of({ onlinePaymentsEnabled: false, paymentMethods: [] }) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No pudimos mostrar tus pedidos');
    expect(fixture.nativeElement.querySelector('button')?.textContent).toContain('Reintentar');
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
    button.click();

    expect(mercadoPago).toHaveBeenCalledWith(42, 'PENDING');
    expect(assign).toHaveBeenCalledWith('https://www.mercadopago.com.ar/checkout');
  });
});
