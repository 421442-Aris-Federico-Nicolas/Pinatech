import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { GuestCheckoutService } from '../../core/guest/guest-checkout.service';
import { CheckoutResultComponent } from './checkout-result.component';
import { GuestCheckoutResultComponent } from './guest-checkout-result.component';

@Component({
  selector: 'app-checkout-result-page',
  imports: [CheckoutResultComponent, GuestCheckoutResultComponent],
  template: `@if (guestPublicId || !auth.user()) { <app-guest-checkout-result /> } @else { <app-checkout-result /> }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutResultPageComponent {
  protected readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly guest = inject(GuestCheckoutService);
  protected readonly guestPublicId = this.guest.returnPublicId(
    this.orderId(),
    this.route.snapshot.queryParamMap.get('guestOrder'),
  );

  private orderId(): number | null {
    const value = this.route.snapshot.queryParamMap.get('orderId');
    if (!value || !/^\d+$/.test(value)) return null;
    const id = Number(value);
    return Number.isSafeInteger(id) && id > 0 ? id : null;
  }
}
