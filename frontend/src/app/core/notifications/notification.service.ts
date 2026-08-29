import { Injectable, OnDestroy, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

export type NotificationTone = 'info' | 'success' | 'warning' | 'error';

export interface NotificationOptions {
  tone?: NotificationTone;
  action?: string;
  duration?: number;
}

export interface AppNotification {
  id: number;
  message: string;
  tone: NotificationTone;
  action: string;
  hasAction: boolean;
  duration: number;
  paused: boolean;
  exiting: boolean;
}
export interface NotificationRef { onAction(): Observable<void>; }

interface ActiveNotification {
  notification: AppNotification;
  action: Subject<void>;
  duration: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationService implements OnDestroy {
  private static readonly exitDuration = 180;
  private readonly notificationState = signal<AppNotification | null>(null);
  private active: ActiveNotification | null = null;
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
    const duration = Math.max(0, options.duration ?? 6500);
    const next: ActiveNotification = {
      notification: {
        id,
        message,
        tone,
        action: options.action ?? '',
        hasAction: options.action !== undefined,
        duration,
        paused: false,
        exiting: false,
      },
      action,
      duration,
    };

    this.present(next);

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

  activate(id: number): void {
    if (this.active?.notification.id !== id || this.active.notification.exiting) return;
    this.active.action.next();
    this.dismiss(id);
  }

  pause(id: number, reason = 'interaction'): void {
    if (this.active?.notification.id !== id) return;
    const exiting = this.active.notification.exiting;
    const wasPaused = this.pauseReasons.size > 0;
    this.pauseReasons.add(reason);
    if (exiting || wasPaused) return;
    this.remainingDuration = Math.max(0, this.remainingDuration - (Date.now() - this.autoCloseStartedAt));
    this.clearAutoClose();
    this.active.notification = { ...this.active.notification, paused: true };
    this.notificationState.set(this.active.notification);
  }

  resume(id: number, reason = 'interaction'): void {
    if (this.active?.notification.id !== id) return;
    this.pauseReasons.delete(reason);
    if (this.active.notification.exiting) return;
    if (this.pauseReasons.size || this.autoCloseTimeout !== null) return;
    this.active.notification = { ...this.active.notification, paused: false };
    this.notificationState.set(this.active.notification);
    this.scheduleAutoClose();
  }

  dismiss(id: number): void {
    if (this.active?.notification.id !== id || this.active.notification.exiting) return;
    this.clearAutoClose();
    this.active.notification = { ...this.active.notification, paused: true, exiting: true };
    this.notificationState.set(this.active.notification);
    this.exitTimeout = setTimeout(() => this.finishDismissal(id), NotificationService.exitDuration);
  }

  ngOnDestroy(): void {
    this.clearAutoClose();
    this.clearExit();
    this.active?.action.complete();
  }

  private present(next: ActiveNotification): void {
    const previous = this.active;
    const inheritedPauseReasons = previous
      ? [...this.pauseReasons].filter((reason) => reason === 'pointer' || reason === 'focus')
      : [];
    this.clearAutoClose();
    this.clearExit();
    this.pauseReasons.clear();
    inheritedPauseReasons.forEach((reason) => this.pauseReasons.add(reason));
    if (this.pauseReasons.size) next.notification = { ...next.notification, paused: true };
    this.active = next;
    this.notificationState.set(next.notification);
    this.remainingDuration = next.duration;
    if (!this.pauseReasons.size) this.scheduleAutoClose();
    previous?.action.complete();
  }

  private finishDismissal(id: number): void {
    if (this.active?.notification.id !== id) return;
    this.exitTimeout = null;
    const dismissed = this.active;
    this.active = null;
    this.pauseReasons.clear();
    this.notificationState.set(null);
    dismissed.action.complete();
  }

  private clearAutoClose(): void {
    if (this.autoCloseTimeout !== null) clearTimeout(this.autoCloseTimeout);
    this.autoCloseTimeout = null;
  }

  private clearExit(): void {
    if (this.exitTimeout !== null) clearTimeout(this.exitTimeout);
    this.exitTimeout = null;
  }

  private scheduleAutoClose(): void {
    const id = this.active?.notification.id;
    if (id === undefined) return;
    this.autoCloseStartedAt = Date.now();
    this.autoCloseTimeout = setTimeout(() => this.dismiss(id), this.remainingDuration);
  }
}
