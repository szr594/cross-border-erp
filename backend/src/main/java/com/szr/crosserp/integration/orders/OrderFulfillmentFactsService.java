package com.szr.crosserp.integration.orders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderFulfillmentFactsService {
    public record Snapshot(Map<Long, Integer> allocations, Map<Long, String> packing,
                           List<Long> readyOrderIds) {}
    private final JdbcTemplate jdbc;
    public OrderFulfillmentFactsService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Snapshot snapshot() {
        Map<Long, Integer> allocations = new LinkedHashMap<>();
        jdbc.query("select sales_order_item_id, sum(quantity) as quantity from inventory_allocations "
            + "where status in ('RESERVED','CONSUMED') group by sales_order_item_id", rs -> {
                allocations.put(rs.getLong(1), rs.getInt(2));
            });
        Map<Long, String> packing = new LinkedHashMap<>();
        jdbc.query("select sales_order_id,status from packing_tasks", rs -> {
            packing.put(rs.getLong(1), rs.getString(2));
        });
        List<Long> ready = jdbc.queryForList("""
            select i.order_id from order_item_business_projections i
            left join (select sales_order_item_id, sum(quantity) quantity from inventory_allocations
                       where status in ('RESERVED','CONSUMED') group by sales_order_item_id) a
                on a.sales_order_item_id=i.order_item_id
            where i.fulfillment_status='NORMAL'
            group by i.order_id having min(coalesce(a.quantity,0) >= i.quantity)=1
            """, Long.class);
        return new Snapshot(allocations, packing, ready);
    }
}
