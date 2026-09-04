import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { Order, OrderService, ShipmentSummary, ShipmentTracking } from '../../core/orders/order.service';
import { BankTransferDetails, BankTransferService } from '../../core/orders/bank-transfer.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { estadoLabel, estadoTono } from '../../core/utils/estado-label';
import { AppBadgeDirective } from '../../shared/ui/app-badge.directive';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';
import { CHECKOUT_WINDOW, CheckoutService } from '../checkout/checkout.service';

@Component({
  selector: 'app-orders',
  imports: [AppBadgeDirective, AppButtonDirective, AppCardDirective, AppFeedbackComponent, CurrencyPipe, DatePipe, RouterLink],
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrdersComponent {
  private readonly service = inject(OrderService);
  private readonly bankTransfers = inject(BankTransferService);
  private readonly checkoutService = inject(CheckoutService);
  private readonly browserWindow = inject(CHECKOUT_WINDOW);
  private readonly notifications = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  readonly orders = signal<Order[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly onlinePaymentAvailable = signal(false);
  readonly loadingCapabilities = signal(false);
  readonly capabilitiesError = signal('');
  readonly payingOrder = signal<number | null>(null);
  readonly paymentError = signal<{ orderId: number; message: string } | null>(null);
  readonly transferDetails = signal<Record<number, BankTransferDetails>>({});
  readonly transferLoading = signal<number[]>([]);
  readonly transferErrors = signal<Record<number, string>>({});
  readonly selectedProofs = signal<Record<number, File>>({});
  readonly proofErrors = signal<Record<number, string>>({});
  readonly uploadingProof = signal<number | null>(null);
  readonly tracking = signal<Record<number, ShipmentTracking>>({});
  readonly trackingLoading = signal<number[]>([]);
  readonly trackingErrors = signal<Record<number, string>>({});
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
    this.paymentError.set(null);
    this.checkoutService.mercadoPago(order.id, order.paymentStatus).pipe(
      finalize(() => this.payingOrder.set(null)),
    ).subscribe({
      next: (payment) => {
        if (!payment.checkoutUrl.trim()) {
          this.paymentError.set({ orderId: order.id, message: 'Mercado Pago no devolvió un enlace válido. Intentá nuevamente.' });
          return;
        }
        this.browserWindow.location.assign(payment.checkoutUrl);
      },
      error: () => this.paymentError.set({ orderId: order.id, message: 'No pudimos iniciar el pago. Podés reintentarlo sin duplicar el intento.' }),
    });
  }

  canPay(order: Order): boolean {
    return this.onlinePaymentAvailable()
      && order.paymentMethod === 'MERCADO_PAGO'
      && order.status === 'PENDING_PAYMENT'
      && ['PENDING', 'REJECTED'].includes(order.paymentStatus)
      && !!order.reservationExpiresAt
      && Number.isFinite(Date.parse(order.reservationExpiresAt))
      && Date.parse(order.reservationExpiresAt) > Date.now();
  }

  isReservationExpired(order: Order): boolean {
    return !order.reservationExpiresAt || !Number.isFinite(Date.parse(order.reservationExpiresAt)) || Date.parse(order.reservationExpiresAt) <= Date.now();
  }

  paymentUnavailableReason(order: Order): string | null {
    if (order.paymentMethod !== 'MERCADO_PAGO') return null;
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
        for (const order of orders.filter((candidate) => candidate.paymentMethod === 'BANK_TRANSFER')) this.loadBankTransfer(order.id);
        for (const order of orders.filter((candidate) => candidate.fulfillmentMethod === 'DELIVERY')) this.loadTracking(order.id);
        this.error.set('');
        const requestedOrder = Number(this.route.snapshot.queryParamMap.get('order'));
        if (requestedOrder > 0) queueMicrotask(() => {
          const target = this.host.nativeElement.querySelector<HTMLElement>(`#order-${requestedOrder}`);
          target?.scrollIntoView({ block: 'start' });
          target?.focus();
        });
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

  shipmentStatusLabel(status: string): string {
    return estadoLabel(status, 'envio');
  }

  shipmentFor(order: Order): ShipmentSummary | null {
    return this.tracking()[order.id] ?? order.shipment;
  }

  loadTracking(orderId: number): void {
    if (this.trackingLoading().includes(orderId)) return;
    this.trackingLoading.update((ids) => [...ids, orderId]);
    this.service.tracking(orderId).pipe(
      finalize(() => this.trackingLoading.update((ids) => ids.filter((id) => id !== orderId))),
    ).subscribe({
      next: (tracking) => {
        this.tracking.update((current) => ({ ...current, [orderId]: tracking }));
        this.trackingErrors.update((current) => ({ ...current, [orderId]: '' }));
      },
      error: (error: unknown) => {
        const unavailable = error instanceof HttpErrorResponse && error.status === 404;
        this.trackingErrors.update((current) => ({
          ...current,
          [orderId]: unavailable
            ? 'El seguimiento estará disponible cuando el correo reciba el envío.'
            : 'No pudimos actualizar el seguimiento del envío.',
        }));
      },
    });
  }

  safeTrackingUrl(value: string | null): string | null {
    if (!value) return null;
    try {
      const url = new URL(value);
      return url.protocol === 'https:' ? url.toString() : null;
    } catch {
      return null;
    }
  }

  loadBankTransfer(orderId: number): void {
    if (this.transferLoading().includes(orderId)) return;
    this.transferLoading.update((ids) => [...ids, orderId]);
    this.bankTransfers.get(orderId).pipe(finalize(() => this.transferLoading.update((ids) => ids.filter((id) => id !== orderId)))).subscribe({
      next: (details) => {
        this.transferDetails.update((current) => ({ ...current, [orderId]: details }));
        this.transferErrors.update((current) => ({ ...current, [orderId]: '' }));
      },
      error: () => this.transferErrors.update((current) => ({ ...current, [orderId]: 'No pudimos cargar los datos de la transferencia.' })),
    });
  }

  selectProof(orderId: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.selectedProofs.update((current) => {
      const next = { ...current };
      delete next[orderId];
      return next;
    });
    const allowedType = ['image/jpeg', 'image/png', 'application/pdf'].includes(file.type);
    const allowedExtension = /\.(?:jpe?g|png|pdf)$/i.test(file.name);
    if (!allowedType && !allowedExtension) {
      this.proofErrors.update((current) => ({ ...current, [orderId]: 'El comprobante debe ser JPEG, PNG o PDF.' }));
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.proofErrors.update((current) => ({ ...current, [orderId]: 'El comprobante supera el máximo de 5 MiB.' }));
      return;
    }
    this.selectedProofs.update((current) => ({ ...current, [orderId]: file }));
    this.proofErrors.update((current) => ({ ...current, [orderId]: '' }));
  }

  uploadProof(orderId: number): void {
    const file = this.selectedProofs()[orderId];
    if (!file || this.uploadingProof() !== null) return;
    this.uploadingProof.set(orderId);
    this.proofErrors.update((current) => ({ ...current, [orderId]: '' }));
    this.bankTransfers.uploadProof(orderId, file).pipe(finalize(() => this.uploadingProof.set(null))).subscribe({
      next: (details) => {
        this.selectedProofs.update((current) => {
          const next = { ...current };
          delete next[orderId];
          return next;
        });
        this.transferDetails.update((current) => ({ ...current, [orderId]: details }));
        const proofStatus = details.proof?.status;
        this.orders.update((orders) => orders.map((order) => order.id !== orderId ? order
          : proofStatus === 'APPROVED' ? { ...order, status: 'PAID', paymentStatus: 'APPROVED' }
            : proofStatus === 'REJECTED' ? { ...order, status: 'CANCELLED', paymentStatus: 'REJECTED', fulfillmentStatus: 'CANCELLED' }
              : { ...order, paymentStatus: 'UNDER_REVIEW' }));
        this.notifications.success(proofStatus === 'APPROVED'
          ? 'La transferencia ya estaba aprobada.'
          : proofStatus === 'REJECTED'
            ? 'El comprobante ya había sido revisado.'
            : 'Comprobante enviado para revisión.');
      },
      error: (error: unknown) => this.proofErrors.update((current) => ({ ...current, [orderId]: this.proofUploadError(error) })),
    });
  }

  canUploadProof(order: Order, transfer: BankTransferDetails): boolean {
    return order.status === 'PENDING_PAYMENT'
      && order.paymentStatus === 'PENDING'
      && transfer.proof === null
      && !!transfer.paymentDueAt
      && Number.isFinite(Date.parse(transfer.paymentDueAt))
      && Date.parse(transfer.paymentDueAt) > Date.now();
  }

  proofStatusLabel(status: string): string {
    return {
      PENDING_REVIEW: 'Comprobante pendiente de revisión',
      APPROVED: 'Transferencia aprobada',
      REJECTED: 'Comprobante rechazado',
      FILE_DELETED: 'Archivo eliminado',
    }[status] ?? status;
  }

  private proofUploadError(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'No pudimos subir el comprobante. Intentá nuevamente.';
    const detail = error.error && typeof error.error === 'object'
      ? (error.error as Record<string, unknown>)['detail']
      : null;
    if (typeof detail === 'string' && detail.includes('processing is busy')) {
      return 'El procesamiento de comprobantes está ocupado. Esperá unos instantes y reintentá.';
    }
    if (error.status === 413) return 'El comprobante supera el máximo de 5 MiB.';
    if (error.status === 409) return 'Este pedido ya no admite otro comprobante o venció el plazo de carga. Actualizá tus pedidos.';
    if (error.status === 400) return 'El archivo no pudo validarse de forma segura. Revisá que sea JPEG, PNG o PDF válido, sin cifrado ni contenido activo.';
    return 'No pudimos subir el comprobante. Intentá nuevamente.';
  }
}
