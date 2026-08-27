import { CurrencyPipe, DOCUMENT } from '@angular/common';
import { AfterRenderRef, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, Injector, afterNextRender, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CartItem, CartService } from '../../core/cart/cart.service';
import { resolveApiContentUrl } from '../../core/utils/api-content-url';
import { AppButtonDirective } from '../../shared/ui/app-button.directive';
import { AppCardDirective } from '../../shared/ui/app-card.directive';
import { AppFeedbackComponent } from '../../shared/ui/feedback/app-feedback.component';

const EASE_OUT = 'cubic-bezier(0.23, 1, 0.32, 1)';
const EASE_IN_OUT = 'cubic-bezier(0.77, 0, 0.175, 1)';

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
  private readonly injector = inject(Injector);
  private readonly animations = new Set<Animation>();
  private readonly ghosts = new Set<HTMLElement>();
  private pendingRender?: AfterRenderRef;
  private undoTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    this.reconcile();
    this.destroyRef.onDestroy(() => {
      clearTimeout(this.undoTimer);
      this.cancelMotion();
    });
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
    this.cancelMotion();
    this.cart.clear();
    this.offerUndo('Se vació el carrito.', items);
  }

  removeItem(item: CartItem): void {
    const before = this.prepareMotion();
    if (before) this.animateRemovedRow(item.variant.id, before.get(`row-${item.variant.id}`));
    this.cart.removeItem(item.variant.id);
    this.offerUndo(`Se eliminó ${item.product.name} del carrito.`, [item]);
    if (before) this.animateAfterRender(before);
  }

  undo(): void {
    const pending = this.pendingUndo();
    if (!pending) return;
    const before = this.prepareMotion();
    clearTimeout(this.undoTimer);
    for (const item of pending.items) this.cart.add(item.product, item.variant, item.quantity);
    const restoreFocus = this.document.activeElement === this.undoButton();
    this.pendingUndo.set(null);
    if (restoreFocus) this.focusHeading();
    this.announce(pending.items.length === 1 ? 'Producto restaurado.' : 'Carrito restaurado.');
    if (before) this.animateAfterRender(before);
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

  private prepareMotion(): Map<string, DOMRect> | null {
    if (this.reducedMotion()) return null;
    const positions = this.measureMotionElements();
    this.cancelMotion();
    return positions;
  }

  private animateRemovedRow(variantId: number, bounds?: DOMRect): void {
    const row = this.host.nativeElement.querySelector<HTMLElement>(`[data-cart-motion="row-${variantId}"]`);
    const view = this.document.defaultView;
    if (!row || !bounds || !view || typeof row.animate !== 'function') return;

    const ghost = row.cloneNode(true) as HTMLElement;
    ghost.classList.add('cart-motion-ghost');
    ghost.setAttribute('aria-hidden', 'true');
    ghost.setAttribute('inert', '');
    ghost.inert = true;
    Object.assign(ghost.style, {
      backgroundColor: view.getComputedStyle(row).backgroundColor,
      boxSizing: 'border-box',
      height: `${bounds.height}px`,
      left: `${bounds.left}px`,
      margin: '0',
      pointerEvents: 'none',
      position: 'fixed',
      top: `${bounds.top}px`,
      transformOrigin: 'center',
      width: `${bounds.width}px`,
      zIndex: '1000',
    });
    this.document.body.appendChild(ghost);
    this.ghosts.add(ghost);

    const animation = ghost.animate(
      [
        { opacity: 1, transform: 'translateY(0) scale(1)' },
        { opacity: 0, transform: 'translateY(-.35rem) scale(.98)' },
      ],
      { duration: 180, easing: EASE_OUT },
    );
    this.trackAnimation(animation, ghost);
  }

  private animateAfterRender(before: Map<string, DOMRect>): void {
    this.pendingRender = afterNextRender({
      read: () => {
        this.pendingRender = undefined;
        const after = this.motionElements();
        const layoutIsNew = after.has('layout') && !before.has('layout');

        for (const [key, element] of after) {
          const previous = before.get(key);
          if (previous) {
            if (key === 'layout') continue;
            const current = element.getBoundingClientRect();
            const deltaX = previous.left - current.left;
            const deltaY = previous.top - current.top;
            if (deltaX || deltaY) {
              this.startAnimation(element, [
                { transform: `translate3d(${deltaX}px, ${deltaY}px, 0)` },
                { transform: 'translate3d(0, 0, 0)' },
              ], { duration: 240, easing: EASE_IN_OUT });
            }
          } else if (!layoutIsNew || key === 'layout') {
            this.startAnimation(element, [
              { opacity: 0, transform: 'translateY(-.5rem) scale(.98)' },
              { opacity: 1, transform: 'none' },
            ], { duration: 200, easing: EASE_OUT });
          }
        }
      },
    }, { injector: this.injector });
  }

  private measureMotionElements(): Map<string, DOMRect> {
    return new Map([...this.motionElements()].map(([key, element]) => [key, element.getBoundingClientRect()]));
  }

  private motionElements(): Map<string, HTMLElement> {
    return new Map([...this.host.nativeElement.querySelectorAll<HTMLElement>('[data-cart-motion]')]
      .map((element) => [element.dataset['cartMotion'] as string, element]));
  }

  private startAnimation(element: HTMLElement, keyframes: Keyframe[], options: KeyframeAnimationOptions): void {
    if (typeof element.animate !== 'function') return;
    this.trackAnimation(element.animate(keyframes, options));
  }

  private trackAnimation(animation: Animation, ghost?: HTMLElement): void {
    this.animations.add(animation);
    const cleanup = () => {
      this.animations.delete(animation);
      if (ghost) {
        this.ghosts.delete(ghost);
        ghost.remove();
      }
    };
    void animation.finished.then(cleanup, cleanup);
  }

  private cancelMotion(): void {
    this.pendingRender?.destroy();
    this.pendingRender = undefined;
    for (const animation of this.animations) animation.cancel();
    this.animations.clear();
    for (const ghost of this.ghosts) ghost.remove();
    this.ghosts.clear();
  }

  private reducedMotion(): boolean {
    return this.document.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  }

  private undoButton(): HTMLButtonElement | null { return this.host.nativeElement.querySelector<HTMLButtonElement>('.undo button'); }
  private focusHeading(): void { this.host.nativeElement.querySelector<HTMLElement>('#cart-title')?.focus(); }
}
