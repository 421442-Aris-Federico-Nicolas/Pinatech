import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, vi } from 'vitest';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [NotificationService] });
  });

  afterEach(() => vi.useRealTimers());

  it('keeps only the latest notification in a burst and gives it a full timer', () => {
    const service = TestBed.inject(NotificationService);

    service.show('Primera', { duration: 1000 });
    vi.advanceTimersByTime(700);
    service.show('Segunda', { duration: 1000 });
    service.show('Tercera', { duration: 1000 });

    expect(service.notification()).toEqual(expect.objectContaining({ message: 'Tercera', exiting: false }));
    vi.advanceTimersByTime(999);
    expect(service.notification()?.exiting).toBe(false);
    vi.advanceTimersByTime(1);
    expect(service.notification()?.exiting).toBe(true);
    vi.advanceTimersByTime(180);
    expect(service.notification()).toBeNull();
  });

  it('replaces a repeated message and restarts its timer', () => {
    const service = TestBed.inject(NotificationService);
    service.show('Guardado', { duration: 1000 });
    const firstId = service.notification()!.id;

    vi.advanceTimersByTime(800);
    service.show('Guardado', { duration: 1000 });

    expect(service.notification()?.id).not.toBe(firstId);
    vi.advanceTimersByTime(999);
    expect(service.notification()?.exiting).toBe(false);
    vi.advanceTimersByTime(1);
    expect(service.notification()?.exiting).toBe(true);
  });

  it('completes a replaced action ref without emitting', () => {
    const service = TestBed.inject(NotificationService);
    const action = vi.fn();
    const complete = vi.fn();
    service.warning('Pedido pendiente', 'Ver pedido').onAction().subscribe({ next: action, complete });

    service.error('Falló la actualización', 'Reintentar');

    expect(action).not.toHaveBeenCalled();
    expect(complete).toHaveBeenCalledOnce();
    expect(service.notification()?.message).toBe('Falló la actualización');
  });

  it('emits only for an explicit action and never for close', () => {
    const service = TestBed.inject(NotificationService);
    const closedAction = vi.fn();
    const explicitAction = vi.fn();
    const closed = service.warning('Sin stock', 'Ver carrito');
    closed.onAction().subscribe(closedAction);
    const closedId = service.notification()!.id;

    service.dismiss(closedId);
    vi.advanceTimersByTime(180);
    expect(closedAction).not.toHaveBeenCalled();

    service.show('Nuevo stock', { action: 'Abrir' }).onAction().subscribe(explicitAction);
    const actionId = service.notification()!.id;
    service.activate(actionId);

    expect(explicitAction).toHaveBeenCalledOnce();
    expect(service.notification()?.exiting).toBe(true);
  });

  it('ignores stale interaction events and timers after replacement', () => {
    const service = TestBed.inject(NotificationService);
    const staleAction = vi.fn();
    service.show('Anterior', { action: 'Abrir', duration: 500 }).onAction().subscribe(staleAction);
    const staleId = service.notification()!.id;
    vi.advanceTimersByTime(300);

    service.show('Actual', { duration: 1000 });
    const currentId = service.notification()!.id;
    service.pause(staleId, 'pointer');
    service.resume(staleId, 'focus');
    service.activate(staleId);
    service.dismiss(staleId);

    vi.advanceTimersByTime(699);
    expect(service.notification()).toEqual(expect.objectContaining({ id: currentId, paused: false, exiting: false }));
    expect(staleAction).not.toHaveBeenCalled();
    vi.advanceTimersByTime(301);
    expect(service.notification()).toEqual(expect.objectContaining({ id: currentId, exiting: true }));
  });

  it('cancels a stale exit when replaced and gives the replacement its full duration', () => {
    const service = TestBed.inject(NotificationService);
    service.show('Anterior');
    const staleId = service.notification()!.id;
    service.dismiss(staleId);
    vi.advanceTimersByTime(100);

    service.show('Actual', { duration: 1000 });
    const currentId = service.notification()!.id;
    vi.advanceTimersByTime(80);

    expect(service.notification()).toEqual(expect.objectContaining({ id: currentId, exiting: false }));
    vi.advanceTimersByTime(919);
    expect(service.notification()?.exiting).toBe(false);
    vi.advanceTimersByTime(1);
    expect(service.notification()?.exiting).toBe(true);
  });

  it('preserves focus pause when an exiting notification is replaced', () => {
    const service = TestBed.inject(NotificationService);
    service.show('Anterior', { duration: 1000 });
    const previousId = service.notification()!.id;
    service.pause(previousId, 'focus');
    service.dismiss(previousId);

    service.show('Actual', { action: 'Continuar', duration: 1200 });
    const currentId = service.notification()!.id;
    expect(service.notification()).toEqual(expect.objectContaining({ id: currentId, paused: true, exiting: false }));
    vi.advanceTimersByTime(2000);
    expect(service.notification()?.exiting).toBe(false);

    service.resume(currentId, 'focus');
    vi.advanceTimersByTime(1199);
    expect(service.notification()?.exiting).toBe(false);
    vi.advanceTimersByTime(1);
    expect(service.notification()?.exiting).toBe(true);
  });

  it('preserves active pointer and focus pauses across replacement and starts a full timer after both clear', () => {
    const service = TestBed.inject(NotificationService);
    service.show('Anterior', { duration: 1000 });
    const previousId = service.notification()!.id;
    vi.advanceTimersByTime(400);
    service.pause(previousId, 'pointer');
    service.pause(previousId, 'focus');

    service.show('Actual', { duration: 1200 });
    const currentId = service.notification()!.id;
    expect(service.notification()).toEqual(expect.objectContaining({ id: currentId, paused: true, exiting: false }));

    service.resume(currentId, 'pointer');
    vi.advanceTimersByTime(2000);
    expect(service.notification()).toEqual(expect.objectContaining({ id: currentId, paused: true, exiting: false }));

    service.resume(currentId, 'focus');
    expect(service.notification()?.paused).toBe(false);
    vi.advanceTimersByTime(1199);
    expect(service.notification()?.exiting).toBe(false);
    vi.advanceTimersByTime(1);
    expect(service.notification()?.exiting).toBe(true);
  });

  it('completes the old action while preserving focus for the replacement action', () => {
    const service = TestBed.inject(NotificationService);
    const previousAction = vi.fn();
    const previousComplete = vi.fn();
    service.show('Anterior', { action: 'Abrir', duration: 1000 }).onAction().subscribe({
      next: previousAction,
      complete: previousComplete,
    });
    const previousId = service.notification()!.id;
    service.pause(previousId, 'focus');

    const replacementAction = vi.fn();
    service.show('Actual', { action: 'Continuar', duration: 1000 }).onAction().subscribe(replacementAction);
    const currentId = service.notification()!.id;
    expect(previousAction).not.toHaveBeenCalled();
    expect(previousComplete).toHaveBeenCalledOnce();
    expect(service.notification()?.paused).toBe(true);

    service.resume(currentId, 'focus');
    service.activate(currentId);
    expect(replacementAction).toHaveBeenCalledOnce();
    expect(service.notification()?.exiting).toBe(true);
  });

  it('pauses the remaining timeout until all overlapping reasons resume', () => {
    const service = TestBed.inject(NotificationService);
    service.show('Acción disponible', { action: 'Abrir', duration: 1000 });
    const id = service.notification()!.id;

    vi.advanceTimersByTime(400);
    service.pause(id, 'pointer');
    service.pause(id, 'focus');
    expect(service.notification()?.paused).toBe(true);
    vi.advanceTimersByTime(2000);
    expect(service.notification()?.exiting).toBe(false);

    service.resume(id, 'pointer');
    vi.advanceTimersByTime(1000);
    expect(service.notification()?.paused).toBe(true);
    expect(service.notification()?.exiting).toBe(false);

    service.resume(id, 'focus');
    expect(service.notification()?.paused).toBe(false);
    vi.advanceTimersByTime(599);
    expect(service.notification()?.exiting).toBe(false);
    vi.advanceTimersByTime(1);
    expect(service.notification()?.exiting).toBe(true);
  });
});
