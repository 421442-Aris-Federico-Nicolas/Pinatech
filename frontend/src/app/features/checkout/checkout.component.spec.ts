import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { CartItem, CartService, OrderConfirmation } from '../../core/cart/cart.service';
import { CheckoutComponent } from './checkout.component';
import { CHECKOUT_WINDOW, CheckoutCapabilities, CheckoutService, MercadoPagoCheckout } from './checkout.service';

describe('CheckoutComponent', () => {
  const item: CartItem = {
    product: { id: 1, name: 'Teclado', slug: 'teclado', description: 'Mecánico', price: 1500, categoryId: 2, categoryName: 'Periféricos', brandId: 3, brandName: 'Marca', images: [], specifications: [], variants: [{ id: 11, colorName: 'Negro', colorHex: '#000000', inStock: true }] },
    variant: { id: 11, colorName: 'Negro', colorHex: '#000000', inStock: true },
    quantity: 2,
  };
  const capabilities: CheckoutCapabilities = {
    currency: 'ARS',
    orderRequestsEnabled: true,
    onlinePaymentsEnabled: true,
    deliveryQuotesEnabled: false,
    paymentMethods: ['MERCADO_PAGO'],
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
  const payment: MercadoPagoCheckout = {
    attemptId: 'attempt-1',
    orderId: 42,
    status: 'PENDING',
    checkoutUrl: 'https://www.mercadopago.com.ar/checkout/v1/redirect',
    expiresAt: '2026-07-29T20:00:00Z',
  };

  it('creates the order, creates the Mercado Pago preference and redirects in that order', async () => {
    const calls: string[] = [];
    const assign = vi.fn(() => calls.push('redirect'));
    const cart = {
      items: signal([item]),
      count: signal(2),
      total: signal(3000),
      confirmation: signal(null),
      checkout: vi.fn(() => { calls.push('order'); return of(order); }),
      completeCheckout: vi.fn(() => calls.push('complete')),
      reconcile: vi.fn(() => of(true)),
      notice: signal(''),
      dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: {
          capabilities: () => of(capabilities),
          mercadoPago: vi.fn(() => { calls.push('payment'); return of(payment); }),
        } },
        { provide: CHECKOUT_WINDOW, useValue: { location: { assign } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Pagar con Mercado Pago');
    expect(fixture.nativeElement.textContent).toContain('Las cotizaciones y los proveedores de entrega todavía no están configurados.');
    const paymentButton = [...fixture.nativeElement.querySelectorAll('button')]
      .find((candidate: HTMLButtonElement) => candidate.textContent?.includes('Pagar con Mercado Pago')) as HTMLButtonElement;
    expect(paymentButton.getAttribute('aria-label')).toBe('Pagar con Mercado Pago');
    expect(fixture.nativeElement.querySelector('.product-list')?.tagName).toBe('UL');

    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(cart.checkout).toHaveBeenCalledOnce();
    expect(cart.completeCheckout).toHaveBeenCalledWith(order);
    expect(assign).toHaveBeenCalledWith(payment.checkoutUrl);
    expect(calls).toEqual(['order', 'payment', 'complete', 'redirect']);
  });

  it('keeps the cart and checkout state when preference creation fails', async () => {
    const cart = {
      items: signal([item]),
      count: signal(2),
      total: signal(3000),
      confirmation: signal(null),
      checkout: vi.fn(() => of(order)),
      completeCheckout: vi.fn(),
      reconcile: vi.fn(() => of(true)),
      notice: signal(''),
      dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: {
          capabilities: () => of(capabilities),
          mercadoPago: () => throwError(() => new Error('unavailable')),
        } },
        { provide: CHECKOUT_WINDOW, useValue: { location: { assign: vi.fn() } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(cart.items()).toEqual([item]);
    expect(cart.completeCheckout).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Conservamos tu carrito');
  });

  it('does not create an order when Mercado Pago is disabled', async () => {
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(() => of(order)), reconcile: vi.fn(() => of(true)), notice: signal(''), dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: { capabilities: () => of({ ...capabilities, onlinePaymentsEnabled: false }) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.componentInstance.submit();

    expect(cart.checkout).not.toHaveBeenCalled();
  });

  it('does not create an order when order requests are disabled', async () => {
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(() => of(order)), reconcile: vi.fn(() => of(true)), notice: signal(''), dismissNotice: vi.fn(),
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
    fixture.detectChanges();
    fixture.componentInstance.submit();

    expect(cart.checkout).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('No se pueden iniciar pedidos en este momento.');
    expect(fixture.nativeElement.textContent).not.toContain('Pagar con Mercado Pago');
  });

  it('keeps the capabilities retry control mounted while retrying', async () => {
    const retry = new Subject<CheckoutCapabilities>();
    const capabilitiesRequest = vi.fn()
      .mockReturnValueOnce(throwError(() => new Error('network')))
      .mockReturnValueOnce(retry);
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(() => of(order)), reconcile: vi.fn(() => of(true)), notice: signal(''), dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: { capabilities: capabilitiesRequest } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
    const retryButton = [...fixture.nativeElement.querySelectorAll('button')]
      .find((button: HTMLButtonElement) => button.textContent?.includes('Reintentar consulta')) as HTMLButtonElement;

    retryButton.click();
    fixture.detectChanges();

    expect(retryButton.isConnected).toBe(true);
    expect(retryButton.disabled).toBe(true);
    expect(retryButton.getAttribute('aria-busy')).toBe('true');
    expect(fixture.nativeElement.querySelector('.error [role="status"]')?.textContent).toContain('Volviendo a consultar');
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
      reconcile: vi.fn(() => of(true)),
      notice: signal(''),
      dismissNotice: vi.fn(),
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
    expect(fixture.nativeElement.querySelector('.result')?.getAttribute('role')).toBeNull();
    expect(fixture.nativeElement.querySelector('.result-status')?.getAttribute('role')).toBe('status');

    dismissButton.click();
    fixture.detectChanges();

    expect(cart.dismissConfirmation).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Tu carrito está vacío');
    expect(fixture.nativeElement.textContent).toContain('Ver catálogo');
  });
});
