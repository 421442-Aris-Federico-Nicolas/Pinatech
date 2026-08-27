import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartItem, CartService, OrderConfirmation } from '../../core/cart/cart.service';
import { CheckoutComponent } from './checkout.component';
import { CHECKOUT_WINDOW, CheckoutCapabilities, CheckoutService, MercadoPagoCheckout } from './checkout.service';

describe('CheckoutComponent', () => {
  const authenticatedUser = signal({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, emailVerified: true, roles: ['CUSTOMER'] });
  const requestEmailVerification = vi.fn(() => of({ message: 'accepted' }));
  const pickupLocation = { code: 'CORDOBA_CENTRO', version: 'v1', name: 'Pinatech Centro', addressLines: ['Av. Colón 123'], locality: 'Córdoba', provinceCode: 'X', postalCode: '5000', instructions: 'Presentá tu DNI.', hours: 'Lunes a viernes de 9 a 18.' };
  const item: CartItem = {
    product: { id: 1, name: 'Teclado', slug: 'teclado', description: 'Mecánico', price: 1500, categoryId: 2, categoryName: 'Periféricos', brandId: 3, brandName: 'Marca', images: [], specifications: [], variants: [{ id: 11, colorName: 'Negro', colorHex: '#000000', inStock: true, availableQuantity: 5 }] },
    variant: { id: 11, colorName: 'Negro', colorHex: '#000000', inStock: true, availableQuantity: 5 },
    quantity: 2,
  };
  const capabilities: CheckoutCapabilities = {
    currency: 'ARS',
    orderRequestsEnabled: true,
    onlinePaymentsEnabled: true,
    deliveryQuotesEnabled: false,
    paymentMethods: ['MERCADO_PAGO'],
    deliveryMethods: [],
    fulfillmentMethods: ['PICKUP'],
    pickupLocations: [pickupLocation],
  };
  const order: OrderConfirmation = {
    id: 42,
    status: 'PENDING_PAYMENT',
    paymentStatus: 'PENDING',
    fulfillmentStatus: 'PENDING',
    currency: 'ARS',
    paymentMethod: null,
    deliveryMethod: null,
    fulfillmentMethod: 'PICKUP',
    pickupLocation,
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

  beforeEach(() => {
    authenticatedUser.set({ ...authenticatedUser(), emailVerified: true });
    requestEmailVerification.mockClear();
    TestBed.configureTestingModule({ providers: [{ provide: AuthService, useValue: { user: authenticatedUser, requestEmailVerification } }] });
  });

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
    expect(fixture.nativeElement.textContent).toContain('Pinatech Centro');
    expect(fixture.nativeElement.textContent).toContain('Entiendo que debo retirar en Córdoba');
    const paymentButton = [...fixture.nativeElement.querySelectorAll('button')]
      .find((candidate: HTMLButtonElement) => candidate.textContent?.includes('Pagar con Mercado Pago')) as HTMLButtonElement;
    expect(paymentButton.getAttribute('aria-label')).toBe('Pagar con Mercado Pago');
    expect(paymentButton.classList).toContain('app-button');
    expect(fixture.nativeElement.querySelectorAll('.panel.app-card').length).toBe(3);
    expect(fixture.nativeElement.querySelector('.summary')?.classList).toContain('app-card');
    expect(fixture.nativeElement.querySelector('.product-list')?.tagName).toBe('UL');
    (fixture.nativeElement.querySelector('.pickup-consent input') as HTMLInputElement).click();
    fixture.detectChanges();

    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(cart.checkout).toHaveBeenCalledWith('PICKUP', pickupLocation.code, pickupLocation.version);
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
    fixture.detectChanges();
    fixture.componentInstance.pickupAccepted.set(true);
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
    expect((fixture.nativeElement.querySelector('button[aria-label="Pagar con Mercado Pago"]') as HTMLButtonElement).disabled).toBe(true);
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
    expect(fixture.nativeElement.querySelector('.error .app-feedback__body[role="status"]')?.textContent).toContain('Volviendo a consultar');
  });

  it('retries cart availability instead of bypassing a failed reconciliation', async () => {
    const reconcile = vi.fn()
      .mockReturnValueOnce(of(false))
      .mockReturnValueOnce(of(true));
    const capabilitiesRequest = vi.fn(() => of(capabilities));
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(() => of(order)), reconcile, notice: signal(''), dismissNotice: vi.fn(),
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
    const retry = [...fixture.nativeElement.querySelectorAll('button')]
      .find((button: HTMLButtonElement) => button.textContent?.includes('Reintentar verificación')) as HTMLButtonElement;

    expect(capabilitiesRequest).not.toHaveBeenCalled();
    retry.click();
    fixture.detectChanges();

    expect(reconcile).toHaveBeenCalledTimes(2);
    expect(capabilitiesRequest).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Pagar con Mercado Pago');
  });

  it('blocks checkout until the customer verifies their email', async () => {
    authenticatedUser.set({ ...authenticatedUser(), emailVerified: false });
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(() => of(order)), reconcile: vi.fn(() => of(true)), notice: signal(''), dismissNotice: vi.fn(),
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
    fixture.componentInstance.pickupAccepted.set(true);
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(cart.checkout).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Verificá tu email para comprar');
    expect(fixture.nativeElement.querySelector('.verification-required a')?.getAttribute('href')).toBe('/profile');
    expect((fixture.nativeElement.querySelector('button[aria-label="Pagar con Mercado Pago"]') as HTMLButtonElement).disabled).toBe(true);
    const resend = [...fixture.nativeElement.querySelectorAll('.verification-required button')]
      .find((button: HTMLButtonElement) => button.textContent?.includes('Reenviar verificación')) as HTMLButtonElement;
    resend.click();
    fixture.detectChanges();
    expect(requestEmailVerification).toHaveBeenCalledWith('ada@example.com');
    expect(fixture.nativeElement.textContent).toContain('te enviamos un nuevo enlace');
  });

  it('blocks checkout when pickup is not configured', async () => {
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(() => of(order)), reconcile: vi.fn(() => of(true)), notice: signal(''), dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: { capabilities: () => of({ ...capabilities, pickupLocations: [] }) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    expect(cart.checkout).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('No hay un punto de retiro disponible');
  });

  it('requires pickup acceptance again when the selected location version changes', async () => {
    const capabilitiesRequest = vi.fn()
      .mockReturnValueOnce(of(capabilities))
      .mockReturnValueOnce(of({ ...capabilities, pickupLocations: [{ ...pickupLocation, version: 'v2' }] }));
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
    fixture.componentInstance.pickupAccepted.set(true);

    fixture.componentInstance.loadCapabilities();

    expect(fixture.componentInstance.selectedPickupCode()).toBe(pickupLocation.code);
    expect(fixture.componentInstance.pickupAccepted()).toBe(false);
  });

  it('shows the profile action when the backend requires email verification', async () => {
    const error = new HttpErrorResponse({
      status: 403,
      error: { type: 'https://computer-store.dev/errors/email-verification-required' },
    });
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(() => throwError(() => error)), completeCheckout: vi.fn(),
      reconcile: vi.fn(() => of(true)), notice: signal(''), dismissNotice: vi.fn(),
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
    fixture.componentInstance.pickupAccepted.set(true);
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Necesitás verificar tu email');
    expect(fixture.nativeElement.querySelector('.submit-error a')?.getAttribute('href')).toBe('/profile');
    expect(cart.items()).toEqual([item]);
  });

  it.each([
    { status: 409, expected: 'Cambió el stock', action: 'Revisar carrito' },
    { status: 0, expected: 'No pudimos conectarnos', action: 'Reintentar' },
  ])('distinguishes checkout error status $status', async ({ status, expected, action }) => {
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(() => throwError(() => new HttpErrorResponse({ status }))), completeCheckout: vi.fn(),
      reconcile: vi.fn(() => of(true)), notice: signal(''), dismissNotice: vi.fn(),
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
    fixture.componentInstance.pickupAccepted.set(true);
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(expected);
    expect(fixture.nativeElement.textContent).toContain(action);
    expect(cart.items()).toEqual([item]);
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
    expect(fixture.nativeElement.querySelector('.result-status .app-feedback__body')?.getAttribute('role')).toBe('status');

    dismissButton.click();
    fixture.detectChanges();

    expect(cart.dismissConfirmation).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.textContent).toContain('Tu carrito está vacío');
    expect(fixture.nativeElement.textContent).toContain('Ver catálogo');
  });
});
