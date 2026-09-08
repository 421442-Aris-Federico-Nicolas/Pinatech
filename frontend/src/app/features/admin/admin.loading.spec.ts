import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AdminComponent } from './admin.component';
import { AdminOrder, PendingBankTransferProof } from './admin.service';
import { HomeSectionsComponent } from './home-sections/home-sections.component';

describe('Admin section HTTP budgets', () => {
  const base = environment.apiBaseUrl;
  const summary = { soldOrders: 150, revenue: 90000, averageTicket: 600, activeOrders: 45, statusCounts: { PAID: 30, CANCELLED: 20 }, recentOrders: [], salesChart: [] };
  const page = { content: [], number: 0, size: 20, totalPages: 3, totalElements: 45 };
  const proof: PendingBankTransferProof = {
    id: 'proof-1', status: 'PENDING_REVIEW', orderId: 1, customerName: 'Ada', customerEmail: 'ada@example.com',
    total: 100, currency: 'ARS', originalFilename: 'proof.pdf', contentType: 'application/pdf', sizeBytes: 100,
    submittedAt: '2026-08-17T11:00:00Z', reviewedAt: null, rejectionReason: null, previewCount: 4,
  };
  const product = (id: number) => ({ id, name: `Product ${id}`, slug: `product-${id}`, description: '', price: 100, categoryId: 1, categoryName: 'Category', brandId: 1, brandName: 'Brand', images: [], specifications: [], variants: [] });

  const order = (id: number): AdminOrder => ({
    id, status: 'PAID', paymentStatus: 'APPROVED', fulfillmentStatus: 'PENDING', currency: 'ARS',
    paymentMethod: 'MERCADO_PAGO', deliveryMethod: null, fulfillmentMethod: 'PICKUP', pickupLocation: null,
    subtotal: 100, paymentDiscount: 0, paymentSurcharge: 0, total: 100, shippingCost: 0,
    createdAt: '2026-08-17T10:00:00Z', reservationExpiresAt: null, cancellationReason: null, cancelledAt: null,
    customerName: 'Ada', customerEmail: 'ada@example.com', deliveryAddress: null, shipment: null, items: [],
  });

  function setup(section = 'overview', query: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [AdminComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), {
        provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ section, ...query }) } },
      }],
    });
    const fixture = TestBed.createComponent(AdminComponent);
    return { fixture, component: fixture.componentInstance, http: TestBed.inject(HttpTestingController) };
  }

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  function flushSales(http: HttpTestingController, content: AdminOrder[] = []) {
    http.expectOne((request) => request.url.endsWith('/orders/page')).flush({ ...page, content });
    http.expectOne(`${base}/admin/orders/summary`).flush(summary);
    http.expectOne((request) => request.url.endsWith('/bank-transfer-proofs')).flush([]);
  }

  it('opens a deep-linked order outside page zero and its filter without changing page totals', () => {
    const { fixture, component, http } = setup('sales', { order: '41', orderStatus: 'CANCELLED' });
    const first = http.expectOne((request) => request.url.endsWith('/orders/page'));
    expect(first.request.params.get('status')).toBe('CANCELLED');
    first.flush({ ...page, content: [{ ...order(1), status: 'CANCELLED' }] });
    http.expectOne(`${base}/admin/orders/summary`).flush(summary);
    http.expectOne((request) => request.url.endsWith('/bank-transfer-proofs')).flush([]);
    http.expectOne(`${base}/admin/orders/41`).flush(order(41));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#order-detail-41')).toBeTruthy();
    expect(component.filteredOrders().map((item) => item.id)).toEqual([41, 1]);
    expect(component.orders().map((item) => item.id)).toEqual([1]);
    expect(component.totalElements()).toBe(45);
    expect(component.orderFilter()).toBe('CANCELLED');
    http.expectNone(`${base}/admin/orders`);
  });

  it('reuses an order already on the requested page without duplicating its detail', () => {
    const { component, http } = setup('sales', { order: '41' });
    flushSales(http, [order(41)]);
    http.expectNone(`${base}/admin/orders/41`);
    expect(component.filteredOrders()).toHaveLength(1);
    expect(component.expandedOrder()).toBe(41);
  });

  it('cancels superseded and destroyed deep-link requests', () => {
    const { fixture, component, http } = setup('sales', { order: '41' });
    flushSales(http);
    const old = http.expectOne(`${base}/admin/orders/41`);
    component.openOrder(42);
    expect(old.cancelled).toBe(true);
    flushSales(http);
    const current = http.expectOne(`${base}/admin/orders/42`);
    fixture.destroy();
    expect(current.cancelled).toBe(true);
    expect(component.requestedOrder()).toBeNull();
  });

  it('reconciles failed shipment cancellation by ID without listing all orders', () => {
    const { component, http } = setup('sales');
    flushSales(http);
    const delivery: AdminOrder = { ...order(41), fulfillmentMethod: 'DELIVERY', shipment: {
      status: 'ACTIVE', providerStatus: 'documentation_ready', providerSubstatus: null, carrier: null,
      trackingCode: null, trackingUrl: null, estimatedDeliveryAt: null, incident: false,
    } };
    component.openCancellationDialog(delivery);
    component.submitCancellation();
    http.expectOne(`${base}/admin/shipping/orders/41/cancel`).flush({}, { status: 500, statusText: 'Error' });
    expect(component.shipmentUpdating()).toBe(41);
    http.expectOne(`${base}/admin/orders/41`).flush({ ...delivery, shipment: { ...delivery.shipment, status: 'CANCELLED' } });
    expect(component.cancellationOrder()).toBeNull();
    expect(component.shipmentUpdating()).toBeNull();
    http.expectNone(`${base}/admin/orders`);
  });

  it('reconciles failed refund confirmation by ID and preserves the entered reference', () => {
    const { component, http } = setup('sales');
    flushSales(http);
    const refund: AdminOrder = { ...order(41), status: 'CANCELLED', paymentMethod: 'BANK_TRANSFER', paymentStatus: 'REFUND_PENDING' };
    component.openRefundDialog(refund);
    component.refundReference = 'REF-123456';
    component.confirmBankTransferRefund();
    http.expectOne(`${base}/admin/orders/41/bank-transfer-refund/confirm`).flush({}, { status: 500, statusText: 'Error' });
    expect(component.refundConfirming()).toBe(41);
    http.expectOne(`${base}/admin/orders/41`).flush(refund);
    expect(component.refundConfirming()).toBeNull();
    expect(component.refundOrder()).toEqual(refund);
    expect(component.refundReference).toBe('REF-123456');
    http.expectNone(`${base}/admin/orders`);
  });

  it('updates selected inventory from refreshed rows without replacing dirty adjustment fields', () => {
    const { component, http } = setup('inventory');
    const stock = { productId: 1, variantId: 11, productName: 'Mouse', brandName: 'Brand', colorName: 'Black', colorHex: null, availableQuantity: 5, reservedQuantity: 2 };
    http.expectOne((request) => request.url.endsWith('/inventory/page')).flush({ ...page, content: [stock] });
    component.selectedVariantId.set(11);
    component.inventory.set(stock);
    component.adjustment = -2;
    component.adjustmentReason = 'Pending count correction';
    component.searchPage();
    const updated = { ...stock, availableQuantity: 9, reservedQuantity: 4 };
    http.expectOne((request) => request.url.endsWith('/inventory/page')).flush({ ...page, content: [updated] });
    expect(component.inventory()).toEqual(updated);
    expect(component.adjustment).toBe(-2);
    expect(component.adjustmentReason).toBe('Pending count correction');
    http.expectNone(`${base}/inventory/11`);
  });

  it('refreshes off-page selected inventory by ID and cancels old stock reads on a new search', () => {
    const { fixture, component, http } = setup('inventory');
    const stock = { productId: 1, variantId: 11, colorName: 'Black', colorHex: null, availableQuantity: 5, reservedQuantity: 2 };
    http.expectOne((request) => request.url.endsWith('/inventory/page')).flush(page);
    component.selectedVariantId.set(11);
    component.inventory.set(stock);
    component.adjustment = 3;
    component.adjustmentReason = 'Restock';
    component.changePage(1);
    http.expectOne((request) => request.url.endsWith('/inventory/page')).flush({ ...page, number: 1 });
    const old = http.expectOne(`${base}/inventory/11`);
    expect(component.inventory()).toBeNull();
    component.searchPage();
    expect(old.cancelled).toBe(true);
    http.expectOne((request) => request.url.endsWith('/inventory/page')).flush(page);
    http.expectOne(`${base}/inventory/11`).flush({ ...stock, availableQuantity: 20 });
    expect(component.inventory()?.availableQuantity).toBe(20);
    expect(component.adjustment).toBe(3);
    expect(component.adjustmentReason).toBe('Restock');
    component.searchPage();
    http.expectOne((request) => request.url.endsWith('/inventory/page')).flush(page);
    const last = http.expectOne(`${base}/inventory/11`);
    fixture.destroy();
    expect(last.cancelled).toBe(true);
  });

  it('loads exactly two summaries on overview, not any global lists or previews', () => {
    const { component, http } = setup();
    const requests = http.match(() => true);
    expect(requests.map((request) => request.request.url).sort()).toEqual([
      `${base}/admin/orders/summary`, `${base}/inventory/summary`,
    ]);
    requests.find((request) => request.request.url.endsWith('/orders/summary'))!.flush(summary);
    requests.find((request) => request.request.url.endsWith('/inventory/summary'))!.flush({ lowStock: 12, availableUnits: 1500 });
    expect(component.revenue()).toBe(90000);
    expect(component.availableUnits()).toBe(1500);
    expect(component.statusCount('ALL')).toBe(50);
    expect(component.orders()).toEqual([]);
  });

  it('mounts Home requests only in its section and does not load other admin resources', () => {
    const { fixture, component, http } = setup('home');
    fixture.detectChanges();
    const requests = http.match(() => true);
    expect(requests.map((request) => request.request.url).sort()).toEqual([
      `${base}/admin/catalog/categories`, `${base}/admin/home/sections`,
    ]);
    requests.find((request) => request.request.url.endsWith('/home/sections'))!.flush([]);
    requests.find((request) => request.request.url.endsWith('/categories'))!.flush([]);
    fixture.detectChanges();
    expect(component.section()).toBe('home');
    http.expectNone((request) => request.url.includes('/products/cards'));

    const homeEditor = fixture.debugElement.query(By.directive(HomeSectionsComponent)).componentInstance as HomeSectionsComponent;
    homeEditor.setMode('AUTOMATIC');
    expect(component.homeDirty()).toBe(true);
    expect(component.hasUnsavedChanges()).toBe(true);
    const confirmation = vi.spyOn(globalThis, 'confirm').mockReturnValue(false);
    expect(component.navigate('overview')).toBe(false);
    expect(confirmation).toHaveBeenCalledOnce();
    expect(component.section()).toBe('home');
    http.expectNone(() => true);
    confirmation.mockReturnValue(true);
    component.navigate('overview');
    expect(confirmation).toHaveBeenCalledTimes(2);
    expect(component.homeDirty()).toBe(false);
    const overview = http.match(() => true);
    expect(overview.map((request) => request.request.url).sort()).toEqual([
      `${base}/admin/orders/summary`, `${base}/inventory/summary`,
    ]);
    overview.forEach((request) => request.flush(request.request.url.endsWith('/orders/summary') ? summary : { lowStock: 0, availableUnits: 0 }));
    confirmation.mockRestore();
  });

  it('blocks leaving Home during candidate loading without opening a confirmation', () => {
    const { fixture, component, http } = setup('home');
    fixture.detectChanges();
    http.expectOne(`${base}/admin/home/sections`).flush([{
      id: 1, displayOrder: 0, eyebrow: '', title: 'Hardware', description: '', buttonLabel: '', mode: 'MANUAL',
      productLimit: 8, sort: 'NEWEST', categoryIds: [], productIds: [1], active: true,
      bannerDesktopUrl: null, bannerMobileUrl: null,
    }]);
    http.expectOne(`${base}/admin/catalog/categories`).flush([]);
    const candidates = http.expectOne((request) => request.url.endsWith('/products/cards'));
    fixture.detectChanges();
    const confirmation = vi.spyOn(globalThis, 'confirm').mockReturnValue(true);

    expect(component.hasPendingOperation()).toBe(true);
    expect(component.navigate('overview')).toBe(false);
    expect(confirmation).not.toHaveBeenCalled();
    const event = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;
    component.protectUnsavedChanges(event);
    expect(event.defaultPrevented).toBe(true);

    candidates.flush({ content: [], number: 0, size: 24, totalPages: 0, totalElements: 0 });
    expect(component.hasPendingOperation()).toBe(false);
    confirmation.mockRestore();
  });

  it('cancels the old section on navigation and outstanding requests on destroy', () => {
    const { fixture, component, http } = setup();
    const old = http.match(() => true);
    component.navigate('inventory');
    expect(old.every((request) => request.cancelled)).toBe(true);
    const inventory = http.expectOne((request) => request.url === `${base}/inventory/page`);
    expect(inventory.request.params.get('size')).toBe('20');
    http.expectNone((request) => request.url.includes('/products'));
    fixture.destroy();
    expect(inventory.cancelled).toBe(true);
    expect(component.inventoryRows()).toEqual([]);
  });

  it('loads only cards and taxonomy in catalog and cancels superseded product details', () => {
    const { fixture, component, http } = setup('catalog');
    const requests = http.match(() => true);
    expect(requests).toHaveLength(3);
    requests.find((request) => request.request.url.endsWith('/products/cards'))!.flush(page);
    requests.filter((request) => !request.request.url.endsWith('/products/cards')).forEach((request) => request.flush([]));
    component.loadProduct(1);
    const first = http.expectOne(`${base}/products/1`);
    component.loadProduct(2);
    expect(first.cancelled).toBe(true);
    http.expectOne(`${base}/products/2`).flush(product(2));
    expect(component.selected()?.id).toBe(2);
    component.loadProduct(3);
    const last = http.expectOne(`${base}/products/3`);
    fixture.destroy();
    expect(last.cancelled).toBe(true);
  });

  it('paginates and searches inventory on the server, resetting the page for a new search', () => {
    const { component, http } = setup('inventory');
    http.expectOne((request) => request.url.endsWith('/inventory/page')).flush(page);
    component.changePage(1);
    const second = http.expectOne((request) => request.url.endsWith('/inventory/page'));
    expect(second.request.params.get('page')).toBe('1');
    second.flush({ ...page, number: 1 });
    component.search = '  Mouse  ';
    component.searchPage();
    const search = http.expectOne((request) => request.url.endsWith('/inventory/page'));
    expect(search.request.params.get('page')).toBe('0');
    expect(search.request.params.get('search')).toBe('Mouse');
    search.flush(page);
  });

  it('searches catalog cards in pages of 20 and cancels superseded searches without overwriting edits', () => {
    const { component, http } = setup('catalog');
    http.expectOne((request) => request.url.endsWith('/products/cards')).flush(page);
    http.expectOne(`${base}/admin/catalog/categories`).flush([]);
    http.expectOne(`${base}/admin/catalog/brands`).flush([]);
    component.select(product(9));
    component.form.name = 'Unsaved name';
    component.search = ' keyboard ';
    component.searchPage();
    const old = http.match(() => true);
    const cards = old.find((request) => request.request.url.endsWith('/products/cards'))!;
    expect(cards.request.params.get('search')).toBe('keyboard');
    expect(cards.request.params.get('size')).toBe('20');
    component.search = 'mouse';
    component.searchPage();
    expect(old.every((request) => request.cancelled)).toBe(true);
    http.expectOne((request) => request.url.endsWith('/products/cards')).flush({ ...page, content: [product(2)] });
    http.expectOne(`${base}/admin/catalog/categories`).flush([]);
    http.expectOne(`${base}/admin/catalog/brands`).flush([]);
    expect(component.products()[0].id).toBe(2);
    expect(component.form.name).toBe('Unsaved name');
    http.expectNone(`${base}/products/9`);
  });

  it('uses server sales filters, omits ALL and keeps global counts independent of the page', () => {
    const { component, http } = setup('sales');
    const first = http.expectOne((request) => request.url.endsWith('/orders/page'));
    expect(first.request.params.has('status')).toBe(false);
    first.flush(page);
    http.expectOne(`${base}/admin/orders/summary`).flush(summary);
    http.expectOne((request) => request.url.endsWith('/bank-transfer-proofs')).flush([proof]);
    http.expectNone((request) => request.url.includes('/previews/'));
    component.filterOrders('PAID');
    const filtered = http.expectOne((request) => request.url.endsWith('/orders/page'));
    expect(filtered.request.params.get('status')).toBe('PAID');
    expect(filtered.request.params.get('page')).toBe('0');
    filtered.flush({ ...page, totalElements: 30 });
    http.expectOne(`${base}/admin/orders/summary`).flush(summary);
    http.expectOne((request) => request.url.endsWith('/bank-transfer-proofs')).flush([]);
    expect(component.statusCount('PAID')).toBe(30);
    expect(component.soldOrders()).toBe(150);
    expect(component.orders()).toEqual([]);
  });

  it('limits preview concurrency to two, blocks approval before completion and cancels queued work', () => {
    const { component, http } = setup('sales');
    http.expectOne((request) => request.url.endsWith('/orders/page')).flush(page);
    http.expectOne(`${base}/admin/orders/summary`).flush(summary);
    http.expectOne((request) => request.url.endsWith('/bank-transfer-proofs')).flush([proof]);
    component.approveProof(proof);
    http.expectNone((request) => request.method === 'POST');
    component.loadProofPreviews(proof);
    component.loadProofPreviews({ ...proof, id: 'proof-2' });
    const active = http.match((request) => request.url.includes('/previews/'));
    expect(active).toHaveLength(2);
    active[0].flush(new Blob(['first'], { type: 'image/png' }));
    const third = http.expectOne(`${base}/admin/bank-transfer-proofs/proof-1/previews/2`);
    expect(component.proofPreviewsReady(proof)).toBe(false);
    component.navigate('inventory');
    expect(active[1].cancelled).toBe(true);
    expect(third.cancelled).toBe(true);
    http.expectNone(`${base}/admin/bank-transfer-proofs/proof-1/previews/3`);
    expect(component.proofPreviewUrls()).toEqual({});
    http.expectOne((request) => request.url.endsWith('/inventory/page')).flush(page);
  });
});
