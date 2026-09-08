import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, map, of, switchMap, tap } from 'rxjs';
import { CartService } from '../../core/cart/cart.service';
import { problemDetail } from '../../core/api/problem-detail';
import { GuestCheckoutService, GuestCustomer, GuestDeliveryAddress, GuestOrderEnvelope } from '../../core/guest/guest-checkout.service';
import { FulfillmentMethod, PaymentMethod } from '../../core/orders/order.service';
import { bankTransferPrice, listPrice, priceWithoutNationalTax, roundMoney } from '../../core/payments/payment-pricing';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { hasVisibleColorVariants } from '../../core/utils/product-variant';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';
import { AppInputComponent } from '../../shared/ui/input/app-input.component';
import { AppSelectComponent, AppSelectOption } from '../../shared/ui/select/app-select.component';
import { CHECKOUT_WINDOW, CheckoutCapabilities, CheckoutService, ShippingQuoteOption } from './checkout.service';

const NAME_PATTERN = /^[\p{L}][\p{L} .'-]{0,99}$/u;
const PHONE_PATTERN = /^(?=(?:\D*\d){6,})[+0-9() .-]{6,50}$/;
const DOCUMENT_PATTERN = /^(?:[0-9][ .-]?){6,11}$/;
const POSTAL_PATTERN = /^[A-Za-z0-9 -]{4,12}$/;

export const ARGENTINE_PROVINCES: readonly AppSelectOption[] = [
  { value: 'C', label: 'Capital Federal' }, { value: 'B', label: 'Buenos Aires' },
  { value: 'K', label: 'Catamarca' }, { value: 'H', label: 'Chaco' },
  { value: 'U', label: 'Chubut' }, { value: 'X', label: 'Córdoba' },
  { value: 'W', label: 'Corrientes' }, { value: 'E', label: 'Entre Ríos' },
  { value: 'P', label: 'Formosa' }, { value: 'Y', label: 'Jujuy' },
  { value: 'L', label: 'La Pampa' }, { value: 'F', label: 'La Rioja' },
  { value: 'M', label: 'Mendoza' }, { value: 'N', label: 'Misiones' },
  { value: 'Q', label: 'Neuquén' }, { value: 'R', label: 'Río Negro' },
  { value: 'A', label: 'Salta' }, { value: 'J', label: 'San Juan' },
  { value: 'D', label: 'San Luis' }, { value: 'Z', label: 'Santa Cruz' },
  { value: 'S', label: 'Santa Fe' }, { value: 'G', label: 'Santiago del Estero' },
  { value: 'V', label: 'Tierra del Fuego' }, { value: 'T', label: 'Tucumán' },
];

@Component({
  selector: 'app-guest-checkout',
  imports: [AppButtonDirective, AppCardDirective, AppFeedbackComponent, AppInputComponent, AppSelectComponent,
    CurrencyPipe, DatePipe, PercentPipe, ReactiveFormsModule, RouterLink],
  templateUrl: './guest-checkout.component.html',
  styleUrl: './guest-checkout.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GuestCheckoutComponent {
  readonly imageUrl = resolveApiContentUrl;
  readonly hasVisibleColorVariants = hasVisibleColorVariants;
  readonly provinces = ARGENTINE_PROVINCES;
  readonly cart = inject(CartService);
  readonly guest = inject(GuestCheckoutService);
  private readonly checkout = inject(CheckoutService);
  private readonly browserWindow = inject(CHECKOUT_WINDOW);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly form = this.fb.group({
    customer: this.fb.group({
      firstName: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(NAME_PATTERN)]],
      lastName: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(NAME_PATTERN)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      phone: ['', [Validators.required, Validators.maxLength(50), Validators.pattern(PHONE_PATTERN)]],
      documentNumber: ['', [Validators.required, Validators.maxLength(50), Validators.pattern(DOCUMENT_PATTERN)]],
    }),
    address: this.fb.group({
      street: ['', [Validators.required, Validators.maxLength(150)]],
      streetNumber: ['', [Validators.required, Validators.maxLength(30)]],
      floorApartment: ['', [Validators.maxLength(50)]],
      locality: ['', [Validators.required, Validators.maxLength(120)]],
      province: ['', [Validators.required]],
      postalCode: ['', [Validators.required, Validators.maxLength(12), Validators.pattern(POSTAL_PATTERN)]],
      reference: ['', [Validators.maxLength(300)]],
    }),
  });

  readonly checkoutStep = signal<'SHIPPING' | 'PAYMENT'>('SHIPPING');
  readonly capabilities = signal<CheckoutCapabilities | null>(null);
  readonly selectedFulfillmentMethod = signal<FulfillmentMethod | null>(null);
  readonly selectedPickupCode = signal('');
  readonly pickupAccepted = signal(false);
  readonly shippingQuotes = signal<ShippingQuoteOption[]>([]);
  readonly selectedShippingQuoteId = signal('');
  readonly quoteClock = signal(Date.now());
  readonly selectedPaymentMethod = signal<PaymentMethod | null>('BANK_TRANSFER');
  readonly loadingSession = signal(true);
  readonly reconciling = signal(false);
  readonly loadingCapabilities = signal(false);
  readonly loadingQuotes = signal(false);
  readonly checkingEligibility = signal(false);
  readonly submitting = signal(false);
  readonly requestingCode = signal(false);
  readonly confirmingCode = signal(false);
  readonly verificationCode = signal('');
  readonly capabilitiesError = signal('');
  readonly reconciliationError = signal('');
  readonly quoteError = signal('');
  readonly submitError = signal('');
  readonly sessionError = signal('');
  readonly verificationError = signal('');
  readonly verificationNotice = signal('');
  readonly stepAnnouncement = signal('');
  readonly accountRequired = signal(false);
  readonly createdPublicId = signal<string | null>(null);
  readonly formVersion = signal(0);
  private readonly eligibleEmail = signal('');
  readonly selectedPickup = computed(() => this.capabilities()?.pickupLocations
    .find((location) => location.code === this.selectedPickupCode()) ?? null);
  readonly selectedShippingQuote = computed(() => this.shippingQuotes()
    .find((quote) => quote.shippingQuoteId === this.selectedShippingQuoteId()) ?? null);
  readonly quoteExpired = computed(() => {
    this.quoteClock();
    const expiresAt = this.selectedShippingQuote()?.expiresAt;
    return !!expiresAt && (!Number.isFinite(Date.parse(expiresAt)) || Date.parse(expiresAt) <= Date.now());
  });
  readonly guestEmailVerified = computed(() => {
    this.formVersion();
    const session = this.guest.session();
    return session?.emailVerified === true
      && session.verifiedEmail?.trim().toLowerCase() === this.form.controls.customer.controls.email.value.trim().toLowerCase();
  });
  readonly verificationDescriptionIds = computed(() => [
    'guest-code-help',
    this.verificationNotice() ? 'guest-code-notice' : '',
    this.verificationError() ? 'guest-code-error' : '',
  ].filter(Boolean).join(' '));
  readonly transferPricing = computed(() => bankTransferPrice(this.cart.total(), this.capabilities()?.bankTransferDiscountRate ?? .1));
  readonly mercadoPagoPricing = computed(() => listPrice(this.cart.total()));
  readonly selectedPricing = computed(() => this.selectedPaymentMethod() === 'BANK_TRANSFER' ? this.transferPricing() : this.mercadoPagoPricing());
  readonly selectedShippingCost = computed(() => this.selectedFulfillmentMethod() === 'DELIVERY' ? this.selectedShippingQuote()?.amount ?? 0 : 0);
  readonly selectedTotal = computed(() => roundMoney(this.selectedPricing().total + this.selectedShippingCost()));
  readonly priceWithoutTax = priceWithoutNationalTax;
  private quoteExpirationTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    this.initializeSession();
    this.reconcileCheckout();
    this.form.controls.customer.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.formVersion.update((value) => value + 1);
      this.invalidateQuote();
      this.verificationCode.set('');
      this.verificationError.set('');
      this.verificationNotice.set('');
    });
    this.form.controls.customer.controls.email.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.eligibleEmail.set('');
      if (!this.accountRequired()) return;
      this.accountRequired.set(false);
      this.submitError.set('');
    });
    this.form.controls.address.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.formVersion.update((value) => value + 1);
      this.invalidateQuote();
    });
    this.destroyRef.onDestroy(() => clearTimeout(this.quoteExpirationTimer));
  }

  initializeSession(): void {
    if (!this.loadingSession()) this.loadingSession.set(true);
    this.sessionError.set('');
    this.guest.initialize().pipe(finalize(() => this.loadingSession.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      error: () => this.sessionError.set('No pudimos iniciar el checkout invitado. Revisá tu conexión e intentá nuevamente.'),
    });
  }

  reconcileCheckout(): void {
    if (this.reconciling()) return;
    this.reconciling.set(true);
    this.reconciliationError.set('');
    this.cart.reconcile().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((success) => {
      this.reconciling.set(false);
      if (!success) this.reconciliationError.set('No pudimos verificar la disponibilidad del carrito. Reintentá antes de continuar.');
      else if (this.cart.items().length) this.loadCapabilities();
    });
  }

  loadCapabilities(): void {
    if (this.loadingCapabilities()) return;
    this.loadingCapabilities.set(true);
    this.checkout.capabilities().pipe(finalize(() => this.loadingCapabilities.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (capabilities) => {
        this.capabilities.set(capabilities);
        this.capabilitiesError.set('');
        this.selectedPickupCode.set(capabilities.pickupLocations[0]?.code ?? '');
        if (!capabilities.paymentMethods.includes(this.selectedPaymentMethod()!)) this.selectedPaymentMethod.set(null);
      },
      error: () => this.capabilitiesError.set('No pudimos consultar las opciones disponibles. Intentá nuevamente.'),
    });
  }

  selectPickup(event: Event): void {
    this.selectedFulfillmentMethod.set('PICKUP');
    this.selectedPickupCode.set((event.target as HTMLInputElement).value);
    this.pickupAccepted.set(false);
    this.submitError.set('');
  }

  selectDelivery(): void {
    this.selectedFulfillmentMethod.set('DELIVERY');
    this.pickupAccepted.set(false);
    this.submitError.set('');
  }

  setPickupAccepted(event: Event): void { this.pickupAccepted.set((event.target as HTMLInputElement).checked); }

  loadShippingQuotes(): void {
    this.form.controls.customer.markAllAsTouched();
    this.form.controls.address.markAllAsTouched();
    if (this.loadingQuotes() || this.loadingSession() || this.form.controls.customer.invalid || this.form.controls.address.invalid) {
      this.focusFirstInvalid();
      return;
    }
    this.loadingQuotes.set(true);
    this.quoteError.set('');
    const requestedVersion = this.formVersion();
    this.guest.shippingQuotes(this.items(), this.customer(), this.deliveryAddress()).pipe(
      finalize(() => this.loadingQuotes.set(false)), takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: ({ options }) => {
        if (this.formVersion() !== requestedVersion) {
          this.quoteError.set('Los datos cambiaron. Calculá el envío nuevamente.');
          return;
        }
        this.shippingQuotes.set(options);
        this.eligibleEmail.set(this.currentEmail());
        this.selectedShippingQuoteId.set(options[0]?.shippingQuoteId ?? '');
        if (!options.length) this.quoteError.set('No encontramos opciones de envío para esta dirección.');
        this.scheduleQuoteExpiration();
      },
      error: (error: unknown) => {
        if (this.formVersion() !== requestedVersion) return;
        if (!this.handleAccountRequired(error)) {
          this.quoteError.set('No pudimos calcular el envío. Revisá los datos e intentá nuevamente.');
        }
      },
    });
  }

  selectShippingQuote(event: Event): void {
    this.selectedFulfillmentMethod.set('DELIVERY');
    this.selectedShippingQuoteId.set((event.target as HTMLInputElement).value);
    this.scheduleQuoteExpiration();
  }

  continueToPayment(): void {
    this.form.controls.customer.markAllAsTouched();
    if (this.selectedFulfillmentMethod() === 'DELIVERY') this.form.controls.address.markAllAsTouched();
    if (!this.fulfillmentEnabled()) { this.focusFirstInvalid(); return; }
    if (this.checkingEligibility()) return;
    const checkedEmail = this.form.controls.customer.controls.email.value.trim().toLowerCase();
    if (this.eligibleEmail() === checkedEmail) { this.openPaymentStep(); return; }
    this.checkingEligibility.set(true);
    this.submitError.set('');
    this.guest.checkEmailEligibility(checkedEmail).pipe(
      finalize(() => this.checkingEligibility.set(false)), takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: () => {
        if (this.currentEmail() === checkedEmail) {
          this.eligibleEmail.set(checkedEmail);
          this.openPaymentStep();
        }
        else this.submitError.set('El email cambió durante la verificación. Volvé a continuar para revisarlo.');
      },
      error: (error: unknown) => {
        if (this.currentEmail() !== checkedEmail) {
          this.submitError.set('El email cambió durante la verificación. Volvé a continuar para revisarlo.');
          return;
        }
        if (!this.handleAccountRequired(error)) {
          this.submitError.set('No pudimos verificar el email. Revisá tu conexión e intentá nuevamente.');
        }
      },
    });
  }

  private openPaymentStep(): void {
    this.accountRequired.set(false);
    this.checkoutStep.set('PAYMENT');
    this.stepAnnouncement.set('Paso 2 de 2: elegí cómo pagar.');
    this.submitError.set('');
    this.focusElement('#guest-payment-title');
  }

  returnToShipping(): void {
    if (this.submitting()) return;
    this.checkoutStep.set('SHIPPING');
    this.stepAnnouncement.set('Paso 1 de 2: revisá tus datos y la entrega.');
    this.focusElement('#customer-title');
  }

  selectPaymentMethod(event: Event): void {
    this.selectedPaymentMethod.set((event.target as HTMLInputElement).value as PaymentMethod);
    this.submitError.set('');
  }

  requestVerification(): void {
    const firstName = this.form.controls.customer.controls.firstName;
    const email = this.form.controls.customer.controls.email;
    firstName.markAsTouched();
    email.markAsTouched();
    if (firstName.invalid || email.invalid || this.requestingCode() || this.loadingSession()) { this.focusFirstInvalid(); return; }
    const requestedEmail = this.currentEmail();
    this.requestingCode.set(true);
    this.verificationError.set('');
    this.verificationNotice.set('');
    this.guest.requestEmailVerification(requestedEmail, firstName.value.trim()).pipe(
      finalize(() => this.requestingCode.set(false)), takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: () => {
        if (this.currentEmail() !== requestedEmail) return;
        this.eligibleEmail.set(requestedEmail);
        this.verificationNotice.set('Si el email puede recibir mensajes, enviamos un código de 6 dígitos. Revisá también spam.');
        this.focusElement('#guest-code');
      },
      error: (error: unknown) => {
        if (this.currentEmail() !== requestedEmail) return;
        if (this.handleAccountRequired(error)) return;
        this.verificationError.set(error instanceof HttpErrorResponse && error.status === 429
          ? 'Esperá un minuto antes de pedir otro código.' : 'No pudimos enviar el código. Intentá nuevamente.');
      },
    });
  }

  updateVerificationCode(event: Event): void {
    this.verificationCode.set((event.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 6));
    this.verificationError.set('');
  }

  confirmVerification(): void {
    const email = this.form.controls.customer.controls.email;
    if (email.invalid || !/^\d{6}$/.test(this.verificationCode()) || this.confirmingCode()) return;
    const confirmedEmail = this.currentEmail();
    this.confirmingCode.set(true);
    this.verificationError.set('');
    this.guest.confirmEmailVerification(confirmedEmail, this.verificationCode()).pipe(
      finalize(() => this.confirmingCode.set(false)), takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: () => {
        if (this.currentEmail() !== confirmedEmail) return;
        this.eligibleEmail.set(confirmedEmail);
        this.formVersion.update((value) => value + 1);
        this.verificationNotice.set('Email confirmado. Ya podés crear el pedido por transferencia.');
      },
      error: (error: unknown) => {
        if (this.currentEmail() !== confirmedEmail) return;
        if (!this.handleAccountRequired(error)) {
          this.verificationError.set('El código es incorrecto o venció. Revisalo o pedí uno nuevo.');
        }
      },
    });
  }

  submit(): void {
    if (!this.canSubmit() || this.submitting()) return;
    const paymentMethod = this.selectedPaymentMethod()!;
    const fulfillmentMethod = this.selectedFulfillmentMethod()!;
    const pickup = this.selectedPickup();
    this.submitting.set(true);
    this.submitError.set('');
    this.createdPublicId.set(null);
    this.guest.createOrder({
      items: this.items(), paymentMethod, fulfillmentMethod,
      pickupLocationCode: fulfillmentMethod === 'PICKUP' ? pickup!.code : null,
      pickupLocationVersion: fulfillmentMethod === 'PICKUP' ? pickup!.version : null,
      shippingQuoteId: fulfillmentMethod === 'DELIVERY' ? this.selectedShippingQuote()!.shippingQuoteId : null,
      customer: this.customer(),
      deliveryAddress: fulfillmentMethod === 'DELIVERY' ? this.deliveryAddress() : null,
    }).pipe(
      tap((response) => this.createdPublicId.set(response.publicId)),
      switchMap((response) => paymentMethod === 'MERCADO_PAGO'
        ? this.guest.mercadoPago(response.publicId, response.order.paymentStatus).pipe(map((payment) => ({ response, payment })))
        : of({ response, payment: null })),
      finalize(() => this.submitting.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: ({ response, payment }) => this.complete(response, payment?.checkoutUrl ?? null),
      error: (error: unknown) => this.handleSubmitError(error),
    });
  }

  pickupEnabled(capabilities = this.capabilities()): boolean {
    return !!capabilities?.orderRequestsEnabled && capabilities.fulfillmentMethods.includes('PICKUP') && !!this.selectedPickup();
  }

  deliveryEnabled(capabilities = this.capabilities()): boolean {
    return !!capabilities?.orderRequestsEnabled && capabilities.deliveryQuotesEnabled
      && capabilities.deliveryMethods.includes('ZIPNOVA') && capabilities.fulfillmentMethods.includes('DELIVERY');
  }

  mercadoPagoEnabled(capabilities = this.capabilities()): boolean {
    return !!capabilities?.orderRequestsEnabled && capabilities.onlinePaymentsEnabled && capabilities.paymentMethods.includes('MERCADO_PAGO');
  }

  bankTransferEnabled(capabilities = this.capabilities()): boolean {
    return !!capabilities?.orderRequestsEnabled && capabilities.paymentMethods.includes('BANK_TRANSFER');
  }

  fulfillmentEnabled(): boolean {
    this.formVersion();
    if (this.form.controls.customer.invalid) return false;
    return this.selectedFulfillmentMethod() === 'PICKUP'
      ? this.pickupEnabled() && this.pickupAccepted()
      : this.selectedFulfillmentMethod() === 'DELIVERY'
        ? this.deliveryEnabled() && this.form.controls.address.valid && !!this.selectedShippingQuote()
          && !this.quoteExpired()
        : false;
  }

  canSubmit(): boolean {
    const method = this.selectedPaymentMethod();
    const paymentEnabled = method === 'MERCADO_PAGO' ? this.mercadoPagoEnabled()
      : method === 'BANK_TRANSFER' ? this.bankTransferEnabled() && this.guestEmailVerified() : false;
    return !!this.guest.session() && !this.accountRequired() && this.checkoutStep() === 'PAYMENT'
      && this.fulfillmentEnabled() && paymentEnabled;
  }

  private complete(response: GuestOrderEnvelope, checkoutUrl: string | null): void {
    if (checkoutUrl) {
      try {
        const target = new URL(checkoutUrl);
        if (target.protocol !== 'https:') throw new Error('Invalid checkout URL');
        this.cart.clear();
        this.browserWindow.location.assign(target.toString());
        return;
      } catch { this.submitError.set('El medio de pago devolvió una dirección inválida. Podés abrir el pedido para reintentar.'); return; }
    }
    this.cart.clear();
    void this.router.navigate(['/pedido', response.publicId]);
  }

  private customer(): GuestCustomer {
    const value = this.form.controls.customer.getRawValue();
    return Object.fromEntries(Object.entries(value).map(([key, field]) => [key, field.trim()])) as unknown as GuestCustomer;
  }

  private currentEmail(): string {
    return this.form.controls.customer.controls.email.value.trim().toLowerCase();
  }

  private deliveryAddress(): GuestDeliveryAddress {
    const value = this.form.controls.address.getRawValue();
    return { ...Object.fromEntries(Object.entries(value).map(([key, field]) => [key, field.trim()])), countryCode: 'AR' } as unknown as GuestDeliveryAddress;
  }

  private items() { return this.cart.items().map((item) => ({ variantId: item.variant.id, quantity: item.quantity })); }

  private invalidateQuote(): void {
    if (!this.shippingQuotes().length && !this.selectedShippingQuoteId()) return;
    this.shippingQuotes.set([]);
    this.selectedShippingQuoteId.set('');
    clearTimeout(this.quoteExpirationTimer);
    this.quoteError.set('Los datos cambiaron. Calculá el envío nuevamente.');
    if (this.checkoutStep() === 'PAYMENT' && this.selectedFulfillmentMethod() === 'DELIVERY') this.checkoutStep.set('SHIPPING');
  }

  private focusFirstInvalid(): void {
    this.focusElement('[aria-invalid="true"]');
  }

  private focusElement(selector: string): void {
    queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus());
  }

  private scheduleQuoteExpiration(): void {
    clearTimeout(this.quoteExpirationTimer);
    this.quoteClock.set(Date.now());
    const expiresAt = this.selectedShippingQuote()?.expiresAt;
    if (!expiresAt || !Number.isFinite(Date.parse(expiresAt))) return;
    const delay = Math.max(0, Date.parse(expiresAt) - Date.now());
    this.quoteExpirationTimer = setTimeout(() => {
      this.quoteClock.set(Date.now());
      if (this.quoteExpired()) {
        this.quoteError.set('La cotización venció. Calculá el envío nuevamente antes de continuar.');
        this.checkoutStep.set('SHIPPING');
        this.stepAnnouncement.set('La cotización venció. Volvimos al paso de entrega.');
        this.focusElement('#guest-fulfillment-title');
      }
    }, Math.min(delay, 2_147_483_647));
  }

  private handleSubmitError(error: unknown): void {
    if (this.handleAccountRequired(error)) {
      return;
    } else if (this.selectedFulfillmentMethod() === 'DELIVERY' && this.isQuoteError(error)) {
      this.shippingQuotes.set([]);
      this.selectedShippingQuoteId.set('');
      clearTimeout(this.quoteExpirationTimer);
      this.checkoutStep.set('SHIPPING');
      this.quoteError.set('La cotización ya no es válida. Calculá el envío nuevamente.');
      this.submitError.set('');
      this.stepAnnouncement.set('La cotización ya no es válida. Volvimos al paso de entrega.');
      this.focusElement('#guest-fulfillment-title');
    } else if (error instanceof HttpErrorResponse && error.status === 409) {
      this.submitError.set('El stock, la cotización o el pedido cambiaron. Conservamos el carrito para que puedas revisar y reintentar.');
    } else if (error instanceof HttpErrorResponse && error.status === 403 && this.selectedPaymentMethod() === 'BANK_TRANSFER') {
      this.submitError.set('Confirmá el mismo email ingresado antes de crear el pedido por transferencia.');
    } else if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 404)) {
      this.submitError.set('La sesión de compra venció. Actualizala y volvé a intentar; conservamos tu carrito.');
      this.guest.session.set(null);
    } else {
      this.submitError.set('No pudimos completar la compra. Conservamos tu carrito para que puedas reintentar sin duplicar el pedido.');
    }
  }

  private handleAccountRequired(error: unknown): boolean {
    if (!(error instanceof HttpErrorResponse) || error.status !== 409
      || !problemDetail(error)?.type?.endsWith('guest-checkout-account-required')) return false;
    this.accountRequired.set(true);
    this.eligibleEmail.set('');
    this.submitError.set('Este email ya está asociado a una cuenta. Iniciá sesión para continuar con tu carrito; tus productos se conservarán.');
    this.verificationError.set('');
    this.quoteError.set('');
    this.focusElement('#guest-account-required');
    return true;
  }

  private isQuoteError(error: unknown): boolean {
    if (!(error instanceof HttpErrorResponse) || ![400, 404, 409].includes(error.status)) return false;
    const detail = error.error && typeof error.error === 'object'
      ? (error.error as Record<string, unknown>)['detail'] : null;
    return typeof detail === 'string' && /shipping quote|delivery profile/i.test(detail);
  }
}
