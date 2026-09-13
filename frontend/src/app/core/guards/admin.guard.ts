import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../auth/auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const result = () => auth.user()?.roles.includes('ADMIN') ? true : router.createUrlTree(['/']);

  return auth.user() ? result() : auth.restoreSession().pipe(map(result));
};
