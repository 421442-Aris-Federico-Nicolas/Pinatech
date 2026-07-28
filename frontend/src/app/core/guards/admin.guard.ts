import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.user()?.roles.includes('ADMIN')
    ? true
    : inject(Router).createUrlTree(['/']);
};
