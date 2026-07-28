import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';

const passwordsMatch: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.get('password')?.value === control.get('confirmPassword')?.value ? null : { passwordsMismatch: true };

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, RouterLink],
  template: `
    <main>
      <a class="back" routerLink="/">← Volver a Pinatech</a>
      <mat-card>
        <p class="eyebrow">Tu cuenta Pinatech</p>
        <h1>Crear cuenta</h1>
        <p class="intro">Registrate para conservar tu carrito y consultar tus pedidos y servicios técnicos.</p>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <div class="name-row">
            <mat-form-field><mat-label>Nombre</mat-label><input matInput formControlName="firstName" autocomplete="given-name">@if(form.controls.firstName.touched && form.controls.firstName.invalid){<mat-error>Ingresá tu nombre.</mat-error>}</mat-form-field>
            <mat-form-field><mat-label>Apellido</mat-label><input matInput formControlName="lastName" autocomplete="family-name">@if(form.controls.lastName.touched && form.controls.lastName.invalid){<mat-error>Ingresá tu apellido.</mat-error>}</mat-form-field>
          </div>
          <mat-form-field><mat-label>Email</mat-label><input matInput type="email" formControlName="email" autocomplete="email" inputmode="email">@if(form.controls.email.touched && form.controls.email.invalid){<mat-error>Ingresá un email válido.</mat-error>}</mat-form-field>
          <mat-form-field><mat-label>Teléfono (opcional)</mat-label><input matInput type="tel" formControlName="phone" autocomplete="tel" inputmode="tel">@if(form.controls.phone.touched && form.controls.phone.invalid){<mat-error>Revisá el número ingresado.</mat-error>}</mat-form-field>
          <mat-form-field><mat-label>Contraseña</mat-label><input matInput type="password" formControlName="password" autocomplete="new-password">@if(form.controls.password.touched && form.controls.password.invalid){<mat-error>Usá 8 caracteres, mayúscula, minúscula y número.</mat-error>}</mat-form-field>
          <mat-form-field><mat-label>Repetir contraseña</mat-label><input matInput type="password" formControlName="confirmPassword" autocomplete="new-password">@if(form.controls.confirmPassword.touched && (form.controls.confirmPassword.invalid || form.hasError('passwordsMismatch'))){<mat-error>Las contraseñas deben coincidir.</mat-error>}</mat-form-field>
          @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
          <button mat-flat-button type="submit" [disabled]="submitting()">{{ submitting() ? 'Creando cuenta…' : 'Crear cuenta' }}</button>
          <p class="login-link">¿Ya tenés cuenta? <a routerLink="/login" [queryParams]="{ returnUrl: route.snapshot.queryParamMap.get('returnUrl') }">Ingresá</a></p>
        </form>
      </mat-card>
    </main>
  `,
  styles: [`
    :host{background:linear-gradient(145deg,#07111d,#062b47);color:#eef7fb;display:block;min-height:calc(100dvh - 110px)}main{display:grid;min-height:calc(100dvh - 110px);padding:5rem 1rem 2rem;place-items:center;position:relative}.back{color:#71ddf0;left:clamp(1rem,5vw,4rem);position:absolute;text-decoration:none;top:2rem}mat-card{background:#fff;border-top:4px solid var(--pin-orange);color:var(--pin-navy);padding:clamp(1.25rem,4vw,2.25rem);width:min(100%,620px)}.eyebrow{color:var(--pin-teal);font-size:.7rem;font-weight:900;letter-spacing:.14em;margin:0;text-transform:uppercase}h1{font-size:2.2rem;letter-spacing:-.05em;margin:.35rem 0 .5rem}.intro{color:#526579;font-size:.9rem;line-height:1.5;margin:0 0 1.25rem}form{display:grid;gap:.55rem}.name-row{display:grid;gap:.75rem;grid-template-columns:1fr 1fr}.error{color:#a12b2b;margin:0 0 .5rem}form>button{margin-top:.4rem;width:100%}.login-link{font-size:.85rem;margin:.75rem 0 0;text-align:center}.login-link a{color:#007c9d;font-weight:800}@media(max-width:600px){.name-row{grid-template-columns:1fr}.back{top:1.25rem}}
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly route = inject(ActivatedRoute);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly form = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    phone: ['', [Validators.maxLength(30), Validators.pattern(/^[0-9+() .-]*$/)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72), Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/)]],
    confirmPassword: ['', Validators.required],
  }, { validators: passwordsMatch });

  submit(): void {
    if (this.submitting()) return;
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.error.set(null);
    this.submitting.set(true);
    const { confirmPassword: _confirmPassword, phone, ...account } = this.form.getRawValue();
    this.auth.register({ ...account, phone: phone.trim() || null }).pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: () => void this.router.navigateByUrl(this.destination()),
      error: (response: HttpErrorResponse) => this.error.set(response.status === 409
        ? 'Ya existe una cuenta registrada con ese email.'
        : 'No pudimos crear la cuenta. Revisá los datos e intentá nuevamente.'),
    });
  }

  private destination(): string {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    return returnUrl?.startsWith('/') && !returnUrl.startsWith('//') && !returnUrl.includes('://') ? returnUrl : '/';
  }
}
