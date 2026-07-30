import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { TicketAttachment } from '../../core/tickets/ticket-attachment.model';
import { TicketAttachmentService } from '../../core/tickets/ticket-attachment.service';

interface AttachmentPreview {
  attachment: TicketAttachment;
  url: string;
  error: boolean;
}

@Component({
  selector: 'app-ticket-attachment-gallery',
  imports: [DatePipe, DecimalPipe],
  templateUrl: './ticket-attachment-gallery.component.html',
  styleUrl: './ticket-attachment-gallery.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TicketAttachmentGalleryComponent {
  private readonly service = inject(TicketAttachmentService);
  readonly attachments = input.required<TicketAttachment[]>();
  readonly previews = signal<AttachmentPreview[]>([]);

  constructor() {
    effect((onCleanup) => {
      const attachments = this.attachments();
      const subscriptions = new Subscription();
      const urls: string[] = [];
      this.previews.set(attachments.map((attachment) => ({ attachment, url: '', error: false })));

      for (const attachment of attachments) {
        subscriptions.add(this.service.content(attachment.id).subscribe({
          next: (blob) => {
            const url = URL.createObjectURL(blob);
            urls.push(url);
            this.previews.update((items) => items.map((item) => item.attachment.id === attachment.id ? { ...item, url } : item));
          },
          error: () => this.previews.update((items) => items.map((item) => item.attachment.id === attachment.id ? { ...item, error: true } : item)),
        }));
      }

      onCleanup(() => {
        subscriptions.unsubscribe();
        urls.forEach((url) => URL.revokeObjectURL(url));
      });
    });
  }

  roleLabel(role: string): string {
    return { CUSTOMER: 'Cliente', TECHNICIAN: 'Técnico', ADMIN: 'Administración' }[role] ?? role;
  }
}
