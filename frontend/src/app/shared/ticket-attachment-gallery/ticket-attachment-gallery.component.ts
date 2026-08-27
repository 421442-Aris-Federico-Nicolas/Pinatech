import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, input, signal, viewChildren } from '@angular/core';
import { Subscription } from 'rxjs';
import { TicketAttachment } from '../../core/tickets/ticket-attachment.model';
import { TicketAttachmentService } from '../../core/tickets/ticket-attachment.service';
import { AppFeedbackComponent } from '../ui/feedback/app-feedback.component';

interface AttachmentPreview {
  attachment: TicketAttachment;
  url: string;
  error: boolean;
  loading: boolean;
}

@Component({
  selector: 'app-ticket-attachment-gallery',
  imports: [AppFeedbackComponent, DatePipe, DecimalPipe],
  templateUrl: './ticket-attachment-gallery.component.html',
  styleUrl: './ticket-attachment-gallery.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TicketAttachmentGalleryComponent {
  private readonly service = inject(TicketAttachmentService);
  private readonly previewElements = viewChildren<ElementRef<HTMLElement>>('preview');
  private downloads = new Subscription();
  private readonly requested = new Set<number>();
  private readonly urls = new Map<number, string>();
  readonly attachments = input.required<TicketAttachment[]>();
  readonly previews = signal<AttachmentPreview[]>([]);

  constructor() {
    effect((onCleanup) => {
      const attachments = this.attachments();
      this.resetDownloads();
      this.previews.set(attachments.map((attachment) => ({ attachment, url: '', error: false, loading: false })));
      onCleanup(() => this.resetDownloads());
    });

    effect((onCleanup) => {
      const elements = this.previewElements();
      if (!elements.length) return;

      if (typeof IntersectionObserver === 'undefined') {
        elements.forEach((element) => this.request(Number(element.nativeElement.dataset['attachmentId'])));
        return;
      }

      const observer = new IntersectionObserver((entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          observer.unobserve(entry.target);
          this.request(Number((entry.target as HTMLElement).dataset['attachmentId']));
        }
      }, { rootMargin: '240px 0px' });
      elements.forEach((element) => observer.observe(element.nativeElement));
      onCleanup(() => observer.disconnect());
    });
  }

  roleLabel(role: string): string {
    return { CUSTOMER: 'Cliente', TECHNICIAN: 'Técnico', ADMIN: 'Administración' }[role] ?? role;
  }

  retry(attachment: TicketAttachment): void {
    this.previews.update((items) => items.map((item) => item.attachment.id === attachment.id ? { ...item, error: false, loading: true } : item));
    this.downloads.add(this.download(attachment));
  }

  private request(attachmentId: number): void {
    const item = this.previews().find((preview) => preview.attachment.id === attachmentId);
    if (!item || item.url || item.loading || this.requested.has(attachmentId)) return;
    this.requested.add(attachmentId);
    this.previews.update((items) => items.map((preview) => preview.attachment.id === attachmentId ? { ...preview, loading: true } : preview));
    this.downloads.add(this.download(item.attachment));
  }

  private download(attachment: TicketAttachment): Subscription {
    return this.service.content(attachment.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const previousUrl = this.urls.get(attachment.id);
        if (previousUrl) URL.revokeObjectURL(previousUrl);
        this.urls.set(attachment.id, url);
        this.previews.update((items) => items.map((item) => item.attachment.id === attachment.id ? { ...item, url, error: false, loading: false } : item));
      },
      error: () => this.previews.update((items) => items.map((item) => item.attachment.id === attachment.id ? { ...item, error: true, loading: false } : item)),
    });
  }

  private resetDownloads(): void {
    this.downloads.unsubscribe();
    this.downloads = new Subscription();
    this.urls.forEach((url) => URL.revokeObjectURL(url));
    this.urls.clear();
    this.requested.clear();
  }
}
