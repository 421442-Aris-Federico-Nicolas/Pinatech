import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize, forkJoin, of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { TicketAttachmentService } from '../../core/tickets/ticket-attachment.service';
import { TicketAttachmentGalleryComponent } from '../../shared/ticket-attachment-gallery/ticket-attachment-gallery.component';
import { TechnicalDetails, TechnicalService, TechnicalTicket, Technician, TicketHistory, TicketPriority, TicketStatus } from './technical.service';

type TechnicalSection = 'overview' | 'queue' | 'mine';
interface StatusAction { label: string; status: TicketStatus; danger?: boolean; }

const CLOSED = new Set<TicketStatus>(['DELIVERED', 'CANCELLED']);
const WORKFLOW: TicketStatus[] = ['RECEIVED', 'UNDER_DIAGNOSIS', 'WAITING_FOR_APPROVAL', 'APPROVED', 'IN_REPAIR', 'WAITING_FOR_PARTS', 'READY_FOR_PICKUP', 'DELIVERED'];

@Component({
  selector: 'app-technical',
  imports: [DatePipe, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, TicketAttachmentGalleryComponent],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './technical.component.html',
  styleUrl: './technical.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TechnicalComponent {
  private readonly service = inject(TechnicalService);
  private readonly attachments = inject(TicketAttachmentService);
  readonly auth = inject(AuthService);
  readonly section = signal<TechnicalSection>('overview');
  readonly sidebarCollapsed = signal(false);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly tickets = signal<TechnicalTicket[]>([]);
  readonly technicians = signal<Technician[]>([]);
  readonly history = signal<TicketHistory[]>([]);
  readonly selected = signal<TechnicalTicket | null>(null);
  readonly statusFilter = signal<string>('ACTIVE');
  readonly priorityFilter = signal<string>('ALL');
  readonly search = signal('');
  readonly error = signal('');
  readonly success = signal('');
  readonly attachmentFile = signal<File | null>(null);
  readonly uploadingAttachment = signal(false);
  readonly statuses = WORKFLOW;
  readonly priorities: TicketPriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];
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

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    const technicians = this.isAdmin() ? this.service.technicians() : of<Technician[]>([]);
    forkJoin({ tickets: this.service.tickets(), technicians }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: ({ tickets, technicians }) => {
        this.tickets.set(tickets);
        this.technicians.set(technicians);
        const selected = tickets.find((ticket) => ticket.id === this.selected()?.id) ?? null;
        if (selected) this.applySelected(selected);
      },
      error: () => this.fail('No se pudo cargar la operación técnica.'),
    });
  }

  navigate(section: TechnicalSection): void {
    this.section.set(section);
    if (section !== 'overview' && !this.selected()) {
      const first = section === 'mine' ? this.myTickets()[0] : this.activeTickets()[0];
      if (first) this.select(first);
    }
    this.clearMessages();
  }

  sectionTitle(): string { return { overview: 'Resumen técnico', queue: 'Bandeja de servicio', mine: 'Mi mesa de trabajo' }[this.section()]; }
  sectionDescription(): string { return {
    overview: 'Carga operativa, prioridades y actividad reciente.',
    queue: 'Clasificá, asigná y seguí todas las reparaciones.',
    mine: 'Tickets asignados a tu usuario técnico.',
  }[this.section()]; }

  select(ticket: TechnicalTicket, navigate = false): void {
    if (navigate) this.section.set('queue');
    this.applySelected(ticket);
    this.attachmentFile.set(null);
    this.history.set([]);
    this.service.history(ticket.id).subscribe({ next: (history) => this.history.set(history), error: () => this.fail('No se pudo cargar el historial del ticket.') });
  }

  saveDetails(): void {
    const ticket = this.selected();
    if (!ticket || !this.canEdit(ticket) || this.saving()) return;
    const details: TechnicalDetails = {
      priority: this.priority,
      diagnosis: this.diagnosis.trim() || null,
      estimatedPrice: this.numberOrNull(this.estimatedPrice),
      finalPrice: this.numberOrNull(this.finalPrice),
    };
    this.saving.set(true);
    this.clearMessages();
    this.service.updateDetails(ticket.id, details).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (updated) => { this.replace(updated); this.success.set('Ficha técnica actualizada.'); },
      error: () => this.fail('No se pudo guardar la ficha. Verificá la asignación y los importes.'),
    });
  }

  changeStatus(action: StatusAction): void {
    const ticket = this.selected();
    if (!ticket || !this.canEdit(ticket) || this.saving()) return;
    if (action.danger && !confirm(`¿Cancelar el ticket #${ticket.id}?`)) return;
    this.saving.set(true);
    this.clearMessages();
    this.service.updateStatus(ticket.id, action.status, this.statusComment.trim() || null).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.statusComment = '';
        this.success.set(`Ticket #${ticket.id} actualizado a ${this.statusLabel(updated.status).toLowerCase()}.`);
        this.service.history(ticket.id).subscribe((history) => this.history.set(history));
      },
      error: () => this.fail('No se pudo cambiar el estado. Revisá la transición o la asignación.'),
    });
  }

  claim(ticket: TechnicalTicket): void {
    this.saving.set(true);
    this.service.claim(ticket.id).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (updated) => { this.replace(updated); this.success.set(`Ticket #${ticket.id} asignado a tu mesa.`); },
      error: () => this.fail('No se pudo tomar el ticket; puede haber sido asignado a otro técnico.'),
    });
  }

  assign(technicianId: number): void {
    const ticket = this.selected();
    if (!ticket || !technicianId) return;
    this.saving.set(true);
    this.service.assign(ticket.id, technicianId).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (updated) => { this.replace(updated); this.success.set('Responsable actualizado.'); },
      error: () => this.fail('No se pudo asignar el técnico.'),
    });
  }

  selectAttachment(event: Event, ticket: TechnicalTicket): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    this.attachmentFile.set(null);
    if (!file) return;
    if (!this.canEdit(ticket)) return this.fail('No tenés permisos para agregar imágenes a este ticket.');
    if (ticket.attachments.length >= 10) return this.fail('El ticket ya alcanzó el máximo de 10 imágenes.');
    if (!['image/jpeg', 'image/png'].includes(file.type)) return this.fail('La imagen debe ser JPEG o PNG.');
    if (file.size > 5 * 1024 * 1024) return this.fail('La imagen supera el máximo de 5 MiB.');
    this.clearMessages();
    this.attachmentFile.set(file);
  }

  uploadAttachment(): void {
    const ticket = this.selected();
    const file = this.attachmentFile();
    if (!ticket || !file || !this.canEdit(ticket) || this.uploadingAttachment()) return;
    this.clearMessages();
    this.uploadingAttachment.set(true);
    this.attachments.upload(ticket.id, file).pipe(finalize(() => this.uploadingAttachment.set(false))).subscribe({
      next: (attachment) => {
        this.replace({ ...ticket, attachments: [...ticket.attachments, attachment] });
        this.attachmentFile.set(null);
        this.success.set(`Imagen "${attachment.fileName}" agregada al ticket #${ticket.id}.`);
      },
      error: () => this.fail('No se pudo subir la imagen. Verificá el archivo e intentá nuevamente.'),
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
  statusLabel(status: TicketStatus | string): string { return {
    RECEIVED: 'Recibido', UNDER_DIAGNOSIS: 'En diagnóstico', WAITING_FOR_APPROVAL: 'Esperando aprobación', APPROVED: 'Aprobado', IN_REPAIR: 'En reparación', WAITING_FOR_PARTS: 'Esperando repuesto', READY_FOR_PICKUP: 'Listo para retirar', DELIVERED: 'Entregado', CANCELLED: 'Cancelado',
  }[status] ?? status; }
  priorityLabel(priority: TicketPriority | string): string { return { LOW: 'Baja', NORMAL: 'Normal', HIGH: 'Alta', URGENT: 'Urgente' }[priority] ?? priority; }
  age(ticket: TechnicalTicket): string {
    const hours = Math.max(0, Math.floor((Date.now() - new Date(ticket.createdAt).getTime()) / 3_600_000));
    return hours < 24 ? `${hours} h` : `${Math.floor(hours / 24)} d`;
  }
  progress(ticket: TechnicalTicket): number {
    if (ticket.status === 'CANCELLED') return 100;
    return Math.max(8, (WORKFLOW.indexOf(ticket.status) + 1) / WORKFLOW.length * 100);
  }

  private applySelected(ticket: TechnicalTicket): void {
    this.selected.set(ticket);
    this.priority = ticket.priority;
    this.diagnosis = ticket.diagnosis ?? '';
    this.estimatedPrice = ticket.estimatedPrice;
    this.finalPrice = ticket.finalPrice;
  }
  private replace(ticket: TechnicalTicket): void {
    this.tickets.update((tickets) => tickets.map((current) => current.id === ticket.id ? ticket : current));
    this.applySelected(ticket);
  }
  private numberOrNull(value: number | null): number | null { return value === null || value === undefined || value === ('' as unknown as number) ? null : Number(value); }
  private clearMessages(): void { this.error.set(''); this.success.set(''); }
  private fail(message: string): void { this.success.set(''); this.error.set(message); }
}
