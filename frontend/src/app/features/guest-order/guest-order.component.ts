import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { GuestCheckoutService } from '../../core/guest/guest-checkout.service';
import { BankTransferDetails } from '../../core/orders/bank-transfer.service';
import { Order, ShipmentTracking } from '../../core/orders/order.service';
import { estadoLabel, estadoTono } from '../../core/utils/estado-label';
import { isDefaultProductVariantName } from '../../core/utils/product-variant';
import { AppBadgeDirective } from '../../shared/ui/app-badge.directive';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';
import { CHECKOUT_WINDOW } from '../checkout/checkout.service';

@Component({
  selector: 'app-guest-order',
  imports: [AppBadgeDirective, AppButtonDirective, AppCardDirective, AppFeedbackComponent, CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './guest-order.component.html',
  styleUrl: './guest-order.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GuestOrderComponent {
  private readonly route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);
  private readonly guest = inject(GuestCheckoutService);
  private readonly browserWindow = inject(CHECKOUT_WINDOW);
  private readonly destroyRef = inject(DestroyRef);
  readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';
  readonly order = signal<Order | null>(null);
  readonly transfer = signal<BankTransferDetails | null>(null);
  readonly tracking = signal<ShipmentTracking | null>(null);
  readonly selectedProof = signal<File | null>(null);
  readonly loading = signal(false);
  readonly sessionLoading = signal(true);
  readonly uploading = signal(false);
  readonly claiming = signal(false);
  readonly paying = signal(false);
  readonly resendingVerification = signal(false);
  readonly claimed = signal(false);
  readonly error = signal('');
  readonly transferError = signal('');
  readonly trackingError = signal('');
  readonly proofError = signal('');
  readonly claimError = signal('');
  readonly paymentError = signal('');
  readonly verificationNotice = signal('');
  readonly matchingAccount = computed(() => {
    const user = this.auth.user();
    const order = this.order();
    return !!user?.roles.includes('CUSTOMER') && !!order
      && user.email.trim().toLowerCase() === order.customerEmail.trim().toLowerCase() && !this.claimed();
  });
  readonly canClaim = computed(() => this.matchingAccount() && this.auth.user()?.emailVerified === true);
  readonly canVerifyForClaim = computed(() => this.matchingAccount() && this.auth.user()?.emailVerified === false);
  readonly isDefaultVariant = isDefaultProductVariantName;

  constructor() {
    this.guest.captureAccessTokenFromFragment(this.publicId);
    this.guest.initialize().pipe(finalize(() => this.sessionLoading.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({ error: () => undefined });
    this.load();
  }

  load(): void {
    if (!this.validPublicId() || this.loading()) {
      if (!this.validPublicId()) this.error.set('El identificador del pedido no es válido.');
      return;
    }
    this.loading.set(true);
    this.error.set('');
    this.guest.getOrder(this.publicId).pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ order }) => {
        this.order.set(order);
        if (order.paymentMethod === 'BANK_TRANSFER') this.loadTransfer();
        if (order.fulfillmentMethod === 'DELIVERY') this.loadTracking();
      },
      error: () => this.error.set('No encontramos este pedido o el acceso ya no es válido. Abrilo desde la misma pestaña o desde el enlace que recibiste por email.'),
    });
  }

  loadTransfer(): void {
    this.transferError.set('');
    this.guest.bankTransfer(this.publicId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (transfer) => this.transfer.set(transfer),
      error: () => this.transferError.set('No pudimos cargar los datos bancarios. Actualizá el pedido para reintentar.'),
    });
  }

  loadTracking(): void {
    this.trackingError.set('');
    this.guest.tracking(this.publicId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (tracking) => this.tracking.set(tracking),
      error: (error: unknown) => this.trackingError.set(error instanceof HttpErrorResponse && error.status === 404
        ? 'El seguimiento estará disponible cuando se genere el envío.' : 'No pudimos consultar el seguimiento en este momento.'),
    });
  }

  selectProof(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    this.selectedProof.set(null);
    this.proofError.set('');
    if (!file) return;
    const allowedType = ['image/jpeg', 'image/png', 'application/pdf'].includes(file.type);
    const allowedExtension = /\.(?:jpe?g|png|pdf)$/i.test(file.name);
    if (!allowedExtension || (file.type !== '' && !allowedType)) { this.proofError.set('El comprobante debe ser JPEG, PNG o PDF.'); return; }
    if (file.size > 5 * 1024 * 1024) { this.proofError.set('El comprobante supera el máximo de 5 MiB.'); return; }
    this.selectedProof.set(file);
  }

  uploadProof(): void {
    const file = this.selectedProof();
    if (!file || this.uploading() || this.sessionLoading()) return;
    this.uploading.set(true);
    this.proofError.set('');
    this.guest.uploadProof(this.publicId, file).pipe(finalize(() => this.uploading.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (transfer) => { this.transfer.set(transfer); this.selectedProof.set(null); this.load(); },
      error: (error: unknown) => this.proofError.set(this.proofUploadError(error)),
    });
  }

  claim(): void {
    if (!this.canClaim() || this.claiming() || this.sessionLoading()) return;
    this.claiming.set(true);
    this.claimError.set('');
    this.guest.claim(this.publicId).pipe(finalize(() => this.claiming.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({ order }) => { this.order.set(order); this.claimed.set(true); },
      error: () => this.claimError.set('No pudimos vincular el pedido. Verificá que tu cuenta use el mismo email confirmado.'),
    });
  }

  resendAccountVerification(): void {
    const user = this.auth.user();
    if (!this.canVerifyForClaim() || !user || this.resendingVerification()) return;
    this.resendingVerification.set(true);
    this.verificationNotice.set('');
    this.auth.requestEmailVerification(user.email).pipe(
      finalize(() => this.resendingVerification.set(false)), takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: () => this.verificationNotice.set('Si la cuenta sigue pendiente, enviamos un nuevo enlace. Después de verificarla, volvé a este pedido.'),
      error: () => this.verificationNotice.set('No pudimos reenviar la verificación. Intentá nuevamente desde Mi perfil.'),
    });
  }

  pay(): void {
    const order = this.order();
    if (!order || !this.canPay(order) || this.paying() || this.sessionLoading()) return;
    this.paying.set(true);
    this.paymentError.set('');
    this.guest.mercadoPago(this.publicId, order.paymentStatus).pipe(
      finalize(() => this.paying.set(false)), takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: ({ checkoutUrl }) => {
        const target = this.safeCheckoutUrl(checkoutUrl);
        if (!target) {
          this.paymentError.set('Mercado Pago no devolvió un enlace HTTPS válido. Intentá nuevamente.');
          return;
        }
        this.browserWindow.location.assign(target);
      },
      error: () => this.paymentError.set('No pudimos iniciar el pago. Podés reintentarlo sin duplicar el intento.'),
    });
  }

  canUploadProof(order: Order, transfer: BankTransferDetails): boolean {
    return order.status === 'PENDING_PAYMENT' && order.paymentStatus === 'PENDING' && transfer.proof === null
      && !!transfer.paymentDueAt && Date.parse(transfer.paymentDueAt) > Date.now();
  }

  canPay(order: Order): boolean {
    const expiresAt = order.reservationExpiresAt ? Date.parse(order.reservationExpiresAt) : Number.NaN;
    return order.paymentMethod === 'MERCADO_PAGO' && order.status === 'PENDING_PAYMENT'
      && (order.paymentStatus === 'PENDING' || order.paymentStatus === 'REJECTED')
      && Number.isFinite(expiresAt) && expiresAt > Date.now();
  }

  safeTrackingUrl(value: string | null): string | null {
    if (!value) return null;
    try { const url = new URL(value); return url.protocol === 'https:' ? url.toString() : null; } catch { return null; }
  }

  private safeCheckoutUrl(value: string | null): string | null {
    if (!value) return null;
    try { const url = new URL(value); return url.protocol === 'https:' ? url.toString() : null; } catch { return null; }
  }

  label(value: string | null, domain: 'pedido' | 'pago' | 'entrega' | 'envio' | 'metodo'): string { return estadoLabel(value, domain); }
  tone(value: string | null, domain: 'pedido' | 'pago' | 'entrega') { return estadoTono(value, domain); }
  proofLabel(status: string): string { return { PENDING_REVIEW: 'Comprobante en revisión', APPROVED: 'Transferencia aprobada', REJECTED: 'Comprobante rechazado', FILE_DELETED: 'Archivo eliminado' }[status] ?? status; }

  private validPublicId(): boolean { return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(this.publicId); }
  private proofUploadError(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'No pudimos subir el comprobante. Intentá nuevamente.';
    if (error.status === 413) return 'El comprobante supera el máximo de 5 MiB.';
    if (error.status === 409) return 'Este pedido ya no admite otro comprobante o venció el plazo de carga.';
    if (error.status === 400) return 'El archivo no pudo validarse de forma segura. Usá un JPEG, PNG o PDF válido.';
    if (error.status === 401 || error.status === 404) return 'El acceso al pedido venció o ya no es válido.';
    return 'No pudimos subir el comprobante. Intentá nuevamente.';
  }
}
