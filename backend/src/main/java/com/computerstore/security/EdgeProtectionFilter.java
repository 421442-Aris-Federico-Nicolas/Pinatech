package com.computerstore.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * First line of defence in the application, ahead of Spring Security.
 *
 * <ul>
 *   <li>Resolves the real client address from the edge proxy header (Cloudflare's CF-Connecting-IP), so every
 *       existing per-IP limiter that calls {@code getRemoteAddr()} sees the client and not the proxy.</li>
 *   <li>Optionally rejects requests that did not come through the edge (shared secret header).</li>
 *   <li>Answers anything outside the API with an empty 404, and temporarily bans addresses that keep probing for
 *       secrets or PHP files.</li>
 *   <li>Applies a generous per-address request budget to the API.</li>
 * </ul>
 *
 * All state is in memory: the backend runs as a single replica.
 */
public class EdgeProtectionFilter extends OncePerRequestFilter {

    public static final String ORIGIN_SECRET_HEADER = "X-Origin-Secret";

    private static final Logger log = LoggerFactory.getLogger(EdgeProtectionFilter.class);

    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    /** Paths no real client of this API ever requests; seeing them means a vulnerability scanner. */
    private static final Pattern PROBE = Pattern.compile(
            "/\\.(?!well-known(?:/|$))[^/]+"
                    + "|\\.(?:php\\d?|phtml|asp|aspx|jsp|cgi|env|bak|old|orig|save|swp|sql|ini|log)(?:$|[/~])"
                    + "|~$"
                    + "|/(?:wp-admin|wp-login|wp-content|wp-includes|wordpress|xmlrpc|phpmyadmin|pma|cgi-bin|phpinfo"
                    + "|_profiler|_environment|server-status|server-info|vendor/phpunit|boaform|hnap1)(?:$|[/.])");

    /** Secret-looking files requested outside the API (credentials.json, sa.json, config.yml...). */
    private static final Pattern CONFIG_FILE = Pattern.compile(
            "\\.(?:json|ya?ml|xml|properties|conf|config|pem|key|p12|tar|gz|zip|rar|7z)$");

    private final Settings settings;
    private final CorsConfigurationSource corsConfigurationSource;
    private final Clock clock;
    private final byte[] originSecret;

    private final ConcurrentHashMap<String, Window> requestWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> probeWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> bannedUntil = new ConcurrentHashMap<>();
    private final AtomicLong checks = new AtomicLong();

    public EdgeProtectionFilter(Settings settings, CorsConfigurationSource corsConfigurationSource, Clock clock) {
        this.settings = settings;
        this.corsConfigurationSource = corsConfigurationSource;
        this.clock = clock;
        this.originSecret = settings.originSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!settings.enabled()) {
            chain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI().toLowerCase(Locale.ROOT);

        if (path.startsWith("/actuator/health")) {
            chain.doFilter(request, response);
            return;
        }
        if (originSecret.length > 0 && !hasOriginSecret(request)) {
            respondEmpty(response, HttpStatus.FORBIDDEN);
            return;
        }

        String clientAddress = resolveClientAddress(request);
        HttpServletRequest forwarded = new ClientAddressRequest(request, clientAddress);
        long now = clock.millis();
        housekeeping(now);

        if (isBanned(clientAddress, now)) {
            respondEmpty(response, HttpStatus.FORBIDDEN);
            return;
        }
        if (isProbe(path)) {
            recordProbe(clientAddress, path, now);
            respondEmpty(response, HttpStatus.NOT_FOUND);
            return;
        }
        if (!isRoutable(path)) {
            respondEmpty(response, HttpStatus.NOT_FOUND);
            return;
        }
        if (isRateLimited(path)) {
            long retryAfterMs = consumeRequestBudget(clientAddress, now);
            if (retryAfterMs > 0) {
                respondTooManyRequests(forwarded, response, retryAfterMs);
                return;
            }
        }
        chain.doFilter(forwarded, response);
    }

    private boolean hasOriginSecret(HttpServletRequest request) {
        String provided = request.getHeader(ORIGIN_SECRET_HEADER);
        return provided != null
                && MessageDigest.isEqual(originSecret, provided.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveClientAddress(HttpServletRequest request) {
        if (!settings.clientIpHeader().isEmpty()) {
            String header = request.getHeader(settings.clientIpHeader());
            if (header != null) {
                String candidate = header.trim();
                if (IP_LITERAL.matcher(candidate).matches()
                        && (candidate.indexOf('.') >= 0 || candidate.indexOf(':') >= 0)) {
                    return candidate;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private static boolean isProbe(String path) {
        if (PROBE.matcher(path).find()) {
            return true;
        }
        if (path.startsWith("/actuator/")) {
            return !path.equals("/actuator/info");
        }
        return !path.startsWith("/api/") && CONFIG_FILE.matcher(path).find();
    }

    private static boolean isRoutable(String path) {
        return path.startsWith("/api/")
                || path.equals("/actuator/info")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    private static boolean isRateLimited(String path) {
        return path.startsWith("/api/")
                && !path.startsWith("/api/payments/webhooks/")
                && !path.startsWith("/api/shipping/webhooks/");
    }

    private boolean isBanned(String clientAddress, long now) {
        Long until = bannedUntil.get(clientAddress);
        if (until == null) {
            return false;
        }
        if (now >= until) {
            bannedUntil.remove(clientAddress, until);
            return false;
        }
        return true;
    }

    private void recordProbe(String clientAddress, String path, long now) {
        long windowMs = settings.probeWindow().toMillis();
        if (!hasRoom(probeWindows, clientAddress)) {
            return;
        }
        Window window = probeWindows.compute(clientAddress, (ignored, existing) ->
                existing == null || now - existing.startedAt() >= windowMs
                        ? new Window(now, 1)
                        : new Window(existing.startedAt(), existing.count() + 1));
        if (window.count() >= settings.probeBanThreshold() && hasRoom(bannedUntil, clientAddress)) {
            Long previous = bannedUntil.put(clientAddress, now + settings.banDuration().toMillis());
            probeWindows.remove(clientAddress);
            if (previous == null) {
                log.warn("Banned {} for {} after {} vulnerability probes (last: {})",
                        clientAddress, settings.banDuration(), window.count(), path);
            }
        }
    }

    /** @return 0 when the request fits the budget, otherwise milliseconds until the window resets. */
    private long consumeRequestBudget(String clientAddress, long now) {
        long windowMs = settings.window().toMillis();
        if (!hasRoom(requestWindows, clientAddress)) {
            // Fail open: under a flood of distinct addresses the edge (Cloudflare) is the right place to block,
            // and refusing every new address here would lock legitimate customers out.
            return 0;
        }
        Window window = requestWindows.compute(clientAddress, (ignored, existing) ->
                existing == null || now - existing.startedAt() >= windowMs
                        ? new Window(now, 1)
                        : new Window(existing.startedAt(), existing.count() + 1));
        if (window.count() <= settings.maxRequestsPerWindow()) {
            return 0;
        }
        return Math.max(1, window.startedAt() + windowMs - now);
    }

    private boolean hasRoom(Map<String, ?> map, String key) {
        return map.size() < settings.maxBuckets() || map.containsKey(key);
    }

    private void housekeeping(long now) {
        boolean periodic = (checks.incrementAndGet() & 255) == 0;
        int max = settings.maxBuckets();
        if (periodic || requestWindows.size() >= max) {
            long windowMs = settings.window().toMillis();
            requestWindows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= windowMs);
        }
        if (periodic || probeWindows.size() >= max) {
            long windowMs = settings.probeWindow().toMillis();
            probeWindows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= windowMs);
        }
        if (periodic || bannedUntil.size() >= max) {
            bannedUntil.entrySet().removeIf(entry -> now >= entry.getValue());
        }
    }

    private static void respondEmpty(HttpServletResponse response, HttpStatus status) {
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentLength(0);
    }

    private void respondTooManyRequests(HttpServletRequest request, HttpServletResponse response, long retryAfterMs)
            throws IOException {
        applyCorsHeaders(request, response);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString((retryAfterMs + 999) / 1000));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        RestAuthenticationEntryPoint.write(response, request, HttpStatus.TOO_MANY_REQUESTS,
                "rate-limit-exceeded", "Too many requests. Please try again later.");
    }

    /** Without these the browser hides the 429 from the storefront and it shows up as a network error. */
    private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || corsConfigurationSource == null) {
            return;
        }
        CorsConfiguration configuration = corsConfigurationSource.getCorsConfiguration(request);
        String allowed = configuration == null ? null : configuration.checkOrigin(origin);
        if (allowed == null) {
            return;
        }
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowed);
        if (Boolean.TRUE.equals(configuration.getAllowCredentials())) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
        if (configuration.getExposedHeaders() != null && !configuration.getExposedHeaders().isEmpty()) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                    String.join(", ", configuration.getExposedHeaders()));
        }
    }

    int trackedAddresses() {
        return Math.max(requestWindows.size(), Math.max(probeWindows.size(), bannedUntil.size()));
    }

    public record Settings(
            boolean enabled,
            String clientIpHeader,
            String originSecret,
            int maxRequestsPerWindow,
            Duration window,
            int probeBanThreshold,
            Duration probeWindow,
            Duration banDuration,
            int maxBuckets
    ) {
        public Settings {
            clientIpHeader = clientIpHeader == null ? "" : clientIpHeader.trim();
            originSecret = originSecret == null ? "" : originSecret.trim();
            if (!originSecret.isEmpty() && originSecret.length() < 24) {
                throw new IllegalArgumentException("The origin secret must be at least 24 characters long.");
            }
            if (maxRequestsPerWindow <= 0 || probeBanThreshold <= 0 || maxBuckets < 100
                    || window.isNegative() || window.isZero()
                    || probeWindow.isNegative() || probeWindow.isZero()
                    || banDuration.isNegative() || banDuration.isZero()) {
                throw new IllegalArgumentException("Edge protection limits must be positive and bounded.");
            }
        }

        Settings withEnabled(boolean value) {
            return new Settings(value, clientIpHeader, originSecret, maxRequestsPerWindow, window,
                    probeBanThreshold, probeWindow, banDuration, maxBuckets);
        }

        Settings withClientIpHeader(String value) {
            return new Settings(enabled, value, originSecret, maxRequestsPerWindow, window,
                    probeBanThreshold, probeWindow, banDuration, maxBuckets);
        }

        Settings withOriginSecret(String value) {
            return new Settings(enabled, clientIpHeader, value, maxRequestsPerWindow, window,
                    probeBanThreshold, probeWindow, banDuration, maxBuckets);
        }
    }

    private record Window(long startedAt, int count) {
    }

    private static final class ClientAddressRequest extends HttpServletRequestWrapper {
        private final String clientAddress;

        ClientAddressRequest(HttpServletRequest request, String clientAddress) {
            super(request);
            this.clientAddress = clientAddress;
        }

        @Override
        public String getRemoteAddr() {
            return clientAddress;
        }

        @Override
        public String getRemoteHost() {
            return clientAddress;
        }
    }
}
