import { Directive, Input } from '@angular/core';

export type AppButtonAppearance = 'filled' | 'outlined' | 'text';
export type AppButtonTone = 'default' | 'danger';

function normalizeAppearance(value: AppButtonAppearance | '' | null): AppButtonAppearance {
  return value === 'outlined' || value === 'text' ? value : 'filled';
}

@Directive({
  selector: 'button[appButton],a[appButton]',
  host: {
    class: 'app-button',
    '[class.app-button--filled]': 'appearance === "filled"',
    '[class.app-button--outlined]': 'appearance === "outlined"',
    '[class.app-button--text]': 'appearance === "text"',
    '[class.app-button--danger]': 'tone === "danger"',
  },
})
export class AppButtonDirective {
  @Input({ alias: 'appButton', transform: normalizeAppearance }) appearance: AppButtonAppearance = 'filled';
  @Input() tone: AppButtonTone = 'default';
}
