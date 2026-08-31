import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Order } from '../../core/orders/order.service';
import { Product } from '../catalog/catalog.service';
import { AdminComponent } from './admin.component';
import { AdminService } from './admin.service';

describe('AdminComponent payments', () => {
  const renderedOrder: Order = {
    id: 41, status: 'PAID', paymentStatus: 'APPROVED', fulfillmentStatus: 'PENDING', currency: 'ARS',
    paymentMethod: 'MERCADO_PAGO', deliveryMethod: null, fulfillmentMethod: 'PICKUP', pickupLocation: null, subtotal: 240, paymentDiscount: 0, paymentSurcharge: 24, total: 264,
    createdAt: '2026-08-17T10:00:00Z', reservationExpiresAt: '2026-08-18T10:00:00Z', customerName: 'Ada Lovelace',
    customerEmail: 'ada@example.com', items: [{ productId: 4, variantId: 9, productName: 'Mouse', colorName: 'Negro', colorHex: '#000000', unitPrice: 120, quantity: 2, subtotal: 240 }],
  };

  async function createSalesFixture(animationsEnabled = false) {
    await TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [{
        provide: AdminService,
        useValue: {
          products: () => of({ content: [] }), categories: () => of([]), brands: () => of([]),
          inventories: () => of([]), orders: () => of([renderedOrder]), pendingBankTransferProofs: () => of([]),
        },
      }],
      animationsEnabled,
    }).compileComponents();
    const fixture = TestBed.createComponent(AdminComponent);
    fixture.componentInstance.section.set('sales');
    fixture.detectChanges();
    return fixture;
  }

  it('calculates sales KPIs only from approved, non-refunded payments and cannot mark orders paid', async () => {
    await TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [{
        provide: AdminService,
        useValue: {
          products: () => of({ content: [] }), categories: () => of([]), brands: () => of([]),
          inventories: () => of([]), orders: () => of([]), pendingBankTransferProofs: () => of([]),
        },
      }],
    }).compileComponents();
    const fixture = TestBed.createComponent(AdminComponent);
    const component = fixture.componentInstance;
    const base: Order = {
      id: 1, status: 'PAID', paymentStatus: 'APPROVED', fulfillmentStatus: 'PENDING', currency: 'ARS',
      paymentMethod: 'MERCADO_PAGO', deliveryMethod: null, fulfillmentMethod: 'PICKUP', pickupLocation: null, subtotal: 100, paymentDiscount: 0, paymentSurcharge: 10, total: 110, createdAt: '2026-08-17T10:00:00Z',
      reservationExpiresAt: '2026-08-18T10:00:00Z', customerName: 'Ada', customerEmail: 'ada@example.com', items: [],
    };

    component.orders.set([
      base,
      { ...base, id: 2, paymentStatus: 'REFUNDED', total: 500 },
      { ...base, id: 3, paymentStatus: 'PENDING', total: 300 },
    ]);

    expect(component.soldOrders().map((order) => order.id)).toEqual([1]);
    expect(component.revenue()).toBe(110);
    expect(component.averageTicket()).toBe(110);
    expect(component.orderActions('PENDING_PAYMENT').map((action) => action.label)).toEqual(['Cancelar']);
    expect(component.orderActions('PENDING_PAYMENT', 'UNDER_REVIEW')).toEqual([]);
    expect(component.orderActions('PAID').map((action) => action.label)).toEqual(['Preparar pedido']);
    expect(component.orderActions('PREPARING').map((action) => action.label)).toEqual(['Marcar listo']);
    expect(component.orderActions('READY').map((action) => action.label)).toEqual(['Registrar entrega física']);
  });

  it('initializes real taxonomy selections and does not refresh over dirty product edits without confirmation', async () => {
    const products = vi.fn(() => of({ content: [] }));
    await TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [{
        provide: AdminService,
        useValue: {
          products,
          categories: () => of([{ id: 3, name: 'Periféricos', slug: 'perifericos' }]),
          brands: () => of([{ id: 8, name: 'Pina' }]),
          inventories: () => of([]),
          orders: () => of([]), pendingBankTransferProofs: () => of([]),
        },
      }],
    }).compileComponents();
    const component = TestBed.createComponent(AdminComponent).componentInstance;

    expect(component.form.categoryId).toBe(3);
    expect(component.form.brandId).toBe(8);
    component.section.set('catalog');
    component.form.name = 'Edición pendiente';
    const confirmation = vi.spyOn(globalThis, 'confirm').mockReturnValue(false);

    component.reload();

    expect(confirmation).toHaveBeenCalledOnce();
    expect(products).toHaveBeenCalledOnce();
  });

  it('uses shared editor controls and rejects a nonpositive product price', async () => {
    const createProduct = vi.fn();
    await TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [{
        provide: AdminService,
        useValue: {
          products: () => of({ content: [] }),
          categories: () => of([{ id: 3, name: 'Periféricos', slug: 'perifericos' }]),
          brands: () => of([{ id: 8, name: 'Pina' }]),
          inventories: () => of([]),
          orders: () => of([]), pendingBankTransferProofs: () => of([]),
          createProduct,
        },
      }],
    }).compileComponents();

    const fixture = TestBed.createComponent(AdminComponent);
    const component = fixture.componentInstance;
    component.section.set('catalog');
    Object.assign(component.form, {
      name: 'Mouse', slug: 'mouse', description: 'Mouse profesional', price: 0, categoryId: 3, brandId: 8,
    });
    fixture.detectChanges();
    component.saveProduct();

    expect(fixture.nativeElement.querySelector('.product-form app-input')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.product-form app-select')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('mat-form-field')).toBeNull();
    expect(createProduct).not.toHaveBeenCalled();
    expect(component.error()).toContain('precio mayor que cero');
  });

  it('offers saved product images per color and clears associations when an image is deleted', async () => {
    const product: Product = {
      id: 5, name: 'Mouse', slug: 'mouse', description: 'Mouse profesional', price: 100,
      categoryId: 3, categoryName: 'Periféricos', brandId: 8, brandName: 'Pina', specifications: [],
      images: [{ id: 21, contentUrl: '/images/black.jpg', altText: 'Mouse negro', originalFilename: 'kumara-red-dragon-negro.png', displayOrder: 0 }],
      variants: [{ id: 51, colorName: 'Negro', colorHex: '#000000', imageId: 21, inStock: true, availableQuantity: 2 }],
    };
    const deleteProductImage = vi.fn(() => of(void 0));
    await TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [{
        provide: AdminService,
        useValue: {
          products: () => of({ content: [product] }), categories: () => of([{ id: 3, name: 'Periféricos', slug: 'perifericos' }]),
          brands: () => of([{ id: 8, name: 'Pina' }]), inventories: () => of([]), orders: () => of([]), pendingBankTransferProofs: () => of([]),
          deleteProductImage,
        },
      }],
    }).compileComponents();
    const fixture = TestBed.createComponent(AdminComponent);
    const component = fixture.componentInstance;
    component.select(product);
    component.section.set('catalog');
    fixture.detectChanges();

    expect(component.form.variants[0].imageId).toBe(21);
    expect(component.productImageOptions().map((option) => option.label)).toEqual(['Sin imagen específica', 'kumara-red-dragon-negro.png']);
    expect(fixture.nativeElement.querySelector('.variant-image-field img')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.product-image-grid span').textContent).toContain('kumara-red-dragon-negro.png');

    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    component.deleteImage(product.images[0]);

    expect(deleteProductImage).toHaveBeenCalledWith(5, 21);
    expect(component.form.variants[0].imageId).toBeNull();
    expect(component.selected()?.variants[0].imageId).toBeNull();
  });

  it('renders an order and preserves toggle semantics and summary focus', async () => {
    const fixture = await createSalesFixture();
    const summary = fixture.nativeElement.querySelector('.order-summary') as HTMLButtonElement;

    expect(summary.getAttribute('aria-expanded')).toBe('false');
    expect(summary.getAttribute('aria-controls')).toBe('order-detail-41');
    expect(fixture.nativeElement.querySelector('#order-detail-41')).toBeNull();

    summary.focus();
    summary.click();
    fixture.detectChanges();

    const detail = fixture.nativeElement.querySelector('#order-detail-41') as HTMLElement;
    expect(summary.getAttribute('aria-expanded')).toBe('true');
    expect(detail.textContent).toContain('2 × Mouse');
    expect(detail.textContent).toContain('Ajuste histórico de pago');
    expect(detail.textContent).toContain('Preparar pedido');
    expect(detail.textContent).not.toContain('recargo');
    expect(detail.getAttribute('aria-hidden')).toBeNull();
    expect(detail.hasAttribute('inert')).toBe(false);
    expect(fixture.nativeElement.querySelector('.filter-tabs')?.textContent).toContain('Listos');
    expect(document.activeElement).toBe(summary);
  });

  it('advances a paid order to preparing from its expanded detail', async () => {
    const inventories = vi.fn(() => of([]));
    const updateOrderStatus = vi.fn(() => of({
      ...renderedOrder,
      status: 'PREPARING',
      fulfillmentStatus: 'PREPARING',
    }));
    await TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [{
        provide: AdminService,
        useValue: {
          products: () => of({ content: [] }), categories: () => of([]), brands: () => of([]),
          inventories, orders: () => of([renderedOrder]), pendingBankTransferProofs: () => of([]),
          updateOrderStatus,
        },
      }],
    }).compileComponents();
    const fixture = TestBed.createComponent(AdminComponent);
    fixture.componentInstance.section.set('sales');
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('.order-summary') as HTMLButtonElement).click();
    fixture.detectChanges();
    const action = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.order-detail button'))
      .find((button) => button.textContent?.includes('Preparar pedido'));
    action?.click();
    fixture.detectChanges();

    expect(action).toBeTruthy();
    expect(updateOrderStatus).toHaveBeenCalledWith(41, 'PREPARING');
    expect(fixture.componentInstance.orders()[0].status).toBe('PREPARING');
    expect(inventories).toHaveBeenCalledTimes(2);
  });

  it('settles rapid order toggles on the final expanded state', async () => {
    const fixture = await createSalesFixture(true);
    const component = fixture.componentInstance;
    const summary = fixture.nativeElement.querySelector('.order-summary') as HTMLButtonElement;

    summary.click();
    fixture.detectChanges();
    summary.click();
    fixture.detectChanges();
    summary.click();
    fixture.detectChanges();

    const accessibleDetails = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.order-detail'))
      .filter((detail) => detail.getAttribute('aria-hidden') !== 'true' && !detail.hasAttribute('inert'));
    expect(component.expandedOrder()).toBe(renderedOrder.id);
    expect(summary.getAttribute('aria-expanded')).toBe('true');
    expect(accessibleDetails).toHaveLength(1);
    expect(accessibleDetails[0].id).toBe('order-detail-41');
  });

  it('leaves no stale accessible detail immediately after collapse', async () => {
    const fixture = await createSalesFixture(true);
    const component = fixture.componentInstance;
    const summary = fixture.nativeElement.querySelector('.order-summary') as HTMLButtonElement;
    summary.click();
    fixture.detectChanges();

    summary.click();
    const leavingDetail = fixture.nativeElement.querySelector('#order-detail-41') as HTMLElement;
    expect(leavingDetail.getAttribute('aria-hidden')).toBe('true');
    expect(leavingDetail.hasAttribute('inert')).toBe(true);
    fixture.detectChanges();

    const retainedDetails = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('#order-detail-41'));
    expect(component.expandedOrder()).toBeNull();
    expect(summary.getAttribute('aria-expanded')).toBe('false');
    for (const detail of retainedDetails) {
      expect(detail.getAttribute('aria-hidden')).toBe('true');
      expect(detail.hasAttribute('inert')).toBe(true);
    }
    expect(fixture.nativeElement.querySelector('.order-detail:not([aria-hidden="true"])')).toBeNull();
  });

  it('requires review fields, invokes proof actions and revokes sanitized preview URLs', async () => {
    const proof = {
      id: '4d2e8ab1-46d7-4fd1-a711-7a4d60197be1', status: 'PENDING_REVIEW' as const, orderId: 41, customerName: 'Ada Lovelace', customerEmail: 'ada@example.com',
      total: 264, currency: 'ARS', originalFilename: 'proof.pdf', contentType: 'application/pdf', sizeBytes: 1200,
      submittedAt: '2026-08-17T11:00:00Z', reviewedAt: null, rejectionReason: null, previewCount: 1,
    };
    const approve = vi.fn(() => of(void 0));
    const reject = vi.fn(() => of(void 0));
    const createDescriptor = Object.getOwnPropertyDescriptor(URL, 'createObjectURL');
    const revokeDescriptor = Object.getOwnPropertyDescriptor(URL, 'revokeObjectURL');
    const createObjectURL = vi.fn(() => 'blob:sanitized-proof');
    const revokeObjectURL = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectURL });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectURL });
    await TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [{
        provide: AdminService,
        useValue: {
          products: () => of({ content: [] }), categories: () => of([]), brands: () => of([]), inventories: () => of([]),
          orders: () => of([renderedOrder]), pendingBankTransferProofs: () => of([proof]),
          bankTransferProofPreview: () => of(new Blob(['sanitized'], { type: 'image/png' })),
          approveBankTransferProof: approve, rejectBankTransferProof: reject,
        },
      }],
    }).compileComponents();
    const fixture = TestBed.createComponent(AdminComponent);
    const component = fixture.componentInstance;
    component.section.set('sales');
    fixture.detectChanges();

    component.approveProof(proof);
    expect(approve).not.toHaveBeenCalled();
    expect(component.proofReviewError()[proof.id]).toContain('importe');
    component.proofAmounts[proof.id] = 264.004;
    component.proofReferences[proof.id] = 'REF-9001';
    component.approveProof(proof);
    expect(approve).not.toHaveBeenCalled();
    expect(component.proofReviewError()[proof.id]).toContain('coincidir exactamente');
    component.proofAmounts[proof.id] = 264;
    component.proofReferences[proof.id] = 'REF-9001';
    component.approveProof(proof);
    expect(approve).toHaveBeenCalledWith(proof.id, 264, 'REF-9001');
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:sanitized-proof');

    component.rejectProof(proof);
    expect(reject).not.toHaveBeenCalled();
    component.proofRejectionReasons[proof.id] = 'El importe no coincide';
    component.rejectProof(proof);
    expect(reject).toHaveBeenCalledWith(proof.id, 'El importe no coincide');
    fixture.destroy();
    if (createDescriptor) Object.defineProperty(URL, 'createObjectURL', createDescriptor); else delete (URL as Partial<typeof URL>).createObjectURL;
    if (revokeDescriptor) Object.defineProperty(URL, 'revokeObjectURL', revokeDescriptor); else delete (URL as Partial<typeof URL>).revokeObjectURL;
  });
});
