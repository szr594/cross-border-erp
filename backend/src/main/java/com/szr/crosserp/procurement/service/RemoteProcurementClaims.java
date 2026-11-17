package com.szr.crosserp.procurement.service;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.http.HttpStatus;
import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.procurement.dto.CreatePurchaseOrderRequest;
import java.util.List;

@Component
@ConditionalOnProperty(name="app.procurement.remote-enabled",havingValue="true")
public class RemoteProcurementClaims {
    public record Item(long requestId,int quantity,long version) {}
    public record Claim(String idempotencyKey,List<Item> items) {}
    public record Result(String claimId,String status,String purchaseReference) {}
    public record Confirmation(String purchaseReference) {}
    private final RestClient client;
    public RemoteProcurementClaims(@Value("${ORDER_SERVICE_INTERNAL_URL}") String url,
        @Value("${ORDER_INTERNAL_SERVICE_TOKEN}") String token){
        var uri=java.net.URI.create(url);
        if(!java.util.Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || token.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<32)
            throw new IllegalArgumentException("Invalid procurement bridge configuration");
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(2000);factory.setReadTimeout(5000);
        client=RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Service-Token",token).build();
    }
    public Result claim(String key,CreatePurchaseOrderRequest input){
        var items=input.items().stream().flatMap(i->i.allocations().stream())
            .map(a->new Item(a.procurementRequestId(),a.quantity(),a.version())).toList();
        return call(()->client.post().uri("/internal/procurement-requests/claims").body(new Claim(key,items)).retrieve().body(Result.class));
    }
    public Result get(String id){return call(()->client.get().uri("/internal/procurement-requests/claims/{id}",id).retrieve().body(Result.class));}
    public Result confirm(String id,String reference){return call(()->client.post().uri("/internal/procurement-requests/claims/{id}/confirm",id).body(new Confirmation(reference)).retrieve().body(Result.class));}
    public Result release(String id){return call(()->client.post().uri("/internal/procurement-requests/claims/{id}/release",id).retrieve().body(Result.class));}
    private Result call(java.util.function.Supplier<Result> action){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Remote procurement call must run outside a database transaction");
        try{var result=action.get();if(result==null)throw pending();return result;}
        catch(RestClientResponseException error){
            if(error.getStatusCode().value()==404)throw new ApiException(HttpStatus.NOT_FOUND,"CLAIM_NOT_FOUND","认领不存在");
            if(error.getStatusCode().value()==400 || error.getStatusCode().value()==409)
                throw new ApiException(HttpStatus.CONFLICT,"PURCHASE_REMOTE_CLAIM_REJECTED","订单服务拒绝本次采购认领");
            throw pending();
        }catch(RestClientException error){throw pending();}
    }
    private ApiException pending(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PURCHASE_OPERATION_PENDING","采购操作结果暂未确定，请使用相同幂等键重试");}
}
