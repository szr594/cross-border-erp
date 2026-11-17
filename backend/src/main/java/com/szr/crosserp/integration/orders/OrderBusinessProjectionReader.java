package com.szr.crosserp.integration.orders;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.szr.crosserp.common.exception.ApiException;
import org.springframework.http.HttpStatus;

@Service
public class OrderBusinessProjectionReader {
    public record OrderSnapshot(long id,String orderNo,String status,boolean exception,Long responsibleUserId,long version) {}
    public record ItemSnapshot(long id,long orderId,String productName,String sku,int quantity,String status,long version) {}
    private final JdbcTemplate jdbc;
    public OrderBusinessProjectionReader(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(readOnly=true)
    public OrderSnapshot order(long id) {
        return jdbc.query("select * from order_business_projections where order_id=?",(r,n)->
            new OrderSnapshot(r.getLong("order_id"),r.getString("order_no"),r.getString("external_status"),
                r.getBoolean("fulfillment_exception"),r.getObject("responsible_user_id",Long.class),r.getLong("source_version")),id)
            .stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"ORDER_PROJECTION_NOT_READY","订单投影尚未就绪"));
    }
    @Transactional(readOnly=true)
    public List<ItemSnapshot> items(long id) {
        order(id);
        return jdbc.query("select * from order_item_business_projections where order_id=? order by order_item_id",(r,n)->
            new ItemSnapshot(r.getLong("order_item_id"),r.getLong("order_id"),r.getString("product_name"),
                r.getString("external_sku"),r.getInt("quantity"),r.getString("fulfillment_status"),r.getLong("source_version")),id);
    }
}
