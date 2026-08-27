import { afterNextRender, ChangeDetectionStrategy, Component, ElementRef, input, signal, viewChild } from '@angular/core';

@Component({
  selector: 'app-pinatech-sale-success',
  standalone: true,
  templateUrl: './pinatech-sale-success.component.html',
  styleUrl: './pinatech-sale-success.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PinatechSaleSuccessComponent {
  readonly title = input('¡Venta concretada!');
  readonly message = input('El pedido se confirmó correctamente.');
  readonly orderNumber = input<string | number | null>(null);
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
