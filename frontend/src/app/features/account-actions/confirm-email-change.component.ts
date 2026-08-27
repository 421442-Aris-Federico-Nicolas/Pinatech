import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { requestErrorMessage } from '../../core/api/problem-detail';
import { consumeActionToken } from '../../core/auth/action-token';
import { AuthService } from '../../core/auth/auth.service';
import { ProfileService } from '../../core/profile/profile.service';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';

@Component({
  selector: 'app-confirm-email-change',
  imports: [AppButtonDirective, AppCardDirective, AppFeedbackComponent, RouterLink],
  templateUrl: './confirm-email-change.component.html',
  styleUrl: '../auth/auth.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmEmailChangeComponent {
  private readonly auth = inject(AuthService);
  private readonly profiles = inject(ProfileService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly token = consumeActionToken();
  readonly submitting = signal(false);
  readonly error = signal('');

  confirm(): void {
    if (!this.token || this.submitting()) return;
    this.error.set('');
    this.submitting.set(true);
    this.profiles.confirmEmailChange(this.token).pipe(
      finalize(() => this.submitting.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: () => {
        this.auth.clearSession();
        void this.router.navigate(['/login'], { queryParams: { reason: 'email-changed' } });
      },
      error: (response: HttpErrorResponse) => this.error.set(requestErrorMessage(response, 'El enlace no es válido o venció. Volvé a solicitar el cambio desde tu perfil.')),
    });
  }
}
