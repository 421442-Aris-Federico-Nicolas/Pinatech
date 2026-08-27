import { CurrencyPipe, DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartItem, CartService } from '../../core/cart/cart.service';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';

@Component({
  imports: [AppButtonDirective, AppCardDirective, AppFeedbackComponent, CurrencyPipe, RouterLink],
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CartComponent {
  readonly imageUrl = resolveApiContentUrl;
  readonly cart = inject(CartService);
  readonly auth = inject(AuthService);
  readonly pendingUndo = signal<{ message: string; items: CartItem[] } | null>(null);
  readonly feedback = signal('');
  readonly reconciling = signal(false);
  readonly reconcileError = signal(false);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private undoTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    this.reconcile();
    this.destroyRef.onDestroy(() => clearTimeout(this.undoTimer));
  }

  reconcile(): void {
    if (this.reconciling()) return;
    this.reconciling.set(true);
    this.reconcileError.set(false);
    this.cart.reconcile().pipe(finalize(() => this.reconciling.set(false))).subscribe((success) => this.reconcileError.set(!success));
  }

  clear(): void {
    const items = this.cart.items();
    if (!items.length) return;
    this.cart.clear();
    this.offerUndo('Se vació el carrito.', items);
  }

  removeItem(item: CartItem): void {
    this.cart.removeItem(item.variant.id);
    this.offerUndo(`Se eliminó ${item.product.name} del carrito.`, [item]);
  }

  undo(): void {
    const pending = this.pendingUndo();
    if (!pending) return;
    clearTimeout(this.undoTimer);
    for (const item of pending.items) this.cart.add(item.product, item.variant, item.quantity);
    const restoreFocus = this.document.activeElement === this.undoButton();
    this.pendingUndo.set(null);
    if (restoreFocus) queueMicrotask(() => this.focusHeading());
    this.announce(pending.items.length === 1 ? 'Producto restaurado.' : 'Carrito restaurado.');
  }

  private offerUndo(message: string, items: CartItem[]): void {
    clearTimeout(this.undoTimer);
    const previous = this.pendingUndo();
    const combined = new Map<number, CartItem>();
    for (const item of [...(previous?.items ?? []), ...items]) combined.set(item.variant.id, item);
    this.pendingUndo.set({ message: previous ? 'Se actualizaron productos del carrito.' : message, items: [...combined.values()] });
    queueMicrotask(() => this.undoButton()?.focus());
    this.undoTimer = setTimeout(() => {
      const restoreFocus = this.document.activeElement === this.undoButton();
      this.pendingUndo.set(null);
      if (restoreFocus) queueMicrotask(() => this.focusHeading());
    }, 5000);
  }

  private announce(message: string): void {
    this.feedback.set('');
    queueMicrotask(() => this.feedback.set(message));
  }

  private undoButton(): HTMLButtonElement | null { return this.host.nativeElement.querySelector<HTMLButtonElement>('.undo button'); }
  private focusHeading(): void { this.host.nativeElement.querySelector<HTMLElement>('#cart-title')?.focus(); }
}
