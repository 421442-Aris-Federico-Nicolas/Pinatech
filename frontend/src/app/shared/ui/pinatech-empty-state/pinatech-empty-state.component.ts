import { afterNextRender, ChangeDetectionStrategy, Component, ElementRef, input, signal, viewChild } from '@angular/core';

@Component({
  selector: 'app-pinatech-empty-state',
  standalone: true,
  templateUrl: './pinatech-empty-state.component.html',
  styleUrl: './pinatech-empty-state.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PinatechEmptyStateComponent {
  readonly title = input('No encontramos productos');
  readonly message = input('Probá con otros términos o quitá los filtros actuales.');
  protected readonly spriteReady = signal(false);
  private readonly spritePreload = viewChild<ElementRef<HTMLImageElement>>('spritePreload');

  constructor() {
    afterNextRender(() => {
      const sprite = this.spritePreload()?.nativeElement;
      if (sprite?.complete && sprite.naturalWidth > 0) this.markSpriteReady();
    });
  }

  protected markSpriteReady(): void {
    this.spriteReady.set(true);
  }
}
