import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, AuthenticatedUser, LoginRequest } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly accessToken = signal<string | null>(null);
  private refreshInFlight?: Observable<void>;
  readonly user = signal<AuthenticatedUser | null>(null);
  readonly isAuthenticated = computed(() => this.accessToken() !== null && this.user() !== null);

  login(request: LoginRequest) {
    return this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, request).pipe(tap((response) => this.apply(response)));
  }
  restoreSession() {
    return this.refreshSession().pipe(catchError(() => {
      this.clear();
      return of(void 0);
    }));
  }
  refreshSession(): Observable<void> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/refresh`, {}).pipe(
        tap((response) => this.apply(response)),
        map(() => void 0),
        finalize(() => this.refreshInFlight = undefined),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refreshInFlight;
  }
  logout() {
    return this.http.post<void>(`${environment.apiBaseUrl}/auth/logout`, {}).pipe(tap(() => this.clear()), catchError(() => { this.clear(); return of(void 0); }));
  }
  getAccessToken(): string | null { return this.accessToken(); }
  clearSession(): void { this.clear(); }
  private apply(response: AuthResponse): void { this.accessToken.set(response.accessToken); this.user.set(response.user); }
  private clear(): void { this.accessToken.set(null); this.user.set(null); }
}
