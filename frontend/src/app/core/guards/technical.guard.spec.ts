import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { technicalGuard } from './technical.guard';

describe('technicalGuard', () => {
  it('allows technicians and administrators', () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { user: () => ({ roles: ['TECHNICIAN'] }) } },
        { provide: Router, useValue: { createUrlTree: () => ({}) } },
      ],
    });

    expect(TestBed.runInInjectionContext(() => technicalGuard({} as never, {} as never))).toBe(true);
  });

  it('redirects customers home', () => {
    const redirect = { redirected: true };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { user: () => ({ roles: ['CUSTOMER'] }) } },
        { provide: Router, useValue: { createUrlTree: () => redirect } },
      ],
    });

    expect(TestBed.runInInjectionContext(() => technicalGuard({} as never, {} as never))).toBe(redirect);
  });
});
