import { ChangeDetectionStrategy, Component, CUSTOM_ELEMENTS_SCHEMA, computed, input } from '@angular/core';

export type AppFeedbackTone = 'info' | 'success' | 'warning' | 'error';
export type AppFeedbackAnnouncement = 'off' | 'polite' | 'assertive';

@Component({
  selector: 'app-feedback',
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  host: {
    '[attr.data-tone]': 'tone()',
  },
  template: `
    <span class="app-feedback__icon" aria-hidden="true"><iconify-icon [icon]="icon()"></iconify-icon></span>
    <div class="app-feedback__body" [attr.role]="semanticRole()" [attr.aria-atomic]="announce() === 'off' ? null : 'true'"><ng-content /></div>
    <div class="app-feedback__actions"><ng-content select="[feedback-actions]" /></div>
  `,
  styleUrl: './app-feedback.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppFeedbackComponent {
  readonly tone = input<AppFeedbackTone>('info');
  readonly announce = input<AppFeedbackAnnouncement>('off');

  protected readonly icon = computed(() => ({
    info: 'line-md:alert-circle',
    success: 'line-md:confirm-circle',
    warning: 'line-md:alert',
    error: 'line-md:alert-circle',
  })[this.tone()]);
  protected readonly semanticRole = computed(() => this.announce() === 'assertive'
    ? 'alert'
    : this.announce() === 'polite' ? 'status' : null);
}
