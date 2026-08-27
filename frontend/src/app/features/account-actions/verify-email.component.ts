import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, finalize, of, switchMap } from 'rxjs';
import { requestErrorMessage } from '../../core/api/problem-detail';
import { consumeActionToken } from '../../core/auth/action-token';
import { AuthService } from '../../core/auth/auth.service';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';

@Component({
  selector: 'app-verify-email',
  imports: [AppButtonDirective, AppCardDirective, AppFeedbackComponent, RouterLink],
  templateUrl: './verify-email.component.html',
  styleUrl: '../auth/auth.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerifyEmailComponent {
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  readonly token = consumeActionToken();
  readonly submitting = signal(false);
  readonly confirmed = signal(false);
  readonly error = signal('');

  confirm(): void {
    if (!this.token || this.submitting()) return;
    this.error.set('');
    this.submitting.set(true);
    this.auth.confirmEmailVerification(this.token).pipe(
      switchMap(() => this.auth.isAuthenticated() ? this.auth.refreshSession().pipe(catchError(() => {
        this.auth.clearSession();
        return of(void 0);
      })) : of(void 0)),
      finalize(() => this.submitting.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: () => {
        this.confirmed.set(true);
      },
      error: (response: HttpErrorResponse) => this.error.set(requestErrorMessage(response, 'El enlace no es válido o venció. Solicitá uno nuevo desde tu perfil.')),
    });
  }
}
