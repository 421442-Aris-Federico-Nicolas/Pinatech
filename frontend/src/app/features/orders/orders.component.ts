import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { Order, OrderService } from '../../core/orders/order.service';
import { CHECKOUT_WINDOW, CheckoutService } from '../checkout/checkout.service';

@Component({
  selector: 'app-orders',
  imports: [CurrencyPipe, DatePipe, MatButtonModule, RouterLink],
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdersComponent {
  private readonly service = inject(OrderService);
  private readonly checkoutService = inject(CheckoutService);
  private readonly browserWindow = inject(CHECKOUT_WINDOW);
  readonly orders = signal<Order[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly onlinePaymentAvailable = signal(false);
  readonly loadingCapabilities = signal(false);
  readonly capabilitiesError = signal('');
  readonly payingOrder = signal<number | null>(null);
  readonly paymentError = signal('');

  constructor() {
    this.load();
    this.loadCapabilities();
  }

  loadCapabilities(): void {
    if (this.loadingCapabilities()) return;
    this.loadingCapabilities.set(true);
    this.checkoutService.capabilities().pipe(finalize(() => this.loadingCapabilities.set(false))).subscribe({
      next: (capabilities) => {
        this.onlinePaymentAvailable.set(
          capabilities.onlinePaymentsEnabled && capabilities.paymentMethods.includes('MERCADO_PAGO'),
        );
        this.capabilitiesError.set('');
      },
      error: () => {
        this.onlinePaymentAvailable.set(false);
        this.capabilitiesError.set('No pudimos consultar si el pago online está disponible.');
      },
    });
  }

  pay(order: Order): void {
    if (!this.canPay(order) || this.payingOrder() !== null) return;
    this.payingOrder.set(order.id);
    this.paymentError.set('');
    this.checkoutService.mercadoPago(order.id, order.paymentStatus).pipe(
      finalize(() => this.payingOrder.set(null)),
    ).subscribe({
      next: (payment) => {
        if (!payment.checkoutUrl.trim()) {
          this.paymentError.set('Mercado Pago no devolvió un enlace válido. Intentá nuevamente.');
          return;
        }
        this.browserWindow.location.assign(payment.checkoutUrl);
      },
      error: () => this.paymentError.set('No pudimos iniciar el pago. Podés reintentarlo sin duplicar el intento.'),
    });
  }

  canPay(order: Order): boolean {
    return this.onlinePaymentAvailable()
      && order.status === 'PENDING_PAYMENT'
      && ['PENDING', 'REJECTED'].includes(order.paymentStatus)
      && Number.isFinite(Date.parse(order.reservationExpiresAt))
      && Date.parse(order.reservationExpiresAt) > Date.now();
  }

  load(): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.service.mine().pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.error.set('');
      },
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
      REFUND_PENDING: 'Reintegro en proceso',
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
      MERCADO_PAGO: 'Mercado Pago',
      PICKUP: 'Retiro',
      DELIVERY: 'Entrega',
      SHIPPING: 'Envío',
    });
  }

  private label(value: string, labels: Record<string, string>): string {
    return labels[value] ?? value.toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase());
  }
}
