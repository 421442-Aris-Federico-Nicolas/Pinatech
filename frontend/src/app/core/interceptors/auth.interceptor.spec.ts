import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { environment } from '../../../environments/environment';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let token = 'initial-token';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: {
          getAccessToken: () => token,
          refreshSession: () => {
            token = 'refreshed-token';
            return of(void 0);
          },
          clearSession: () => undefined,
        } },
        { provide: Router, useValue: { navigate: () => Promise.resolve(true) } },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('does not forward credentials or bearer tokens to third-party URLs', () => {
    http.get('https://payments.example/checkout').subscribe();

    const request = httpTesting.expectOne('https://payments.example/checkout');
    expect(request.request.withCredentials).toBe(false);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('refreshes once and retries an API request after a 401', () => {
    const url = `${environment.apiBaseUrl}/orders`;
    http.get(url).subscribe();

    const initial = httpTesting.expectOne(url);
    expect(initial.request.headers.get('Authorization')).toBe('Bearer initial-token');
    initial.flush(null, { status: 401, statusText: 'Unauthorized' });

    const retry = httpTesting.expectOne(url);
    expect(retry.request.headers.get('Authorization')).toBe('Bearer refreshed-token');
    retry.flush([]);
  });
});
