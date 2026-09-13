# Public image caching

Local card images use `/api/products/images/{id}/thumbnail-{width}.webp`, where `width` is `320` or `640`; detail and upload responses keep `/content`. Hero images use `/api/home/hero/images/{id}/{width}.webp`, where `width` is `480`, `720`, `1280` or `1920`. External legacy URLs are unchanged (no remote fetch). Product, home hero and home section uploads are stored as WebP. Existing JPEG/PNG files generate a WebP derivative on first public or admin access, so no database migration or backfill is needed. Ticket attachments and payment proofs keep their existing evidence-preserving storage flow.

Derived files live alongside originals under `app.storage.root`. Responsive files use `<storage-key>.public-w{width}-v2.webp` and `<storage-key>.thumbnail-w{width}-v3.webp`; the unversioned API keeps the legacy `<storage-key>.public-v1.webp` and `<storage-key>.thumbnail-v2.webp` files. Product variants flatten transparency onto white, and all variants preserve aspect ratio. JPEG EXIF orientation is applied before conversion. Cache hits do not open or decode the original. Generation reuses the upload limits (5 MiB, 6000 per axis and 12 million pixels). Bounded JVM locks serialize generation/deletion, at most 16 cold derivation requests are admitted, and a global two-permit limit bounds concurrent decode/encode memory use. Atomic file publication prevents partial reads across processes. The volume must support atomic moves. Deletion after commit removes the original, responsive WebP derivatives and legacy thumbnails. Cached derivatives can be removed to reclaim disk; they regenerate lazily.

Responsive image endpoints return `public, max-age=31536000, immutable` and `image/webp`; legacy public image endpoints keep their seven-day TTL. `/api/home/hero/current/{device}/{width}.webp` is only a temporary discovery redirect and returns `no-store`. Uploads always allocate a new image ID and UUID file; no API replaces bytes under an existing ID. Do not overwrite originals or change a derivative recipe for an existing public URL: version the suffix and public URL if the recipe changes. The origin checks that the owning product, hero or section is active on every request, including disk-cache hits. A responsive image already cached by a browser can remain visible for one year after deletion or deactivation. Purge Cloudflare for immediate edge removal, but do not rely on a CDN purge to clear browser caches.

## Cloudflare Cache Rule

Create one Cache Rule for the `api.pinatech.com.ar` zone with this custom filter expression:

```text
(http.host eq "api.pinatech.com.ar" and http.request.method eq "GET" and
 (http.request.uri.path wildcard "/api/home/hero/images/*/*.webp" or
  http.request.uri.path wildcard "/api/products/images/*/thumbnail-*.webp") and
 not any(http.request.headers["authorization"][*] ne "") and http.cookie eq "")
```

Set **Cache eligibility** to `Eligible for cache`, **Edge TTL** to `Use cache-control header if present`, and **Browser TTL** to `Respect existing headers`. Do not customize the cache key. Keep `/api/home/hero/current/*`, legacy `/content` and `/thumbnail`, JSON, admin, order, payment, error, redirect, `Set-Cookie` and authenticated responses outside this rule. Never enable a broad cache-everything rule for `/api/*`.

After deployment and rule activation, request one responsive URL twice and verify the first response is `MISS` and the second is `HIT`, both are `200 image/webp`, and both retain `Cache-Control: public, max-age=31536000, immutable`. Also verify the matching `current` hero URL remains `no-store` and never becomes `HIT`. No real Cloudflare configuration is changed by this repository.
