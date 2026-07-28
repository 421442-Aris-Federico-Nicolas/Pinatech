import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(AuthService).getAccessToken();
  return next(request.clone({
    headers: token ? request.headers.set('Authorization', `Bearer ${token}`) : request.headers,
    withCredentials: true,
  }));
};
