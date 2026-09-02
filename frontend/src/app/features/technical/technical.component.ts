import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, ElementRef, computed, inject, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, forkJoin, of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { TicketAttachmentService } from '../../core/tickets/ticket-attachment.service';
import { prepareTicketImage, TicketImageError, ticketImageUploadError } from '../../core/tickets/ticket-image';
import { estadoLabel, estadoTono } from '../../core/utils/estado-label';
import { TicketAttachmentGalleryComponent } from '../../shared/ticket-attachment-gallery/ticket-attachment-gallery.component';
import { TicketImagePickerComponent } from '../../shared/ticket-image-picker/ticket-image-picker.component';
import { AppBadgeDirective } from '../../shared/ui/app-badge.directive';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';
import { AppInputComponent } from '../../shared/ui/input/app-input.component';
import { AppSelectComponent, AppSelectOption } from '../../shared/ui/select/app-select.component';
import { AppTextareaComponent } from '../../shared/ui/textarea/app-textarea.component';
import { TechnicalDetails, TechnicalService, TechnicalTicket, Technician, TicketHistory, TicketPriority, TicketStatus } from './technical.service';

type TechnicalSection = 'overview' | 'queue' | 'mine';
interface StatusAction { label: string; status: TicketStatus; danger?: boolean; }

const CLOSED = new Set<TicketStatus>(['DELIVERED', 'CANCELLED']);
const WORKFLOW: TicketStatus[] = ['RECEIVED', 'UNDER_DIAGNOSIS', 'WAITING_FOR_APPROVAL', 'APPROVED', 'IN_REPAIR', 'WAITING_FOR_PARTS', 'READY_FOR_PICKUP', 'DELIVERED'];
const STATUS_FILTERS = ['ALL', 'ACTIVE', ...WORKFLOW, 'CANCELLED'];
const PRIORITY_FILTERS = ['ALL', 'LOW', 'NORMAL', 'HIGH', 'URGENT'];

