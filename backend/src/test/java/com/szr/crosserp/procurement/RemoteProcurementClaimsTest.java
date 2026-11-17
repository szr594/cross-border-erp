package com.szr.crosserp.procurement;
import com.szr.crosserp.procurement.service.RemoteProcurementClaims;
import com.szr.crosserp.common.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RemoteProcurementClaimsTest {
    @Test void sendsIndependentCredentialAndMapsUnknownRemoteResultTo503() throws Exception {
        var seen=new java.util.concurrent.atomic.AtomicReference<String>();
        var status=new java.util.concurrent.atomic.AtomicInteger(200);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/procurement-requests/claims/test",exchange->{
            seen.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));
            byte[] body="{\"claimId\":\"test\",\"status\":\"CLAIMED\",\"purchaseReference\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status.get(),body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });server.start();
        try {
            String credential="bridge-test-only-token-at-least-32-bytes";
            var client=new RemoteProcurementClaims("http://127.0.0.1:"+server.getAddress().getPort(),credential);
            assertThat(client.get("test").status()).isEqualTo("CLAIMED");assertThat(seen.get()).isEqualTo(credential);
            status.set(503);
            assertThatThrownBy(()->client.get("test")).isInstanceOfSatisfying(ApiException.class,error->{
                assertThat(error.getStatus().value()).isEqualTo(503);assertThat(error.getCode()).isEqualTo("PURCHASE_OPERATION_PENDING");});
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
            try {assertThatThrownBy(()->client.get("test")).isInstanceOf(IllegalStateException.class);}
            finally {org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
        } finally {server.stop(0);}
    }
}
