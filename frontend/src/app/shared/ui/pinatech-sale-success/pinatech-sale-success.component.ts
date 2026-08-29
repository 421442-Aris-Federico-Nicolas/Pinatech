import { ChangeDetectionStrategy, Component, input } from '@angular/core';

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
}
