import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

@Component({
  imports: [MatButtonModule, RouterLink],
  template: `<main><p class="eyebrow">Error 404</p><h1>Esta página no existe</h1><p class="description">La dirección puede ser incorrecta o el contenido pudo haber cambiado.</p><div><a mat-flat-button routerLink="/">Ir al inicio</a><a mat-stroked-button routerLink="/catalog">Ver catálogo</a></div></main>`,
  styles: [`:host{background:#080d14;color:#eef7fb;display:block;min-height:calc(100dvh - 110px)}main{align-items:center;display:flex;flex-direction:column;justify-content:center;margin:auto;min-height:65dvh;padding:2rem;text-align:center}.eyebrow{color:var(--pin-orange);font-size:.75rem;font-weight:900;letter-spacing:.2em;text-transform:uppercase}h1{font-size:clamp(2.5rem,7vw,5rem);letter-spacing:-.07em;line-height:.95;margin:.5rem 0}.description{color:#9fb3c2;line-height:1.6;margin:1rem 0 2rem}div{display:flex;flex-wrap:wrap;gap:.75rem;justify-content:center}`],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundComponent {}
