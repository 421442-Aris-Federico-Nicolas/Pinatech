import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { requestErrorMessage } from '../../core/api/problem-detail';
import { consumeActionToken } from '../../core/auth/action-token';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppInputComponent } from '../../shared/ui/input/app-input.component';

const matchPasswords: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.get('password')?.value === control.get('confirmPassword')?.value ? null : { passwordsMismatch: true };

@Component({
  selector: 'app-reset-password',
  imports: [AppButtonDirective, AppCardDirective, AppInputComponent, ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password.component.html',
  styleUrl: '../auth/auth.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  readonly token = consumeActionToken();
  readonly form = this.fb.group({
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72), Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/)]],
    confirmPassword: ['', [Validators.required, Validators.maxLength(72)]],
  }, { validators: matchPasswords });
  readonly submitting = signal(false);
  readonly completed = signal(false);
  readonly error = signal('');

  submit(): void {
    if (!this.token || this.submitting()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notifications.error('Revisá las contraseñas marcadas antes de continuar.');
      const name = this.form.hasError('passwordsMismatch') ? 'confirmPassword' : 'password';
      this.host.nativeElement.querySelector<HTMLInputElement>(`[formControlName="${name}"] input`)?.focus();
      return;
    }
    this.error.set('');
    this.submitting.set(true);
    this.auth.resetPassword(this.token, this.form.controls.password.value).pipe(
      finalize(() => this.submitting.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: () => {
        this.auth.clearSession();
        this.completed.set(true);
        this.notifications.success('Tu contraseña se actualizó.');
      },
      error: (response: HttpErrorResponse) => this.error.set(requestErrorMessage(response, 'El enlace no es válido o venció. Solicitá uno nuevo.')),
    });
  }
}
