import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../notifications/notification.service';
import { environment } from '../../../environments/environment';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let token = 'initial-token';
  let refreshFails = false;
  let navigation: { commands: string[]; options: { queryParams?: { returnUrl?: string; reason?: string } } } | null;
  const warning = vi.fn();

  beforeEach(() => {
    token = 'initial-token';
    refreshFails = false;
    navigation = null;
    warning.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: {
          getAccessToken: () => token,
          refreshSession: () => {
            if (refreshFails) return throwError(() => new Error('Refresh failed'));
            token = 'refreshed-token';
            return of(void 0);
          },
          clearSession: () => undefined,
        } },
        { provide: Router, useValue: { url: '/orders?page=2', navigate: (commands: string[], options: { queryParams?: { returnUrl: string } }) => {
          navigation = { commands, options };
          return Promise.resolve(true);
        } } },
        { provide: NotificationService, useValue: { warning } },
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

  it('preserves the current URL when session refresh fails', () => {
    refreshFails = true;
    const url = `${environment.apiBaseUrl}/orders`;
    http.get(url).subscribe({ error: () => undefined });

    httpTesting.expectOne(url).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(navigation).toEqual({ commands: ['/login'], options: { queryParams: { returnUrl: '/orders?page=2', reason: 'session-expired' } } });
    expect(warning).toHaveBeenCalledWith('Tu sesión venció. Ingresá nuevamente para continuar.');
  });
});
