package com.ecommerce.product.dto;

import java.math.BigDecimal;
import java.util.List;

/** Public discovery metadata, calculated from active catalogue products only. */
public record CatalogueFacetsResponse(List<Facet> categories, List<Facet> brands, PriceRange priceRange) {
    public record Facet(String name, long count) {}
    public record PriceRange(BigDecimal min, BigDecimal max) {}
}
