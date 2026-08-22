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
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(toggle.getAttribute('aria-controls')).toBe('ticket-images-7');
    toggle.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-ticket-attachment-gallery')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#ticket-images-7')).not.toBeNull();
    expect(content).toHaveBeenCalledOnce();
    expect(toggle.textContent?.trim()).toBe('Ocultar imágenes');
    expect(toggle.getAttribute('aria-expanded')).toBe('true');

    toggle.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-ticket-attachment-gallery')).toBeNull();
  });

  it('protects unfinished requests and disables request controls while creating', async () => {
    await TestBed.configureTestingModule({
      imports: [TicketsComponent],
      providers: [
        { provide: TicketsService, useValue: { tickets: () => of([]) } },
        { provide: TicketAttachmentService, useValue: { upload: vi.fn() } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketsComponent);
    const component = fixture.componentInstance;
    component.form.reportedProblem = 'No enciende';
    component.createImages.set([{ file: new File(['image'], 'equipo.jpg', { type: 'image/jpeg' }), previewUrl: 'blob:preview' }]);
    component.creating.set(true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement.querySelector('[name="reportedProblem"]') as HTMLTextAreaElement).disabled).toBe(true);
    expect((fixture.nativeElement.querySelector('[name="newTicketImages"]') as HTMLInputElement).disabled).toBe(true);
    expect(Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('.local-previews button'))[0].disabled).toBe(true);

    const event = { preventDefault: vi.fn(), returnValue: undefined } as unknown as BeforeUnloadEvent;
    component.protectUnfinishedRequest(event);
    expect(event.preventDefault).toHaveBeenCalledOnce();
  });

  it('disables an existing ticket file picker and removal while that ticket uploads', async () => {
    await TestBed.configureTestingModule({
      imports: [TicketsComponent],
      providers: [
        { provide: TicketsService, useValue: { tickets: () => of([ticket]) } },
        { provide: TicketAttachmentService, useValue: { upload: vi.fn(), content: () => of(new Blob()) } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(TicketsComponent);
    const component = fixture.componentInstance;
    component.ticketImages.set({ 7: [{ file: new File(['image'], 'extra.jpg', { type: 'image/jpeg' }), previewUrl: 'blob:extra' }] });
    component.uploadingTicket.set(7);
    fixture.detectChanges();

    expect((fixture.nativeElement.querySelector('[name="ticketImages7"]') as HTMLInputElement).disabled).toBe(true);
    expect((fixture.nativeElement.querySelector('.selected-files button') as HTMLButtonElement).disabled).toBe(true);
  });
});
