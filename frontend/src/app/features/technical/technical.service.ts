import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';

export type TicketStatus = 'RECEIVED' | 'UNDER_DIAGNOSIS' | 'WAITING_FOR_APPROVAL' | 'APPROVED' | 'IN_REPAIR' | 'WAITING_FOR_PARTS' | 'READY_FOR_PICKUP' | 'DELIVERED' | 'CANCELLED';
export type TicketPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
export interface TechnicalTicket {
  id: number; customerName: string; customerEmail: string; technicianId: number | null; technicianName: string;
  deviceType: string; brand: string; model: string; serialNumber: string | null; reportedProblem: string;
  diagnosis: string | null; estimatedPrice: number | null; finalPrice: number | null; status: TicketStatus;
  priority: TicketPriority; createdAt: string; updatedAt: string;
}
export interface Technician { id: number; name: string; }
export interface TicketHistory { id: number; previousStatus: TicketStatus | null; newStatus: TicketStatus; comment: string | null; changedBy: string; changedAt: string; }
export interface TechnicalDetails { priority: TicketPriority; diagnosis: string | null; estimatedPrice: number | null; finalPrice: number | null; }

@Injectable({ providedIn: 'root' })
export class TechnicalService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/technical/tickets`;
  tickets() { return this.http.get<TechnicalTicket[]>(this.baseUrl); }
  technicians() { return this.http.get<Technician[]>(`${this.baseUrl}/technicians`); }
  history(id: number) { return this.http.get<TicketHistory[]>(`${this.baseUrl}/${id}/history`); }
  updateStatus(id: number, status: TicketStatus, comment: string | null) { return this.http.patch<TechnicalTicket>(`${this.baseUrl}/${id}/status`, { status, comment }); }
  updateDetails(id: number, details: TechnicalDetails) { return this.http.patch<TechnicalTicket>(`${this.baseUrl}/${id}/details`, details); }
  assign(id: number, technicianId: number) { return this.http.patch<TechnicalTicket>(`${this.baseUrl}/${id}/technician`, { technicianId }); }
  claim(id: number) { return this.http.patch<TechnicalTicket>(`${this.baseUrl}/${id}/claim`, {}); }
}
