import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    let meta = document.querySelector('meta[name="theme-color"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.setAttribute('name', 'theme-color');
      document.head.appendChild(meta);
    }
    meta.setAttribute('content', '#041f34');
  });

  function service(): ThemeService {
    TestBed.configureTestingModule({});
    const theme = TestBed.inject(ThemeService);
    theme.initialize();
    return theme;
  }

  it('starts dark without stored preference and leaves the root untouched', () => {
    const theme = service();

    expect(theme.theme()).toBe('dark');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
    expect(document.querySelector('meta[name="theme-color"]')?.getAttribute('content')).toBe('#041f34');
  });

  it('restores a stored light preference and applies it to the document', () => {
    localStorage.setItem('pinatech-theme', 'light');

    expect(service().theme()).toBe('light');
    expect(document.documentElement.dataset['theme']).toBe('light');
    expect(document.querySelector('meta[name="theme-color"]')?.getAttribute('content')).toBe('#FDF5E6');
  });

  it('toggles between themes, updates the document and persists the choice', () => {
    const theme = service();

    theme.toggle();
    expect(theme.theme()).toBe('light');
    expect(document.documentElement.dataset['theme']).toBe('light');
    expect(localStorage.getItem('pinatech-theme')).toBe('light');

    theme.toggle();
    expect(theme.theme()).toBe('dark');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
    expect(localStorage.getItem('pinatech-theme')).toBe('dark');
    expect(document.querySelector('meta[name="theme-color"]')?.getAttribute('content')).toBe('#041f34');
  });

  it('ignores unknown stored values and falls back to dark', () => {
    localStorage.setItem('pinatech-theme', 'sepia');

    expect(service().theme()).toBe('dark');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
  });

  it('keeps working in memory when storage is unavailable', () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });

    const theme = service();
    theme.toggle();

    expect(theme.theme()).toBe('light');
    expect(document.documentElement.dataset['theme']).toBe('light');
    getItem.mockRestore();
    setItem.mockRestore();
  });
});
