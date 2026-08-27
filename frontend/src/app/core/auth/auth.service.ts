import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, AuthenticatedUser, GenericMessageResponse, LoginRequest, RegisterRequest } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly accessToken = signal<string | null>(null);
  private refreshInFlight?: Observable<void>;
  readonly user = signal<AuthenticatedUser | null>(null);
  readonly isAuthenticated = computed(() => this.accessToken() !== null && this.user() !== null);

  login(request: LoginRequest) {
    return this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, request, { withCredentials: true }).pipe(tap((response) => this.apply(response)));
  }
  register(request: RegisterRequest) {
    return this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/register`, request, { withCredentials: true }).pipe(tap((response) => this.apply(response)));
  }
  requestEmailVerification(email: string) {
    return this.http.post<GenericMessageResponse>(`${environment.apiBaseUrl}/auth/email-verification/request`, { email });
  }
  confirmEmailVerification(token: string) {
    return this.http.post<void>(`${environment.apiBaseUrl}/auth/email-verification/confirm`, { token });
  }
  forgotPassword(email: string) {
    return this.http.post<GenericMessageResponse>(`${environment.apiBaseUrl}/auth/forgot-password`, { email });
  }
  resetPassword(token: string, password: string) {
    return this.http.post<void>(`${environment.apiBaseUrl}/auth/reset-password`, { token, password });
  }
  restoreSession() {
    return this.refreshSession().pipe(catchError(() => {
      this.clear();
      return of(void 0);
    }));
  }
  refreshSession(): Observable<void> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/refresh`, {}, { withCredentials: true }).pipe(
        tap((response) => this.apply(response)),
        map(() => void 0),
        finalize(() => this.refreshInFlight = undefined),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refreshInFlight;
  }
  logout() {
    return this.http.post<void>(`${environment.apiBaseUrl}/auth/logout`, {}, { withCredentials: true }).pipe(tap(() => this.clear()), catchError(() => { this.clear(); return of(void 0); }));
  }
  getAccessToken(): string | null { return this.accessToken(); }
  replaceUser(user: AuthenticatedUser): void { this.user.set(user); }
  clearSession(): void { this.clear(); }
  private apply(response: AuthResponse): void { this.accessToken.set(response.accessToken); this.user.set(response.user); }
  private clear(): void { this.accessToken.set(null); this.user.set(null); }
}
