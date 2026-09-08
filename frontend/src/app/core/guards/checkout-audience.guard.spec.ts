import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { checkoutAudienceGuard } from './checkout-audience.guard';

describe('checkoutAudienceGuard', () => {
  it.each([
    { user: null, allowed: true },
    { user: { roles: ['CUSTOMER'] }, allowed: true },
    { user: { roles: ['ADMIN'] }, allowed: false },
    { user: { roles: ['TECHNICIAN'] }, allowed: false },
  ])('allows guests/customers and redirects staff: $user', ({ user, allowed }) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: { user: () => user } }] });
    const result = TestBed.runInInjectionContext(() => checkoutAudienceGuard({} as never, {} as never));
    expect(result === true).toBe(allowed);
    if (!allowed) expect(TestBed.inject(Router).serializeUrl(result as never)).toMatch(/^\/(admin|technical)$/);
  });
});
