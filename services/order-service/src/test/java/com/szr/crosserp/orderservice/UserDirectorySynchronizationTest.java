package com.szr.crosserp.orderservice;

import com.szr.crosserp.orderservice.user.UserDirectorySynchronization;
import com.szr.crosserp.orderservice.common.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.assertj.core.api.Assertions.*;

class UserDirectorySynchronizationTest {
    @Test void safeDirectoryAppliesNewerVersionsAndDoesNotRegressDeactivation() throws Exception {
        var data=new DriverManagerDataSource(System.getenv().getOrDefault("DIRECTORY_TEST_DATABASE_URL","jdbc:h2:mem:directory"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1"),System.getenv().getOrDefault("DIRECTORY_TEST_DATABASE_USER","sa"),System.getenv().getOrDefault("DIRECTORY_TEST_DATABASE_PASSWORD",""));
        var jdbc=new JdbcTemplate(data);
        jdbc.execute("create table user_display_projections(id bigint primary key,login_name varchar(100),display_name varchar(255),status varchar(20),source_version bigint)");
        var body=new java.util.concurrent.atomic.AtomicReference<>("[{\"id\":1,\"loginName\":\"owner\",\"displayName\":\"测试员工\",\"status\":\"ACTIVE\",\"sourceVersion\":1}]");
        String token="directory-test-only-at-least-32-bytes";
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/identity/user-displays",exchange->{
            if(!token.equals(exchange.getRequestHeaders().getFirst("X-Service-Token"))){exchange.sendResponseHeaders(401,-1);exchange.close();return;}
            byte[] bytes=body.get().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            var sync=new UserDirectorySynchronization("http://127.0.0.1:"+server.getAddress().getPort(),token,jdbc,new DataSourceTransactionManager(data));
            sync.refresh();assertThat(jdbc.queryForObject("select count(*) from user_display_projections",Long.class)).isEqualTo(1);
            body.set(body.get().replace("ACTIVE","INACTIVE").replace("Version\":1","Version\":2"));sync.refresh();
            body.set(body.get().replace("INACTIVE","ACTIVE").replace("Version\":2","Version\":1"));sync.refresh();
            assertThat(jdbc.queryForObject("select status from user_display_projections where id=1",String.class)).isEqualTo("INACTIVE");
            assertThat(jdbc.queryForObject("select source_version from user_display_projections where id=1",Long.class)).isEqualTo(2);
            var pool=java.util.concurrent.Executors.newFixedThreadPool(4);
            var start=new java.util.concurrent.CountDownLatch(1);
            try {
                var pending=new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for(int i=0;i<20;i++) pending.add(pool.submit(()->{try{start.await();sync.refresh();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}));
                start.countDown();
                for(var future:pending) future.get(20,java.util.concurrent.TimeUnit.SECONDS);
                assertThat(jdbc.queryForObject("select status from user_display_projections where id=1",String.class)).isEqualTo("INACTIVE");
            } finally { pool.shutdownNow(); }
            body.set("[{\"id\":2,\"loginName\":\"invalid\",\"displayName\":\"invalid\",\"status\":\"INVALID\",\"sourceVersion\":1}]");
            assertThatThrownBy(sync::refresh).isInstanceOf(ApiException.class);
            assertThat(jdbc.queryForObject("select count(*) from user_display_projections",Long.class)).isEqualTo(1);
        } finally {server.stop(0);}
    }
}
