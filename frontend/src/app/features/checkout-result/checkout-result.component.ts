import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, switchMap, take, takeWhile, timer } from 'rxjs';
import { Order, OrderService } from '../../core/orders/order.service';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';

type PaymentResult = 'approved' | 'pending' | 'rejected' | 'refund-pending' | 'refunded';

const TERMINAL_PAYMENT_STATUSES = new Set(['APPROVED', 'REJECTED', 'FAILED', 'EXPIRED', 'CANCELLED', 'REFUNDED']);
const MAX_POLL_ATTEMPTS = 6;
const POLL_INTERVAL_MS = 2000;

@Component({
  selector: 'app-checkout-result',
  imports: [AppButtonDirective, AppCardDirective, CurrencyPipe, RouterLink],
  templateUrl: './checkout-result.component.html',
  styleUrl: './checkout-result.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutResultComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly orderService = inject(OrderService);
  private readonly destroyRef = inject(DestroyRef);

  readonly orderId = this.parseOrderId(this.route.snapshot.queryParamMap.get('orderId'));
  readonly order = signal<Order | null>(null);
  readonly polling = signal(false);
  readonly error = signal('');

  constructor() {
    if (this.orderId === null) {
      this.error.set('No encontramos un número de pedido válido en este regreso.');
      return;
    }
    this.load();
  }

  load(): void {
    if (this.orderId === null || this.polling()) return;
    this.polling.set(true);

    timer(0, POLL_INTERVAL_MS).pipe(
      take(MAX_POLL_ATTEMPTS),
      switchMap(() => this.orderService.get(this.orderId!)),
      takeWhile((order) => !TERMINAL_PAYMENT_STATUSES.has(order.paymentStatus), true),
      finalize(() => this.polling.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (order) => {
        this.order.set(order);
        this.error.set('');
      },
      error: () => this.error.set('No pudimos verificar el pedido con el servidor. Intentá nuevamente.'),
    });
  }

  result(order: Order): PaymentResult {
    if (order.paymentStatus === 'APPROVED') return 'approved';
    if (order.paymentStatus === 'REFUND_PENDING') return 'refund-pending';
    if (order.paymentStatus === 'REFUNDED') return 'refunded';
    if (TERMINAL_PAYMENT_STATUSES.has(order.paymentStatus)) return 'rejected';
    return 'pending';
  }

  private parseOrderId(value: string | null): number | null {
    if (!value || !/^\d+$/.test(value)) return null;
    const id = Number(value);
    return Number.isSafeInteger(id) && id > 0 ? id : null;
  }
}
