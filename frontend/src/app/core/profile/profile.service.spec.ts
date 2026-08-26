import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { ProfileService } from './profile.service';

describe('ProfileService', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('uses the password-confirmed email change payload', () => {
    const service = TestBed.inject(ProfileService);
    const http = TestBed.inject(HttpTestingController);

    service.requestEmailChange({ email: 'new@example.com', currentPassword: 'Password1' }).subscribe();
    const email = http.expectOne(`${environment.apiBaseUrl}/profile/email-change/request`);
    expect(email.request.body).toEqual({ email: 'new@example.com', currentPassword: 'Password1' });
    email.flush(null);
  });
});
