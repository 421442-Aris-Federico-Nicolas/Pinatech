import { CanDeactivateFn } from '@angular/router';

export interface UnsavedAdminComponent {
  hasUnsavedChanges(): boolean;
  hasPendingOperation(): boolean;
  confirmDiscard(): boolean;
}

export const unsavedAdminGuard: CanDeactivateFn<UnsavedAdminComponent> = (component) =>
  !component.hasPendingOperation() && (!component.hasUnsavedChanges() || component.confirmDiscard());
