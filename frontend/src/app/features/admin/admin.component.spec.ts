import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Order } from '../../core/orders/order.service';
import { AdminComponent } from './admin.component';
import { AdminService } from './admin.service';

describe('AdminComponent payments', () => {
  const renderedOrder: Order = {
    id: 41, status: 'PAID', paymentStatus: 'APPROVED', fulfillmentStatus: 'PENDING', currency: 'ARS',
    paymentMethod: 'MERCADO_PAGO', deliveryMethod: null, fulfillmentMethod: 'PICKUP', pickupLocation: null, total: 240,
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
          inventories: () => of([]), orders: () => of([renderedOrder]),
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
          inventories: () => of([]), orders: () => of([]),
        },
      }],
    }).compileComponents();
    const fixture = TestBed.createComponent(AdminComponent);
    const component = fixture.componentInstance;
    const base: Order = {
      id: 1, status: 'PAID', paymentStatus: 'APPROVED', fulfillmentStatus: 'PENDING', currency: 'ARS',
      paymentMethod: 'MERCADO_PAGO', deliveryMethod: null, fulfillmentMethod: 'PICKUP', pickupLocation: null, total: 100, createdAt: '2026-08-17T10:00:00Z',
      reservationExpiresAt: '2026-08-18T10:00:00Z', customerName: 'Ada', customerEmail: 'ada@example.com', items: [],
    };

    component.orders.set([
      base,
      { ...base, id: 2, paymentStatus: 'REFUNDED', total: 500 },
      { ...base, id: 3, paymentStatus: 'PENDING', total: 300 },
    ]);

    expect(component.soldOrders().map((order) => order.id)).toEqual([1]);
    expect(component.revenue()).toBe(100);
    expect(component.averageTicket()).toBe(100);
    expect(component.orderActions('PENDING_PAYMENT').map((action) => action.label)).toEqual(['Cancelar']);
    expect(component.orderActions('PAID').map((action) => action.label)).toEqual(['Preparar pedido']);
    expect(component.orderActions('PREPARING').map((action) => action.label)).toEqual(['Marcar listo']);
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
          orders: () => of([]),
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
          orders: () => of([]),
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
    expect(detail.getAttribute('aria-hidden')).toBeNull();
    expect(detail.hasAttribute('inert')).toBe(false);
    expect(document.activeElement).toBe(summary);
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
});
