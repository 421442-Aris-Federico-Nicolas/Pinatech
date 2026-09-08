import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { GuestCheckoutService } from '../../core/guest/guest-checkout.service';
import { Order } from '../../core/orders/order.service';
import { CHECKOUT_WINDOW } from '../checkout/checkout.service';
import { GuestOrderComponent } from './guest-order.component';

describe('GuestOrderComponent', () => {
  const publicId = '123e4567-e89b-42d3-a456-426614174000';
  const order: Order = { id: 42, status: 'PENDING_PAYMENT', paymentStatus: 'PENDING', fulfillmentStatus: 'PENDING', currency: 'ARS',
    paymentMethod: 'BANK_TRANSFER', deliveryMethod: null, fulfillmentMethod: 'PICKUP', pickupLocation: { code: 'CENTRO', version: 'v1', name: 'Pinatech Centro', addressLines: ['Av. Colón 123'], locality: 'Córdoba', provinceCode: 'X', postalCode: '5000', instructions: 'DNI', hours: '9 a 18' },
    subtotal: 3000, shippingCost: 0, paymentDiscount: 300, paymentSurcharge: 0, total: 2700, createdAt: '2026-09-08T10:00:00Z',
    reservationExpiresAt: null, cancellationReason: null, cancelledAt: null, customerName: 'Ada Lovelace', customerEmail: 'ada@example.com',
    items: [{ productId: 1, variantId: 11, productName: 'Teclado', colorName: 'Negro', colorHex: '#000', unitPrice: 1500, quantity: 2, subtotal: 3000 }], deliveryAddress: null, shipment: null };
  const details = { orderId: 42, paymentDueAt: '2099-09-09T10:00:00Z', bankAccount: { holder: 'Pinatech', taxId: '30-123', bankName: 'Banco', alias: 'PINATECH', cbu: '123', currency: 'ARS' }, proof: null };

  it('shows one guest order, validates proof files and offers explicit claim to a matching verified customer', async () => {
    const user = signal({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, documentNumber: '12345678', emailVerified: true, roles: ['CUSTOMER'] });
    const uploadProof = vi.fn(() => of({ ...details, proof: { id: 'proof', status: 'PENDING_REVIEW', originalFilename: 'proof.pdf', contentType: 'application/pdf', sizeBytes: 5, submittedAt: '2026-09-08T11:00:00Z', reviewedAt: null, rejectionReason: null, previewCount: 1 } }));
    const claim = vi.fn(() => of({ publicId, accessToken: null, order }));
    const captureAccessTokenFromFragment = vi.fn();
    await TestBed.configureTestingModule({ imports: [GuestOrderComponent], providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ publicId }) } } },
      { provide: AuthService, useValue: { user, requestEmailVerification: vi.fn(() => of({ message: 'accepted' })) } },
      { provide: CHECKOUT_WINDOW, useValue: { location: { assign: vi.fn() } } },
      { provide: GuestCheckoutService, useValue: { captureAccessTokenFromFragment, initialize: () => of({}), getOrder: () => of({ publicId, accessToken: null, order }), bankTransfer: () => of(details), tracking: () => throwError(() => ({ status: 404 })), uploadProof, claim, mercadoPago: vi.fn() } },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(GuestOrderComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Pedido');
    expect(fixture.nativeElement.textContent).toContain('PINATECH');
    expect(fixture.nativeElement.textContent).toContain('Vincular pedido');
    expect(captureAccessTokenFromFragment).toHaveBeenCalledWith(publicId);
    fixture.componentInstance.selectProof({ target: { files: [new File(['x'], 'proof.txt', { type: 'text/plain' })], value: 'proof.txt' } } as unknown as Event);
    expect(fixture.componentInstance.proofError()).toContain('JPEG, PNG o PDF');
    const file = new File(['proof'], 'proof.pdf', { type: 'application/pdf' });
    fixture.componentInstance.selectProof({ target: { files: [file], value: 'proof.pdf' } } as unknown as Event);
    fixture.componentInstance.uploadProof();
    expect(uploadProof).toHaveBeenCalledWith(publicId, file);
    fixture.componentInstance.claim();
    expect(claim).toHaveBeenCalledWith(publicId);
    expect(fixture.componentInstance.claimed()).toBe(true);

    const emptyMime = new File(['proof'], 'scan.pdf', { type: '' });
    fixture.componentInstance.selectProof({ target: { files: [emptyMime], value: 'scan.pdf' } } as unknown as Event);
    expect(fixture.componentInstance.selectedProof()).toBe(emptyMime);
  });

  it('continues or retries a pending Mercado Pago order only with a valid HTTPS checkout URL', async () => {
    const mercadoPago = vi.fn(() => of({ checkoutUrl: 'https://www.mercadopago.com.ar/checkout' }));
    const assign = vi.fn();
    const mercadoPagoOrder = { ...order, paymentMethod: 'MERCADO_PAGO' as const, reservationExpiresAt: '2099-09-09T10:00:00Z' };
    await TestBed.configureTestingModule({ imports: [GuestOrderComponent], providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ publicId }) } } },
      { provide: AuthService, useValue: { user: signal(null), requestEmailVerification: vi.fn() } },
      { provide: CHECKOUT_WINDOW, useValue: { location: { assign } } },
      { provide: GuestCheckoutService, useValue: { captureAccessTokenFromFragment: vi.fn(), initialize: () => of({}), getOrder: () => of({ publicId, accessToken: null, order: mercadoPagoOrder }), bankTransfer: vi.fn(), tracking: vi.fn(), uploadProof: vi.fn(), claim: vi.fn(), mercadoPago } },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(GuestOrderComponent);
    fixture.detectChanges();

    const button = [...fixture.nativeElement.querySelectorAll('button')]
      .find((candidate: HTMLButtonElement) => candidate.textContent?.includes('Continuar pago')) as HTMLButtonElement;
    button.click();

    expect(mercadoPago).toHaveBeenCalledWith(publicId, 'PENDING');
    expect(assign).toHaveBeenCalledWith('https://www.mercadopago.com.ar/checkout');
  });

  it('keeps one rejected-payment retry in flight and rejects a non-HTTPS checkout URL', async () => {
    const response = new Subject<{ checkoutUrl: string }>();
    const mercadoPago = vi.fn(() => response);
    const assign = vi.fn();
    const rejected = { ...order, paymentMethod: 'MERCADO_PAGO' as const, paymentStatus: 'REJECTED', reservationExpiresAt: '2099-09-09T10:00:00Z' };
    await TestBed.configureTestingModule({ imports: [GuestOrderComponent], providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ publicId }) } } },
      { provide: AuthService, useValue: { user: signal(null), requestEmailVerification: vi.fn() } },
      { provide: CHECKOUT_WINDOW, useValue: { location: { assign } } },
      { provide: GuestCheckoutService, useValue: { captureAccessTokenFromFragment: vi.fn(), initialize: () => of({}), getOrder: () => of({ publicId, accessToken: null, order: rejected }), bankTransfer: vi.fn(), tracking: vi.fn(), uploadProof: vi.fn(), claim: vi.fn(), mercadoPago } },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(GuestOrderComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Reintentar pago');
    fixture.componentInstance.pay();
    fixture.componentInstance.pay();
    expect(mercadoPago).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.paying()).toBe(true);
    response.next({ checkoutUrl: 'http://unsafe.example/checkout' });
    response.complete();

    expect(assign).not.toHaveBeenCalled();
    expect(fixture.componentInstance.paymentError()).toContain('HTTPS válido');
    expect(fixture.componentInstance.paying()).toBe(false);
  });

  it('offers verification instead of claim for a matching unverified account', async () => {
    const requestEmailVerification = vi.fn(() => of({ message: 'accepted' }));
    const user = signal({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, documentNumber: '12345678', emailVerified: false, roles: ['CUSTOMER'] });
    await TestBed.configureTestingModule({ imports: [GuestOrderComponent], providers: [provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ publicId }) } } },
      { provide: AuthService, useValue: { user, requestEmailVerification } },
      { provide: CHECKOUT_WINDOW, useValue: { location: { assign: vi.fn() } } },
      { provide: GuestCheckoutService, useValue: { captureAccessTokenFromFragment: vi.fn(), initialize: () => of({}), getOrder: () => of({ publicId, accessToken: null, order }), bankTransfer: () => of(details), tracking: vi.fn(), uploadProof: vi.fn(), claim: vi.fn(), mercadoPago: vi.fn() } },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(GuestOrderComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Verificá tu cuenta para vincular el pedido');
    expect(fixture.nativeElement.textContent).not.toContain('Vincular pedido');
    fixture.componentInstance.resendAccountVerification();
    fixture.detectChanges();
    expect(requestEmailVerification).toHaveBeenCalledWith('ada@example.com');
    expect(fixture.nativeElement.textContent).toContain('enviamos un nuevo enlace');
  });
});
