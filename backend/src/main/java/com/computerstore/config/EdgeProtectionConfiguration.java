package com.computerstore.config;

import java.time.Clock;
import java.time.Duration;

import com.computerstore.security.EdgeProtectionFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class EdgeProtectionConfiguration {

    /**
     * Runs right after Spring's ForwardedHeaderFilter (HIGHEST_PRECEDENCE) and long before Spring Security (-100),
     * so scanners are answered without touching authentication and every later filter and controller sees the
     * resolved client address.
     */
    static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Bean
    FilterRegistrationBean<EdgeProtectionFilter> edgeProtectionFilter(
            @Value("${app.edge-protection.enabled:true}") boolean enabled,
            @Value("${app.edge-protection.client-ip-header:}") String clientIpHeader,
            @Value("${app.edge-protection.origin-secret:}") String originSecret,
            @Value("${app.edge-protection.max-requests-per-window:300}") int maxRequestsPerWindow,
            @Value("${app.edge-protection.window:PT1M}") Duration window,
            @Value("${app.edge-protection.probe-ban-threshold:5}") int probeBanThreshold,
            @Value("${app.edge-protection.probe-window:PT10M}") Duration probeWindow,
            @Value("${app.edge-protection.ban-duration:PT1H}") Duration banDuration,
            @Value("${app.edge-protection.max-buckets:10000}") int maxBuckets,
            CorsConfigurationSource corsConfigurationSource
    ) {
        EdgeProtectionFilter.Settings settings = new EdgeProtectionFilter.Settings(enabled, clientIpHeader,
                originSecret, maxRequestsPerWindow, window, probeBanThreshold, probeWindow, banDuration, maxBuckets);
        FilterRegistrationBean<EdgeProtectionFilter> registration = new FilterRegistrationBean<>(
                new EdgeProtectionFilter(settings, corsConfigurationSource, Clock.systemUTC()));
        registration.setName("edgeProtectionFilter");
        registration.setOrder(ORDER);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
