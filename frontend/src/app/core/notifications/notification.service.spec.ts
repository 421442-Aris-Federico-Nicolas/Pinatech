import { TestBed } from '@angular/core/testing';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  it('publishes a notification and runs its contextual action', () => {
    TestBed.configureTestingModule({ providers: [NotificationService] });
    const service = TestBed.inject(NotificationService);
    const action = vi.fn();

    service.warning('Alcanzaste el máximo.', 'Ver carrito').onAction().subscribe(action);

    expect(service.notification()).toEqual(expect.objectContaining({
      message: 'Alcanzaste el máximo.', action: 'Ver carrito', tone: 'warning',
    }));
    service.activate();
    expect(action).toHaveBeenCalledOnce();
    expect(service.notification()).toBeNull();
  });
});
