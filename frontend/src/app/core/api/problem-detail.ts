import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';

export interface ProblemDetail {
  type?: string;
  title?: string;
  detail?: string;
  status?: number;
  errors?: Record<string, string>;
  retryAfterSeconds?: number;
}

export function problemDetail(response: HttpErrorResponse): ProblemDetail | null {
  return response.error && typeof response.error === 'object' ? response.error as ProblemDetail : null;
}

export function applyFieldErrors(form: FormGroup, response: HttpErrorResponse): boolean {
  const errors = problemDetail(response)?.errors;
  if (!errors) return false;
  let applied = false;
  for (const [field, message] of Object.entries(errors)) {
    const control = form.get(field);
    if (!control) continue;
    control.setErrors({ ...control.errors, server: message });
    control.markAsTouched();
    applied = true;
  }
  return applied;
}

export function requestErrorMessage(response: HttpErrorResponse, fallback: string): string {
  if (response.status === 0 || response.status >= 500) return 'No pudimos conectarnos con el servicio. Revisá tu conexión e intentá nuevamente.';
  if (response.status === 429) return 'Hiciste varios intentos seguidos. Esperá un minuto y volvé a intentar.';
  return fallback;
}
