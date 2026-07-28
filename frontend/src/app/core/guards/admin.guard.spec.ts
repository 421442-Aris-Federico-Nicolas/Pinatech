import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  it('allows only administrators', () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { user: () => ({ roles: ['ADMIN'] }) } },
        { provide: Router, useValue: { createUrlTree: () => ({}) } },
      ],
    });

    expect(TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never))).toBe(true);
  });

  it('redirects non-administrators home', () => {
    const redirect = { redirected: true };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { user: () => ({ roles: ['CUSTOMER'] }) } },
        { provide: Router, useValue: { createUrlTree: () => redirect } },
      ],
    });

    expect(TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never))).toBe(redirect);
  });
});
