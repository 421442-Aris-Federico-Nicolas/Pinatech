import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { Order, OrderService } from '../../core/orders/order.service';
import { BankTransferService } from '../../core/orders/bank-transfer.service';
import { CHECKOUT_WINDOW, CheckoutService } from '../checkout/checkout.service';
import { OrdersComponent } from './orders.component';

describe('OrdersComponent', () => {
  const pickupLocation = { code: 'CORDOBA_CENTRO', version: 'v1', name: 'Pinatech Centro', addressLines: ['Av. Colón 123'], locality: 'Córdoba', provinceCode: 'X', postalCode: '5000', instructions: 'Presentá tu DNI.', hours: 'Lunes a viernes de 9 a 18.' };
  const order: Order = {
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
    paymentSurcharge: 300,
    total: 3300,
    createdAt: '2026-07-28T20:00:00Z',
    reservationExpiresAt: '2099-07-29T20:00:00Z',
    customerName: 'Ada Lovelace',
    customerEmail: 'ada@example.com',
    items: [{ productId: 1, variantId: 11, productName: 'Teclado', colorName: 'Negro', colorHex: '#000000', unitPrice: 1500, quantity: 2, subtotal: 3000 }],
    deliveryAddress: null,
    shipment: null,
  };

  it('renders Spanish statuses and the expiration for pending payment', async () => {
    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: { mine: () => of([order]) } },
        { provide: BankTransferService, useValue: { get: vi.fn(), uploadProof: vi.fn() } },
        { provide: CheckoutService, useValue: { capabilities: () => of({ onlinePaymentsEnabled: false, paymentMethods: [] }) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Pendiente de pago');
    expect(fixture.nativeElement.textContent).toContain('Preparación pendiente');
    expect(fixture.nativeElement.textContent).toContain('Reserva vigente hasta');
    expect(fixture.nativeElement.textContent).toContain('Retiro');
    expect(fixture.nativeElement.textContent).toContain('Pinatech Centro');
    expect(fixture.nativeElement.textContent).toContain('Av. Colón 123');
    expect(fixture.nativeElement.textContent).toContain('Lunes a viernes de 9 a 18.');
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
        { provide: BankTransferService, useValue: { get: vi.fn(), uploadProof: vi.fn() } },
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
    expect(fixture.nativeElement.querySelector('.state .app-feedback__body[role="status"]')?.textContent).toContain('Volviendo a cargar');
  });

  it('explains when an expired reservation can no longer be paid', async () => {
    const expired = { ...order, reservationExpiresAt: '2000-07-29T20:00:00Z' };
    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: { mine: () => of([expired]) } },
        { provide: BankTransferService, useValue: { get: vi.fn(), uploadProof: vi.fn() } },
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
        { provide: BankTransferService, useValue: { get: vi.fn(), uploadProof: vi.fn() } },
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
    expect(fixture.nativeElement.querySelector('.capability-error .app-feedback__body[role="status"]')?.textContent).toContain('Volviendo a consultar');

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
        { provide: BankTransferService, useValue: { get: vi.fn(), uploadProof: vi.fn() } },
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

  it('isolates transfer proof status and upload validation from Mercado Pago retry', async () => {
    const transfer = { ...order, paymentMethod: 'BANK_TRANSFER' as const, paymentDiscount: 300, paymentSurcharge: 0, total: 2700 };
    const details = {
      orderId: 42,
      paymentDueAt: '2099-07-29T20:00:00Z',
      bankAccount: { holder: 'Pinatech SA', taxId: '30-12345678-9', bankName: 'Banco', alias: 'PINATECH.PAGOS', cbu: '1234567890123456789012', currency: 'ARS' },
      proof: null,
    };
    const uploaded = { ...details, proof: { id: '4d2e8ab1-46d7-4fd1-a711-7a4d60197be1', status: 'PENDING_REVIEW' as const, originalFilename: 'comprobante.pdf', contentType: 'application/pdf', sizeBytes: 1200, submittedAt: '2026-07-28T21:00:00Z', reviewedAt: null, rejectionReason: null, previewCount: 1 } };
    const rejected = { ...uploaded, proof: { ...uploaded.proof, status: 'REJECTED' as const, reviewedAt: '2026-07-28T22:00:00Z', rejectionReason: 'El importe no coincide.' } };
    const get = vi.fn(() => of(details));
    const uploadProof = vi.fn()
      .mockReturnValueOnce(of(uploaded))
      .mockReturnValueOnce(of(rejected));
    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: { mine: () => of([transfer]) } },
        { provide: BankTransferService, useValue: { get, uploadProof } },
        { provide: CheckoutService, useValue: { capabilities: () => of({ onlinePaymentsEnabled: true, paymentMethods: ['MERCADO_PAGO'] }) } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('PINATECH.PAGOS');
    expect(fixture.nativeElement.textContent).not.toContain('Continuar pago');
    expect(fixture.componentInstance.canUploadProof(transfer, { ...details, paymentDueAt: '2000-01-01T00:00:00Z' })).toBe(false);

    const invalid = new File(['x'], 'proof.txt', { type: 'text/plain' });
    fixture.componentInstance.selectProof(42, { target: { files: [invalid], value: 'proof.txt' } } as unknown as Event);
    expect(fixture.componentInstance.proofErrors()[42]).toContain('JPEG, PNG o PDF');

    const valid = new File(['proof'], 'proof.pdf', { type: 'application/pdf' });
    fixture.componentInstance.selectProof(42, { target: { files: [valid], value: 'proof.pdf' } } as unknown as Event);
    const invalidAfterValid = new File(['x'], 'proof.txt', { type: 'text/plain' });
    fixture.componentInstance.selectProof(42, { target: { files: [invalidAfterValid], value: 'proof.txt' } } as unknown as Event);
    expect(fixture.componentInstance.selectedProofs()[42]).toBeUndefined();
    fixture.componentInstance.selectProof(42, { target: { files: [valid], value: 'proof.pdf' } } as unknown as Event);
    fixture.componentInstance.uploadProof(42);
    fixture.detectChanges();

    expect(uploadProof).toHaveBeenCalledWith(42, valid);
    expect(fixture.componentInstance.orders()[0].paymentStatus).toBe('UNDER_REVIEW');
    expect(fixture.nativeElement.textContent).toContain('Comprobante pendiente de revisión');

    fixture.componentInstance.selectProof(42, { target: { files: [valid], value: 'proof.pdf' } } as unknown as Event);
    fixture.componentInstance.uploadProof(42);

    expect(fixture.componentInstance.orders()[0]).toMatchObject({
      status: 'CANCELLED',
      paymentStatus: 'REJECTED',
      fulfillmentStatus: 'CANCELLED',
    });
  });

  it('renders a delivery address, customer tracking timeline, incident and safe carrier link', async () => {
    const delivery: Order = {
      ...order, fulfillmentMethod: 'DELIVERY', pickupLocation: null, shippingCost: 850, total: 3850,
      deliveryAddress: { recipientName: 'Ada Lovelace', street: 'San Martín', streetNumber: '123', floorApartment: '2 B', locality: 'Córdoba', province: 'Córdoba', provinceCode: 'X', postalCode: '5000', countryCode: 'AR', reference: 'Portón negro' },
      shipment: { status: 'ACTIVE', providerStatus: 'in_transit', providerSubstatus: null, carrier: 'Andreani', trackingCode: 'AR123', trackingUrl: 'https://tracking.example/AR123', estimatedDeliveryAt: '2099-08-03T20:00:00Z', incident: true },
    };
    const tracking = vi.fn(() => of({
      ...delivery.shipment!,
      history: [{ status: 'ready_to_ship', substatus: null, occurredAt: '2026-08-01T10:00:00Z' }, { status: 'in_transit', substatus: 'sorting_center', occurredAt: '2026-08-02T10:00:00Z' }],
    }));
    await TestBed.configureTestingModule({
      imports: [OrdersComponent],
      providers: [
        provideRouter([]),
        { provide: OrderService, useValue: { mine: () => of([delivery]), tracking } },
        { provide: BankTransferService, useValue: { get: vi.fn(), uploadProof: vi.fn() } },
        { provide: CheckoutService, useValue: { capabilities: () => of({ onlinePaymentsEnabled: false, paymentMethods: [] }) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(OrdersComponent);
    fixture.detectChanges();

    expect(tracking).toHaveBeenCalledWith(42);
    expect(fixture.nativeElement.textContent).toContain('San Martín 123, 2 B');
    expect(fixture.nativeElement.textContent).toContain('registra una incidencia');
    expect(fixture.nativeElement.textContent).toContain('Listo para despachar');
    expect(fixture.nativeElement.textContent).toContain('En tránsito');
    const link = fixture.nativeElement.querySelector('.tracking-link') as HTMLAnchorElement;
    expect(link.href).toBe('https://tracking.example/AR123');
    expect(link.rel).toBe('noopener noreferrer');
    expect(fixture.componentInstance.safeTrackingUrl('javascript:alert(1)')).toBeNull();
  });
});
