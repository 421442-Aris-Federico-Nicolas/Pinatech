import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { TicketAttachmentService } from '../../core/tickets/ticket-attachment.service';
import { Ticket, TicketsService } from './tickets.service';
import { TicketsComponent } from './tickets.component';

describe('TicketsComponent attachment gallery', () => {
  const ticket: Ticket = {
    id: 7,
    deviceType: 'Notebook',
    brand: 'Pina',
    model: 'Pro',
    reportedProblem: 'No enciende',
    status: 'RECEIVED',
    createdAt: '2026-07-29T12:00:00Z',
    attachments: [{
      id: 12,
      fileName: 'equipo.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 100,
      uploadedByName: 'Ada',
      uploaderRole: 'CUSTOMER',
      createdAt: '2026-07-29T12:05:00Z',
    }],
  };

  it('mounts and downloads a ticket gallery only while expanded', async () => {
    const content = vi.fn(() => of(new Blob(['image'], { type: 'image/jpeg' })));
    await TestBed.configureTestingModule({
      imports: [TicketsComponent],
      providers: [
        { provide: TicketsService, useValue: { tickets: () => of([ticket]) } },
        { provide: TicketAttachmentService, useValue: { content, upload: vi.fn() } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(TicketsComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-ticket-attachment-gallery')).toBeNull();
    expect(content).not.toHaveBeenCalled();

    const toggle = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
      .find((button) => button.textContent?.trim() === 'Ver imágenes')!;
    toggle.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-ticket-attachment-gallery')).not.toBeNull();
    expect(content).toHaveBeenCalledOnce();
    expect(toggle.textContent?.trim()).toBe('Ocultar imágenes');

    toggle.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-ticket-attachment-gallery')).toBeNull();
  });
});
