import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';

@Component({
  imports: [DecimalPipe, MatButtonModule, RouterLink],
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CartComponent {
  readonly cart = inject(CartService);
  readonly auth = inject(AuthService);
  readonly error = signal('');
  readonly submitting = signal(false);

  checkout(): void {
    if (this.submitting() || !this.cart.items().length) return;
    this.submitting.set(true);
    this.error.set('');
    this.cart.checkout().pipe(finalize(() => this.submitting.set(false))).subscribe({
      error: () => this.error.set('No se pudo confirmar el pedido. Revisá las cantidades e intentá nuevamente.'),
    });
  }

  clear(): void {
    if (confirm('¿Vaciar todos los productos del carrito?')) this.cart.clear();
  }
}
