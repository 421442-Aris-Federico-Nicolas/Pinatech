import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { Order, OrderService } from '../../core/orders/order.service';

@Component({
  selector: 'app-orders',
  imports: [CurrencyPipe, DatePipe, MatButtonModule, RouterLink],
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdersComponent {
  private readonly service = inject(OrderService);
  readonly orders = signal<Order[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');

  constructor() {
    this.load();
  }

  load(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    this.service.mine().pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (orders) => this.orders.set(orders),
      error: () => this.error.set('No pudimos cargar tus pedidos. Intentá nuevamente.'),
    });
  }

  statusLabel(status: string): string {
    return this.label(status, {
      PENDING_PAYMENT: 'Pendiente de pago',
      PENDING: 'Pendiente',
      REQUESTED: 'Solicitud registrada',
      PAID: 'Pagado',
      PREPARING: 'En preparación',
      READY: 'Listo',
      SHIPPED: 'Enviado',
      DELIVERED: 'Entregado',
      CANCELLED: 'Cancelado',
    });
  }

  paymentLabel(status: string): string {
    return this.label(status, {
      PENDING: 'Pago pendiente',
      UNPAID: 'Sin pagar',
      PAID: 'Pago acreditado',
      APPROVED: 'Pago aprobado',
      FAILED: 'Pago rechazado',
      REJECTED: 'Pago rechazado',
      EXPIRED: 'Pago vencido',
      REFUNDED: 'Pago reintegrado',
      CANCELLED: 'Pago cancelado',
      NOT_REQUIRED: 'Pago no requerido',
    });
  }

  fulfillmentLabel(status: string): string {
    return this.label(status, {
      PENDING: 'Preparación pendiente',
      UNFULFILLED: 'Sin preparar',
      RESERVED: 'Stock reservado',
      PREPARING: 'En preparación',
      READY: 'Listo para entregar',
      SHIPPED: 'Enviado',
      DELIVERED: 'Entregado',
      CANCELLED: 'Entrega cancelada',
    });
  }

  methodLabel(method: string | null): string {
    if (!method) return 'A definir';
    return this.label(method, {
      CASH: 'Efectivo',
      BANK_TRANSFER: 'Transferencia bancaria',
      CARD: 'Tarjeta',
      PICKUP: 'Retiro',
      DELIVERY: 'Entrega',
      SHIPPING: 'Envío',
    });
  }

  private label(value: string, labels: Record<string, string>): string {
    return labels[value] ?? value.toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase());
  }
}
