import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { applyFieldErrors, requestErrorMessage } from '../../core/api/problem-detail';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Profile, ProfileAddress } from '../../core/profile/profile.models';
import { ProfileService } from '../../core/profile/profile.service';
import { AppBadgeDirective } from '../../shared/ui/app-badge.directive';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppInputComponent } from '../../shared/ui/input/app-input.component';
import { AppTextareaComponent } from '../../shared/ui/textarea/app-textarea.component';

type PendingAction = 'profile' | 'email' | 'verification' | 'address' | 'delete-address' | null;

@Component({
  selector: 'app-profile',
  imports: [
    AppBadgeDirective, AppButtonDirective, AppCardDirective, AppInputComponent, AppTextareaComponent,
    ReactiveFormsModule, RouterLink,
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly notifications = inject(NotificationService);
  private readonly profiles = inject(ProfileService);
  private readonly destroyRef = inject(DestroyRef);

  readonly profile = signal<Profile | null>(null);
  readonly loading = signal(true);
  readonly loadError = signal('');
  readonly actionError = signal('');
  readonly emailStatus = signal('');
  readonly pending = signal<PendingAction>(null);
  readonly editingAddress = signal(false);
  readonly confirmingDelete = signal(false);

  readonly personalForm = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    phone: ['', [Validators.maxLength(30), Validators.pattern(/^$|^\+?[0-9 ()-]{6,30}$/)]],
  });
  readonly emailForm = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    currentPassword: ['', [Validators.required, Validators.maxLength(72)]],
  });
  readonly addressForm = this.fb.group({
    street: ['', [Validators.required, Validators.maxLength(150)]],
    number: ['', [Validators.required, Validators.maxLength(30)]],
    floorApartment: ['', Validators.maxLength(50)],
    locality: ['', [Validators.required, Validators.maxLength(120)]],
    provinceCode: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{1,3}$/)]],
    postalCode: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9 -]{4,12}$/)]],
    countryCode: ['AR', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    reference: ['', Validators.maxLength(300)],
  });

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.profiles.get().pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (profile) => this.applyLoadedProfile(profile),
      error: (response: HttpErrorResponse) => this.loadError.set(requestErrorMessage(response, 'No pudimos cargar tu perfil. Intentá nuevamente.')),
    });
  }

  savePersonal(): void {
    if (!this.prepareForm(this.personalForm, '#personal-form', 'Revisá los datos personales marcados.')) return;
    const value = this.personalForm.getRawValue();
    this.start('profile');
    this.profiles.update({
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      phone: value.phone.trim(),
    }).pipe(finalize(() => this.pending.set(null)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.personalForm.reset({ firstName: profile.firstName, lastName: profile.lastName, phone: profile.phone ?? '' });
        this.replaceSessionUser(profile);
        this.notifications.success('Tus datos personales se guardaron.');
      },
      error: (response: HttpErrorResponse) => this.handleFormError(this.personalForm, '#personal-form', response, 'No pudimos guardar tus datos personales.'),
    });
  }

  requestEmailChange(): void {
    if (!this.prepareForm(this.emailForm, '#email-form', 'Ingresá un email válido para solicitar el cambio.')) return;
    const email = this.emailForm.controls.email.value.trim().toLowerCase();
    if (email === this.profile()?.email.toLowerCase()) {
      this.emailForm.controls.email.setErrors({ sameEmail: true });
      this.emailForm.controls.email.markAsTouched();
      this.notifications.error('Ingresá un email diferente del actual.');
      this.focusFirstInvalid('#email-form');
      return;
    }
    this.start('email');
    this.profiles.requestEmailChange({ email, currentPassword: this.emailForm.controls.currentPassword.value }).pipe(
      finalize(() => this.pending.set(null)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: () => {
        this.emailForm.reset({ email: '', currentPassword: '' });
        this.emailStatus.set('Enviamos un enlace al email nuevo. El cambio se aplicará solamente cuando lo confirmes.');
        this.notifications.success('Revisá el email nuevo para confirmar el cambio.');
      },
      error: (response: HttpErrorResponse) => {
        if (response.status === 409) {
          const message = 'Ese email ya está asociado a otra cuenta.';
          this.emailForm.controls.email.setErrors({ ...this.emailForm.controls.email.errors, server: message });
          this.emailForm.controls.email.markAsTouched();
          this.actionError.set(message);
          queueMicrotask(() => this.focusFirstInvalid('#email-form'));
          return;
        }
        this.handleFormError(this.emailForm, '#email-form', response, 'No pudimos solicitar el cambio de email.');
      },
    });
  }

  resendVerification(): void {
    const profile = this.profile();
    if (!profile || profile.emailVerified || this.pending()) return;
    this.start('verification');
    this.auth.requestEmailVerification(profile.email).pipe(finalize(() => this.pending.set(null)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.emailStatus.set('Si la cuenta sigue pendiente, enviamos un nuevo enlace de verificación.');
        this.notifications.success('Revisá tu email para completar la verificación.');
      },
      error: (response: HttpErrorResponse) => this.actionError.set(requestErrorMessage(response, 'No pudimos reenviar la verificación. Intentá nuevamente.')),
    });
  }

  beginAddressEdit(): void {
    this.patchAddress(this.profile()?.address ?? null);
    this.confirmingDelete.set(false);
    this.editingAddress.set(true);
  }

  cancelAddressEdit(): void {
    this.patchAddress(this.profile()?.address ?? null);
    this.editingAddress.set(false);
  }

  saveAddress(): void {
    if (!this.prepareForm(this.addressForm, '#address-form', 'Revisá los campos marcados de la dirección.')) return;
    const value = this.addressForm.getRawValue();
    this.start('address');
    this.profiles.putAddress({
      street: value.street.trim(),
      number: value.number.trim(),
      floorApartment: value.floorApartment.trim(),
      locality: value.locality.trim(),
      provinceCode: value.provinceCode.trim().toUpperCase(),
      postalCode: value.postalCode.trim().toUpperCase(),
      countryCode: value.countryCode.trim().toUpperCase(),
      reference: value.reference.trim(),
    }).pipe(finalize(() => this.pending.set(null)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (address) => {
        this.profile.update((profile) => profile ? { ...profile, address } : profile);
        this.patchAddress(address);
        this.editingAddress.set(false);
        this.notifications.success('Tu dirección se guardó.');
      },
      error: (response: HttpErrorResponse) => this.handleFormError(this.addressForm, '#address-form', response, 'No pudimos guardar la dirección.'),
    });
  }

  deleteAddress(): void {
    if (this.pending()) return;
    this.start('delete-address');
    this.profiles.deleteAddress().pipe(finalize(() => this.pending.set(null)), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.profile.update((profile) => profile ? { ...profile, address: null } : profile);
        this.patchAddress(null);
        this.confirmingDelete.set(false);
        this.editingAddress.set(true);
        this.notifications.success('La dirección se eliminó.');
      },
      error: (response: HttpErrorResponse) => this.actionError.set(requestErrorMessage(response, 'No pudimos eliminar la dirección. Intentá nuevamente.')),
    });
  }

  roleLabel(role: string): string {
    return { CUSTOMER: 'Cliente', ADMIN: 'Administrador', TECHNICIAN: 'Técnico' }[role] ?? role;
  }

  fieldError(control: AbstractControl, label: string, formatMessage = ''): string {
    if (!control.touched || !control.invalid) return '';
    if (control.hasError('required')) return `Ingresá ${label.toLowerCase()}.`;
    if (control.hasError('email')) return 'Ingresá un email válido.';
    if (control.hasError('sameEmail')) return 'Ingresá un email diferente del actual.';
    if (control.hasError('maxlength')) return `${label} supera el máximo permitido.`;
    if (control.hasError('pattern')) return formatMessage || `Revisá el formato de ${label.toLowerCase()}.`;
    if (control.hasError('server')) return typeof control.getError('server') === 'string' ? control.getError('server') : `Revisá ${label.toLowerCase()}.`;
    return '';
  }

  private applyLoadedProfile(profile: Profile): void {
    this.profile.set(profile);
    this.personalForm.reset({ firstName: profile.firstName, lastName: profile.lastName, phone: profile.phone ?? '' });
    this.emailForm.reset({ email: '', currentPassword: '' });
    this.patchAddress(profile.address);
    this.editingAddress.set(!profile.address);
    this.replaceSessionUser(profile);
  }

  private replaceSessionUser(profile: Profile): void {
    const currentUser = this.auth.user();
    if (!this.auth.isAuthenticated() || currentUser?.id !== profile.id) return;
    this.auth.replaceUser({
      id: profile.id,
      firstName: profile.firstName,
      lastName: profile.lastName,
      email: profile.email,
      phone: profile.phone,
      emailVerified: profile.emailVerified,
      roles: [...profile.roles],
    });
  }

  private patchAddress(address: ProfileAddress | null): void {
    this.addressForm.reset({
      street: address?.street ?? '',
      number: address?.number ?? '',
      floorApartment: address?.floorApartment ?? '',
      locality: address?.locality ?? '',
      provinceCode: address?.provinceCode ?? '',
      postalCode: address?.postalCode ?? '',
      countryCode: address?.countryCode ?? 'AR',
      reference: address?.reference ?? '',
    });
  }

  private prepareForm(form: typeof this.personalForm | typeof this.emailForm | typeof this.addressForm, selector: string, message: string): boolean {
    if (this.pending()) return false;
    if (form.valid) return true;
    form.markAllAsTouched();
    this.notifications.error(message);
    this.focusFirstInvalid(selector);
    return false;
  }

  private handleFormError(form: typeof this.personalForm | typeof this.emailForm | typeof this.addressForm, selector: string, response: HttpErrorResponse, fallback: string): void {
    const hasFieldErrors = applyFieldErrors(form, response);
    this.actionError.set(requestErrorMessage(response, hasFieldErrors ? 'Revisá los campos marcados.' : fallback));
    if (hasFieldErrors) queueMicrotask(() => this.focusFirstInvalid(selector));
  }

  private focusFirstInvalid(selector: string): void {
    this.host.nativeElement.querySelector<HTMLElement>(`${selector} .ng-invalid input, ${selector} .ng-invalid textarea`)?.focus();
  }

  private start(action: Exclude<PendingAction, null>): void {
    this.actionError.set('');
    this.emailStatus.set('');
    this.pending.set(action);
  }
}
