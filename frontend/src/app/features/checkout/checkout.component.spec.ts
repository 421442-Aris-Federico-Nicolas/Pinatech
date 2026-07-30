import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CartItem, CartService, OrderConfirmation } from '../../core/cart/cart.service';
import { CheckoutComponent } from './checkout.component';
import { CheckoutCapabilities, CheckoutService } from './checkout.service';

describe('CheckoutComponent', () => {
  const item: CartItem = {
    product: { id: 1, name: 'Teclado', slug: 'teclado', description: 'Mecánico', price: 1500, categoryId: 2, categoryName: 'Periféricos', brandId: 3, brandName: 'Marca', images: [], specifications: [] },
    quantity: 2,
  };
  const capabilities: CheckoutCapabilities = {
    currency: 'ARS',
    orderRequestsEnabled: true,
    onlinePaymentsEnabled: false,
    deliveryQuotesEnabled: false,
    paymentMethods: [],
    deliveryMethods: [],
  };
  const order: OrderConfirmation = {
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
  };

  it('shows unavailable providers honestly and registers through CartService', async () => {
    const cart = {
      items: signal([item]),
      count: signal(2),
      total: signal(3000),
      confirmation: signal(null),
      checkout: vi.fn(() => of(order)),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: { capabilities: () => of(capabilities) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Los proveedores de pago online todavía no están configurados.');
    expect(fixture.nativeElement.textContent).toContain('Las cotizaciones y los proveedores de entrega todavía no están configurados.');

    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(cart.checkout).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Recibimos tu solicitud #42');
    expect(fixture.nativeElement.textContent).not.toContain('Compra confirmada');
  });

  it('does not register when order requests are disabled', async () => {
    const cart = {
      items: signal([item]),
      count: signal(2),
      total: signal(3000),
      confirmation: signal(null),
      checkout: vi.fn(() => of(order)),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: { capabilities: () => of({ ...capabilities, orderRequestsEnabled: false }) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.componentInstance.submit();

    expect(cart.checkout).not.toHaveBeenCalled();
  });

  it('marks an expired confirmation and lets the user dismiss it', async () => {
    const cart = {
      items: signal<CartItem[]>([]),
      count: signal(0),
      total: signal(0),
      confirmation: signal<OrderConfirmation | null>({
        ...order,
        reservationExpiresAt: '2000-01-01T20:00:00Z',
      }),
      dismissConfirmation: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: { capabilities: vi.fn() } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('La vigencia informada de la reserva ya venció');
    expect(fixture.nativeElement.textContent).not.toContain('Reserva vigente hasta');
    const dismissButton = [...fixture.nativeElement.querySelectorAll('button')]
      .find((button: HTMLButtonElement) => button.textContent?.includes('Cerrar confirmación')) as HTMLButtonElement;
    expect(dismissButton).toBeTruthy();

    dismissButton.click();
    fixture.detectChanges();

    expect(cart.dismissConfirmation).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Tu carrito está vacío');
    expect(fixture.nativeElement.textContent).toContain('Ver catálogo');
  });
});
