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
      user: { id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, roles: ['CUSTOMER'] },
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
});
