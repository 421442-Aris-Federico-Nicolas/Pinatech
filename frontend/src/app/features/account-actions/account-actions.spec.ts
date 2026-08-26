import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ProfileService } from '../../core/profile/profile.service';
import { ConfirmEmailChangeComponent } from './confirm-email-change.component';
import { ResetPasswordComponent } from './reset-password.component';
import { VerifyEmailComponent } from './verify-email.component';

describe('account action screens', () => {
  beforeEach(() => history.replaceState(null, '', '/account-action#token=action-token'));
  afterEach(() => history.replaceState(null, '', '/'));

  it('does not verify an email until the user explicitly confirms', async () => {
    const confirmEmailVerification = vi.fn(() => of(void 0));
    await TestBed.configureTestingModule({
      imports: [VerifyEmailComponent],
      providers: [provideRouter([]), {
        provide: AuthService,
        useValue: { confirmEmailVerification, isAuthenticated: signal(false), clearSession: vi.fn() },
      }],
    }).compileComponents();
    const fixture = TestBed.createComponent(VerifyEmailComponent);
    fixture.detectChanges();
    expect(confirmEmailVerification).not.toHaveBeenCalled();
    fixture.componentInstance.confirm();
    expect(confirmEmailVerification).toHaveBeenCalledWith('action-token');
  });

  it('submits only token and password when resetting a password', async () => {
    const resetPassword = vi.fn(() => of(void 0));
    const clearSession = vi.fn();
    await TestBed.configureTestingModule({
      imports: [ResetPasswordComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: { resetPassword, clearSession } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.componentInstance.form.setValue({ password: 'Password1', confirmPassword: 'Password1' });
    fixture.componentInstance.submit();
    expect(resetPassword).toHaveBeenCalledWith('action-token', 'Password1');
    expect(clearSession).toHaveBeenCalledOnce();
  });

  it('does not confirm an email change while merely opening the link', async () => {
    const confirmEmailChange = vi.fn(() => of(void 0));
    await TestBed.configureTestingModule({
      imports: [ConfirmEmailChangeComponent],
      providers: [provideRouter([]), { provide: ProfileService, useValue: { confirmEmailChange } }, { provide: AuthService, useValue: { clearSession: vi.fn() } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(ConfirmEmailChangeComponent);
    fixture.detectChanges();
    expect(confirmEmailChange).not.toHaveBeenCalled();
  });
});
