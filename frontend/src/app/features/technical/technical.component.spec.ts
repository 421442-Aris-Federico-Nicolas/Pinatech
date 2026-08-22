import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { TicketAttachmentService } from '../../core/tickets/ticket-attachment.service';
import { TechnicalComponent } from './technical.component';
import { TechnicalService, TechnicalTicket } from './technical.service';

describe('TechnicalComponent filters', () => {
  const ticket = (id: number, status: TechnicalTicket['status']): TechnicalTicket => ({
    id,
    status,
    customerName: `Cliente ${id}`,
    customerEmail: `cliente${id}@example.com`,
    technicianId: 5,
    technicianName: 'Técnica',
    deviceType: 'Notebook',
    brand: 'Pina',
    model: `P${id}`,
    reportedProblem: 'No enciende',
    diagnosis: null,
    estimatedPrice: null,
    finalPrice: null,
    priority: 'NORMAL',
    createdAt: '2026-08-20T10:00:00Z',
    updatedAt: '2026-08-20T10:00:00Z',
    attachments: [],
  });

  it('selects a visible ticket when the active filter excludes the current one', async () => {
    const tickets = [ticket(1, 'RECEIVED'), ticket(2, 'READY_FOR_PICKUP')];
    await TestBed.configureTestingModule({
      imports: [TechnicalComponent],
      providers: [
        { provide: TechnicalService, useValue: { tickets: () => of(tickets), history: () => of([]) } },
        { provide: TicketAttachmentService, useValue: { upload: vi.fn() } },
        { provide: AuthService, useValue: { user: () => ({ id: 5, roles: ['TECHNICIAN'] }) } },
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ section: 'queue' }) } } },
      ],
    }).compileComponents();

    const component = TestBed.createComponent(TechnicalComponent).componentInstance;
    expect(component.selected()?.id).toBe(1);

    component.setStatusFilter('READY_FOR_PICKUP');

    expect(component.filteredTickets().map((item) => item.id)).toEqual([2]);
    expect(component.selected()?.id).toBe(2);
  });
});
