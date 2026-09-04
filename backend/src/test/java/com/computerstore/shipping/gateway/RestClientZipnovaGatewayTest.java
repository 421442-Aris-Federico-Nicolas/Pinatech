package com.computerstore.shipping.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.computerstore.shipping.config.ZipnovaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientZipnovaGatewayTest {
    @Test
    void mapsOnlySelectableHomeDeliveryUsingTaxInclusivePrice() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.zipnova.com.ar/v2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new RestClientZipnovaGateway(builder.build(), properties());
        server.expect(requestTo("https://api.zipnova.com.ar/v2/shipments/quote"))
                .andExpect(method(HttpMethod.POST)).andExpect(jsonPath("$.account_id").value(7))
                .andExpect(jsonPath("$.origin_id").value(12)).andExpect(jsonPath("$.type_packaging").value("dynamic"))
                .andExpect(jsonPath("$.items.length()").value(1)).andRespond(withSuccess("""
                    {"all_results":[
                      {"selectable":true,"logistic_type":"carrier_pickup","carrier":{"id":3,"name":"Andreani"},
                       "service_type":{"code":"standard_delivery","name":"Estandar"},
                       "delivery_time":{"estimated_delivery":"2026-09-10T23:59:59Z"},
                       "amounts":{"price":100,"price_incl_tax":121.00},"tags":["cheapest"]},
                      {"selectable":true,"logistic_type":"carrier_pickup","carrier":{"id":4,"name":"OCA"},
                       "service_type":{"code":"pickup_point","name":"Sucursal"},"delivery_time":{},
                       "amounts":{"price_incl_tax":90},"tags":[]},
                      {"selectable":false,"logistic_type":"carrier_pickup","carrier":{"id":5,"name":"X"},
                       "service_type":{"code":"express","name":"Express"},"amounts":{"price_incl_tax":80}}
                    ]}
                    """, MediaType.APPLICATION_JSON));
        var result = gateway.quote(command());
        assertEquals(1, result.size()); assertEquals(new BigDecimal("121.00"), result.getFirst().priceInclTax());
        assertEquals("standard_delivery", result.getFirst().serviceCode()); assertEquals(List.of("cheapest"), result.getFirst().tags());
        server.verify();
    }

    @Test
    void preservesRetryAfterWithoutLeakingProviderBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.zipnova.com.ar/v2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new RestClientZipnovaGateway(builder.build(), properties());
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "37").body("token=should-not-leak"));
        ShippingProviderException failure = assertThrows(ShippingProviderException.class, () -> gateway.quote(command()));
        assertEquals(Duration.ofSeconds(37), failure.retryAfter()); assertFalse(failure.getMessage().contains("token"));
    }

    @Test
    void rejectsMoreThanOneThousandExpandedItemsBeforeCallingProvider() {
        var gateway = new RestClientZipnovaGateway(RestClient.create(), properties());
        var item = command().items().getFirst();
        assertThrows(IllegalArgumentException.class, () -> gateway.quote(new ZipnovaGateway.QuoteCommand(
                command().destination(), BigDecimal.TEN, java.util.Collections.nCopies(1001, item))));
    }

    @Test
    void createsShipmentWithNumericOriginAndCompleteDestinationExtras() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.zipnova.com.ar/v2");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new RestClientZipnovaGateway(builder.build(), properties());
        server.expect(requestTo("https://api.zipnova.com.ar/v2/shipments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.origin_id").isNumber())
                .andExpect(jsonPath("$.origin_id").value(12))
                .andExpect(jsonPath("$.destination.street_extras").value("2 B - Porton negro"))
                .andRespond(withSuccess("""
                    {"id":99,"external_id":"PIN-42","status":"new","created_at":"2026-09-04T12:00:00Z",
                     "carrier":{"name":"Andreani"}}
                    """, MediaType.APPLICATION_JSON));
        var destination = new ZipnovaGateway.Destination("Ada", "12345678", "a@b.com", "3515550000",
                "San Martin", "10", "2 B - Porton negro", "Cordoba", "Córdoba", "5000");
        var result = gateway.createShipment(new ZipnovaGateway.CreateShipmentCommand("PIN-42", destination,
                BigDecimal.TEN, "standard_delivery", "carrier_pickup", 3, command().items()));
        assertEquals(99L, result.id());
        server.verify();
    }

    private ZipnovaGateway.QuoteCommand command() {
        return new ZipnovaGateway.QuoteCommand(new ZipnovaGateway.Destination("Ada", "12345678", "a@b.com",
                "3515550000", "San Martin", "10", null, "Cordoba", "Córdoba", "5000"),
                new BigDecimal("100.00"), List.of(new ZipnovaGateway.Item(500, 10, 20, 30, "1", "Teclado", false)));
    }
    private ZipnovaProperties properties() { return new ZipnovaProperties(true, true, "token", "secret", 7L, 12L,
            "pinatech", "dynamic", Duration.ofMinutes(15), Duration.ofSeconds(1), Duration.ofSeconds(2),
            "012345678901234567890123", Duration.ofMinutes(10)); }
}
