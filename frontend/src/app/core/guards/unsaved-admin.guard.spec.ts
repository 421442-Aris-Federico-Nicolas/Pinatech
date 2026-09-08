import { unsavedAdminGuard, UnsavedAdminComponent } from './unsaved-admin.guard';
import { routes } from '../../app.routes';

describe('unsavedAdminGuard', () => {
  const run = (component: UnsavedAdminComponent) => unsavedAdminGuard(component, {} as never, {} as never, {} as never);

  it('leaves clean admin routes without asking for confirmation', () => {
    const component = { hasUnsavedChanges: vi.fn(() => false), hasPendingOperation: vi.fn(() => false), confirmDiscard: vi.fn(() => false) };
    expect(run(component)).toBe(true);
    expect(component.confirmDiscard).not.toHaveBeenCalled();
  });

  it('delegates one confirmation to the component for dirty SPA navigation', () => {
    const component = { hasUnsavedChanges: vi.fn(() => true), hasPendingOperation: vi.fn(() => false), confirmDiscard: vi.fn(() => false) };
    expect(run(component)).toBe(false);
    expect(component.confirmDiscard).toHaveBeenCalledOnce();
  });

  it('blocks pending operations without opening a discard confirmation', () => {
    const component = { hasUnsavedChanges: vi.fn(() => true), hasPendingOperation: vi.fn(() => true), confirmDiscard: vi.fn(() => true) };
    expect(run(component)).toBe(false);
    expect(component.confirmDiscard).not.toHaveBeenCalled();
  });

  it('is registered on the lazy admin route so RouterLink navigation is guarded', () => {
    const adminRoute = routes.find((route) => route.path === 'admin');
    expect(adminRoute?.canDeactivate).toContain(unsavedAdminGuard);
  });
});
