import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { LoginComponent } from './login.component';
import { RegisterComponent } from './register.component';

describe('authentication screens', () => {
  it('renders login with shared controls and focuses the first invalid field', async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { login: vi.fn(), user: signal(null) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('app-input').length).toBe(2);
    expect(fixture.nativeElement.querySelector('.auth-card')?.classList).toContain('app-card');
    expect(fixture.nativeElement.querySelector('button[type="submit"]')?.classList).toContain('app-button');
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('app-input input'));
  });

  it('renders registration with shared controls and focuses a mismatched confirmation', async () => {
    const register = vi.fn();
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { register, user: signal(null) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.componentInstance.form.setValue({
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      phone: '',
      password: 'Password1',
      confirmPassword: 'Password2',
    });
    fixture.detectChanges();
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('app-input').length).toBe(6);
    expect(fixture.nativeElement.querySelector('.auth-card')?.classList).toContain('app-card');
    expect(register).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('[formControlName="confirmPassword"] input'));
  });
});
