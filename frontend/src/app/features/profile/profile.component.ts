import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { AppBadgeDirective } from '../../shared/ui/app-badge.directive';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';

@Component({
  selector: 'app-profile',
  imports: [AppBadgeDirective, AppButtonDirective, AppCardDirective, RouterLink],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileComponent {
  readonly auth = inject(AuthService);

  roleLabel(role: string): string {
    return { CUSTOMER: 'Cliente', ADMIN: 'Administrador', TECHNICIAN: 'Técnico' }[role] ?? role;
  }
}
