import { Directive, Input } from '@angular/core';
import { EstadoTono } from '../../core/utils/estado-label';

@Directive({
  selector: '[appBadge]',
  host: {
    class: 'app-badge',
    '[class.app-badge--neutral]': 'tone === "neutral"',
    '[class.app-badge--info]': 'tone === "info"',
    '[class.app-badge--accent]': 'tone === "accent"',
    '[class.app-badge--success]': 'tone === "success"',
    '[class.app-badge--warning]': 'tone === "warning"',
    '[class.app-badge--danger]': 'tone === "danger"',
  },
})
export class AppBadgeDirective {
  @Input({ alias: 'appBadge' }) tone: EstadoTono = 'neutral';
}
