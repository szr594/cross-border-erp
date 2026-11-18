package com.szr.crosserp.orderservice.order.service;

import com.szr.crosserp.orderservice.common.exception.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Reads operational facts from their owner; never connects to the warehouse database. */
@Component
public class FulfillmentFactsClient {
    public record Snapshot(Map<Long, Integer> allocations, Map<Long, String> packing,
                           List<Long> readyOrderIds) {
        public Snapshot {
            allocations = Map.copyOf(allocations);
            packing = Map.copyOf(packing);
            readyOrderIds = List.copyOf(readyOrderIds);
        }
        public int allocated(long itemId) { return allocations.getOrDefault(itemId, 0); }
        public String packing(long orderId) { return packing.get(orderId); }
    }
    private final String url;
    private final String token;
    public FulfillmentFactsClient(@Value("${LEGACY_BACKEND_INTERNAL_URL:}") String url,
                                  @Value("${ORDER_INTERNAL_SERVICE_TOKEN:}") String token) {
        this.url = url;
        this.token = token;
    }
    public Snapshot snapshot() {
        if (url.isBlank() || token.length() < 32) throw unavailable();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        try {
            Snapshot snapshot = RestClient.builder().baseUrl(url).requestFactory(factory)
                .defaultHeader("X-Service-Token", token).build().get()
                .uri("/internal/orders/fulfillment-facts").retrieve().body(Snapshot.class);
            if (snapshot == null) throw unavailable();
            return snapshot;
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }
    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ORDER_FULFILLMENT_UNAVAILABLE",
            "履约数据暂不可用，请稍后重试");
    }
}
