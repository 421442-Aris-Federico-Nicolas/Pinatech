import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../notifications/notification.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const isApiRequest = request.url === environment.apiBaseUrl || request.url.startsWith(`${environment.apiBaseUrl}/`);
  if (!isApiRequest) {
    return next(request);
  }

  const auth = inject(AuthService);
  const router = inject(Router);
  const notifications = inject(NotificationService);
  const authenticate = (source: typeof request) => source.clone({
    headers: auth.getAccessToken() ? source.headers.set('Authorization', `Bearer ${auth.getAccessToken()}`) : source.headers,
    withCredentials: true,
  });
  const requestPath = request.url.slice(environment.apiBaseUrl.length).split(/[?#]/, 1)[0];
  const isRefreshable = !['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout']
    .includes(requestPath);

  return next(authenticate(request)).pipe(catchError((error: unknown) => {
    if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !isRefreshable) {
      return throwError(() => error);
    }

    return auth.refreshSession().pipe(
      catchError((refreshError: unknown) => {
        auth.clearSession();
        notifications.warning('Tu sesión venció. Ingresá nuevamente para continuar.');
        const returnUrl = router.url.startsWith('/login') ? undefined : router.url;
        void router.navigate(['/login'], { queryParams: returnUrl ? { returnUrl, reason: 'session-expired' } : { reason: 'session-expired' } });
        return throwError(() => refreshError);
      }),
      switchMap(() => next(authenticate(request))),
    );
  }));
};
