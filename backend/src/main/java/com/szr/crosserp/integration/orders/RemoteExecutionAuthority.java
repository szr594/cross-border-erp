package com.szr.crosserp.integration.orders;
import com.szr.crosserp.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service @ConditionalOnProperty(name="app.procurement.remote-enabled",havingValue="true")
public class RemoteExecutionAuthority {
    public record Grant(String operationId,long orderId,String status) {}
    private final RestClient client;
    public RemoteExecutionAuthority(@Value("${ORDER_SERVICE_INTERNAL_URL}")String url,@Value("${ORDER_INTERNAL_SERVICE_TOKEN}")String token){
        if(token.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<32)throw new IllegalArgumentException("Execution credential is required");
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(2000);factory.setReadTimeout(5000);
        client=RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Service-Token",token).build();
    }
    public void acquire(long id,String operation){call(id,operation,false);}
    public void release(long id,String operation){call(id,operation,true);}
    private void call(long id,String operation,boolean release){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Execution HTTP must run outside the business transaction");
        try{
            var result=client.post().uri("/internal/orders/{id}/executions/{operation}"+(release?"/release":""),id,operation).retrieve().body(Grant.class);
            if(result==null||!operation.equals(result.operationId())||result.orderId()!=id||!(release?"RELEASED":"ACTIVE").equals(result.status()))throw pending();
        }catch(RestClientResponseException e){
            if(!release&&e.getStatusCode().value()==409)throw new ApiException(HttpStatus.CONFLICT,"ORDER_EXECUTION_FORBIDDEN","订单当前不能执行履约操作");
            if(release&&e.getStatusCode().value()==404)return;
            throw pending();
        }catch(RestClientException e){throw pending();}
    }
    private ApiException pending(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ORDER_EXECUTION_PENDING","执行授权结果未决，请使用原幂等键重试");}
}
