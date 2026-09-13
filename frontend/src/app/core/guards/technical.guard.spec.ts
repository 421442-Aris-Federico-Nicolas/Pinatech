import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';
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

  it('waits for restoration before redirecting an anonymous user', async () => {
    const redirect = { redirected: true };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { user: () => null, restoreSession: () => of(void 0) } },
        { provide: Router, useValue: { createUrlTree: () => redirect } },
      ],
    });

    const decision = TestBed.runInInjectionContext(() => technicalGuard({} as never, {} as never));
    expect(await firstValueFrom(decision as Observable<unknown>)).toBe(redirect);
  });
});
