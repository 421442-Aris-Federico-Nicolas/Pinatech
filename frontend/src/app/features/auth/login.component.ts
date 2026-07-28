import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({ selector: 'app-login', imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule], template: `<main><mat-card><mat-card-header><mat-card-title>Iniciar sesión</mat-card-title></mat-card-header><mat-card-content><form [formGroup]="form" (ngSubmit)="submit()"><mat-form-field><mat-label>Email</mat-label><input matInput type="email" formControlName="email"></mat-form-field><mat-form-field><mat-label>Contraseña</mat-label><input matInput type="password" formControlName="password"></mat-form-field>@if (error()) { <p>{{ error() }}</p> }<button mat-flat-button type="submit">Ingresar</button></form></mat-card-content></mat-card></main>`, styles: ['main { display:grid; min-height:calc(100dvh - 64px); place-items:center; padding:1rem } mat-card { width:min(100%, 400px) } form { display:grid; gap:1rem; margin-top:1rem } p { color:#b91c1c }'], changeDetection: ChangeDetectionStrategy.OnPush })
export class LoginComponent {
  private readonly fb = inject(NonNullableFormBuilder); private readonly auth = inject(AuthService); private readonly router = inject(Router); private readonly route = inject(ActivatedRoute);
  readonly error = signal<string | null>(null); readonly form = this.fb.group({ email: ['', [Validators.required, Validators.email]], password: ['', Validators.required] });
  submit(): void { if (this.form.invalid) { this.form.markAllAsTouched(); return; } this.auth.login(this.form.getRawValue()).subscribe({ next: () => void this.router.navigateByUrl(this.route.snapshot.queryParamMap.get('returnUrl') ?? '/'), error: () => this.error.set('Credenciales inválidas.') }); }
}
