import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
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

  it('redirects unauthenticated users to login with the original URL', () => {
    let requestedUrl = '';
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAuthenticated: () => false } },
        { provide: Router, useValue: { createUrlTree: (_commands: string[], options: { queryParams: { returnUrl: string } }) => {
          requestedUrl = options.queryParams.returnUrl;
          return deniedRoute;
        } } },
      ],
    });

    const result = TestBed.runInInjectionContext(() => authGuard({} as never, { url: '/orders' } as never));

    expect(result).toBe(deniedRoute);
    expect(requestedUrl).toBe('/orders');
  });
});
