import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { CartService, OrderConfirmation } from '../../core/cart/cart.service';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { CheckoutCapabilities, CheckoutService } from './checkout.service';

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

  readonly capabilities = signal<CheckoutCapabilities | null>(null);
  readonly created = signal<OrderConfirmation | null>(this.cart.items().length ? null : this.cart.confirmation());
  readonly loadingCapabilities = signal(false);
  readonly submitting = signal(false);
  readonly capabilitiesError = signal('');
  readonly submitError = signal('');

  constructor() {
    if (this.cart.items().length) this.loadCapabilities();
  }

  loadCapabilities(): void {
    if (this.loadingCapabilities()) return;
    this.loadingCapabilities.set(true);
    this.capabilitiesError.set('');
    this.checkoutService.capabilities().pipe(finalize(() => this.loadingCapabilities.set(false))).subscribe({
      next: (capabilities) => this.capabilities.set(capabilities),
      error: () => {
        this.capabilities.set(null);
        this.capabilitiesError.set('No pudimos consultar las opciones disponibles. Intentá nuevamente antes de registrar la solicitud.');
      },
    });
  }

  submit(): void {
    const capabilities = this.capabilities();
    if (this.submitting() || !this.cart.items().length || !capabilities?.orderRequestsEnabled) return;

    this.submitting.set(true);
    this.submitError.set('');
    this.cart.checkout().pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (order) => this.created.set(order),
      error: () => this.submitError.set('No pudimos registrar la solicitud. Revisá el carrito e intentá nuevamente.'),
    });
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
      PICKUP: 'Retiro',
      DELIVERY: 'Entrega',
      SHIPPING: 'Envío',
    };
    return labels[method] ?? method.toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase());
  }
}
