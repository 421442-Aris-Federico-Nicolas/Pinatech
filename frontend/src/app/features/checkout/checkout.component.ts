import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, map, of, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartService, CheckoutFulfillment, OrderConfirmation } from '../../core/cart/cart.service';
import { FulfillmentMethod, PaymentMethod } from '../../core/orders/order.service';
import { bankTransferPrice, listPrice, priceWithoutNationalTax, roundMoney } from '../../core/payments/payment-pricing';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { estadoLabel } from '../../core/utils/estado-label';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';
import { CHECKOUT_WINDOW, CheckoutCapabilities, CheckoutService, ShippingQuoteOption } from './checkout.service';

@Component({
  selector: 'app-checkout',
  imports: [AppButtonDirective, AppCardDirective, AppFeedbackComponent, CurrencyPipe, DatePipe, PercentPipe, RouterLink],
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutComponent {
  readonly imageUrl = resolveApiContentUrl;
  readonly cart = inject(CartService);
  private readonly checkoutService = inject(CheckoutService);
  private readonly browserWindow = inject(CHECKOUT_WINDOW);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly capabilities = signal<CheckoutCapabilities | null>(null);
  readonly checkoutStep = signal<'SHIPPING' | 'PAYMENT'>('SHIPPING');
  readonly selectedFulfillmentMethod = signal<FulfillmentMethod | null>(null);
  readonly selectedPickupCode = signal('');
  readonly shippingQuotes = signal<ShippingQuoteOption[]>([]);
  readonly selectedShippingQuoteId = signal('');
  readonly quoteClock = signal(Date.now());
  readonly selectedPaymentMethod = signal<PaymentMethod | null>(
    this.route.snapshot.queryParamMap.get('paymentMethod') === 'BANK_TRANSFER' ? 'BANK_TRANSFER' : null,
  );
  readonly pickupAccepted = signal(false);
  readonly created = signal<OrderConfirmation | null>(this.cart.items().length ? null : this.cart.confirmation());
  readonly reconciling = signal(false);
  readonly loadingCapabilities = signal(false);
  readonly submitting = signal(false);
  readonly resendingVerification = signal(false);
  readonly reconciliationError = signal('');
  readonly capabilitiesError = signal('');
  readonly quoteError = signal('');
  readonly quoteNeedsProfile = signal(false);
  readonly loadingQuotes = signal(false);
  readonly submitError = signal('');
  readonly verificationNotice = signal('');
  readonly submitErrorKind = signal<'verification' | 'pending-transfer' | 'shipping' | 'conflict' | 'network' | 'generic' | null>(null);
  readonly emailVerified = computed(() => this.auth.user()?.emailVerified === true);
  readonly selectedPickup = computed(() => this.capabilities()?.pickupLocations
    .find((location) => location.code === this.selectedPickupCode()) ?? null);
  readonly selectedShippingQuote = computed(() => this.shippingQuotes()
    .find((quote) => quote.shippingQuoteId === this.selectedShippingQuoteId()) ?? null);
  readonly quoteExpired = computed(() => {
    this.quoteClock();
    const expiration = this.selectedShippingQuote()?.expiresAt;
    return !expiration || !Number.isFinite(Date.parse(expiration)) || Date.parse(expiration) <= Date.now();
  });
  readonly transferPricing = computed(() => bankTransferPrice(
    this.cart.total(),
    this.capabilities()?.bankTransferDiscountRate ?? 0.1,
  ));
  readonly mercadoPagoPricing = computed(() => listPrice(this.cart.total()));
  readonly selectedPricing = computed(() => this.selectedPaymentMethod() === 'BANK_TRANSFER'
    ? this.transferPricing()
    : this.mercadoPagoPricing());
  readonly selectedShippingCost = computed(() => this.selectedFulfillmentMethod() === 'DELIVERY'
    && !this.quoteExpired() ? this.selectedShippingQuote()?.amount ?? 0 : 0);
  readonly selectedTotal = computed(() => roundMoney(this.selectedPricing().total + this.selectedShippingCost()));
  readonly priceWithoutTax = priceWithoutNationalTax;
  private quoteExpirationTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    this.reconcileCheckout();
    this.destroyRef.onDestroy(() => clearTimeout(this.quoteExpirationTimer));
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
        if (this.selectedPaymentMethod() && !capabilities.paymentMethods.includes(this.selectedPaymentMethod()!)) {
          this.selectedPaymentMethod.set(null);
        }
        const currentCode = this.selectedPickupCode();
        const selectedCode = capabilities.pickupLocations.some((location) => location.code === currentCode)
          ? currentCode
          : capabilities.pickupLocations[0]?.code ?? '';
        const selectedPickup = capabilities.pickupLocations.find((location) => location.code === selectedCode);
        if (selectedCode !== currentCode || previousPickup?.version !== selectedPickup?.version) {
          this.selectedPickupCode.set(selectedCode);
          this.pickupAccepted.set(false);
        }
        const currentFulfillment = this.selectedFulfillmentMethod();
        const selectedFulfillment = currentFulfillment && capabilities.fulfillmentMethods.includes(currentFulfillment)
          ? currentFulfillment
          : null;
        this.selectedFulfillmentMethod.set(selectedFulfillment);
        this.capabilitiesError.set('');
        if (this.deliveryEnabled(capabilities)) this.loadShippingQuotes();
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
    const paymentMethod = this.selectedPaymentMethod();
    const fulfillmentMethod = this.selectedFulfillmentMethod();
    if (fulfillmentMethod === 'DELIVERY' && this.quoteExpired()) {
      this.markQuoteExpired();
      return;
    }
    if (this.checkoutStep() !== 'PAYMENT' || this.submitting() || !this.cart.items().length
        || !paymentMethod || !fulfillmentMethod || !this.canSubmit(capabilities)) return;

    const fulfillment: CheckoutFulfillment = fulfillmentMethod === 'PICKUP'
      ? { fulfillmentMethod, pickupLocationCode: pickup!.code, pickupLocationVersion: pickup!.version, shippingQuoteId: null }
      : { fulfillmentMethod, pickupLocationCode: null, pickupLocationVersion: null, shippingQuoteId: this.selectedShippingQuote()!.shippingQuoteId };

    this.submitting.set(true);
    this.submitError.set('');
    this.submitErrorKind.set(null);
    this.cart.checkout(paymentMethod, fulfillment).pipe(
      switchMap((order) => paymentMethod === 'MERCADO_PAGO'
        ? this.checkoutService.mercadoPago(order.id, order.paymentStatus).pipe(map((payment) => {
          if (!payment.checkoutUrl.trim()) throw new Error('Mercado Pago did not return a checkout URL.');
          return { order, payment };
        }))
        : of({ order, payment: null })),
      finalize(() => this.submitting.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: ({ order, payment }) => {
        this.cart.completeCheckout(order);
        this.created.set(order);
        if (payment) this.browserWindow.location.assign(payment.checkoutUrl);
        else void this.router.navigate(['/orders'], { queryParams: { order: order.id } });
      },
      error: (error: unknown) => this.handleSubmitError(error),
    });
  }

  selectPickup(event: Event): void {
    this.selectedFulfillmentMethod.set('PICKUP');
    this.selectedPickupCode.set((event.target as HTMLInputElement).value);
    this.pickupAccepted.set(false);
    this.submitError.set('');
    this.submitErrorKind.set(null);
  }

  loadShippingQuotes(): void {
    if (this.loadingQuotes() || !this.deliveryEnabled()) return;
    this.loadingQuotes.set(true);
    this.quoteNeedsProfile.set(false);
    const items = this.cart.items().map((item) => ({ variantId: item.variant.id, quantity: item.quantity }));
    this.checkoutService.shippingQuotes(items).pipe(
      finalize(() => this.loadingQuotes.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: ({ options }) => {
        this.shippingQuotes.set(options);
        const currentId = this.selectedShippingQuoteId();
        this.selectedShippingQuoteId.set(options.some((option) => option.shippingQuoteId === currentId)
          ? currentId : options[0]?.shippingQuoteId ?? '');
        this.quoteError.set(options.length ? '' : 'No encontramos opciones de envío para tu dirección.');
        this.scheduleQuoteExpiration();
      },
      error: (error: unknown) => {
        this.shippingQuotes.set([]);
        this.selectedShippingQuoteId.set('');
        clearTimeout(this.quoteExpirationTimer);
        const detail = error instanceof HttpErrorResponse && error.error && typeof error.error === 'object'
          ? (error.error as Record<string, unknown>)['detail'] : null;
        this.quoteNeedsProfile.set(typeof detail === 'string' && /address|phone|document|street|postal|delivery profile/i.test(detail));
        this.quoteError.set(this.quoteNeedsProfile()
          ? 'Completá tus datos personales y tu dirección de entrega para calcular el envío.'
          : 'No pudimos calcular el envío. Revisá tu dirección o intentá nuevamente.');
      },
    });
  }

  selectShippingQuote(event: Event): void {
    this.selectedFulfillmentMethod.set('DELIVERY');
    this.selectedShippingQuoteId.set((event.target as HTMLInputElement).value);
    this.submitError.set('');
    this.submitErrorKind.set(null);
    this.scheduleQuoteExpiration();
  }

  setPickupAccepted(event: Event): void {
    this.pickupAccepted.set((event.target as HTMLInputElement).checked);
  }

  selectPaymentMethod(event: Event): void {
    this.selectedPaymentMethod.set((event.target as HTMLInputElement).value as PaymentMethod);
    this.submitError.set('');
    this.submitErrorKind.set(null);
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
      },
      error: () => {
        const message = 'No pudimos reenviar el email de verificación. Intentá nuevamente desde Mi perfil.';
        this.verificationNotice.set(message);
      },
    });
  }

  mercadoPagoEnabled(capabilities = this.capabilities()): boolean {
    return !!capabilities?.orderRequestsEnabled
      && capabilities.onlinePaymentsEnabled
      && capabilities.paymentMethods.includes('MERCADO_PAGO');
  }

  bankTransferEnabled(capabilities = this.capabilities()): boolean {
    return !!capabilities?.orderRequestsEnabled && capabilities.paymentMethods.includes('BANK_TRANSFER');
  }

  selectedPaymentEnabled(capabilities = this.capabilities()): boolean {
    const method = this.selectedPaymentMethod();
    return method === 'BANK_TRANSFER' ? this.bankTransferEnabled(capabilities)
      : method === 'MERCADO_PAGO' ? this.mercadoPagoEnabled(capabilities) : false;
  }

  pickupEnabled(capabilities = this.capabilities()): boolean {
    return !!capabilities?.fulfillmentMethods.includes('PICKUP') && !!this.selectedPickup();
  }

  continueToPayment(): void {
    if (this.selectedFulfillmentMethod() === 'DELIVERY' && this.quoteExpired()) {
      this.markQuoteExpired();
      return;
    }
    if (this.reconciling() || this.loadingCapabilities() || !this.fulfillmentEnabled()) return;
    this.submitError.set('');
    this.submitErrorKind.set(null);
    this.checkoutStep.set('PAYMENT');
  }

  returnToShipping(): void {
    if (this.submitting()) return;
    this.checkoutStep.set('SHIPPING');
  }

  deliveryEnabled(capabilities = this.capabilities()): boolean {
    return !!capabilities?.orderRequestsEnabled
      && capabilities.deliveryQuotesEnabled
      && capabilities.deliveryMethods.includes('ZIPNOVA')
      && capabilities.fulfillmentMethods.includes('DELIVERY');
  }

  fulfillmentEnabled(capabilities = this.capabilities()): boolean {
    return this.selectedFulfillmentMethod() === 'PICKUP'
      ? this.pickupEnabled(capabilities) && this.pickupAccepted()
      : this.selectedFulfillmentMethod() === 'DELIVERY'
        ? this.deliveryEnabled(capabilities) && !!this.selectedShippingQuote() && !this.quoteExpired()
        : false;
  }

  canSubmit(capabilities = this.capabilities()): boolean {
    return this.selectedPaymentEnabled(capabilities)
      && this.fulfillmentEnabled(capabilities)
      && this.emailVerified();
  }

  dismissConfirmation(): void {
    this.cart.dismissConfirmation();
    this.created.set(null);
  }

  reservationExpiration(order: OrderConfirmation): string | null {
    return order.reservationExpiresAt && Number.isFinite(Date.parse(order.reservationExpiresAt)) ? order.reservationExpiresAt : null;
  }

  isReservationExpired(order: OrderConfirmation): boolean {
    const expiration = this.reservationExpiration(order);
    return expiration === null || Date.parse(expiration) <= Date.now();
  }

  methodLabel(method: string): string {
    return estadoLabel(method, 'metodo');
  }

  carrierLogo(carrier: string): string | null {
    const normalized = carrier.normalize('NFD').replace(/\p{M}/gu, '').trim().toLowerCase();
    if (/\boca\b/.test(normalized)) return '/Logooca.png';
    if (normalized.includes('correo argentino') || normalized === 'correo') return '/logo-correo.png';
    return null;
  }

  carrierInitials(carrier: string): string {
    return carrier.trim().split(/\s+/).slice(0, 2).map((word) => word[0] ?? '').join('').toUpperCase() || 'ENV';
  }

  private scheduleQuoteExpiration(): void {
    clearTimeout(this.quoteExpirationTimer);
    this.quoteClock.set(Date.now());
    const expiration = this.selectedShippingQuote()?.expiresAt;
    if (!expiration || !Number.isFinite(Date.parse(expiration))) return;
    const delay = Math.max(0, Date.parse(expiration) - Date.now());
    const maximumDelay = 2_147_483_647;
    this.quoteExpirationTimer = setTimeout(
      () => delay > maximumDelay ? this.scheduleQuoteExpiration() : this.markQuoteExpired(),
      Math.min(delay, maximumDelay),
    );
  }

  private markQuoteExpired(): void {
    this.quoteClock.set(Date.now());
    if (this.selectedFulfillmentMethod() === 'DELIVERY') {
      this.quoteError.set('La cotización venció. Calculá el envío nuevamente antes de continuar.');
      this.checkoutStep.set('SHIPPING');
    }
  }

  private handleSubmitError(error: unknown): void {
    let kind: 'verification' | 'pending-transfer' | 'shipping' | 'conflict' | 'network' | 'generic' = 'generic';
    let message = 'No pudimos completar la compra. Conservamos tu carrito para que puedas reintentarlo sin duplicar el pedido.';

    if (error instanceof HttpErrorResponse) {
      const problemType = error.error && typeof error.error === 'object'
        ? (error.error as Record<string, unknown>)['type']
        : null;
      const problemDetail = error.error && typeof error.error === 'object'
        ? (error.error as Record<string, unknown>)['detail']
        : null;
      if (error.status === 403 && typeof problemType === 'string' && problemType.endsWith('email-verification-required')) {
        kind = 'verification';
        message = 'Necesitás verificar tu email antes de comprar. Revisá tu perfil y volvé a intentarlo; conservamos tu carrito.';
      } else if (error.status === 409 && this.selectedPaymentMethod() === 'BANK_TRANSFER'
          && typeof problemDetail === 'string' && problemDetail.includes('pending bank transfer order')) {
        kind = 'pending-transfer';
        message = this.mercadoPagoEnabled()
          ? 'Ya tenés un pedido por transferencia pendiente. Revisalo antes de crear otro o elegí Mercado Pago.'
          : 'Ya tenés un pedido por transferencia pendiente. Revisalo antes de crear otro.';
      } else if (error.status === 409) {
        kind = 'conflict';
        message = 'Cambió el stock o el pedido entró en conflicto. Revisá el carrito antes de reintentar; no quitamos tus productos.';
      } else if (this.selectedFulfillmentMethod() === 'DELIVERY'
          && ((error.status === 400 && typeof problemDetail === 'string' && /shipping quote|delivery profile/i.test(problemDetail))
            || error.status === 502 || error.status === 503)) {
        kind = 'shipping';
        message = 'La cotización venció o cambiaron los datos del envío. Calculá el envío nuevamente; conservamos tu carrito.';
        this.shippingQuotes.set([]);
        this.selectedShippingQuoteId.set('');
      } else if (error.status === 0) {
        kind = 'network';
        message = 'No pudimos conectarnos con el servidor. Conservamos tu carrito; revisá tu conexión y reintentá.';
      }
    }

    this.submitErrorKind.set(kind);
    this.submitError.set(message);
  }
}
