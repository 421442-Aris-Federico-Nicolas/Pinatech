import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { TicketAttachment } from '../../core/tickets/ticket-attachment.model';
import { TicketAttachmentService } from '../../core/tickets/ticket-attachment.service';
import { TicketAttachmentGalleryComponent } from './ticket-attachment-gallery.component';

class IntersectionObserverMock {
  static current: IntersectionObserverMock;
  readonly observe = vi.fn();
  readonly unobserve = vi.fn();
  readonly disconnect = vi.fn();

  constructor(private readonly callback: IntersectionObserverCallback) {
    IntersectionObserverMock.current = this;
  }

  trigger(target: Element): void {
    this.callback([{ isIntersecting: true, target } as IntersectionObserverEntry], this as unknown as IntersectionObserver);
  }
}

describe('TicketAttachmentGalleryComponent', () => {
  const attachment: TicketAttachment = {
    id: 12,
    fileName: 'equipo.jpg',
    contentType: 'image/jpeg',
    sizeBytes: 100,
    uploadedByName: 'Ada',
    uploaderRole: 'CUSTOMER',
    createdAt: '2026-07-29T12:05:00Z',
  };

  afterEach(() => vi.unstubAllGlobals());

  it('downloads an attachment only when its preview approaches the viewport', async () => {
    const content = vi.fn(() => of(new Blob(['image'], { type: 'image/jpeg' })));
    vi.stubGlobal('IntersectionObserver', IntersectionObserverMock);
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:attachment-preview'),
      revokeObjectURL: vi.fn(),
    });
    await TestBed.configureTestingModule({
      imports: [TicketAttachmentGalleryComponent],
      providers: [{ provide: TicketAttachmentService, useValue: { content } }],
    }).compileComponents();

    const fixture = TestBed.createComponent(TicketAttachmentGalleryComponent);
    fixture.componentRef.setInput('attachments', [attachment]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(content).not.toHaveBeenCalled();
    const preview = fixture.nativeElement.querySelector('[data-attachment-id="12"]');
    expect(IntersectionObserverMock.current.observe).toHaveBeenCalledWith(preview);

    IntersectionObserverMock.current.trigger(preview);
    fixture.detectChanges();

    expect(content).toHaveBeenCalledWith(12);
    expect(fixture.nativeElement.querySelector('img')?.getAttribute('src')).toBe('blob:attachment-preview');
  });
});
