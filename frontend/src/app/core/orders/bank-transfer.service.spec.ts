import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { BankTransferService } from './bank-transfer.service';

describe('BankTransferService', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('loads details and uploads one multipart proof with an idempotency key', () => {
    const service = TestBed.inject(BankTransferService);
    const http = TestBed.inject(HttpTestingController);
    service.get(42).subscribe();
    const details = http.expectOne(`${environment.apiBaseUrl}/orders/42/bank-transfer`);
    expect(details.request.method).toBe('GET');
    details.flush({});

    const file = new File(['proof'], 'proof.pdf', { type: 'application/pdf' });
    service.uploadProof(42, file).subscribe();
    const upload = http.expectOne(`${environment.apiBaseUrl}/orders/42/bank-transfer/proof`);
    expect(upload.request.method).toBe('POST');
    expect((upload.request.body as FormData).get('file')).toBe(file);
    const idempotencyKey = upload.request.headers.get('Idempotency-Key');
    expect(idempotencyKey).toBeTruthy();
    upload.flush({});

    service.uploadProof(42, file).subscribe();
    const retry = http.expectOne(`${environment.apiBaseUrl}/orders/42/bank-transfer/proof`);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(idempotencyKey);
    retry.flush({});
  });
});
