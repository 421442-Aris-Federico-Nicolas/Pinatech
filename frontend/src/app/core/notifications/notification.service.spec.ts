import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, vi } from 'vitest';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [NotificationService] });
  });

  afterEach(() => vi.useRealTimers());

  it('publishes a notification and runs its contextual action', () => {
    const service = TestBed.inject(NotificationService);
    const action = vi.fn();

    service.warning('Alcanzaste el máximo.', 'Ver carrito').onAction().subscribe(action);

    expect(service.notification()).toEqual(expect.objectContaining({
      message: 'Alcanzaste el máximo.', action: 'Ver carrito', tone: 'warning',
    }));
    service.activate();
    expect(action).toHaveBeenCalledOnce();
    expect(service.notification()?.exiting).toBe(true);
    vi.advanceTimersByTime(180);
    expect(service.notification()).toBeNull();
  });

  it('keeps consecutive notifications in FIFO order and starts each auto-close when shown', () => {
    const service = TestBed.inject(NotificationService);

    service.show('Primera', { duration: 1000 });
    service.show('Segunda', { duration: 1000 });
    service.show('Tercera', { duration: 1000 });

    expect(service.notification()?.message).toBe('Primera');
    vi.advanceTimersByTime(1000);
    expect(service.notification()).toEqual(expect.objectContaining({ message: 'Primera', exiting: true }));
    vi.advanceTimersByTime(180);
    expect(service.notification()).toEqual(expect.objectContaining({ message: 'Segunda', exiting: false }));

    service.dismiss();
    vi.advanceTimersByTime(180);
    expect(service.notification()?.message).toBe('Tercera');

    vi.advanceTimersByTime(1180);
    expect(service.notification()).toBeNull();
  });

  it('pauses the remaining timeout for overlapping pointer and focus interaction', () => {
    const service = TestBed.inject(NotificationService);
    service.show('Acción disponible', { action: 'Abrir', duration: 1000 });

    vi.advanceTimersByTime(400);
    service.pause('pointer');
    service.pause('focus');
    vi.advanceTimersByTime(2000);
    expect(service.notification()?.exiting).toBe(false);

    service.resume('pointer');
    vi.advanceTimersByTime(1000);
    expect(service.notification()?.exiting).toBe(false);

    service.resume('focus');
    vi.advanceTimersByTime(599);
    expect(service.notification()?.exiting).toBe(false);
    vi.advanceTimersByTime(1);
    expect(service.notification()?.exiting).toBe(true);
  });
});
