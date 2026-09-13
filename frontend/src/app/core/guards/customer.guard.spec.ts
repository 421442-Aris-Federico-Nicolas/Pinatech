import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { customerGuard } from './customer.guard';

describe('customerGuard', () => {
  it('allows customers', () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { user: () => ({ roles: ['CUSTOMER'] }) } },
        { provide: Router, useValue: { createUrlTree: vi.fn() } },
      ],
    });

    expect(TestBed.runInInjectionContext(() => customerGuard({} as never, { url: '/checkout' } as never))).toBe(true);
  });

  it('preserves the checkout return URL after restoring an unauthenticated session', async () => {
    const redirect = { redirected: true };
    const createUrlTree = vi.fn(() => redirect);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { user: () => null, restoreSession: () => of(void 0) } },
        { provide: Router, useValue: { createUrlTree } },
      ],
    });

    const decision = TestBed.runInInjectionContext(() => customerGuard({} as never, { url: '/checkout' } as never));
    const result = await firstValueFrom(decision as Observable<unknown>);

    expect(result).toBe(redirect);
    expect(createUrlTree).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/checkout' } });
  });

  it('redirects authenticated non-customers to their panel', () => {
    const redirect = { redirected: true };
    const createUrlTree = vi.fn(() => redirect);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { user: () => ({ roles: ['TECHNICIAN'] }) } },
        { provide: Router, useValue: { createUrlTree } },
      ],
    });

    expect(TestBed.runInInjectionContext(() => customerGuard({} as never, { url: '/checkout' } as never))).toBe(redirect);
    expect(createUrlTree).toHaveBeenCalledWith(['/technical']);
  });
});
