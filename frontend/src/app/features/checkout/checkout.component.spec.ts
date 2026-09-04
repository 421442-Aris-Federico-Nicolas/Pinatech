import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
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
    bankTransferDiscountRate: 0.1,
    deliveryMethods: ['ZIPNOVA'],
    fulfillmentMethods: ['PICKUP'],
    pickupLocations: [pickupLocation],
  };
  const order: OrderConfirmation = {
    id: 42,
    status: 'PENDING_PAYMENT',
    paymentStatus: 'PENDING',
    fulfillmentStatus: 'PENDING',
    currency: 'ARS',
    paymentMethod: 'MERCADO_PAGO',
    deliveryMethod: null,
    fulfillmentMethod: 'PICKUP',
    pickupLocation,
    subtotal: 3000,
    shippingCost: 0,
    paymentDiscount: 0,
    paymentSurcharge: 0,
    total: 3000,
    createdAt: '2026-07-28T20:00:00Z',
    reservationExpiresAt: '2026-07-29T20:00:00Z',
    deliveryAddress: null,
    shipment: null,
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

  function enterPaymentStep(fixture: ComponentFixture<CheckoutComponent>): void {
    fixture.componentInstance.selectPickup({ target: { value: pickupLocation.code } } as unknown as Event);
    fixture.componentInstance.pickupAccepted.set(true);
    fixture.componentInstance.continueToPayment();
    fixture.detectChanges();
  }

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
    expect(fixture.nativeElement.querySelectorAll('input[name="paymentMethod"]').length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Pinatech Centro');
    expect(fixture.nativeElement.textContent).not.toContain('Av. Colón 123');
    expect(fixture.nativeElement.querySelector('.pickup-consent')).toBeNull();
    expect(fixture.nativeElement.querySelector('.carrier-brand--pickup iconify-icon')?.getAttribute('icon')).toBe('line-md:map-marker');

    (fixture.nativeElement.querySelector(`input[value="${pickupLocation.code}"]`) as HTMLInputElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Av. Colón 123');
    expect(fixture.nativeElement.textContent).toContain('Lunes a viernes de 9 a 18.');
    expect(fixture.nativeElement.textContent).toContain('Presentá tu DNI.');
    expect(fixture.nativeElement.textContent).toContain('Entiendo que debo retirar en Córdoba');
    expect(fixture.nativeElement.querySelector('.delivery-card--pickup .pickup-consent')).toBeTruthy();
    (fixture.nativeElement.querySelector('.pickup-consent input') as HTMLInputElement).click();
    fixture.detectChanges();
    fixture.componentInstance.continueToPayment();
    fixture.detectChanges();
    const radios = fixture.nativeElement.querySelectorAll('input[name="paymentMethod"]') as NodeListOf<HTMLInputElement>;
    expect([...radios].every((radio) => !radio.checked)).toBe(true);
    radios[0].click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Pagar con Mercado Pago');
    const paymentButton = [...fixture.nativeElement.querySelectorAll('button')]
      .find((candidate: HTMLButtonElement) => candidate.textContent?.includes('Pagar con Mercado Pago')) as HTMLButtonElement;
    expect(paymentButton.getAttribute('aria-label')).toBe('Pagar con Mercado Pago');
    expect(paymentButton.classList).toContain('app-button');
    expect(fixture.nativeElement.querySelectorAll('.panel.app-card').length).toBe(1);
    expect(fixture.nativeElement.querySelector('.summary')?.classList).toContain('app-card');
    expect(fixture.nativeElement.querySelector('.product-list')).toBeNull();
    expect((fixture.nativeElement.querySelector('img[src="/mercadopago-logo.png"]') as HTMLImageElement).src).toContain('mercadopago-logo.png');
    fixture.componentInstance.returnToShipping();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.product-list')?.tagName).toBe('UL');
    fixture.componentInstance.continueToPayment();

    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(cart.checkout).toHaveBeenCalledWith('MERCADO_PAGO', {
      fulfillmentMethod: 'PICKUP', pickupLocationCode: pickupLocation.code,
      pickupLocationVersion: pickupLocation.version, shippingQuoteId: null,
    });
    expect(cart.completeCheckout).toHaveBeenCalledWith(order);
    expect(assign).toHaveBeenCalledWith(payment.checkoutUrl);
    expect(calls).toEqual(['order', 'payment', 'complete', 'redirect']);
  });

  it('keeps bank transfer preselected when requested by the cart', async () => {
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(), completeCheckout: vi.fn(), reconcile: vi.fn(() => of(true)),
      notice: signal(''), dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ paymentMethod: 'BANK_TRANSFER' }) } } },
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: { capabilities: () => of({ ...capabilities, paymentMethods: ['BANK_TRANSFER', 'MERCADO_PAGO'] }) } },
        { provide: CHECKOUT_WINDOW, useValue: { location: { assign: vi.fn() } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
    enterPaymentStep(fixture);

    expect(fixture.componentInstance.selectedPaymentMethod()).toBe('BANK_TRANSFER');
    expect((fixture.nativeElement.querySelector('input[value="BANK_TRANSFER"]') as HTMLInputElement).checked).toBe(true);
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
    fixture.componentInstance.selectedPaymentMethod.set('MERCADO_PAGO');
    enterPaymentStep(fixture);
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(cart.items()).toEqual([item]);
    expect(cart.completeCheckout).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Conservamos tu carrito');
  });

  it('creates a transfer order without calling Mercado Pago, clears the cart and deep-links to orders', async () => {
    const transferOrder: OrderConfirmation = { ...order, paymentMethod: 'BANK_TRANSFER', paymentDiscount: 300, total: 2700, reservationExpiresAt: null };
    const mercadoPago = vi.fn();
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(() => of(transferOrder)), completeCheckout: vi.fn(), reconcile: vi.fn(() => of(true)),
      notice: signal(''), dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: { capabilities: () => of({ ...capabilities, paymentMethods: ['BANK_TRANSFER', 'MERCADO_PAGO'] }), mercadoPago } },
        { provide: CHECKOUT_WINDOW, useValue: { location: { assign: vi.fn() } } },
      ],
    }).compileComponents();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate');
    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
    fixture.componentInstance.selectedPaymentMethod.set('BANK_TRANSFER');
    enterPaymentStep(fixture);

    fixture.componentInstance.submit();

    expect(cart.checkout).toHaveBeenCalledWith('BANK_TRANSFER', {
      fulfillmentMethod: 'PICKUP', pickupLocationCode: pickupLocation.code,
      pickupLocationVersion: pickupLocation.version, shippingQuoteId: null,
    });
    expect(mercadoPago).not.toHaveBeenCalled();
    expect(cart.completeCheckout).toHaveBeenCalledWith(transferOrder);
    expect(navigate).toHaveBeenCalledWith(['/orders'], { queryParams: { order: 42 } });
  });

  it('uses the backend transfer discount rate once on the list subtotal', async () => {
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(), reconcile: vi.fn(() => of(true)), notice: signal(''), dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: { capabilities: () => of({ ...capabilities, bankTransferDiscountRate: 0.125, paymentMethods: ['BANK_TRANSFER', 'MERCADO_PAGO'] }) } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
    fixture.componentInstance.selectedPaymentMethod.set('BANK_TRANSFER');
    enterPaymentStep(fixture);
    fixture.detectChanges();

    expect(fixture.componentInstance.transferPricing()).toEqual({ subtotal: 3000, discount: 375, total: 2625 });
    expect(fixture.nativeElement.textContent).toContain('Descuento por transferencia');
    expect(fixture.nativeElement.textContent).not.toContain('recargo');
  });

  it('quotes delivery, excludes shipping from the transfer discount and sends the selected quote', async () => {
    const quote = {
      shippingQuoteId: '0b47f21d-a03a-4bc6-a59a-a761a31bd68d', carrier: 'Andreani', serviceCode: 'standard',
      service: 'Estándar', logisticType: 'carrier_pickup', amount: 850, currency: 'ARS',
      estimatedDeliveryAt: '2099-08-03T20:00:00Z', expiresAt: '2099-08-01T20:10:00Z', tags: [],
    };
    const deliveryOrder: OrderConfirmation = {
      ...order, paymentMethod: 'BANK_TRANSFER', fulfillmentMethod: 'DELIVERY', pickupLocation: null,
      shippingCost: 850, paymentDiscount: 300, total: 3550, reservationExpiresAt: null,
      deliveryAddress: { recipientName: 'Ada Lovelace', street: 'San Martín', streetNumber: '123', floorApartment: null, locality: 'Córdoba', province: 'Córdoba', provinceCode: 'X', postalCode: '5000', countryCode: 'AR', reference: null },
    };
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(() => of(deliveryOrder)), completeCheckout: vi.fn(), reconcile: vi.fn(() => of(true)),
      notice: signal(''), dismissNotice: vi.fn(),
    };
    const shippingQuotes = vi.fn(() => of({ options: [quote] }));
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: {
          capabilities: () => of({ ...capabilities, deliveryQuotesEnabled: true, paymentMethods: ['BANK_TRANSFER'], fulfillmentMethods: ['PICKUP', 'DELIVERY'] }),
          shippingQuotes,
        } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();

    fixture.componentInstance.selectShippingQuote({ target: { value: quote.shippingQuoteId } } as unknown as Event);
    fixture.componentInstance.selectedPaymentMethod.set('BANK_TRANSFER');
    fixture.detectChanges();

    expect(shippingQuotes).toHaveBeenCalledWith([{ variantId: 11, quantity: 2 }]);
    expect(fixture.componentInstance.transferPricing()).toEqual({ subtotal: 3000, discount: 300, total: 2700 });
    expect(fixture.componentInstance.selectedTotal()).toBe(3550);
    expect(fixture.nativeElement.textContent).toContain('Andreani');
    expect(fixture.nativeElement.textContent).toContain('El envío no tiene descuento');

    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.componentInstance.continueToPayment();
    fixture.componentInstance.submit();
    expect(cart.checkout).toHaveBeenCalledWith('BANK_TRANSFER', {
      fulfillmentMethod: 'DELIVERY', pickupLocationCode: null, pickupLocationVersion: null,
      shippingQuoteId: quote.shippingQuoteId,
    });
  });

  it('shows every Zipnova carrier beside pickup and applies known carrier logos', async () => {
    const baseQuote = {
      serviceCode: 'standard', service: 'Entrega a domicilio', logisticType: 'carrier_dropoff',
      currency: 'ARS', estimatedDeliveryAt: null, expiresAt: '2099-01-01T00:00:00Z', tags: [],
    };
    const quotes = [
      { ...baseQuote, shippingQuoteId: 'oca', carrier: 'OCA', amount: 1000 },
      { ...baseQuote, shippingQuoteId: 'correo', carrier: 'Correo Argentino', amount: 1200 },
      { ...baseQuote, shippingQuoteId: 'lo-bruno', carrier: 'Lo Bruno', amount: 1400 },
    ];
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null), checkout: vi.fn(),
      reconcile: vi.fn(() => of(true)), notice: signal(''), dismissNotice: vi.fn(),
    };
    const shippingQuotes = vi.fn(() => of({ options: quotes }));
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]), { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: {
          capabilities: () => of({ ...capabilities, deliveryQuotesEnabled: true, fulfillmentMethods: ['PICKUP', 'DELIVERY'] }),
          shippingQuotes,
        } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();

    expect(shippingQuotes).toHaveBeenCalledOnce();
    expect(fixture.nativeElement.querySelectorAll('.delivery-card').length).toBe(4);
    expect(fixture.nativeElement.textContent).toContain('OCA');
    expect(fixture.nativeElement.textContent).toContain('Correo Argentino');
    expect(fixture.nativeElement.textContent).toContain('Lo Bruno');
    expect(fixture.nativeElement.querySelector('img[src="/Logooca.png"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('img[src="/logo-correo.png"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('input[value="lo-bruno"] + .carrier-brand')?.textContent).toContain('LB');

    (fixture.nativeElement.querySelector(`input[value="${pickupLocation.code}"]`) as HTMLInputElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Av. Colón 123');

    (fixture.nativeElement.querySelector('input[value="oca"]') as HTMLInputElement).click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedFulfillmentMethod()).toBe('DELIVERY');
    expect(fixture.componentInstance.selectedShippingQuoteId()).toBe('oca');
    expect(fixture.nativeElement.textContent).not.toContain('Av. Colón 123');
    expect(fixture.nativeElement.querySelector('.pickup-consent')).toBeNull();
  });

  it('blocks an expired quote and lets the customer calculate delivery again', async () => {
    const expired = { shippingQuoteId: 'expired', carrier: 'Correo', serviceCode: 'standard', service: 'Estándar', logisticType: 'home_delivery', amount: 500, currency: 'ARS', estimatedDeliveryAt: null, expiresAt: '2000-01-01T00:00:00Z', tags: [] };
    const fresh = { ...expired, shippingQuoteId: 'fresh', expiresAt: '2099-01-01T00:00:00Z' };
    const shippingQuotes = vi.fn().mockReturnValueOnce(of({ options: [expired] })).mockReturnValueOnce(of({ options: [fresh] }));
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null), checkout: vi.fn(),
      reconcile: vi.fn(() => of(true)), notice: signal(''), dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]), { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: {
          capabilities: () => of({ ...capabilities, deliveryQuotesEnabled: true, fulfillmentMethods: ['PICKUP', 'DELIVERY'] }),
          shippingQuotes,
        } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.canSubmit()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('La cotización venció');
    const retry = [...fixture.nativeElement.querySelectorAll('.quote-feedback button')]
      .find((button: HTMLButtonElement) => button.textContent?.includes('Calcular nuevamente')) as HTMLButtonElement;
    retry.click();
    fixture.detectChanges();

    expect(shippingQuotes).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.selectedShippingQuote()?.shippingQuoteId).toBe('fresh');
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
    fixture.componentInstance.selectedPaymentMethod.set('MERCADO_PAGO');
    enterPaymentStep(fixture);
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
    expect(fixture.nativeElement.textContent).toContain('Elegí cómo pagar');
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
    fixture.componentInstance.selectedPaymentMethod.set('MERCADO_PAGO');
    enterPaymentStep(fixture);
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(cart.checkout).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Verificá tu email para comprar');
    expect(fixture.nativeElement.querySelector('.verification-required a')?.getAttribute('href')).toBe('/profile');
    expect(getComputedStyle(fixture.nativeElement.querySelector('.verification-required')).getPropertyValue('--feedback-actions-column').trim()).toBe('2 / -1');
    expect(getComputedStyle(fixture.nativeElement.querySelector('.verification-actions')).width).not.toBe('0px');
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
    fixture.componentInstance.selectedPaymentMethod.set('MERCADO_PAGO');
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
    fixture.componentInstance.selectPickup({ target: { value: pickupLocation.code } } as unknown as Event);
    fixture.componentInstance.pickupAccepted.set(true);

    fixture.componentInstance.loadCapabilities();

    expect(fixture.componentInstance.selectedPickupCode()).toBe(pickupLocation.code);
    expect(fixture.componentInstance.pickupAccepted()).toBe(false);
  });

  it('renders and selects each configured pickup location as its own option', async () => {
    const secondPickup = {
      ...pickupLocation,
      code: 'CORDOBA_NORTE',
      name: 'Pinatech Norte',
      addressLines: ['Av. Rafael Núñez 456'],
      instructions: 'Ingresá por recepción.',
    };
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(), reconcile: vi.fn(() => of(true)), notice: signal(''), dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: { capabilities: () => of({ ...capabilities, pickupLocations: [pickupLocation, secondPickup] }) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.delivery-card--pickup').length).toBe(2);
    expect(fixture.nativeElement.textContent).not.toContain('Av. Colón 123');
    expect(fixture.nativeElement.textContent).not.toContain('Av. Rafael Núñez 456');
    (fixture.nativeElement.querySelector('input[value="CORDOBA_NORTE"]') as HTMLInputElement).click();
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedPickupCode()).toBe('CORDOBA_NORTE');
    expect(fixture.componentInstance.pickupAccepted()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Av. Rafael Núñez 456');
    expect(fixture.nativeElement.textContent).not.toContain('Av. Colón 123');
    expect(fixture.nativeElement.querySelector('.delivery-card--pickup.selected')?.textContent).toContain('Pinatech Norte');
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
    fixture.componentInstance.selectedPaymentMethod.set('MERCADO_PAGO');
    enterPaymentStep(fixture);
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
    fixture.componentInstance.selectedPaymentMethod.set('MERCADO_PAGO');
    enterPaymentStep(fixture);
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(expected);
    expect(fixture.nativeElement.textContent).toContain(action);
    expect(cart.items()).toEqual([item]);
  });

  it('directs an existing bank transfer conflict to the pending order', async () => {
    const conflict = new HttpErrorResponse({
      status: 409,
      error: { detail: 'You already have a pending bank transfer order.' },
    });
    const cart = {
      items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      checkout: vi.fn(() => throwError(() => conflict)), completeCheckout: vi.fn(),
      reconcile: vi.fn(() => of(true)), notice: signal(''), dismissNotice: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideRouter([]),
        { provide: CartService, useValue: cart },
        { provide: CheckoutService, useValue: { capabilities: () => of({ ...capabilities, paymentMethods: ['BANK_TRANSFER', 'MERCADO_PAGO'] }) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
    fixture.componentInstance.selectedPaymentMethod.set('BANK_TRANSFER');
    enterPaymentStep(fixture);
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ya tenés un pedido por transferencia pendiente');
    expect(fixture.nativeElement.querySelector('.submit-error a')?.getAttribute('href')).toBe('/orders');
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
