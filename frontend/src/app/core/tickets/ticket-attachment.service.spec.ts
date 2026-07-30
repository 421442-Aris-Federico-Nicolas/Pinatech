import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { TicketAttachmentService } from './ticket-attachment.service';

describe('TicketAttachmentService', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('uploads multipart files and downloads private content as a blob', () => {
    const service = TestBed.inject(TicketAttachmentService);
    const http = TestBed.inject(HttpTestingController);
    const file = new File(['image'], 'device.png', { type: 'image/png' });

    service.upload(4, file).subscribe();
    const upload = http.expectOne(`${environment.apiBaseUrl}/tickets/4/attachments`);
    expect(upload.request.body instanceof FormData).toBe(true);
    expect((upload.request.body as FormData).get('file')).toBe(file);
    upload.flush({ id: 9, fileName: 'device.png', contentType: 'image/png', sizeBytes: 5, uploadedByName: 'Ada', uploaderRole: 'CUSTOMER', createdAt: '2026-07-29T12:00:00Z' });

    service.content(9).subscribe();
    const content = http.expectOne(`${environment.apiBaseUrl}/tickets/attachments/9/content`);
    expect(content.request.responseType).toBe('blob');
    content.flush(new Blob(['image'], { type: 'image/png' }));
  });
});
