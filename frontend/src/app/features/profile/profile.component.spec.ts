import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthenticatedUser } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { ProfileComponent } from './profile.component';

describe('ProfileComponent', () => {
  it('shows the authenticated customer profile', async () => {
    const user = signal<AuthenticatedUser | null>({
      id: 7,
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      phone: null,
      roles: ['CUSTOMER'],
    });
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ada Lovelace');
    expect(fixture.nativeElement.textContent).toContain('ada@example.com');
    expect(fixture.nativeElement.textContent).toContain('Cliente');
    expect(fixture.nativeElement.textContent).toContain('Ver Mis pedidos');
  });
});
