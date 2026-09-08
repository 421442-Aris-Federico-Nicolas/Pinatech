import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

export const checkoutAudienceGuard: CanActivateFn = () => {
  const user = inject(AuthService).user();
  const router = inject(Router);
  if (!user || user.roles.includes('CUSTOMER')) return true;
  if (user.roles.includes('ADMIN')) return router.createUrlTree(['/admin']);
  if (user.roles.includes('TECHNICIAN')) return router.createUrlTree(['/technical']);
  return router.createUrlTree(['/']);
};
