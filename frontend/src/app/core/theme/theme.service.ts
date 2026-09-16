import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';

export type Theme = 'dark' | 'light';

const STORAGE_KEY = 'pinatech-theme';
const DARK_META_COLOR = '#041f34';
const LIGHT_META_COLOR = '#FDF5E6';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  readonly theme = signal<Theme>('dark');

  initialize(): void {
    this.set(this.stored() ?? 'dark', false);
  }

  toggle(): void {
    this.set(this.theme() === 'dark' ? 'light' : 'dark');
  }

  private set(theme: Theme, persist = true): void {
    this.theme.set(theme);
    if (theme === 'light') this.document.documentElement.dataset['theme'] = 'light';
    else delete this.document.documentElement.dataset['theme'];
    this.document.querySelector('meta[name="theme-color"]')
      ?.setAttribute('content', theme === 'light' ? LIGHT_META_COLOR : DARK_META_COLOR);
    if (persist) {
      try {
        localStorage.setItem(STORAGE_KEY, theme);
      } catch {
        // La preferencia queda solo en memoria (modo privado o almacenamiento bloqueado).
      }
    }
  }

  private stored(): Theme | null {
    try {
      const value = localStorage.getItem(STORAGE_KEY);
      return value === 'light' || value === 'dark' ? value : null;
    } catch {
      return null;
    }
  }
}
