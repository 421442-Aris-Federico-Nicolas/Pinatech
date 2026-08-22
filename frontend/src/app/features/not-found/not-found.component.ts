import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';

@Component({
  imports: [AppButtonDirective, RouterLink],
  template: `<main><p class="eyebrow">Error 404</p><h1>Esta página no existe</h1><p class="description">La dirección puede ser incorrecta o el contenido pudo haber cambiado.</p><div><a appButton="filled" routerLink="/">Ir al inicio</a><a appButton="outlined" routerLink="/catalog">Ver catálogo</a></div></main>`,
  styles: [`:host{background:var(--color-bg);color:var(--color-text);display:block;min-height:calc(100dvh - 100px)}main{align-items:center;display:flex;flex-direction:column;justify-content:center;margin:auto;min-height:65dvh;padding:var(--space-8);text-align:center}.eyebrow{color:var(--color-orange);font-size:var(--text-xs);font-weight:var(--font-weight-black);letter-spacing:.2em;text-transform:uppercase}h1{font-size:clamp(2.5rem,7vw,5rem);letter-spacing:-.07em;line-height:.95;margin:var(--space-2) 0}.description{color:var(--color-text-muted);line-height:1.6;margin:var(--space-4) 0 var(--space-8)}div{display:flex;flex-wrap:wrap;gap:var(--space-3);justify-content:center}@media(max-width:500px){:host{min-height:calc(100dvh - 72px)}div{align-items:stretch;flex-direction:column;width:100%}}`],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundComponent {}
