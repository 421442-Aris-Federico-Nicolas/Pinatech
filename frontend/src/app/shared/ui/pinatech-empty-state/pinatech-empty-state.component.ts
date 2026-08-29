import { ChangeDetectionStrategy, Component, input } from '@angular/core';

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
}
