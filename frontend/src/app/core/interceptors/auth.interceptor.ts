import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const isApiRequest = request.url === environment.apiBaseUrl || request.url.startsWith(`${environment.apiBaseUrl}/`);
  if (!isApiRequest) {
    return next(request);
  }

  const auth = inject(AuthService);
  const router = inject(Router);
  const authenticate = (source: typeof request) => source.clone({
    headers: auth.getAccessToken() ? source.headers.set('Authorization', `Bearer ${auth.getAccessToken()}`) : source.headers,
    withCredentials: true,
  });
  const isRefreshable = !['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout']
    .some((path) => request.url.startsWith(`${environment.apiBaseUrl}${path}`));

  return next(authenticate(request)).pipe(catchError((error: unknown) => {
    if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !isRefreshable) {
      return throwError(() => error);
    }

    return auth.refreshSession().pipe(
      switchMap(() => next(authenticate(request))),
      catchError((refreshError: unknown) => {
        auth.clearSession();
        void router.navigate(['/login']);
        return throwError(() => refreshError);
      }),
    );
  }));
};
