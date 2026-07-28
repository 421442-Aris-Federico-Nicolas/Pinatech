import { inject } from '@angular/core'; import { CanActivateFn, Router } from '@angular/router'; import { AuthService } from '../auth/auth.service';
export const technicalGuard:CanActivateFn=()=>{const user=inject(AuthService).user();return user?.roles.some(role=>role==='TECHNICIAN'||role==='ADMIN')?true:inject(Router).createUrlTree(['/']);};
