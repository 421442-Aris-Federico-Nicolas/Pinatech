import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize, map, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartService, OrderConfirmation } from '../../core/cart/cart.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { estadoLabel } from '../../core/utils/estado-label';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { CHECKOUT_WINDOW, CheckoutCapabilities, CheckoutService } from './checkout.service';

@Component({
  selector: 'app-checkout',
  imports: [AppButtonDirective, AppCardDirective, CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutComponent {
  readonly imageUrl = resolveApiContentUrl;
  readonly cart = inject(CartService);
  private readonly checkoutService = inject(CheckoutService);
  private readonly browserWindow = inject(CHECKOUT_WINDOW);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly capabilities = signal<CheckoutCapabilities | null>(null);
  readonly selectedPickupCode = signal('');
  readonly pickupAccepted = signal(false);
  readonly created = signal<OrderConfirmation | null>(this.cart.items().length ? null : this.cart.confirmation());
  readonly reconciling = signal(false);
  readonly loadingCapabilities = signal(false);
  readonly submitting = signal(false);
  readonly resendingVerification = signal(false);
  readonly reconciliationError = signal('');
  readonly capabilitiesError = signal('');
  readonly submitError = signal('');
  readonly verificationNotice = signal('');
  readonly submitErrorKind = signal<'verification' | 'conflict' | 'network' | 'generic' | null>(null);
  readonly emailVerified = computed(() => this.auth.user()?.emailVerified === true);
  readonly selectedPickup = computed(() => this.capabilities()?.pickupLocations
    .find((location) => location.code === this.selectedPickupCode()) ?? null);

  constructor() {
    this.reconcileCheckout();
  }

  reconcileCheckout(): void {
    if (this.reconciling()) return;
    this.reconciling.set(true);
    this.reconciliationError.set('');
    this.capabilities.set(null);
    this.cart.reconcile().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((success) => {
      this.reconciling.set(false);
      if (!success) {
        this.reconciliationError.set('No pudimos verificar la disponibilidad del carrito. Reintentá la verificación antes de continuar.');
        return;
      }
      if (this.cart.items().length) this.loadCapabilities();
    });
  }

  loadCapabilities(): void {
    if (this.reconciling() || this.reconciliationError() || this.loadingCapabilities()) return;
    this.loadingCapabilities.set(true);
    const previousPickup = this.selectedPickup();
    this.checkoutService.capabilities().pipe(
      finalize(() => this.loadingCapabilities.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (capabilities) => {
        this.capabilities.set(capabilities);
        const currentCode = this.selectedPickupCode();
        const selectedCode = capabilities.pickupLocations.some((location) => location.code === currentCode)
          ? currentCode
          : capabilities.pickupLocations[0]?.code ?? '';
        const selectedPickup = capabilities.pickupLocations.find((location) => location.code === selectedCode);
        if (selectedCode !== currentCode || previousPickup?.version !== selectedPickup?.version) {
          this.selectedPickupCode.set(selectedCode);
          this.pickupAccepted.set(false);
        }
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
    const pickup = this.selectedPickup();
    if (this.submitting() || !this.cart.items().length || !pickup || !this.canSubmit(capabilities)) return;

    this.submitting.set(true);
    this.submitError.set('');
    this.submitErrorKind.set(null);
    this.cart.checkout('PICKUP', pickup.code, pickup.version).pipe(
      switchMap((order) => this.checkoutService.mercadoPago(order.id, order.paymentStatus).pipe(
        map((payment) => {
          if (!payment.checkoutUrl.trim()) throw new Error('Mercado Pago did not return a checkout URL.');
          return { order, payment };
        }),
      )),
      finalize(() => this.submitting.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: ({ order, payment }) => {
        this.cart.completeCheckout(order);
        this.created.set(order);
        this.browserWindow.location.assign(payment.checkoutUrl);
      },
      error: (error: unknown) => this.handleSubmitError(error),
    });
  }

  selectPickup(event: Event): void {
    this.selectedPickupCode.set((event.target as HTMLSelectElement).value);
    this.pickupAccepted.set(false);
    this.submitError.set('');
    this.submitErrorKind.set(null);
  }

  setPickupAccepted(event: Event): void {
    this.pickupAccepted.set((event.target as HTMLInputElement).checked);
  }

  resendVerification(): void {
    const email = this.auth.user()?.email;
    if (!email || this.resendingVerification()) return;
    this.resendingVerification.set(true);
    this.verificationNotice.set('');
    this.auth.requestEmailVerification(email).pipe(
      finalize(() => this.resendingVerification.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: () => {
        const message = 'Si el email está pendiente, te enviamos un nuevo enlace de verificación.';
        this.verificationNotice.set(message);
        this.notifications.success(message);
      },
      error: () => {
        const message = 'No pudimos reenviar el email de verificación. Intentá nuevamente desde Mi perfil.';
        this.verificationNotice.set(message);
        this.notifications.error(message);
      },
    });
  }

  mercadoPagoEnabled(capabilities = this.capabilities()): boolean {
    return !!capabilities?.orderRequestsEnabled
      && capabilities.onlinePaymentsEnabled
      && capabilities.paymentMethods.includes('MERCADO_PAGO');
  }

  pickupEnabled(capabilities = this.capabilities()): boolean {
    return !!capabilities?.fulfillmentMethods.includes('PICKUP') && !!this.selectedPickup();
  }

  canSubmit(capabilities = this.capabilities()): boolean {
    return this.mercadoPagoEnabled(capabilities)
      && this.pickupEnabled(capabilities)
      && this.pickupAccepted()
      && this.emailVerified();
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
    return estadoLabel(method, 'metodo');
  }

  private handleSubmitError(error: unknown): void {
    let kind: 'verification' | 'conflict' | 'network' | 'generic' = 'generic';
    let message = 'No pudimos iniciar el pago. Conservamos tu carrito para que puedas reintentarlo sin duplicar el pedido.';

    if (error instanceof HttpErrorResponse) {
      const problemType = error.error && typeof error.error === 'object'
        ? (error.error as Record<string, unknown>)['type']
        : null;
      if (error.status === 403 && typeof problemType === 'string' && problemType.endsWith('email-verification-required')) {
        kind = 'verification';
        message = 'Necesitás verificar tu email antes de comprar. Revisá tu perfil y volvé a intentarlo; conservamos tu carrito.';
      } else if (error.status === 409) {
        kind = 'conflict';
        message = 'Cambió el stock o el pedido entró en conflicto. Revisá el carrito antes de reintentar; no quitamos tus productos.';
      } else if (error.status === 0) {
        kind = 'network';
        message = 'No pudimos conectarnos con el servidor. Conservamos tu carrito; revisá tu conexión y reintentá.';
      }
    }

    this.submitErrorKind.set(kind);
    this.submitError.set(message);
    if (kind === 'verification' || kind === 'conflict') this.notifications.warning(message);
    else this.notifications.error(message);
  }
}
