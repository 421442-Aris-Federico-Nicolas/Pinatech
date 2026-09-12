package com.computerstore.seo;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.repository.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SeoControllerTest {

    @Test
    void sitemapListsCanonicalPagesAndActiveProductsAsXml() throws Exception {
        ProductRepository products = mock(ProductRepository.class);
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(39L);
        when(product.getSlug()).thenReturn("auriculares-inalambricos");
        when(products.findAllByActiveTrueOrderByIdAsc()).thenReturn(List.of(product));
        var mvc = MockMvcBuilders.standaloneSetup(new SeoController(products, "https://pinatech.com.ar/")).build();

        mvc.perform(get("/api/seo/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/xml"))
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(content().string(containsString("<loc>https://pinatech.com.ar</loc>")))
                .andExpect(content().string(containsString("<loc>https://pinatech.com.ar/catalog</loc>")))
                .andExpect(content().string(containsString("<loc>https://pinatech.com.ar/products/39/auriculares-inalambricos</loc>")))
                .andExpect(content().string(not(containsString("<lastmod>"))))
                .andExpect(content().string(not(containsString("<priority>"))))
                .andExpect(content().string(not(containsString("<changefreq>"))));
    }
}
