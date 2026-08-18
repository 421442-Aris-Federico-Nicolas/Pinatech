import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Order } from '../../core/orders/order.service';
import { AdminComponent } from './admin.component';
import { AdminService } from './admin.service';

describe('AdminComponent payments', () => {
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
      paymentMethod: 'MERCADO_PAGO', deliveryMethod: null, total: 100, createdAt: '2026-08-17T10:00:00Z',
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
});
