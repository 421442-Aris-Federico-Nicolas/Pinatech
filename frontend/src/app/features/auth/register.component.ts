import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { requestErrorMessage } from '../../core/api/problem-detail';
import { AuthService } from '../../core/auth/auth.service';
import { safeReturnUrl } from '../../core/auth/safe-return-url';
import { NotificationService } from '../../core/notifications/notification.service';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppInputComponent } from '../../shared/ui/input/app-input.component';

const passwordsMatch: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.get('password')?.value === control.get('confirmPassword')?.value ? null : { passwordsMismatch: true };

@Component({
  selector: 'app-register',
  imports: [AppButtonDirective, AppCardDirective, AppInputComponent, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './auth.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  readonly route = inject(ActivatedRoute);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly form = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    phone: ['', [Validators.maxLength(30), Validators.pattern(/^[0-9+() .-]*$/)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72), Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/)]],
    confirmPassword: ['', [Validators.required, Validators.maxLength(72)]],
  }, { validators: passwordsMatch });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.error.set(null));
  }

  submit(): void {
    if (this.submitting()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notifications.error('Revisá los campos marcados antes de crear la cuenta.');
      const firstInvalid = Object.entries(this.form.controls).find(([, control]) => control.invalid)?.[0]
        ?? (this.form.hasError('passwordsMismatch') ? 'confirmPassword' : null);
      if (firstInvalid) this.host.nativeElement.querySelector<HTMLInputElement>(`[formControlName="${firstInvalid}"]`)?.focus();
      return;
    }
    this.error.set(null);
    this.submitting.set(true);
    const { confirmPassword: _confirmPassword, phone, ...account } = this.form.getRawValue();
    this.auth.register({ ...account, phone: phone.trim() || null }).pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.notifications.success('Cuenta creada. Revisá tu email para verificarla; tu sesión ya está iniciada.');
        void this.router.navigateByUrl(this.destination());
      },
      error: (response: HttpErrorResponse) => this.error.set(response.status === 409
        ? 'Ya existe una cuenta registrada con ese email.'
        : requestErrorMessage(response, 'No pudimos crear la cuenta. Revisá los datos marcados e intentá nuevamente.')),
    });
  }

  private destination(): string {
    return safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
  }
}