@Component({
  selector: 'app-technical',
  imports: [AppBadgeDirective, AppButtonDirective, AppCardDirective, AppFeedbackComponent, AppInputComponent, AppSelectComponent, AppTextareaComponent, DatePipe, FormsModule, TicketAttachmentGalleryComponent, TicketImagePickerComponent],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './technical.component.html',
  styleUrl: './technical.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TechnicalComponent {
  private readonly service = inject(TechnicalService);
  private readonly attachments = inject(TicketAttachmentService);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly router = inject(Router, { optional: true });
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly notifications = inject(NotificationService);
  private detailsSnapshot = '';
  private historyRequestId = 0;
  readonly auth = inject(AuthService);
  readonly section = signal<TechnicalSection>('overview');
  readonly sidebarCollapsed = signal(false);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly tickets = signal<TechnicalTicket[]>([]);
  readonly technicians = signal<Technician[]>([]);
  readonly history = signal<TicketHistory[]>([]);
  readonly historyLoading = signal(false);
  readonly historyError = signal('');
  readonly selected = signal<TechnicalTicket | null>(null);
  readonly statusFilter = signal<string>('ACTIVE');
  readonly priorityFilter = signal<string>('ALL');
  readonly search = signal('');
  readonly error = signal('');
  readonly assignmentDraft = signal<number | null>(null);
  readonly attachmentFile = signal<File | null>(null);
  readonly processingAttachment = signal(false);
  readonly uploadingAttachment = signal(false);
  readonly editingBusy = computed(() => this.saving() || this.processingAttachment() || this.uploadingAttachment());
  readonly statuses = WORKFLOW;
  readonly priorities: TicketPriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];
  readonly priorityOptions: readonly AppSelectOption[] = this.priorities.map((priority) => ({ value: priority, label: this.priorityLabel(priority) }));
  readonly technicianOptions = computed<readonly AppSelectOption[]>(() => this.technicians().map((technician) => ({ value: technician.id, label: technician.name })));
  readonly estadoTono = estadoTono;
  statusComment = '';
  diagnosis = '';
  estimatedPrice: number | null = null;
  finalPrice: number | null = null;
  priority: TicketPriority = 'NORMAL';

  readonly activeTickets = computed(() => this.tickets().filter((ticket) => !CLOSED.has(ticket.status)));
  readonly urgentTickets = computed(() => this.activeTickets().filter((ticket) => ['HIGH', 'URGENT'].includes(ticket.priority)).length);
  readonly unassignedTickets = computed(() => this.activeTickets().filter((ticket) => ticket.technicianId === null).length);
  readonly readyTickets = computed(() => this.tickets().filter((ticket) => ticket.status === 'READY_FOR_PICKUP').length);
  readonly myTickets = computed(() => this.activeTickets().filter((ticket) => ticket.technicianId === this.auth.user()?.id));
  readonly recentTickets = computed(() => this.tickets().slice(0, 5));
  readonly filteredTickets = computed(() => {
    const text = this.search().trim().toLowerCase();
    return this.tickets().filter((ticket) => {
      if (this.section() === 'mine' && ticket.technicianId !== this.auth.user()?.id) return false;
      if (this.statusFilter() === 'ACTIVE' && CLOSED.has(ticket.status)) return false;
      if (!['ALL', 'ACTIVE'].includes(this.statusFilter()) && ticket.status !== this.statusFilter()) return false;
      if (this.priorityFilter() !== 'ALL' && ticket.priority !== this.priorityFilter()) return false;
      return !text || `${ticket.id} ${ticket.customerName} ${ticket.deviceType} ${ticket.brand} ${ticket.model} ${ticket.reportedProblem}`.toLowerCase().includes(text);
    });
  });
  readonly intakeChart = computed(() => {
    const days = Array.from({ length: 7 }, (_, index) => {
      const date = new Date();
      date.setHours(0, 0, 0, 0);
      date.setDate(date.getDate() - (6 - index));
      const count = this.tickets().filter((ticket) => new Date(ticket.createdAt).toDateString() === date.toDateString()).length;
      return { label: new Intl.DateTimeFormat('es-AR', { weekday: 'short' }).format(date).replace('.', ''), count };
    });
    const maximum = Math.max(...days.map((day) => day.count), 1);
    return days.map((day) => ({ ...day, height: day.count ? Math.max(15, day.count / maximum * 100) : 4 }));
  });
  readonly teamWorkload = computed(() => this.technicians().map((technician) => ({
    ...technician,
    count: this.activeTickets().filter((ticket) => ticket.technicianId === technician.id).length,
  })).sort((left, right) => right.count - left.count));

  constructor() {
    const section = this.route?.snapshot.queryParamMap.get('section');
    if (this.isSection(section)) this.section.set(section);
    const status = this.route?.snapshot.queryParamMap.get('status');
    const priority = this.route?.snapshot.queryParamMap.get('priority');
    this.statusFilter.set(status && STATUS_FILTERS.includes(status) ? status : 'ACTIVE');
    this.priorityFilter.set(priority && PRIORITY_FILTERS.includes(priority) ? priority : 'ALL');
    if (status && !STATUS_FILTERS.includes(status) || priority && !PRIORITY_FILTERS.includes(priority)) {
      queueMicrotask(() => this.syncUrl({ status: this.statusFilter(), priority: this.priorityFilter() }));
    }
    this.load(true);
  }

  load(force = false): void {
    if (this.loading() || this.editingBusy() || (!force && !this.confirmDiscardDetails())) return;
    this.clearMessages();
    this.loading.set(true);
    const technicians = this.isAdmin() ? this.service.technicians() : of<Technician[]>([]);
    forkJoin({ tickets: this.service.tickets(), technicians }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: ({ tickets, technicians }) => {
        this.tickets.set(tickets);
        this.technicians.set(technicians);
        const requestedId = Number(this.route?.snapshot.queryParamMap.get('ticket'));
        const requested = tickets.find((ticket) => ticket.id === (this.selected()?.id ?? requestedId)) ?? null;
        const selected = this.section() === 'overview' || requested && this.filteredTickets().some((ticket) => ticket.id === requested.id)
          ? requested
          : this.filteredTickets()[0] ?? null;
        if (selected) {
          this.applySelected(selected);
          this.loadHistory(selected.id);
        } else {
          this.selected.set(null);
          this.history.set([]);
        }
      },
      error: () => this.fail('No se pudo cargar la operación técnica.'),
    });
  }

  navigate(section: TechnicalSection): void {
    if (section !== this.section() && !this.confirmDiscardDetails()) return;
    this.section.set(section);
    if (section !== 'overview') this.reconcileSelection();
    this.syncUrl({ section, ticket: section === 'overview' ? null : this.selected()?.id ?? null });
    this.clearMessages();
  }

  sectionTitle(): string { return { overview: 'Resumen técnico', queue: 'Bandeja de servicio', mine: 'Mi mesa de trabajo' }[this.section()]; }
  sectionDescription(): string { return {
    overview: 'Carga operativa, prioridades y actividad reciente.',
    queue: 'Clasificá, asigná y seguí todas las reparaciones.',
    mine: 'Tickets asignados a tu usuario técnico.',
  }[this.section()]; }

  select(ticket: TechnicalTicket, navigate = false): void {
    if (this.editingBusy()) return;
    if (this.selected()?.id !== ticket.id && !this.confirmDiscardDetails()) return;
    if (navigate) this.section.set('queue');
    this.applySelected(ticket);
    this.attachmentFile.set(null);
    this.syncUrl({ section: this.section(), ticket: ticket.id });
    this.loadHistory(ticket.id);
    queueMicrotask(() => {
      if (globalThis.matchMedia?.('(max-width: 850px)').matches) this.host.nativeElement.querySelector<HTMLElement>('.ticket-detail')?.scrollIntoView({ block: 'start' });
    });
  }

  saveDetails(detailsForm?: NgForm): void {
    const ticket = this.selected();
    if (!ticket || !this.canEdit(ticket) || this.editingBusy()) return;
    if (detailsForm?.invalid) {
      this.fail('Revisá los importes de la ficha técnica.');
      queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>('.technical-form :is(app-input, app-select, app-textarea).ng-invalid')?.focus());
      return;
    }
    const invalidPrice = [
      { value: this.estimatedPrice, name: 'estimatedPrice' },
      { value: this.finalPrice, name: 'finalPrice' },
    ].find(({ value }) => value !== null && (!Number.isFinite(Number(value)) || Number(value) < 0));
    if (invalidPrice) {
      this.fail('Los importes deben ser números iguales o mayores que cero.');
      queueMicrotask(() => this.host.nativeElement.querySelector<HTMLElement>(`[name="${invalidPrice.name}"]`)?.focus());
      return;
    }
    const details: TechnicalDetails = {
      priority: this.priority,
      diagnosis: this.diagnosis.trim() || null,
      estimatedPrice: this.numberOrNull(this.estimatedPrice),
      finalPrice: this.numberOrNull(this.finalPrice),
    };
    this.saving.set(true);
    this.clearMessages();
    this.service.updateDetails(ticket.id, details).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (updated) => { this.replace(updated); this.succeed('Ficha técnica actualizada.'); },
      error: () => this.fail('No se pudo guardar la ficha. Verificá la asignación y los importes.'),
    });
  }

  changeStatus(action: StatusAction): void {
    const ticket = this.selected();
    if (!ticket || !this.canEdit(ticket) || this.editingBusy()) return;
    if (action.danger && !confirm(`¿Cancelar el ticket #${ticket.id}?`)) return;
    this.saving.set(true);
    this.clearMessages();
    this.service.updateStatus(ticket.id, action.status, this.statusComment.trim() || null).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (updated) => {
        this.statusComment = '';
        this.replace(updated);
        this.succeed(`Ticket #${ticket.id} actualizado a ${this.statusLabel(updated.status).toLowerCase()}.`);
        this.loadHistory(ticket.id);
      },
      error: () => this.fail('No se pudo cambiar el estado. Revisá la transición o la asignación.'),
    });
  }

  claim(ticket: TechnicalTicket): void {
    if (this.editingBusy()) return;
    this.saving.set(true);
    this.service.claim(ticket.id).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (updated) => { this.replace(updated); this.succeed(`Ticket #${ticket.id} asignado a tu mesa.`); },
      error: () => this.fail('No se pudo tomar el ticket; puede haber sido asignado a otro técnico.'),
    });
  }

  assign(technicianId: number): void {
    const ticket = this.selected();
    if (!ticket || !technicianId || this.editingBusy()) return;
    this.assignmentDraft.set(technicianId);
    this.saving.set(true);
    this.clearMessages();
    this.service.assign(ticket.id, technicianId).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (updated) => { this.replace(updated); this.succeed('Responsable actualizado.'); },
      error: () => { this.assignmentDraft.set(ticket.technicianId); this.fail('No se pudo asignar el técnico.'); },
    });
  }

  async selectAttachment(files: File[], ticket: TechnicalTicket): Promise<void> {
    const file = files[0] ?? null;
    if (this.editingBusy()) return;
    this.attachmentFile.set(null);
    if (!file) return;
    if (!this.canEdit(ticket)) return this.fail('No tenés permisos para agregar imágenes a este ticket.');
    if (ticket.attachments.length >= 10) return this.fail('El ticket ya alcanzó el máximo de 10 imágenes.');
    this.clearMessages();
    this.processingAttachment.set(true);
    try {
      this.attachmentFile.set(await prepareTicketImage(file));
    } catch (error) {
      this.fail(error instanceof TicketImageError ? error.message : 'No pudimos preparar la imagen. Intentá con otro archivo.');
    } finally {
      this.processingAttachment.set(false);
    }
  }

  uploadAttachment(): void {
    const ticket = this.selected();
    const file = this.attachmentFile();
    if (!ticket || !file || !this.canEdit(ticket) || this.editingBusy()) return;
    this.clearMessages();
    this.uploadingAttachment.set(true);
    this.attachments.upload(ticket.id, file).pipe(finalize(() => this.uploadingAttachment.set(false))).subscribe({
      next: (attachment) => {
        this.replace({ ...ticket, attachments: [...ticket.attachments, attachment] });
        this.attachmentFile.set(null);
        this.succeed(`Imagen "${attachment.fileName}" agregada al ticket #${ticket.id}.`);
      },
      error: (error: unknown) => this.fail(ticketImageUploadError(error)),
    });
  }

  statusActions(status: TicketStatus): StatusAction[] {
    switch (status) {
      case 'RECEIVED': return [{ label: 'Iniciar diagnóstico', status: 'UNDER_DIAGNOSIS' }, { label: 'Cancelar', status: 'CANCELLED', danger: true }];
      case 'UNDER_DIAGNOSIS': return [{ label: 'Solicitar aprobación', status: 'WAITING_FOR_APPROVAL' }, { label: 'Iniciar reparación', status: 'IN_REPAIR' }, { label: 'Cancelar', status: 'CANCELLED', danger: true }];
      case 'WAITING_FOR_APPROVAL': return [{ label: 'Registrar aprobación', status: 'APPROVED' }, { label: 'Cancelar', status: 'CANCELLED', danger: true }];
      case 'APPROVED': return [{ label: 'Iniciar reparación', status: 'IN_REPAIR' }, { label: 'Cancelar', status: 'CANCELLED', danger: true }];
      case 'IN_REPAIR': return [{ label: 'Esperando repuesto', status: 'WAITING_FOR_PARTS' }, { label: 'Marcar listo', status: 'READY_FOR_PICKUP' }, { label: 'Cancelar', status: 'CANCELLED', danger: true }];
      case 'WAITING_FOR_PARTS': return [{ label: 'Retomar reparación', status: 'IN_REPAIR' }, { label: 'Cancelar', status: 'CANCELLED', danger: true }];
      case 'READY_FOR_PICKUP': return [{ label: 'Registrar entrega', status: 'DELIVERED' }];
      default: return [];
    }
  }

  canEdit(ticket: TechnicalTicket): boolean { return this.isAdmin() || ticket.technicianId === null || ticket.technicianId === this.auth.user()?.id; }
  isAdmin(): boolean { return this.auth.user()?.roles.includes('ADMIN') ?? false; }
  statusCount(status: TicketStatus): number { return this.tickets().filter((ticket) => ticket.status === status).length; }
  statusLabel(status: TicketStatus | string): string { return estadoLabel(status, 'ticket'); }
  priorityLabel(priority: TicketPriority | string): string { return estadoLabel(priority, 'prioridad-ticket'); }
  age(ticket: TechnicalTicket): string {
    const hours = Math.max(0, Math.floor((Date.now() - new Date(ticket.createdAt).getTime()) / 3_600_000));
    const formatter = new Intl.RelativeTimeFormat('es-AR', { numeric: 'auto' });
    return hours < 24 ? formatter.format(-hours, 'hour') : formatter.format(-Math.floor(hours / 24), 'day');
  }
  progress(ticket: TechnicalTicket): number {
    if (ticket.status === 'CANCELLED') return 100;
    return Math.max(8, (WORKFLOW.indexOf(ticket.status) + 1) / WORKFLOW.length * 100);
  }

  private applySelected(ticket: TechnicalTicket): void {
    this.selected.set(ticket);
    this.assignmentDraft.set(ticket.technicianId);
    this.priority = ticket.priority;
    this.diagnosis = ticket.diagnosis ?? '';
    this.estimatedPrice = ticket.estimatedPrice;
    this.finalPrice = ticket.finalPrice;
    this.statusComment = '';
    this.detailsSnapshot = this.detailsState();
  }
  private replace(ticket: TechnicalTicket): void {
    const statusComment = this.statusComment;
    this.tickets.update((tickets) => tickets.map((current) => current.id === ticket.id ? ticket : current));
    this.applySelected(ticket);
    this.statusComment = statusComment;
    this.detailsSnapshot = this.detailsState('');
  }
  private numberOrNull(value: number | null): number | null { return value === null || value === undefined || value === ('' as unknown as number) ? null : Number(value); }
  private clearMessages(): void { this.error.set(''); }
  private fail(message: string): void { this.error.set(message); }
  private succeed(message: string): void { this.error.set(''); this.notifications.success(message); }
  private detailsState(statusComment = this.statusComment): string { return JSON.stringify({ priority: this.priority, diagnosis: this.diagnosis, estimatedPrice: this.estimatedPrice, finalPrice: this.finalPrice, statusComment }); }
  protected loadHistory(ticketId: number): void {
    const requestId = ++this.historyRequestId;
    this.history.set([]);
    this.historyError.set('');
    this.historyLoading.set(true);
    this.service.history(ticketId).pipe(finalize(() => {
      if (requestId === this.historyRequestId) this.historyLoading.set(false);
    })).subscribe({
      next: (history) => {
        if (requestId === this.historyRequestId && this.selected()?.id === ticketId) this.history.set(history);
      },
      error: () => {
        if (requestId === this.historyRequestId && this.selected()?.id === ticketId) this.historyError.set('No se pudo cargar el historial del ticket.');
      },
    });
  }
  private confirmDiscardDetails(): boolean {
    if (!this.selected() || this.detailsState() === this.detailsSnapshot) return true;
    return confirm('Tenés cambios sin guardar en la ficha técnica. ¿Querés descartarlos?');
  }
  private isSection(value: string | null): value is TechnicalSection { return ['overview', 'queue', 'mine'].includes(value ?? ''); }
  syncFilters(): void { this.syncUrl({ status: this.statusFilter(), priority: this.priorityFilter() }); }
  setStatusFilter(status: string): void { this.statusFilter.set(STATUS_FILTERS.includes(status) ? status : 'ACTIVE'); this.reconcileSelection(); this.syncFilters(); }
  setPriorityFilter(priority: string): void { this.priorityFilter.set(PRIORITY_FILTERS.includes(priority) ? priority : 'ALL'); this.reconcileSelection(); this.syncFilters(); }
  private reconcileSelection(): void {
    const current = this.selected();
    if (current && this.filteredTickets().some((ticket) => ticket.id === current.id)) return;
    const next = this.filteredTickets()[0] ?? null;
    this.selected.set(next);
    this.history.set([]);
    if (next) {
      this.applySelected(next);
      this.loadHistory(next.id);
    }
    this.syncUrl({ ticket: next?.id ?? null });
  }
  private syncUrl(queryParams: Record<string, string | number | null | undefined>): void {
    if (!this.router || !this.route) return;
    void this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge', replaceUrl: true });
  }
}
