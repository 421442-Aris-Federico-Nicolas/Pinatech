import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthenticatedUser } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { Profile } from '../../core/profile/profile.models';
import { ProfileService } from '../../core/profile/profile.service';
import { ProfileComponent } from './profile.component';

describe('ProfileComponent', () => {
  const profile: Profile = {
    id: 7,
    firstName: 'Ada',
    lastName: 'Lovelace',
    email: 'ada@example.com',
    phone: null,
    emailVerified: false,
    roles: ['CUSTOMER'],
    address: null,
  };

  it('loads editable account forms and maps profile changes explicitly into the session', async () => {
    const user = signal<AuthenticatedUser | null>({ ...profile, address: undefined } as AuthenticatedUser);
    const isAuthenticated = signal(true);
    const replaceUser = vi.fn((value: AuthenticatedUser) => user.set(value));
    const profiles = {
      get: vi.fn(() => of(profile)),
      update: vi.fn(() => of({ ...profile, firstName: 'Augusta' })),
      putAddress: vi.fn(), deleteAddress: vi.fn(), requestEmailChange: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user, isAuthenticated, replaceUser, requestEmailVerification: vi.fn() } },
        { provide: ProfileService, useValue: profiles },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ada Lovelace');
    expect(fixture.nativeElement.textContent).toContain('Verificación pendiente');
    expect(fixture.nativeElement.querySelectorAll('#personal-form app-input').length).toBe(3);
    expect(fixture.nativeElement.querySelector('#address-form')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Ver mis pedidos');

    fixture.componentInstance.personalForm.setValue({ firstName: 'Augusta', lastName: 'Lovelace', phone: '+54 11 1234-5678' });
    fixture.componentInstance.savePersonal();
    expect(profiles.update).toHaveBeenCalledWith({ firstName: 'Augusta', lastName: 'Lovelace', phone: '+54 11 1234-5678' });
    expect(replaceUser).toHaveBeenLastCalledWith({
      id: 7, firstName: 'Augusta', lastName: 'Lovelace', email: 'ada@example.com', phone: null,
      emailVerified: false, roles: ['CUSTOMER'],
    });
  });

  it('does not submit an invalid address', async () => {
    const profiles = { get: vi.fn(() => of(profile)), putAddress: vi.fn() };
    const user = signal<AuthenticatedUser | null>({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, emailVerified: false, roles: ['CUSTOMER'] });
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user, isAuthenticated: signal(true), replaceUser: vi.fn() } },
        { provide: ProfileService, useValue: profiles },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    fixture.componentInstance.saveAddress();
    expect(profiles.putAddress).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('#address-form .ng-invalid input')).toBe(document.activeElement);
  });

  it('always requires the current password for email changes and associates a 409 with the email field', async () => {
    const requestEmailChange = vi.fn(() => throwError(() => new HttpErrorResponse({ status: 409 })));
    const user = signal<AuthenticatedUser | null>({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, emailVerified: false, roles: ['CUSTOMER'] });
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user, isAuthenticated: signal(true), replaceUser: vi.fn() } },
        { provide: ProfileService, useValue: { get: () => of(profile), requestEmailChange } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    fixture.componentInstance.emailForm.setValue({ email: 'new@example.com', currentPassword: '' });
    fixture.componentInstance.requestEmailChange();
    expect(requestEmailChange).not.toHaveBeenCalled();

    fixture.componentInstance.emailForm.setValue({ email: 'new@example.com', currentPassword: 'Password1' });

    fixture.componentInstance.requestEmailChange();
    await Promise.resolve();
    fixture.detectChanges();

    expect(requestEmailChange).toHaveBeenCalledWith({ email: 'new@example.com', currentPassword: 'Password1' });
    expect(fixture.componentInstance.emailForm.controls.email.getError('server')).toContain('asociado');
    expect(fixture.nativeElement.querySelector('#email-form .ng-invalid input')).toBe(document.activeElement);
  });

  it('does not replace a session that changed before the profile response arrives', async () => {
    const response = new Subject<Profile>();
    const user = signal<AuthenticatedUser | null>({ id: 7, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: null, emailVerified: false, roles: ['CUSTOMER'] });
    const isAuthenticated = signal(true);
    const replaceUser = vi.fn();
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user, isAuthenticated, replaceUser } },
        { provide: ProfileService, useValue: { get: () => response } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    user.set({ ...user()!, id: 8 });

    response.next(profile);

    expect(replaceUser).not.toHaveBeenCalled();
    fixture.destroy();
    expect(response.observed).toBe(false);
  });
});
