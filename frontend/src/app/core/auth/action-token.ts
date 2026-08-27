import { DOCUMENT } from '@angular/common';
import { inject } from '@angular/core';

export function consumeActionToken(): string {
  const document = inject(DOCUMENT);
  const browserWindow = document.defaultView;
  if (!browserWindow) return '';

  const url = new URL(browserWindow.location.href);
  const fragment = new URLSearchParams(url.hash.startsWith('#') ? url.hash.slice(1) : url.hash);
  const fragmentToken = fragment.get('token')?.trim() ?? '';
  const queryToken = url.searchParams.get('token')?.trim() ?? '';

  if (fragment.has('token') || url.searchParams.has('token')) {
    fragment.delete('token');
    url.searchParams.delete('token');
    url.hash = fragment.size ? fragment.toString() : '';
    browserWindow.history.replaceState(browserWindow.history.state, '', `${url.pathname}${url.search}${url.hash}`);
  }

  return fragmentToken || queryToken;
}
