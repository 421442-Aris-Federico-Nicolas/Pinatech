import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { TicketsService } from './tickets.service';

describe('TicketsService', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('loads the authenticated customer tickets', () => {
    TestBed.inject(TicketsService).tickets().subscribe();

    const request = TestBed.inject(HttpTestingController).expectOne(`${environment.apiBaseUrl}/tickets/me`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('creates a ticket as JSON before attachments are uploaded', () => {
    const payload = { deviceType: 'Notebook', brand: 'Dell', model: 'XPS', reportedProblem: 'No enciende' };
    TestBed.inject(TicketsService).create(payload).subscribe();

    const request = TestBed.inject(HttpTestingController).expectOne(`${environment.apiBaseUrl}/tickets`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ id: 1, ...payload, status: 'RECEIVED', createdAt: '2026-07-29T12:00:00Z', attachments: [] });
  });
});
