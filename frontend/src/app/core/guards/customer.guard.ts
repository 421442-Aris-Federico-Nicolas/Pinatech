import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

export const customerGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.user();

  if (!user) return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  if (user.roles.includes('CUSTOMER')) return true;
  if (user.roles.includes('ADMIN')) return router.createUrlTree(['/admin']);
  if (user.roles.includes('TECHNICIAN')) return router.createUrlTree(['/technical']);
  return router.createUrlTree(['/']);
};
