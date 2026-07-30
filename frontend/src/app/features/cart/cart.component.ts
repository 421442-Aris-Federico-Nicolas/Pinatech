import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';

@Component({
  imports: [CurrencyPipe, MatButtonModule, RouterLink],
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CartComponent {
  readonly imageUrl = resolveApiContentUrl;
  readonly cart = inject(CartService);
  readonly auth = inject(AuthService);

  clear(): void {
    if (confirm('¿Vaciar todos los productos del carrito?')) this.cart.clear();
  }
}
