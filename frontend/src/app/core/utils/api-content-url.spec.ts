import { environment } from '../../../environments/environment';
import { resolveApiContentUrl } from './api-content-url';

describe('resolveApiContentUrl', () => {
  it('resolves backend /api paths against the API origin without duplicating /api', () => {
    const origin = new URL(environment.apiBaseUrl, globalThis.location.origin).origin;

    expect(resolveApiContentUrl('/api/products/1/images/2/content')).toBe(`${origin}/api/products/1/images/2/content`);
  });

  it('keeps absolute content URLs unchanged', () => {
    expect(resolveApiContentUrl('https://cdn.example.com/image.png')).toBe('https://cdn.example.com/image.png');
  });
});
