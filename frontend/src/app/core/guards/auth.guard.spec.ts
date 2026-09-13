import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  const deniedRoute = { redirected: true };
  const router = { createUrlTree: () => deniedRoute };

  it('allows authenticated users', () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAuthenticated: () => true } },
        { provide: Router, useValue: router },
      ],
    });

    const result = TestBed.runInInjectionContext(() => authGuard({} as never, { url: '/orders' } as never));

    expect(result).toBe(true);
  });

  it('waits for session restoration before redirecting unauthenticated users', async () => {
    let requestedUrl = '';
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAuthenticated: () => false, restoreSession: () => of(void 0) } },
        { provide: Router, useValue: { createUrlTree: (_commands: string[], options: { queryParams: { returnUrl: string } }) => {
          requestedUrl = options.queryParams.returnUrl;
          return deniedRoute;
        } } },
      ],
    });

    const decision = TestBed.runInInjectionContext(() => authGuard({} as never, { url: '/orders' } as never));
    const result = await firstValueFrom(decision as Observable<unknown>);

    expect(result).toBe(deniedRoute);
    expect(requestedUrl).toBe('/orders');
  });
});
