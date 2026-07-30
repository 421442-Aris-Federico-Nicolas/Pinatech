import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { TicketAttachment } from '../../core/tickets/ticket-attachment.model';

export interface Ticket {
  id: number;
  deviceType: string;
  brand: string;
  model: string;
  reportedProblem: string;
  status: string;
  createdAt: string;
  attachments: TicketAttachment[];
}

export interface CreateTicketPayload {
  deviceType: string;
  brand: string;
  model: string;
  reportedProblem: string;
}

@Injectable({ providedIn: 'root' })
export class TicketsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/tickets`;

  tickets() { return this.http.get<Ticket[]>(`${this.baseUrl}/me`); }
  create(payload: CreateTicketPayload) { return this.http.post<Ticket>(this.baseUrl, payload); }
}
