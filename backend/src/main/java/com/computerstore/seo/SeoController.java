package com.computerstore.seo;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

@RestController
@RequestMapping("/api/seo")
public class SeoController {
    private static final MediaType APPLICATION_XML = MediaType.parseMediaType("application/xml");

    private final ProductRepository products;
    private final String storefrontBaseUrl;

    public SeoController(ProductRepository products,
            @Value("${app.storefront.base-url:http://localhost:4200}") String storefrontBaseUrl) {
        this.products = products;
        this.storefrontBaseUrl = storefrontBaseUrl.replaceAll("/+$", "");
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml")
    public ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);
        appendUrl(xml, storefrontBaseUrl);
        appendUrl(xml, storefrontBaseUrl + "/catalog");
        for (Product product : products.findAllByActiveTrueOrderByIdAsc()) {
            String url = UriComponentsBuilder.fromUriString(storefrontBaseUrl)
                    .pathSegment("products", product.getId().toString(), product.getSlug())
                    .build().encode().toUriString();
            appendUrl(xml, url);
        }
        xml.append("</urlset>\n");

        return ResponseEntity.ok()
                .contentType(APPLICATION_XML)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(xml.toString());
    }

    private void appendUrl(StringBuilder xml, String location) {
        xml.append("  <url>\n    <loc>").append(xmlEscape(location)).append("</loc>\n");
        xml.append("  </url>\n");
    }

    private String xmlEscape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
