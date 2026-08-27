import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, concatMap, finalize, from, map, of, toArray } from 'rxjs';
import { TicketAttachment } from '../../core/tickets/ticket-attachment.model';
import { TicketAttachmentService } from '../../core/tickets/ticket-attachment.service';
import { estadoLabel, estadoTono } from '../../core/utils/estado-label';
import { summarizeUploadResults, UploadResult } from '../../core/utils/upload-results';
import { TicketAttachmentGalleryComponent } from '../../shared/ticket-attachment-gallery/ticket-attachment-gallery.component';
import { AppBadgeDirective } from '../../shared/ui/app-badge.directive';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';
import { AppInputComponent } from '../../shared/ui/input/app-input.component';
import { AppSelectComponent, AppSelectOption } from '../../shared/ui/select/app-select.component';
import { AppTextareaComponent } from '../../shared/ui/textarea/app-textarea.component';
import { CreateTicketPayload, Ticket, TicketsService } from './tickets.service';

interface PendingImage { file: File; previewUrl: string; }
interface TicketMessage { text: string; tone: 'success' | 'warning' | 'error'; }

@Component({
  imports: [AppBadgeDirective, AppButtonDirective, AppCardDirective, AppFeedbackComponent, AppInputComponent, AppSelectComponent, AppTextareaComponent, DatePipe, FormsModule, TicketAttachmentGalleryComponent],
  templateUrl: './tickets.component.html',
  styleUrl: './tickets.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TicketsComponent {
  private readonly service = inject(TicketsService);
  private readonly attachments = inject(TicketAttachmentService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly router = inject(Router, { optional: true });
  readonly tickets = signal<Ticket[]>([]);
  readonly loading = signal(true);
  readonly creating = signal(false);
  readonly uploadingTicket = signal<number | null>(null);
  readonly error = signal('');
  readonly success = signal('');
  readonly successTone = signal<'success' | 'warning'>('success');
  readonly listError = signal('');
  readonly ticketMessages = signal<Record<number, TicketMessage>>({});
  readonly createImages = signal<PendingImage[]>([]);
  readonly ticketImages = signal<Record<number, PendingImage[]>>({});
  readonly expandedTickets = signal<Set<number>>(new Set());
  readonly deviceTypes = ['Consola', 'Notebook', 'PC de escritorio'];
  readonly deviceTypeOptions: readonly AppSelectOption[] = [
    { value: '', label: 'Seleccioná un equipo', disabled: true },
    ...this.deviceTypes.map((deviceType) => ({ value: deviceType, label: deviceType })),
  ];
  readonly estadoTono = estadoTono;
  readonly form = this.emptyForm();

  constructor() {
    const expanded = Number(this.route?.snapshot.queryParamMap.get('ticket'));
    if (expanded > 0) this.expandedTickets.set(new Set([expanded]));
    this.destroyRef.onDestroy(() => {
      this.revoke(this.createImages());
      Object.values(this.ticketImages()).forEach((images) => this.revoke(images));
    });
    this.load();
  }

  load(): void {
    this.listError.set('');
    this.loading.set(true);
    this.service.tickets().pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (tickets) => this.tickets.set(tickets),
      error: () => this.listError.set('No se pudieron cargar tus solicitudes. Revisá tu conexión e intentá nuevamente.'),
    });
  }

  create(): void {
    if (this.creating()) return;
    const requiresBrand = this.requiresBrand(this.form.deviceType);
    const payload: CreateTicketPayload = {
      deviceType: this.form.deviceType.trim(),
      brand: requiresBrand ? this.form.brand.trim() : '',
      model: this.form.deviceType === 'PC de escritorio' ? '' : this.form.model.trim(),
      reportedProblem: this.form.reportedProblem.trim(),
    };
    if (!payload.deviceType || !payload.reportedProblem) return this.invalid('Completá equipo y problema informado.');
    if (requiresBrand && !payload.brand) return this.invalid('Completá la marca del equipo.');
    if (payload.deviceType !== 'PC de escritorio' && !payload.model) return this.invalid('Completá el modelo del equipo.');

    this.clearMessages();
    this.creating.set(true);
    const images = [...this.createImages()];
    this.service.create(payload).subscribe({
      next: (ticket) => {
        if (!images.length) {
          this.creating.set(false);
          this.completeCreate(ticket, []);
          return;
        }
        this.upload(ticket.id, images).pipe(finalize(() => this.creating.set(false))).subscribe((results) => this.completeCreate(ticket, results));
      },
      error: () => { this.creating.set(false); this.fail('No se pudo crear la solicitud. Revisá los datos e intentá nuevamente.'); },
    });
  }

  selectCreateImages(event: Event): void {
    if (this.creating()) return;
    const files = this.readFiles(event);
    if (!files.length || !this.validate(files, 10 - this.createImages().length)) return;
    this.createImages.update((current) => [...current, ...this.previews(files)]);
    this.clearMessages();
  }

  selectTicketImages(ticket: Ticket, event: Event): void {
    if (this.uploadingTicket() === ticket.id) return;
    const files = this.readFiles(event);
    const current = this.pendingFor(ticket.id);
    if (!files.length || !this.validate(files, 10 - ticket.attachments.length - current.length)) return;
    this.ticketImages.update((records) => ({ ...records, [ticket.id]: [...current, ...this.previews(files)] }));
    this.clearMessages();
  }

  removeCreateImage(index: number): void {
    if (this.creating()) return;
    this.removePreview(this.createImages(), index);
    this.createImages.update((images) => images.filter((_, current) => current !== index));
  }

  removeTicketImage(ticketId: number, index: number): void {
    if (this.uploadingTicket() === ticketId) return;
    const images = this.pendingFor(ticketId);
    this.removePreview(images, index);
    this.ticketImages.update((records) => ({ ...records, [ticketId]: images.filter((_, current) => current !== index) }));
  }

  uploadToTicket(ticket: Ticket): void {
    const images = [...this.pendingFor(ticket.id)];
    if (!images.length || this.uploadingTicket() !== null) return;
    this.clearMessages();
    this.clearTicketMessage(ticket.id);
    this.uploadingTicket.set(ticket.id);
    this.upload(ticket.id, images).pipe(finalize(() => this.uploadingTicket.set(null))).subscribe((results) => {
      const { uploaded, succeeded, failed } = summarizeUploadResults(results);
      const updated = { ...ticket, attachments: [...ticket.attachments, ...uploaded] };
      this.tickets.update((tickets) => tickets.map((current) => current.id === ticket.id ? updated : current));
      this.revoke(succeeded);
      this.ticketImages.update((records) => ({ ...records, [ticket.id]: failed }));
      const message: TicketMessage = uploaded.length === images.length
        ? { text: `${uploaded.length} ${uploaded.length === 1 ? 'imagen agregada' : 'imágenes agregadas'} al ticket #${ticket.id}.`, tone: 'success' }
        : uploaded.length
          ? { text: `Se agregaron ${uploaded.length} de ${images.length} imágenes al ticket #${ticket.id}. Podés volver a intentar las restantes.`, tone: 'warning' }
          : { text: `No se pudo agregar ninguna de las ${images.length} imágenes al ticket #${ticket.id}. Revisá tu conexión y reintentá.`, tone: 'error' };
      this.ticketMessages.update((messages) => ({ ...messages, [ticket.id]: message }));
    });
  }

  pendingFor(ticketId: number): PendingImage[] { return this.ticketImages()[ticketId] ?? []; }
  requiresBrand(deviceType: string): boolean { return deviceType === 'Consola' || deviceType === 'Notebook'; }

  imagesExpanded(ticketId: number): boolean { return this.expandedTickets().has(ticketId); }

  toggleImages(ticketId: number): void {
    this.expandedTickets.update((expanded) => expanded.has(ticketId) ? new Set() : new Set([ticketId]));
    this.syncUrl(this.imagesExpanded(ticketId) ? ticketId : null);
  }

  createLimitReached(): boolean { return this.createImages().length >= 10; }
  attachmentLimitReached(ticket: Ticket): boolean { return ticket.attachments.length + this.pendingFor(ticket.id).length >= 10; }
  statusLabel(status: string): string { return estadoLabel(status, 'ticket'); }

  @HostListener('window:beforeunload', ['$event'])
  protectUnfinishedRequest(event: BeforeUnloadEvent): void {
    const hasFormValues = Object.values(this.form).some((value) => value.trim());
    const hasLocalImages = this.createImages().length > 0 || Object.values(this.ticketImages()).some((images) => images.length > 0);
    if (!hasFormValues && !hasLocalImages) return;
    event.preventDefault();
    event.returnValue = '';
  }

  private upload(ticketId: number, images: PendingImage[]) {
    return from(images).pipe(
      concatMap((image) => this.attachments.upload(ticketId, image.file).pipe(
        map((uploaded): UploadResult<PendingImage, TicketAttachment> => ({ pending: image, uploaded })),
        catchError(() => of<UploadResult<PendingImage, TicketAttachment>>({ pending: image, uploaded: null })),
      )),
      toArray(),
    );
  }

  private completeCreate(ticket: Ticket, results: UploadResult<PendingImage, TicketAttachment>[]): void {
    const { uploaded, succeeded, failed } = summarizeUploadResults(results);
    const attempted = results.length;
    const updated = { ...ticket, attachments: [...(ticket.attachments ?? []), ...uploaded] };
    this.tickets.update((tickets) => [updated, ...tickets.filter((current) => current.id !== ticket.id)]);
    this.revoke(succeeded);
    this.createImages.set([]);
    if (failed.length) this.ticketImages.update((records) => ({ ...records, [ticket.id]: failed }));
    Object.assign(this.form, this.emptyForm());
    this.successTone.set(failed.length ? 'warning' : 'success');
    this.success.set(!attempted
      ? `Solicitud #${ticket.id} creada.`
      : uploaded.length === attempted
        ? `Solicitud #${ticket.id} creada con ${uploaded.length} ${uploaded.length === 1 ? 'imagen' : 'imágenes'}.`
        : `La solicitud #${ticket.id} se creó correctamente, pero solo se subieron ${uploaded.length} de ${attempted} imágenes. Las restantes quedaron seleccionadas para reintentar con "Subir imágenes".`);
  }

  private validate(files: File[], available: number): boolean {
    if (files.length > Math.max(0, available)) {
      this.fail(`Podés seleccionar hasta ${Math.max(0, available)} imágenes más; el máximo es 10 por ticket.`);
      return false;
    }
    const invalid = files.find((file) => !['image/jpeg', 'image/png'].includes(file.type));
    if (invalid) { this.fail(`“${invalid.name}” no es JPEG ni PNG.`); return false; }
    const oversized = files.find((file) => file.size > 5 * 1024 * 1024);
    if (oversized) { this.fail(`“${oversized.name}” supera el máximo de 5 MiB.`); return false; }
    return true;
  }

  private readFiles(event: Event): File[] {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    return files;
  }
  private previews(files: File[]): PendingImage[] { return files.map((file) => ({ file, previewUrl: URL.createObjectURL(file) })); }
  private removePreview(images: PendingImage[], index: number): void { const image = images[index]; if (image) URL.revokeObjectURL(image.previewUrl); }
  private revoke(images: PendingImage[]): void { images.forEach((image) => URL.revokeObjectURL(image.previewUrl)); }
  private emptyForm() { return { deviceType: '', brand: '', model: '', reportedProblem: '' }; }
  private clearMessages(): void { this.error.set(''); this.success.set(''); this.successTone.set('success'); }
  private fail(message: string): void { this.success.set(''); this.error.set(message); }
  private clearTicketMessage(ticketId: number): void {
    this.ticketMessages.update((messages) => {
      const next = { ...messages };
      delete next[ticketId];
      return next;
    });
  }
  private invalid(message: string): void {
    this.fail(message);
    queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('form :is(app-input, app-select, app-textarea).ng-invalid')?.focus());
  }
  private syncUrl(ticket: number | null): void {
    if (!this.router || !this.route) return;
    void this.router.navigate([], { relativeTo: this.route, queryParams: { ticket }, queryParamsHandling: 'merge', replaceUrl: true });
  }
}
