import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { Order, OrderService } from '../../core/orders/order.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { estadoLabel, estadoTono } from '../../core/utils/estado-label';
import { AppBadgeDirective } from '../../shared/ui/app-badge.directive';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { CHECKOUT_WINDOW, CheckoutService } from '../checkout/checkout.service';

@Component({
  selector: 'app-orders',
  imports: [AppBadgeDirective, AppButtonDirective, AppCardDirective, CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdersComponent {
  private readonly service = inject(OrderService);
  private readonly checkoutService = inject(CheckoutService);
  private readonly browserWindow = inject(CHECKOUT_WINDOW);
  private readonly notifications = inject(NotificationService);
  readonly orders = signal<Order[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly onlinePaymentAvailable = signal(false);
  readonly loadingCapabilities = signal(false);
  readonly capabilitiesError = signal('');
  readonly payingOrder = signal<number | null>(null);
  readonly paymentError = signal('');
  readonly estadoTono = estadoTono;

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

  isReservationExpired(order: Order): boolean {
    return !Number.isFinite(Date.parse(order.reservationExpiresAt)) || Date.parse(order.reservationExpiresAt) <= Date.now();
  }

  paymentUnavailableReason(order: Order): string | null {
    if (order.status !== 'PENDING_PAYMENT' || !['PENDING', 'REJECTED'].includes(order.paymentStatus)) return null;
    if (this.isReservationExpired(order)) return 'La reserva venció; este pedido ya no admite un nuevo intento de pago.';
    if (this.loadingCapabilities()) return 'Estamos consultando las opciones de pago disponibles…';
    if (!this.capabilitiesError() && !this.onlinePaymentAvailable()) return 'El pago online no está disponible en este momento.';
    return null;
  }

  load(showConfirmation = false): void {
    if (this.loading()) return;
    this.loading.set(true);
    this.service.mine().pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.error.set('');
        if (showConfirmation) this.notifications.success('Tus pedidos están actualizados.');
      },
      error: () => this.error.set('No pudimos cargar tus pedidos. Intentá nuevamente.'),
    });
  }

  statusLabel(status: string): string {
    return estadoLabel(status, 'pedido');
  }

  paymentLabel(status: string): string {
    return estadoLabel(status, 'pago');
  }

  fulfillmentLabel(status: string): string {
    return estadoLabel(status, 'entrega');
  }

  methodLabel(method: string | null): string {
    return estadoLabel(method, 'metodo');
  }
}
