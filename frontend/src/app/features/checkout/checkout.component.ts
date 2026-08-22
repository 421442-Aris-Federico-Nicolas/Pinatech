import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { finalize, map, switchMap } from 'rxjs';
import { CartService, OrderConfirmation } from '../../core/cart/cart.service';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { CHECKOUT_WINDOW, CheckoutCapabilities, CheckoutService } from './checkout.service';

@Component({
  selector: 'app-checkout',
  imports: [CurrencyPipe, DatePipe, MatButtonModule, RouterLink],
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutComponent {
  readonly imageUrl = resolveApiContentUrl;
  readonly cart = inject(CartService);
  private readonly checkoutService = inject(CheckoutService);
  private readonly browserWindow = inject(CHECKOUT_WINDOW);

  readonly capabilities = signal<CheckoutCapabilities | null>(null);
  readonly created = signal<OrderConfirmation | null>(this.cart.items().length ? null : this.cart.confirmation());
  readonly loadingCapabilities = signal(false);
  readonly submitting = signal(false);
  readonly capabilitiesError = signal('');
  readonly submitError = signal('');

  constructor() {
    this.cart.reconcile().subscribe((success) => {
      if (!success) {
        this.capabilitiesError.set('No pudimos verificar la disponibilidad del carrito. Volvé al carrito y reintentá antes de continuar.');
        return;
      }
      if (this.cart.items().length) this.loadCapabilities();
    });
  }

  loadCapabilities(): void {
    if (this.loadingCapabilities()) return;
    this.loadingCapabilities.set(true);
    this.checkoutService.capabilities().pipe(finalize(() => this.loadingCapabilities.set(false))).subscribe({
      next: (capabilities) => {
        this.capabilities.set(capabilities);
        this.capabilitiesError.set('');
      },
      error: () => {
        this.capabilities.set(null);
        this.capabilitiesError.set('No pudimos consultar las opciones disponibles. Intentá nuevamente antes de continuar con el pago.');
      },
    });
  }

  submit(): void {
    const capabilities = this.capabilities();
    if (this.submitting() || !this.cart.items().length || !this.mercadoPagoEnabled(capabilities)) return;

    this.submitting.set(true);
    this.submitError.set('');
    this.cart.checkout().pipe(
      switchMap((order) => this.checkoutService.mercadoPago(order.id, order.paymentStatus).pipe(
        map((payment) => {
          if (!payment.checkoutUrl.trim()) throw new Error('Mercado Pago did not return a checkout URL.');
          return { order, payment };
        }),
      )),
      finalize(() => this.submitting.set(false)),
    ).subscribe({
      next: ({ order, payment }) => {
        this.cart.completeCheckout(order);
        this.created.set(order);
        this.browserWindow.location.assign(payment.checkoutUrl);
      },
      error: () => this.submitError.set('No pudimos iniciar el pago. Conservamos tu carrito para que puedas reintentarlo sin duplicar el pedido.'),
    });
  }

  mercadoPagoEnabled(capabilities = this.capabilities()): boolean {
    return !!capabilities?.orderRequestsEnabled
      && capabilities.onlinePaymentsEnabled
      && capabilities.paymentMethods.includes('MERCADO_PAGO');
  }

  dismissConfirmation(): void {
    this.cart.dismissConfirmation();
    this.created.set(null);
  }

  reservationExpiration(order: OrderConfirmation): string | null {
    return Number.isFinite(Date.parse(order.reservationExpiresAt)) ? order.reservationExpiresAt : null;
  }

  isReservationExpired(order: OrderConfirmation): boolean {
    const expiration = this.reservationExpiration(order);
    return expiration === null || Date.parse(expiration) <= Date.now();
  }

  methodLabel(method: string): string {
    const labels: Record<string, string> = {
      CASH: 'Efectivo',
      BANK_TRANSFER: 'Transferencia bancaria',
      CARD: 'Tarjeta',
      MERCADO_PAGO: 'Mercado Pago',
      PICKUP: 'Retiro',
      DELIVERY: 'Entrega',
      SHIPPING: 'Envío',
    };
    return labels[method] ?? method.toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase());
  }
}
