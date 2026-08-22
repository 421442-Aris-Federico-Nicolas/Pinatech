import { Directive } from '@angular/core';

@Directive({
  selector: '[appCard]',
  host: { class: 'app-card' },
})
export class AppCardDirective {}

@Directive({
  selector: '[appCardHeader]',
  host: { class: 'app-card__header' },
})
export class AppCardHeaderDirective {}

@Directive({
  selector: '[appCardActions]',
  host: { class: 'app-card__actions' },
})
export class AppCardActionsDirective {}
