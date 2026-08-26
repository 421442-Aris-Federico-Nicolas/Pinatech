import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { requestErrorMessage } from '../../core/api/problem-detail';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppInputComponent } from '../../shared/ui/input/app-input.component';

@Component({
  selector: 'app-forgot-password',
  imports: [AppButtonDirective, AppCardDirective, AppInputComponent, ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password.component.html',
  styleUrl: '../auth/auth.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgotPasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  readonly form = this.fb.group({ email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]] });
  readonly submitting = signal(false);
  readonly sent = signal(false);
  readonly error = signal('');

  submit(): void {
    if (this.submitting()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notifications.error('Ingresá un email válido para continuar.');
      this.host.nativeElement.querySelector<HTMLInputElement>('input')?.focus();
      return;
    }
    this.error.set('');
    this.submitting.set(true);
    this.auth.forgotPassword(this.form.controls.email.value.trim()).pipe(
      finalize(() => this.submitting.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: () => {
        this.sent.set(true);
        this.notifications.success('Si existe una cuenta con ese email, enviamos las instrucciones.');
      },
      error: (response: HttpErrorResponse) => this.error.set(requestErrorMessage(response, 'No pudimos enviar las instrucciones. Intentá nuevamente.')),
    });
  }
}
