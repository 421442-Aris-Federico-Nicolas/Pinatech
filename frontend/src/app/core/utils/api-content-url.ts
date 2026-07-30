import { environment } from '../../../environments/environment';

export function resolveApiContentUrl(contentUrl: string): string {
  if (!contentUrl) return '';
  if (/^https?:\/\//i.test(contentUrl)) return contentUrl;

  const origin = globalThis.location?.origin ?? 'http://localhost';
  const baseUrl = new URL(environment.apiBaseUrl, origin);
  return new URL(contentUrl, contentUrl.startsWith('/') ? baseUrl.origin : `${baseUrl.toString().replace(/\/$/, '')}/`).toString();
}
