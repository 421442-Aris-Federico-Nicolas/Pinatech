package com.computerstore.payment.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class MercadoPagoConfiguration {

    @Bean
    @Qualifier("mercadoPagoRestClient")
    RestClient mercadoPagoRestClient(RestClient.Builder builder, MercadoPagoProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder
                .baseUrl("https://api.mercadopago.com")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + nullToEmpty(properties.accessToken()))
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
