import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { CartItem, CartService } from '../../core/cart/cart.service';
import { GuestCheckoutService } from '../../core/guest/guest-checkout.service';
import { Order } from '../../core/orders/order.service';
import { CHECKOUT_WINDOW, CheckoutCapabilities, CheckoutService } from './checkout.service';
import { GuestCheckoutComponent } from './guest-checkout.component';

describe('GuestCheckoutComponent', () => {
  const publicId = '123e4567-e89b-42d3-a456-426614174000';
  const session = signal({ csrfToken: 'c'.repeat(43), expiresAt: '2099-01-01T00:00:00Z', emailVerified: false, verifiedEmail: null as string | null });
  const item: CartItem = {
    product: { id: 1, name: 'Teclado', slug: 'teclado', description: 'Mecánico', price: 1500, categoryId: 2, categoryName: 'Periféricos', brandId: 3, brandName: 'Marca', images: [], specifications: [], variants: [{ id: 11, colorName: 'Negro', colorHex: '#000', inStock: true, availableQuantity: 5 }] },
    variant: { id: 11, colorName: 'Negro', colorHex: '#000', inStock: true, availableQuantity: 5 }, quantity: 2,
  };
  const pickup = { code: 'CORDOBA_CENTRO', version: 'v1', name: 'Pinatech Centro', addressLines: ['Av. Colón 123'], locality: 'Córdoba', provinceCode: 'X', postalCode: '5000', instructions: 'Presentá tu DNI.', hours: 'Lunes a viernes' };
  const capabilities: CheckoutCapabilities = { currency: 'ARS', orderRequestsEnabled: true, onlinePaymentsEnabled: true,
    deliveryQuotesEnabled: true, paymentMethods: ['BANK_TRANSFER', 'MERCADO_PAGO'], bankTransferDiscountRate: .1,
    deliveryMethods: ['ZIPNOVA'], fulfillmentMethods: ['PICKUP', 'DELIVERY'], pickupLocations: [pickup] };
  const order: Order = { id: 42, status: 'PENDING_PAYMENT', paymentStatus: 'PENDING', fulfillmentStatus: 'PENDING', currency: 'ARS',
    paymentMethod: 'MERCADO_PAGO', deliveryMethod: null, fulfillmentMethod: 'PICKUP', pickupLocation: pickup, subtotal: 3000,
    shippingCost: 0, paymentDiscount: 0, paymentSurcharge: 0, total: 3000, createdAt: '2026-09-08T10:00:00Z',
    reservationExpiresAt: '2099-09-08T10:00:00Z', cancellationReason: null, cancelledAt: null, customerName: 'Ada Lovelace',
    customerEmail: 'ada@example.com', items: [], deliveryAddress: null, shipment: null };

  function customer(component: GuestCheckoutComponent): void {
    component.form.controls.customer.setValue({ firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: '3515551234', documentNumber: '12345678' });
  }

  async function setup(overrides: Record<string, unknown> = {}) {
    const cart = { items: signal([item]), count: signal(2), total: signal(3000), confirmation: signal(null),
      reconcile: vi.fn(() => of(true)), clear: vi.fn(), notice: signal(''), dismissNotice: vi.fn() };
    const guest = {
      session,
      initialize: vi.fn(() => of(session())),
      checkEmailEligibility: vi.fn(() => of(void 0)),
      shippingQuotes: vi.fn(() => of({ options: [{ shippingQuoteId: 'quote-1', carrier: 'OCA', serviceCode: 'home', service: 'Domicilio', logisticType: 'door', amount: 850, currency: 'ARS', estimatedDeliveryAt: '2099-09-10T10:00:00Z', expiresAt: '2099-09-08T11:00:00Z', tags: [] }] })),
      requestEmailVerification: vi.fn(() => of({ message: 'accepted' })),
      confirmEmailVerification: vi.fn(() => { session.set({ ...session(), emailVerified: true, verifiedEmail: 'ada@example.com' }); return of(session()); }),
      createOrder: vi.fn(() => of({ publicId, accessToken: 'a'.repeat(43), order })),
      mercadoPago: vi.fn(() => of({ attemptId: 'attempt', orderId: 42, status: 'PENDING', checkoutUrl: 'https://www.mercadopago.com.ar/checkout', expiresAt: '2099-09-08T11:00:00Z' })),
      ...overrides,
    };
    const assign = vi.fn();
    await TestBed.configureTestingModule({ imports: [GuestCheckoutComponent], providers: [provideRouter([]),
      { provide: CartService, useValue: cart }, { provide: CheckoutService, useValue: { capabilities: () => of(capabilities) } },
      { provide: GuestCheckoutService, useValue: guest }, { provide: CHECKOUT_WINDOW, useValue: { location: { assign } } }] }).compileComponents();
    const fixture = TestBed.createComponent(GuestCheckoutComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, cart, guest, assign };
  }

  beforeEach(() => session.set({ csrfToken: 'c'.repeat(43), expiresAt: '2099-01-01T00:00:00Z', emailVerified: false, verifiedEmail: null }));

  it('creates a pickup guest order and starts Mercado Pago without email verification', async () => {
    const { component, guest, cart, assign } = await setup();
    customer(component);
    component.selectPickup({ target: { value: pickup.code } } as unknown as Event);
    component.pickupAccepted.set(true);
    component.continueToPayment();
    component.selectedPaymentMethod.set('MERCADO_PAGO');

    expect(component.canSubmit()).toBe(true);
    component.submit();

    expect(guest.createOrder).toHaveBeenCalledWith(expect.objectContaining({ fulfillmentMethod: 'PICKUP', deliveryAddress: null }));
    expect(guest.mercadoPago).toHaveBeenCalledWith(publicId, 'PENDING');
    expect(cart.clear).toHaveBeenCalled();
    expect(assign).toHaveBeenCalledWith('https://www.mercadopago.com.ar/checkout');
  });

  it('stops before payment and asks registered emails to sign in', async () => {
    const conflict = new HttpErrorResponse({ status: 409, error: {
      type: 'https://computer-store.dev/errors/guest-checkout-account-required',
    } });
    const { fixture, component, guest } = await setup({
      checkEmailEligibility: vi.fn(() => throwError(() => conflict)),
    });
    customer(component);
    component.selectPickup({ target: { value: pickup.code } } as unknown as Event);
    component.pickupAccepted.set(true);

    component.continueToPayment();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(guest.checkEmailEligibility).toHaveBeenCalledWith('ada@example.com');
    expect(component.checkoutStep()).toBe('SHIPPING');
    expect(component.accountRequired()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Este email ya está asociado a una cuenta');
    expect(fixture.nativeElement.querySelector('#guest-account-required a')?.getAttribute('href'))
      .toBe('/login?returnUrl=%2Fcheckout');
    expect(guest.createOrder).not.toHaveBeenCalled();

    component.form.controls.customer.controls.email.setValue('new@example.com');
    fixture.detectChanges();
    expect(component.accountRequired()).toBe(false);
    expect(component.submitError()).toBe('');
  });

  it('handles a registered email conflict returned by final order creation', async () => {
    const conflict = new HttpErrorResponse({ status: 409, error: {
      type: 'https://computer-store.dev/errors/guest-checkout-account-required',
    } });
    const { fixture, component, guest, cart } = await setup({
      createOrder: vi.fn(() => throwError(() => conflict)),
    });
    customer(component);
    component.selectPickup({ target: { value: pickup.code } } as unknown as Event);
    component.pickupAccepted.set(true);
    component.continueToPayment();
    component.selectedPaymentMethod.set('MERCADO_PAGO');

    component.submit();
    fixture.detectChanges();

    expect(component.accountRequired()).toBe(true);
    expect(guest.mercadoPago).not.toHaveBeenCalled();
    expect(cart.clear).not.toHaveBeenCalled();
  });

  it('ignores a registered-email response after the customer changes the email', async () => {
    const verification = new Subject<{ message: string }>();
    const conflict = new HttpErrorResponse({ status: 409, error: {
      type: 'https://computer-store.dev/errors/guest-checkout-account-required',
    } });
    const { component } = await setup({ requestEmailVerification: vi.fn(() => verification) });
    customer(component);
    component.selectPickup({ target: { value: pickup.code } } as unknown as Event);
    component.pickupAccepted.set(true);
    component.continueToPayment();
    component.requestVerification();

    component.form.controls.customer.controls.email.setValue('new@example.com');
    verification.error(conflict);

    expect(component.accountRequired()).toBe(false);
    expect(component.submitError()).toBe('');
  });

  it('quotes delivery with complete guest customer and Argentine address data', async () => {
    const { component, guest } = await setup();
    customer(component);
    component.selectDelivery();
    component.form.controls.address.setValue({ street: 'San Martín', streetNumber: '123', floorApartment: '', locality: 'Córdoba', province: 'X', postalCode: '5000', reference: 'Portón gris' });
    component.loadShippingQuotes();

    expect(guest.shippingQuotes).toHaveBeenCalledWith([{ variantId: 11, quantity: 2 }],
      expect.objectContaining({ email: 'ada@example.com', documentNumber: '12345678' }),
      expect.objectContaining({ province: 'X', countryCode: 'AR' }));
    expect(component.selectedShippingQuote()?.shippingQuoteId).toBe('quote-1');
  });

  it('requires and confirms a six-digit code for guest bank transfer', async () => {
    const { component, guest } = await setup();
    customer(component);
    component.selectPickup({ target: { value: pickup.code } } as unknown as Event);
    component.pickupAccepted.set(true);
    component.continueToPayment();

    expect(component.canSubmit()).toBe(false);
    component.requestVerification();
    component.updateVerificationCode({ target: { value: '123456' } } as unknown as Event);
    component.confirmVerification();

    expect(guest.requestEmailVerification).toHaveBeenCalledWith('ada@example.com', 'Ada');
    expect(guest.confirmEmailVerification).toHaveBeenCalledWith('ada@example.com', '123456');
    expect(component.guestEmailVerified()).toBe(true);
    expect(component.canSubmit()).toBe(true);
  });

  it('requires at least six actual digits in the guest phone number', async () => {
    const { component } = await setup();
    component.form.controls.customer.controls.phone.setValue('+() -- 1');
    expect(component.form.controls.customer.controls.phone.invalid).toBe(true);
    component.form.controls.customer.controls.phone.setValue('+54 (351) 12');
    expect(component.form.controls.customer.controls.phone.valid).toBe(true);
  });

  it('clears a rejected delivery quote and returns focus to the delivery step', async () => {
    const error = new HttpErrorResponse({ status: 400, error: { detail: 'The shipping quote has expired.' } });
    const { fixture, component } = await setup({ createOrder: vi.fn(() => throwError(() => error)) });
    customer(component);
    component.selectDelivery();
    component.form.controls.address.setValue({ street: 'San Martín', streetNumber: '123', floorApartment: '', locality: 'Córdoba', province: 'X', postalCode: '5000', reference: '' });
    component.loadShippingQuotes();
    component.continueToPayment();
    component.selectedPaymentMethod.set('MERCADO_PAGO');
    component.submit();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.checkoutStep()).toBe('SHIPPING');
    expect(component.shippingQuotes()).toEqual([]);
    expect(component.selectedShippingQuoteId()).toBe('');
    expect(component.quoteError()).toContain('ya no es válida');
    expect(component.stepAnnouncement()).toContain('Volvimos al paso de entrega');
    expect((document.activeElement as HTMLElement).id).toBe('guest-fulfillment-title');
  });

  it('announces step changes, focuses their heading and describes the OTP input', async () => {
    const { fixture, component } = await setup();
    customer(component);
    component.selectPickup({ target: { value: pickup.code } } as unknown as Event);
    component.pickupAccepted.set(true);
    component.continueToPayment();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.stepAnnouncement()).toContain('Paso 2 de 2');
    expect((document.activeElement as HTMLElement).id).toBe('guest-payment-title');
    const code = fixture.nativeElement.querySelector('#guest-code') as HTMLInputElement;
    expect(code.getAttribute('aria-describedby')).toContain('guest-code-help');

    component.requestVerification();
    fixture.detectChanges();
    await fixture.whenStable();
    expect((document.activeElement as HTMLElement).id).toBe('guest-code');
    expect(code.getAttribute('aria-describedby')).toContain('guest-code-notice');
  });
});
