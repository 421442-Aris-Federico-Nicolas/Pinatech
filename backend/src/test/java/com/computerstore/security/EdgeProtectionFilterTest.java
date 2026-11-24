package com.computerstore.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class EdgeProtectionFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final MutableClock clock = new MutableClock();

    @Test
    void passesLegitimateApiRequestsThrough() throws Exception {
        Result result = run(filter(settings()), get("/api/products", "198.51.100.7"));

        assertEquals(200, result.status());
        assertTrue(result.reachedChain());
    }

    @Test
    void answersNotFoundForRoutesOutsideTheApiWithoutReachingSecurity() throws Exception {
        EdgeProtectionFilter filter = filter(settings());

        for (String path : List.of("/", "/info", "/robots.txt", "/.well-known/security.txt")) {
            Result result = run(filter, get(path, "198.51.100.7"));
            assertEquals(404, result.status(), path);
            assertEquals(false, result.reachedChain(), path);
        }
    }

    @Test
    void bansAnAddressThatKeepsProbingForSecretsAndPhpFiles() throws Exception {
        EdgeProtectionFilter filter = filter(settings());
        List<String> probes = List.of("/.env", "/aws/.env", "/phpinfo.php", "/.git/config",
                "/service-account.json");

        for (String probe : probes) {
            assertEquals(404, run(filter, get(probe, "203.0.113.9")).status(), probe);
        }

        Result legitimateAfterBan = run(filter, get("/api/products", "203.0.113.9"));
        assertEquals(403, legitimateAfterBan.status());
        assertEquals(false, legitimateAfterBan.reachedChain());
        assertEquals(200, run(filter, get("/api/products", "198.51.100.7")).status());
    }

    @Test
    void recognisesThePathsSeenInTheScannerLogs() throws Exception {
        List<String> probes = List.of("/transactional/.env", "/config/app/.env", "/phpinfo", "/info.php",
                "/_profiler/phpinfo", "/_environment", "/webroot/index.php/_environment", "/phpinfo.php.bak",
                "/phpinfo.php~", "/wp-admin/phpinfo.php", "/sa.json", "/gcp-key.json", "/firebase-adminsdk.json",
                "/.config/gcloud/application_default_credentials.json", "/server-status.php");
        int address = 0;

        for (String probe : probes) {
            EdgeProtectionFilter filter = filter(settings());
            String client = "203.0.113." + (++address);
            for (int i = 0; i < 5; i++) {
                run(filter, get(probe, client));
            }
            assertEquals(403, run(filter, get("/api/products", client)).status(), probe);
        }
    }

    @Test
    void liftsTheBanAfterItsDuration() throws Exception {
        EdgeProtectionFilter filter = filter(settings());
        for (int i = 0; i < 6; i++) {
            run(filter, get("/phpinfo.php", "203.0.113.9"));
        }
        assertEquals(403, run(filter, get("/api/products", "203.0.113.9")).status());

        clock.advance(Duration.ofHours(1).plusSeconds(1));

        assertEquals(200, run(filter, get("/api/products", "203.0.113.9")).status());
    }

    @Test
    void unknownButHarmlessPathsDoNotCountTowardsTheBan() throws Exception {
        EdgeProtectionFilter filter = filter(settings());
        for (int i = 0; i < 20; i++) {
            run(filter, get("/", "203.0.113.9"));
        }

        assertEquals(200, run(filter, get("/api/products", "203.0.113.9")).status());
    }

    @Test
    void probesUnderTheApiPrefixAreStillRecognised() throws Exception {
        EdgeProtectionFilter filter = filter(settings());

        Result result = run(filter, get("/api/.env", "203.0.113.9"));

        assertEquals(404, result.status());
        assertEquals(false, result.reachedChain());
    }

    @Test
    void actuatorEndpointsOtherThanHealthAndInfoAreProbes() throws Exception {
        EdgeProtectionFilter filter = filter(settings());

        assertEquals(404, run(filter, get("/actuator/env", "203.0.113.9")).status());
        assertEquals(404, run(filter, get("/actuator/heapdump", "203.0.113.9")).status());
        assertTrue(run(filter, get("/actuator/info", "203.0.113.9")).reachedChain());
    }

    @Test
    void rateLimitsAnAddressThatExceedsTheWindowBudget() throws Exception {
        EdgeProtectionFilter filter = filter(settings());
        for (int i = 0; i < 5; i++) {
            assertEquals(200, run(filter, get("/api/products", "198.51.100.7")).status());
        }

        Result limited = run(filter, get("/api/products", "198.51.100.7"));

        assertEquals(429, limited.status());
        assertEquals("60", limited.response().getHeader("Retry-After"));
        assertTrue(limited.response().getContentAsString().contains("rate-limit-exceeded"));
        assertEquals(200, run(filter, get("/api/products", "192.0.2.44")).status());

        clock.advance(Duration.ofSeconds(61));
        assertEquals(200, run(filter, get("/api/products", "198.51.100.7")).status());
    }

    @Test
    void rateLimitedResponsesKeepCorsHeadersSoTheStorefrontCanReadThem() throws Exception {
        EdgeProtectionFilter filter = filter(settings());
        for (int i = 0; i < 5; i++) {
            run(filter, get("/api/products", "198.51.100.7"));
        }
        MockHttpServletRequest request = get("/api/products", "198.51.100.7");
        request.addHeader("Origin", "https://store.example.com");

        Result limited = run(filter, request);

        assertEquals(429, limited.status());
        assertEquals("https://store.example.com", limited.response().getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", limited.response().getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    void neverRateLimitsHealthChecksOrPaymentAndShippingWebhooks() throws Exception {
        EdgeProtectionFilter filter = filter(settings());

        for (int i = 0; i < 20; i++) {
            assertEquals(200, run(filter, get("/actuator/health/readiness", "10.0.0.2")).status());
            assertEquals(200, run(filter, post("/api/payments/webhooks/mercado-pago", "203.0.113.50")).status());
            assertEquals(200, run(filter, post("/api/shipping/webhooks/zipnova/abc", "203.0.113.51")).status());
        }
    }

    @Test
    void usesTheConfiguredEdgeHeaderAsTheClientAddress() throws Exception {
        EdgeProtectionFilter filter = filter(settings().withClientIpHeader("CF-Connecting-IP"));
        MockHttpServletRequest request = get("/api/products", "172.70.1.1");
        request.addHeader("CF-Connecting-IP", "198.51.100.7");

        Result result = run(filter, request);

        assertEquals("198.51.100.7", result.forwarded().getRemoteAddr());
        assertEquals("198.51.100.7", result.forwarded().getRemoteHost());
    }

    @Test
    void edgeHeaderSeparatesClientsBehindTheSameProxy() throws Exception {
        EdgeProtectionFilter filter = filter(settings().withClientIpHeader("CF-Connecting-IP"));
        for (int i = 0; i < 5; i++) {
            run(filter, withHeader(get("/api/products", "172.70.1.1"), "CF-Connecting-IP", "198.51.100.7"));
        }

        Result otherClient = run(filter,
                withHeader(get("/api/products", "172.70.1.1"), "CF-Connecting-IP", "192.0.2.44"));

        assertEquals(200, otherClient.status());
    }

    @Test
    void ignoresAnEdgeHeaderThatIsNotAnIpAddress() throws Exception {
        EdgeProtectionFilter filter = filter(settings().withClientIpHeader("CF-Connecting-IP"));
        MockHttpServletRequest request = get("/api/products", "172.70.1.1");
        request.addHeader("CF-Connecting-IP", "<script>");

        assertEquals("172.70.1.1", run(filter, request).forwarded().getRemoteAddr());
    }

    @Test
    void acceptsIpv6EdgeAddresses() throws Exception {
        EdgeProtectionFilter filter = filter(settings().withClientIpHeader("CF-Connecting-IP"));
        MockHttpServletRequest request = get("/api/products", "172.70.1.1");
        request.addHeader("CF-Connecting-IP", "2001:db8::7");

        assertEquals("2001:db8::7", run(filter, request).forwarded().getRemoteAddr());
    }

    @Test
    void rejectsRequestsThatBypassTheEdgeWhenAnOriginSecretIsConfigured() throws Exception {
        EdgeProtectionFilter filter = filter(settings().withOriginSecret(SECRET));

        Result direct = run(filter, get("/api/products", "198.51.100.7"));
        Result wrongSecret = run(filter, withHeader(get("/api/products", "198.51.100.7"),
                EdgeProtectionFilter.ORIGIN_SECRET_HEADER, "not-the-secret"));
        Result viaEdge = run(filter, withHeader(get("/api/products", "198.51.100.7"),
                EdgeProtectionFilter.ORIGIN_SECRET_HEADER, SECRET));
        Result healthCheck = run(filter, get("/actuator/health/readiness", "10.0.0.2"));

        assertEquals(403, direct.status());
        assertEquals(403, wrongSecret.status());
        assertTrue(viaEdge.reachedChain());
        assertTrue(healthCheck.reachedChain());
    }

    @Test
    void refusesAWeakOriginSecret() {
        assertThrows(IllegalArgumentException.class, () -> settings().withOriginSecret("short"));
    }

    @Test
    void doesNothingWhenDisabled() throws Exception {
        EdgeProtectionFilter filter = filter(settings().withEnabled(false));

        Result result = run(filter, get("/.env", "203.0.113.9"));

        assertTrue(result.reachedChain());
    }

    @Test
    void keepsTrackingBoundedUnderAFloodOfDistinctAddresses() throws Exception {
        EdgeProtectionFilter filter = filter(settings());

        for (int i = 0; i < 1_000; i++) {
            run(filter, get("/api/products", "10.1." + (i / 250) + "." + (i % 250)));
        }

        assertTrue(filter.trackedAddresses() <= 100);
        assertEquals(200, run(filter, get("/api/products", "198.51.100.200")).status());
    }

    private EdgeProtectionFilter.Settings settings() {
        return new EdgeProtectionFilter.Settings(true, "", "", 5, Duration.ofMinutes(1),
                5, Duration.ofMinutes(10), Duration.ofHours(1), 100);
    }

    private EdgeProtectionFilter filter(EdgeProtectionFilter.Settings settings) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of("https://store.example.com"));
        cors.setAllowCredentials(true);
        cors.setExposedHeaders(List.of("Location", "Retry-After"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return new EdgeProtectionFilter(settings, source, clock);
    }

    private Result run(EdgeProtectionFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ServletRequest> forwarded = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> forwarded.set(req));
        return new Result(response, forwarded.get());
    }

    private static MockHttpServletRequest get(String path, String remoteAddress) {
        return request("GET", path, remoteAddress);
    }

    private static MockHttpServletRequest post(String path, String remoteAddress) {
        return request("POST", path, remoteAddress);
    }

    private static MockHttpServletRequest request(String method, String path, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private static MockHttpServletRequest withHeader(MockHttpServletRequest request, String name, String value) {
        request.addHeader(name, value);
        return request;
    }

    private record Result(MockHttpServletResponse response, ServletRequest forwarded) {
        int status() {
            return response.getStatus();
        }

        boolean reachedChain() {
            return forwarded != null;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-24T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
