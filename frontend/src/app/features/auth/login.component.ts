import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { requestErrorMessage } from '../../core/api/problem-detail';
import { AuthService } from '../../core/auth/auth.service';
import { safeReturnUrl } from '../../core/auth/safe-return-url';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';
import { AppInputComponent } from '../../shared/ui/input/app-input.component';

@Component({
  selector: 'app-login',
  imports: [AppButtonDirective, AppCardDirective, AppFeedbackComponent, AppInputComponent, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './auth.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  readonly route = inject(ActivatedRoute);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly form = this.fb.group({ email: ['', [Validators.required, Validators.email]], password: ['', Validators.required] });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.error.set(null));
  }

  submit(): void {
    if (this.submitting()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      const firstInvalid = Object.entries(this.form.controls).find(([, control]) => control.invalid)?.[0];
      if (firstInvalid) queueMicrotask(() => this.host.nativeElement.querySelector<HTMLInputElement>(`[formControlName="${firstInvalid}"]`)?.focus());
      return;
    }
    this.error.set(null);
    this.submitting.set(true);
    this.auth.login(this.form.getRawValue()).pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => void this.router.navigateByUrl(this.destination()),
      error: (response: HttpErrorResponse) => this.error.set(response.status === 401 || response.status === 403
        ? 'El email o la contraseña no son correctos.'
        : requestErrorMessage(response, 'No pudimos iniciar sesión. Intentá nuevamente.')),
    });
  }

  private destination(): string {
    const returnUrl = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'), '');
    if (returnUrl) return returnUrl;
    if (this.auth.user()?.roles.includes('ADMIN')) return '/admin';
    if (this.auth.user()?.roles.includes('TECHNICIAN')) return '/technical';
    return '/';
  }
}
