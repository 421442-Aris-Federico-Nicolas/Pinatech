export function safeReturnUrl(value: string | null, fallback = '/'): string {
  return value?.startsWith('/') && !value.startsWith('//') && !value.includes('://') && !value.includes('\\') ? value : fallback;
}
