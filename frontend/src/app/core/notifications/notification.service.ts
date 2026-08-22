import { Injectable, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

export type NotificationTone = 'info' | 'success' | 'warning' | 'error';

export interface NotificationOptions {
  tone?: NotificationTone;
  action?: string;
  duration?: number;
}

export interface AppNotification { id: number; message: string; tone: NotificationTone; action: string; }
export interface NotificationRef { onAction(): Observable<void>; }

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly notificationState = signal<AppNotification | null>(null);
  private action = new Subject<void>();
  private timeout: ReturnType<typeof setTimeout> | null = null;
  private nextId = 0;
  readonly notification = this.notificationState.asReadonly();

  show(message: string, options: NotificationOptions = {}): NotificationRef {
    const tone = options.tone ?? 'info';
    this.closeCurrent();
    const id = ++this.nextId;
    this.action = new Subject<void>();
    this.notificationState.set({ id, message, tone, action: options.action ?? 'Cerrar' });
    this.timeout = setTimeout(() => this.dismiss(id), options.duration ?? 6500);
    return { onAction: () => this.action.asObservable() };
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
    const notification = this.notificationState();
    if (!notification) return;
    this.action.next();
    this.dismiss(notification.id);
  }

  dismiss(id = this.notificationState()?.id): void {
    if (id === undefined || this.notificationState()?.id !== id) return;
    this.closeCurrent();
  }

  private closeCurrent(): void {
    if (this.timeout !== null) clearTimeout(this.timeout);
    this.timeout = null;
    this.notificationState.set(null);
    this.action.complete();
  }
}
