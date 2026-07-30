import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { TicketAttachment } from './ticket-attachment.model';

@Injectable({ providedIn: 'root' })
export class TicketAttachmentService {
  private readonly http = inject(HttpClient);

  upload(ticketId: number, file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<TicketAttachment>(`${environment.apiBaseUrl}/tickets/${ticketId}/attachments`, body);
  }

  content(attachmentId: number) {
    return this.http.get(`${environment.apiBaseUrl}/tickets/attachments/${attachmentId}/content`, { responseType: 'blob' });
  }
}
