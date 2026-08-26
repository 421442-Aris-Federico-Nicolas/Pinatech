import { Injectable, OnDestroy, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

export type NotificationTone = 'info' | 'success' | 'warning' | 'error';

export interface NotificationOptions {
  tone?: NotificationTone;
  action?: string;
  duration?: number;
}

export interface AppNotification { id: number; message: string; tone: NotificationTone; action: string; hasAction: boolean; exiting: boolean; }
export interface NotificationRef { onAction(): Observable<void>; }

interface QueuedNotification {
  notification: AppNotification;
  action: Subject<void>;
  duration: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationService implements OnDestroy {
  private static readonly exitDuration = 180;
  private readonly notificationState = signal<AppNotification | null>(null);
  private readonly queue: QueuedNotification[] = [];
  private active: QueuedNotification | null = null;
  private autoCloseTimeout: ReturnType<typeof setTimeout> | null = null;
  private exitTimeout: ReturnType<typeof setTimeout> | null = null;
  private readonly pauseReasons = new Set<string>();
  private autoCloseStartedAt = 0;
  private remainingDuration = 0;
  private nextId = 0;
  readonly notification = this.notificationState.asReadonly();

  show(message: string, options: NotificationOptions = {}): NotificationRef {
    const tone = options.tone ?? 'info';
    const id = ++this.nextId;
    const action = new Subject<void>();
    const queued: QueuedNotification = {
      notification: { id, message, tone, action: options.action ?? 'Cerrar', hasAction: options.action !== undefined, exiting: false },
      action,
      duration: options.duration ?? 6500,
    };

    if (this.active) this.queue.push(queued);
    else this.present(queued);

    return { onAction: () => action.asObservable() };
  }

  success(message: string, action?: string) {
    return this.show(message, { tone: 'success', action });
  }

  warning(message: string, action?: string) {
    return this.show(message, { tone: 'warning', action, duration: 8500 });
  }

  error(message: string, action?: string) {
    return this.show(message, { tone: 'error', action, duration: 9000 });
  }

  activate(): void {
    if (!this.active || this.active.notification.exiting) return;
    const id = this.active.notification.id;
    this.active.action.next();
    this.dismiss(id);
  }

  pause(reason = 'interaction'): void {
    if (!this.active || this.active.notification.exiting) return;
    const wasPaused = this.pauseReasons.size > 0;
    this.pauseReasons.add(reason);
    if (wasPaused) return;
    this.remainingDuration = Math.max(0, this.remainingDuration - (Date.now() - this.autoCloseStartedAt));
    this.clearAutoClose();
  }

  resume(reason = 'interaction'): void {
    this.pauseReasons.delete(reason);
    if (this.pauseReasons.size || !this.active || this.active.notification.exiting || this.autoCloseTimeout !== null) return;
    this.scheduleAutoClose();
  }

  dismiss(id = this.notificationState()?.id): void {
    if (id === undefined || this.active?.notification.id !== id || this.active.notification.exiting) return;
    this.clearAutoClose();
    this.pauseReasons.clear();
    this.active.notification = { ...this.active.notification, exiting: true };
    this.notificationState.set(this.active.notification);
    this.exitTimeout = setTimeout(() => this.finishDismissal(id), NotificationService.exitDuration);
  }

  ngOnDestroy(): void {
    this.clearAutoClose();
    if (this.exitTimeout !== null) clearTimeout(this.exitTimeout);
    this.active?.action.complete();
    for (const queued of this.queue) queued.action.complete();
  }

  private present(queued: QueuedNotification): void {
    this.active = queued;
    this.notificationState.set(queued.notification);
    this.remainingDuration = queued.duration;
    this.pauseReasons.clear();
    this.scheduleAutoClose();
  }

  private finishDismissal(id: number): void {
    if (this.active?.notification.id !== id) return;
    this.exitTimeout = null;
    this.active.action.complete();
    this.active = null;
    this.notificationState.set(null);
    const next = this.queue.shift();
    if (next) this.present(next);
  }

  private clearAutoClose(): void {
    if (this.autoCloseTimeout !== null) clearTimeout(this.autoCloseTimeout);
    this.autoCloseTimeout = null;
  }

  private scheduleAutoClose(): void {
    const id = this.active?.notification.id;
    if (id === undefined) return;
    this.autoCloseStartedAt = Date.now();
    this.autoCloseTimeout = setTimeout(() => this.dismiss(id), this.remainingDuration);
  }
}
