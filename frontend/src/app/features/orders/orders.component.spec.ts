import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Order, OrderService } from '../../core/orders/order.service';
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
    items: [{ productId: 1, productName: 'Teclado', unitPrice: 1500, quantity: 2, subtotal: 3000 }],
  };

  it('renders Spanish statuses and the expiration for pending payment', async () => {
    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [provideRouter([]), { provide: OrderService, useValue: { mine: () => of([order]) } }],
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
      providers: [provideRouter([]), { provide: OrderService, useValue: { mine: () => throwError(() => new Error('network')) } }],
    }).compileComponents();

    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No pudimos mostrar tus pedidos');
    expect(fixture.nativeElement.querySelector('button')?.textContent).toContain('Reintentar');
  });
});
