import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, RouterLink],
  template: `
    <main>
      <a class="back" routerLink="/">← Volver a Pinatech</a>
      <mat-card>
        <p class="eyebrow">Tu cuenta</p><h1>Iniciar sesión</h1>
        @if (route.snapshot.queryParamMap.get('returnUrl') === '/cart') { <p class="context">Ingresá para confirmar el carrito. Tus productos se conservarán.</p> }
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field><mat-label>Email</mat-label><input matInput type="email" formControlName="email" autocomplete="email" inputmode="email">@if(form.controls.email.touched && form.controls.email.invalid){<mat-error>Ingresá un email válido.</mat-error>}</mat-form-field>
          <mat-form-field><mat-label>Contraseña</mat-label><input matInput type="password" formControlName="password" autocomplete="current-password">@if(form.controls.password.touched && form.controls.password.invalid){<mat-error>Ingresá tu contraseña.</mat-error>}</mat-form-field>
          @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
          <button mat-flat-button type="submit" [disabled]="submitting()">{{ submitting() ? 'Ingresando…' : 'Ingresar' }}</button>
          <p class="register-link">¿Todavía no tenés cuenta? <a routerLink="/register" [queryParams]="{ returnUrl: route.snapshot.queryParamMap.get('returnUrl') }">Registrate</a></p>
        </form>
      </mat-card>
    </main>`,
  styles: [`
    :host{background:linear-gradient(145deg,#07111d,#062b47);color:#eef7fb;display:block;min-height:calc(100dvh - 110px)}main{display:grid;min-height:calc(100dvh - 110px);padding:2rem;place-items:center;position:relative}.back{color:#71ddf0;left:clamp(1rem,5vw,4rem);position:absolute;text-decoration:none;top:2rem}mat-card{background:#fff;border-top:4px solid var(--pin-orange);color:var(--pin-navy);padding:clamp(1.25rem,4vw,2.25rem);width:min(100%,430px)}.eyebrow{color:var(--pin-teal);font-size:.7rem;font-weight:900;letter-spacing:.14em;margin:0;text-transform:uppercase}h1{font-size:2.2rem;letter-spacing:-.05em;margin:.35rem 0 1.5rem}.context{background:#e7f7fa;border-left:3px solid var(--pin-teal);font-size:.85rem;line-height:1.5;padding:.75rem}form{display:grid;gap:.65rem}.error{color:#a12b2b;margin:0 0 .5rem}form>button{margin-top:.5rem;width:100%}.register-link{font-size:.85rem;margin:.75rem 0 0;text-align:center}.register-link a{color:#007c9d;font-weight:800}@media(max-width:500px){main{padding:5rem 1rem 1rem}.back{top:1.25rem}}
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly route = inject(ActivatedRoute);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly form = this.fb.group({ email: ['', [Validators.required, Validators.email]], password: ['', Validators.required] });

  submit(): void {
    if (this.submitting()) return;
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.error.set(null);
    this.submitting.set(true);
    this.auth.login(this.form.getRawValue()).pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: () => void this.router.navigateByUrl(this.destination()),
      error: () => this.error.set('No pudimos iniciar sesión. Revisá tus credenciales.'),
    });
  }

  private destination(): string {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    if (returnUrl?.startsWith('/') && !returnUrl.startsWith('//') && !returnUrl.includes('://')) return returnUrl;
    if (this.auth.user()?.roles.includes('ADMIN')) return '/admin';
    if (this.auth.user()?.roles.includes('TECHNICIAN')) return '/technical';
    return '/';
  }
}
