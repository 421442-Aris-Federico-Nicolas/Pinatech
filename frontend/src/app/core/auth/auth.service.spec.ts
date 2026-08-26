import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { AuthResponse, RegisterRequest } from './auth.models';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  beforeEach(() => TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting()],
  }));

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('registers a customer, keeps cookie credentials and applies the returned session', () => {
    const auth = TestBed.inject(AuthService);
    const http = TestBed.inject(HttpTestingController);
    const account: RegisterRequest = {
      firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', password: 'Secure123', phone: null,
    };
    const response: AuthResponse = {
      accessToken: 'access-token', tokenType: 'Bearer', expiresIn: 900,
      user: { id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, emailVerified: false, roles: ['CUSTOMER'] },
    };

    auth.register(account).subscribe();

    const request = http.expectOne(`${environment.apiBaseUrl}/auth/register`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(account);
    expect(request.request.withCredentials).toBe(true);
    request.flush(response);
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.user()?.id).toBe(7);
    expect(auth.getAccessToken()).toBe('access-token');
  });

  it('uses the account lifecycle endpoints with narrow payloads', () => {
    const auth = TestBed.inject(AuthService);
    const http = TestBed.inject(HttpTestingController);

    auth.requestEmailVerification('ada@example.com').subscribe();
    http.expectOne(`${environment.apiBaseUrl}/auth/email-verification/request`).flush({ message: 'accepted' });
    auth.confirmEmailVerification('verify-token').subscribe();
    expect(http.expectOne(`${environment.apiBaseUrl}/auth/email-verification/confirm`).request.body).toEqual({ token: 'verify-token' });
    auth.forgotPassword('ada@example.com').subscribe();
    expect(http.expectOne(`${environment.apiBaseUrl}/auth/forgot-password`).request.body).toEqual({ email: 'ada@example.com' });
    auth.resetPassword('reset-token', 'Password1').subscribe();
    expect(http.expectOne(`${environment.apiBaseUrl}/auth/reset-password`).request.body).toEqual({ token: 'reset-token', password: 'Password1' });
  });
});
