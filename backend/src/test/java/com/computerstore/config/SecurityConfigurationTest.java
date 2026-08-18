package com.computerstore.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class SecurityConfigurationTest {

    private final SecurityConfiguration security = new SecurityConfiguration(null, null, null);

    @Test
    void productionAllowsOnlyTheExactConfiguredOrigin() {
        Environment environment = environment(true);

        CorsConfiguration configuration = configuration(
                "https://store.example.com", "http://localhost:*,https://*.devtunnels.ms", environment);

        assertEquals(List.of("https://store.example.com"), configuration.getAllowedOrigins());
        assertNull(configuration.getAllowedOriginPatterns());
    }

    @Test
    void developmentCanUseExplicitOriginPatterns() {
        Environment environment = environment(false);

        CorsConfiguration configuration = configuration(
                "http://localhost:4200", "http://localhost:*, https://*.devtunnels.ms", environment);

        assertEquals(List.of("http://localhost:*", "https://*.devtunnels.ms"),
                configuration.getAllowedOriginPatterns());
    }

    private Environment environment(boolean production) {
        Environment environment = Mockito.mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(production);
        return environment;
    }

    private CorsConfiguration configuration(String origin, String patterns, Environment environment) {
        var source = (UrlBasedCorsConfigurationSource) security.corsConfigurationSource(origin, patterns, environment);
        return source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/products"));
    }
}
