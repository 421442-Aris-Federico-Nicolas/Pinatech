# Public product images

Local card images use `/api/products/images/{id}/thumbnail`; detail and upload responses keep `/content`. External legacy URLs are unchanged (no remote fetch). No database migration or backfill is needed: existing and future local uploads generate a JPEG on first access, at most 640x640, preserving aspect ratio without upscaling. Transparency is flattened onto white.

Derived files live alongside originals as `<storage-key>.thumbnail-v1.jpg` under `app.storage.root`. Cache hits do not open or decode the original. Generation reuses the upload limits (5 MiB, 6000 per axis, 12 million pixels, JPEG/PNG only). Bounded JVM locks serialize generation/deletion; atomic file publication prevents partial reads across processes. The volume must support atomic moves. Deletion after commit removes both files. Cached derivatives can be removed to reclaim disk; they regenerate lazily.

Both successful image endpoints return `public, max-age=604800, immutable`. Uploads always allocate a new image ID and UUID file; no API replaces bytes under an existing ID. Do not overwrite originals or change the thumbnail recipe for an existing public URL: version the URL if the recipe changes. The origin checks that the product is active on every request, including disk-cache hits. Already cached public images can remain visible for seven days after deletion/deactivation; use a CDN purge if immediate removal is needed (browser caches still obey their TTL).

## Optional CDN rule (documentation only)

Do not enable a broad "cache everything" rule. Allow caching ONLY anonymous GET/HEAD requests matching `^/api/products/images/[0-9]+/(content|thumbnail)$`, with no Authorization header or session/auth cookies. Respect origin Cache-Control and cache only successful image/jpeg or image/png responses; never cache errors, redirects, Set-Cookie responses, JSON, admin, orders, payments or authenticated requests. Bypass all other API paths, including `/api/products`, `/cards` and product detail JSON. No real Cloudflare configuration is changed here.
