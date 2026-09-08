import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { CheckoutComponent } from './checkout.component';
import { GuestCheckoutComponent } from './guest-checkout.component';

@Component({
  selector: 'app-checkout-page',
  imports: [CheckoutComponent, GuestCheckoutComponent],
  template: `@if (auth.user()) { <app-checkout /> } @else { <app-guest-checkout /> }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutPageComponent {
  protected readonly auth = inject(AuthService);
}
