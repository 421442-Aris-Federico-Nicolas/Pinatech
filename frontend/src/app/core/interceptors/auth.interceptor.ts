import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';

export const GUEST_REQUEST = new HttpContextToken<boolean>(() => false);

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const origin = globalThis.location?.origin ?? 'http://localhost';
  const apiUrl = new URL(environment.apiBaseUrl, origin);
  const requestUrl = new URL(request.url, origin);
  const apiPath = apiUrl.pathname.replace(/\/$/, '');
  const isApiRequest = requestUrl.origin === apiUrl.origin
    && (requestUrl.pathname === apiPath || requestUrl.pathname.startsWith(`${apiPath}/`));
  if (!isApiRequest) {
    return next(request);
  }

  const auth = inject(AuthService);
  const router = inject(Router);
  const authenticate = (source: typeof request) => source.clone({
    headers: auth.getAccessToken() ? source.headers.set('Authorization', `Bearer ${auth.getAccessToken()}`) : source.headers,
    withCredentials: true,
  });
  const requestPath = requestUrl.pathname.slice(apiPath.length);
  const isRefreshable = !request.context.get(GUEST_REQUEST)
    && !['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'].includes(requestPath);

  return next(authenticate(request)).pipe(catchError((error: unknown) => {
    if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !isRefreshable) {
      return throwError(() => error);
    }

    return auth.refreshSession().pipe(
      catchError((refreshError: unknown) => {
        auth.clearSession();
        const returnUrl = router.url.startsWith('/login') ? undefined : router.url;
        void router.navigate(['/login'], { queryParams: returnUrl ? { returnUrl, reason: 'session-expired' } : { reason: 'session-expired' } });
        return throwError(() => refreshError);
      }),
      switchMap(() => next(authenticate(request))),
    );
  }));
};
