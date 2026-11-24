# Edge protection and hosting cost

What drives the Railway bill, how vulnerability scanners are stopped, and the dashboard settings the
repository cannot apply by itself.

## Where the cost comes from

Railway bills reserved memory per minute. With `-XX:MaxRAMPercentage=75` the JVM sized its heap from the
host and settled around 800–900 MiB while CPU idled near 0 and egress stayed in the tens of MB per day.
Scanner traffic (hundreds of `GET /.env`, `/phpinfo.php` answered in ~2 ms) is a security nuisance, not a
cost driver.

The Dockerfile now pins the JVM to a small heap that is handed back to the OS after spikes. Same workload on
the same image (catalog load plus four concurrent 12 MP uploads with their WebP derivatives), measured with
`docker stats` after ~3 minutes idle:

| JVM options                                   | Idle after start | Idle after the workload |
|-----------------------------------------------|------------------|-------------------------|
| `-XX:MaxRAMPercentage=75` (previous)          | ~550–630 MiB     | ~840 MiB                |
| Dockerfile default (G1, 384 MiB heap, trims)  | ~360 MiB         | ~355 MiB                |

## Layers

1. **Cloudflare** (`api.pinatech.com.ar` is proxied): blocks scanners and floods before they reach Railway,
   caches responsive images (see `backend/IMAGE_CACHE.md`) and stamps every request with an origin secret.
2. **`EdgeProtectionFilter`** in the backend, ahead of Spring Security:
   - takes the client address from `CF-Connecting-IP` (`CLIENT_IP_HEADER`), so the existing login,
     registration, guest-checkout and upload limiters see the real client and cannot be dodged by forging
     `X-Forwarded-For`;
   - with `ORIGIN_SHARED_SECRET` set, answers 403 to anything that skipped Cloudflare (health checks excepted);
   - answers an empty 404 to every path outside `/api/`, and bans for `EDGE_BAN_DURATION` (1 h) an address
     that requests `EDGE_PROBE_BAN_THRESHOLD` (5) scanner paths such as `/.env`, `*.php`, `/.git/…`,
     `credentials.json` within 10 minutes;
   - allows `EDGE_RATE_LIMIT_MAX_REQUESTS` (300) API requests per address per minute, then 429 with
     `Retry-After` and CORS headers. Payment and shipping webhooks are never limited.

   State is in memory, which is correct for the single Railway replica. If the service ever scales out, each
   replica keeps its own counters.

## Rollout

Order matters for the origin secret: the Cloudflare rule must be live before Railway enforces it.

### 1. Railway variables

- `JAVA_TOOL_OPTIONS`: **a Railway variable overrides the Dockerfile default.** Delete the variable, or set
  it to the value in `backend/railway.env.example`. Leaving the old `-XX:MaxRAMPercentage=75.0` keeps the
  old memory use.
- `SERVER_TOMCAT_THREADS_MAX=40` is the default; nothing to set.
- `CLIENT_IP_HEADER` defaults to `CF-Connecting-IP` in the `prod` profile; nothing to set.

Deploy, then watch **Metrics → Memory** for a day. Expect roughly 350–450 MiB. If an admin image upload
ever fails with an out-of-memory restart, raise `-Xmx` to 448m.

### 2. Cloudflare (zone `pinatech.com.ar`)

**Transform rule: origin secret.** Rules → Transform Rules → Modify Request Header → Create.
- When: custom filter `(http.host eq "api.pinatech.com.ar")`.
- Then: Set static, header `X-Origin-Secret`, value: a random string of 32+ characters
  (`openssl rand -hex 32`). Keep it out of the repository.

**WAF custom rule: scanners.** Security → WAF → Custom rules → Create, action **Block**:

```text
(http.host eq "api.pinatech.com.ar" and not http.request.uri.path contains "/.well-known/" and (not http.request.uri.path contains "/api/" or http.request.uri.path contains "/." or http.request.uri.path contains ".php" or http.request.uri.path contains "phpinfo"))
```

Paste it with **Edit expression**, not into the builder's Value box. The dashboard rejected versions using
`starts_with()`, `lower()` and `wildcard`; `eq`, `contains` and `not` work on the free plan. `/.well-known/`
stays open so Railway can renew the certificate of the custom domain. Railway health checks reach the
container directly and are not affected.

**Rate limiting rule: JSON API floods.** Security → WAF → Rate limiting rules → Create. The free plan allows one
rule with short periods; use the closest values the dashboard offers.

```text
(http.host eq "api.pinatech.com.ar" and http.request.uri.path contains "/api/" and not http.request.uri.path contains "/images/" and not http.request.uri.path contains "/webhooks/")
```

Characteristics: IP. Threshold: 50 requests per 10 seconds. Action: Block for 10 seconds. Images are
excluded because a catalog page legitimately loads dozens of them and Cloudflare serves them from cache.

**Leave Bot Fight Mode off for this zone.** On the free plan it cannot be bypassed per path, and it may
challenge Mercado Pago and Zipnova webhooks.

### 3. Railway: enforce the edge

After the transform rule is active:

1. Set `ORIGIN_SHARED_SECRET` to the same value as the Cloudflare header and deploy.
2. Check: `curl -I https://api.pinatech.com.ar/api/health` returns 200 (through Cloudflare).
3. Settings → Networking: remove the generated `*.up.railway.app` domain if one exists. With the secret in
   place it only returns 403 anyway, but removing it hides the origin.

To roll back, delete `ORIGIN_SHARED_SECRET`; the filter then serves requests without the header again.

### 4. Vercel (optional)

The storefront is static; scanners there cost only edge requests. If **Usage** shows unusual Edge Requests,
add a Firewall custom rule that denies paths containing `.php`, `.env` or `/.git`, and enable the managed
bot rules available on the plan.

## Settings reference

| Variable                        | Default                        | Meaning                                      |
|---------------------------------|--------------------------------|----------------------------------------------|
| `EDGE_PROTECTION_ENABLED`       | `true`                         | Turns the filter off entirely.               |
| `CLIENT_IP_HEADER`              | empty; `CF-Connecting-IP` in prod | Header holding the real client address.   |
| `ORIGIN_SHARED_SECRET`          | empty (not enforced)           | Required `X-Origin-Secret` value, 24+ chars. |
| `EDGE_RATE_LIMIT_MAX_REQUESTS`  | `300`                          | API requests per address per window.         |
| `EDGE_RATE_LIMIT_WINDOW`        | `PT1M`                         | Rate-limit window.                           |
| `EDGE_PROBE_BAN_THRESHOLD`      | `5`                            | Scanner paths before a ban.                  |
| `EDGE_PROBE_WINDOW`             | `PT10M`                        | Window for counting scanner paths.           |
| `EDGE_BAN_DURATION`             | `PT1H`                         | Ban length.                                  |
| `EDGE_PROTECTION_MAX_BUCKETS`   | `10000`                        | Max tracked addresses per map (fails open).  |
