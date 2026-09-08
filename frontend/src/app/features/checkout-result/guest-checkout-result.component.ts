import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, switchMap, take, takeWhile, timer } from 'rxjs';
import { GuestCheckoutService } from '../../core/guest/guest-checkout.service';
import { Order } from '../../core/orders/order.service';
import { estadoLabel } from '../../core/utils/estado-label';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';
import { PinatechSaleSuccessComponent } from '../../shared/ui/pinatech-sale-success/pinatech-sale-success.component';

type PaymentResult = 'approved' | 'pending' | 'rejected' | 'refund-pending' | 'refunded' | 'mediation' | 'chargeback';
const TERMINAL_PAYMENT_STATUSES = new Set(['APPROVED', 'REJECTED', 'FAILED', 'EXPIRED', 'CANCELLED', 'REFUNDED', 'IN_MEDIATION', 'CHARGEBACK']);

@Component({
  selector: 'app-guest-checkout-result',
  imports: [AppButtonDirective, AppCardDirective, AppFeedbackComponent, CurrencyPipe,
    PinatechSaleSuccessComponent, RouterLink],
  templateUrl: './guest-checkout-result.component.html',
  styleUrl: './checkout-result.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GuestCheckoutResultComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly guest = inject(GuestCheckoutService);
  private readonly destroyRef = inject(DestroyRef);
  readonly orderId = this.parseOrderId(this.route.snapshot.queryParamMap.get('orderId'));
  readonly publicId = this.guest.returnPublicId(
    this.orderId,
    this.route.snapshot.queryParamMap.get('guestOrder'),
  );
  readonly order = signal<Order | null>(null);
  readonly polling = signal(false);
  readonly error = signal('');

  constructor() {
    if (!this.publicId) this.error.set('No encontramos el pedido de esta compra en la pestaña actual. Usá el enlace que recibiste por email para abrirlo.');
    else this.load();
  }

  load(): void {
    if (!this.publicId || this.polling()) return;
    this.polling.set(true);
    timer(0, 2000).pipe(
      take(6),
      switchMap(() => this.guest.getOrder(this.publicId!)),
      takeWhile(({ order }) => !TERMINAL_PAYMENT_STATUSES.has(order.paymentStatus), true),
      finalize(() => this.polling.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: ({ order }) => { this.order.set(order); this.error.set(''); },
      error: () => this.error.set('No pudimos verificar el pedido con el servidor. Intentá nuevamente desde esta pestaña.'),
    });
  }

  result(order: Order): PaymentResult {
    if (order.paymentStatus === 'APPROVED') return 'approved';
    if (order.paymentStatus === 'REFUND_PENDING') return 'refund-pending';
    if (order.paymentStatus === 'REFUNDED') return 'refunded';
    if (order.paymentStatus === 'IN_MEDIATION') return 'mediation';
    if (order.paymentStatus === 'CHARGEBACK') return 'chargeback';
    if (TERMINAL_PAYMENT_STATUSES.has(order.paymentStatus)) return 'rejected';
    return 'pending';
  }

  methodLabel(method: string | null): string { return estadoLabel(method, 'metodo'); }

  private parseOrderId(value: string | null): number | null {
    if (!value || !/^\d+$/.test(value)) return null;
    const id = Number(value);
    return Number.isSafeInteger(id) && id > 0 ? id : null;
  }
}
