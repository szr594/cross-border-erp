package com.szr.crosserp.store.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import com.szr.crosserp.common.exception.ApiException;

@Service
public class DemoStoreService {
    public record Result(long channelId,long storeId,String code,String name,String status) {}
    private final JdbcTemplate jdbc;
    public DemoStoreService(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @Transactional
    public Result initialize(List<String> roles) {
        if(roles==null||!roles.contains("OWNER")) throw new ApiException(HttpStatus.FORBIDDEN,"STORE_INITIALIZATION_FORBIDDEN","仅老板可以初始化演示店铺");
        jdbc.update("insert ignore into sales_channels(code,name,enabled) values('DEMO','演示销售渠道',true)");
        var channel=jdbc.queryForMap("select id,enabled from sales_channels where code='DEMO' for update");
        long channelId=((Number)channel.get("id")).longValue();
        jdbc.update("insert ignore into sales_stores(channel_id,external_store_id,code,name,status) values(?,'demo-store','DEMO-STORE','演示店铺','ACTIVE')",channelId);
        var store=jdbc.queryForMap("select id,channel_id,external_store_id,name,status from sales_stores where code='DEMO-STORE' for update");
        if(((Number)store.get("channel_id")).longValue()!=channelId||!"demo-store".equals(store.get("external_store_id")))
            throw new ApiException(HttpStatus.CONFLICT,"DEMO_STORE_CONFLICT","已有同编码店铺与演示配置不一致");
        jdbc.update("update sales_channels set enabled=true where id=?",channelId);
        jdbc.update("update sales_stores set status='ACTIVE',updated_at=CURRENT_TIMESTAMP(6) where id=?",store.get("id"));
        return new Result(channelId,((Number)store.get("id")).longValue(),"DEMO-STORE",(String)store.get("name"),"ACTIVE");
    }
}
