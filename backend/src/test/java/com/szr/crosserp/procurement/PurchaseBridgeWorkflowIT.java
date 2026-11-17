package com.szr.crosserp.procurement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.procurement.dto.*;
import com.szr.crosserp.procurement.model.PurchaseOrderStatus;
import com.szr.crosserp.procurement.service.*;
import com.szr.crosserp.user.repository.UserRepository;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real MySQL state/transaction tests; remote outcomes are controlled fault fixtures. */
class PurchaseBridgeWorkflowIT {
    static DriverManagerDataSource source;
    static JdbcTemplate jdbc;
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    RemoteProcurementClaims remote;
    PurchaseOrderCommandService local;
    PurchaseBridgeWorkflow workflow;
    String key;
    static final java.util.concurrent.atomic.AtomicLong ids=new java.util.concurrent.atomic.AtomicLong(100);
    long purchaseId;

    @BeforeAll static void database() {
        String url=System.getenv().getOrDefault("BRIDGE_TEST_DATABASE_URL","jdbc:mysql://127.0.0.1:13318/mysql?allowPublicKeyRetrieval=true&useSSL=false");
        String password=System.getenv().getOrDefault("BRIDGE_TEST_DATABASE_PASSWORD","order-it-root-only");
        var admin=new JdbcTemplate(new DriverManagerDataSource(url,"root",password));
        String schema="bridge_it_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("create database "+schema);
        source=new DriverManagerDataSource(url.replace("/mysql?","/"+schema+"?"),"root",password);
        jdbc=new JdbcTemplate(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V11__purchase_operations.sql"),
            new ClassPathResource("db/migration/V12__purchase_bridge_recovery.sql")).execute(source);
        jdbc.execute("create table purchase_orders(id bigint primary key)");
        jdbc.update("insert into purchase_orders(id) values(1),(2),(3),(12),(123)");
        jdbc.update("insert into purchase_create_operations(idempotency_key,actor_id,request_hash,request_payload,state,actor_roles,response_payload,operation_phase,created_at,updated_at) values('pre-v13-pending',1,?,'{}','PENDING','[\"OWNER\"]','{\"id\":12,\"status\":\"DRAFT\"}','CONFIRM_PENDING',now(6),now(6)),('pre-v13-confirmed',1,?,'{}','SUCCEEDED','[\"OWNER\"]','{\"id\":2,\"status\":\"DRAFT\"}','CONFIRMED',now(6),now(6))","0".repeat(64),"0".repeat(64));
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V13__purchase_bridge_visibility.sql")).execute(source);
        jdbc.execute("create table bridge_local_purchases(operation_key varchar(64) primary key)");
    }
    @BeforeEach void setup() throws Exception {
        remote=mock(RemoteProcurementClaims.class);local=mock(PurchaseOrderCommandService.class);
        workflow=newWorkflow();key=UUID.randomUUID().toString();purchaseId=ids.incrementAndGet();
        var command=new CreatePurchaseOrderRequest(1L,"USD",null,List.of());
        jdbc.update("insert into purchase_create_operations(idempotency_key,actor_id,request_hash,request_payload,state,actor_roles,created_at,updated_at) values(?,1,?,?,'PENDING','[\"OWNER\"]',now(6),now(6))",
            key,"0".repeat(64),mapper.writeValueAsString(command));
        when(remote.get(key)).thenAnswer(i->{outsideTransaction();return claim("CLAIMED");});
        when(remote.confirm(eq(key),eq(reference()))).thenAnswer(i->{outsideTransaction();return claim("CONFIRMED");});
        when(local.createClaimed(any(),eq("1"),eq(List.of("OWNER")))).thenAnswer(i->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            jdbc.update("insert into bridge_local_purchases(operation_key) values(?)",key);
            jdbc.update("insert into purchase_orders(id,bridge_confirmed) values(?,false)",purchaseId);
            return new PurchaseOrderCommandResponse(purchaseId,"PO-TEST",1L,PurchaseOrderStatus.DRAFT,"USD",0L,0L,Instant.EPOCH,List.of());
        });
    }
    PurchaseBridgeWorkflow newWorkflow(){return new PurchaseBridgeWorkflow(jdbc,mapper,new DataSourceTransactionManager(source),remote,local,mock(UserRepository.class));}
    void outsideTransaction(){assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();}
    String reference(){return Long.toString(purchaseId);}
    RemoteProcurementClaims.Result claim(String state){return new RemoteProcurementClaims.Result(key,state,"CONFIRMED".equals(state)?reference():null);}
    ApiException pending(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PURCHASE_OPERATION_PENDING","test fault");}
    void assertPending(org.assertj.core.api.ThrowableAssert.ThrowingCallable action){assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("PURCHASE_OPERATION_PENDING"));}
    long localCount(){return jdbc.queryForObject("select count(*) from bridge_local_purchases where operation_key=?",Long.class,key);}
    boolean visible(){return jdbc.queryForObject("select bridge_confirmed from purchase_orders where id=?",Boolean.class,purchaseId);}

    @Test void repeatsOriginalResponseWithoutCreatingOrConfirmingAgain(){
        var response=workflow.resume(key);
        assertThat(workflow.resume(key)).isEqualTo(response);
        assertThat(localCount()).isEqualTo(1);assertThat(visible()).isTrue();
        assertThat(jdbc.queryForObject("select purchase_order_id from purchase_create_operations where idempotency_key=?",Long.class,key)).isEqualTo(purchaseId);
        verify(local,times(1)).createClaimed(any(),anyString(),anyList());verify(remote,times(1)).confirm(key,reference());
    }
    @Test void lostConfirmationResponseRecoversFromDurableLocalResultAfterRestart(){
        when(remote.confirm(key,reference())).thenThrow(pending());
        assertPending(()->workflow.resume(key));assertThat(localCount()).isEqualTo(1);assertThat(visible()).isFalse();
        doReturn(claim("CONFIRMED")).when(remote).get(key);doReturn(claim("CONFIRMED")).when(remote).confirm(key,reference());
        assertThat(newWorkflow().resume(key).id()).isEqualTo(purchaseId);assertThat(visible()).isTrue();
        assertThat(localCount()).isEqualTo(1);verify(local,times(1)).createClaimed(any(),anyString(),anyList());
    }
    @Test void claimTimeoutQueriesBeforeAttemptingAnotherClaim(){
        when(remote.get(key)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,"CLAIM_NOT_FOUND","missing"));
        when(remote.claim(eq(key),any())).thenThrow(pending());
        assertPending(()->workflow.resume(key));assertThat(localCount()).isZero();
        doReturn(claim("CLAIMED")).when(remote).get(key);
        assertThat(newWorkflow().resume(key).id()).isEqualTo(purchaseId);verify(remote,times(1)).claim(eq(key),any());
    }
    @Test void localRollbackReleasesClaimAndRemembersOriginalFailure(){
        when(local.createClaimed(any(),anyString(),anyList())).thenAnswer(i->{
            jdbc.update("insert into bridge_local_purchases(operation_key) values(?)",key);
            throw new ApiException(HttpStatus.CONFLICT,"SUPPLIER_INACTIVE","inactive");
        });
        assertPending(()->workflow.resume(key));assertThat(localCount()).isZero();
        when(remote.release(key)).thenReturn(claim("RELEASED"));
        assertThatThrownBy(()->newWorkflow().resume(key)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("SUPPLIER_INACTIVE"));
        assertThat(jdbc.queryForObject("select operation_phase from purchase_create_operations where idempotency_key=?",String.class,key)).isEqualTo("FAILED");
        verify(remote).release(key);verify(remote,never()).confirm(anyString(),anyString());
    }
    @Test void concurrentRetryCannotCreateSecondPurchase() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(remote.get(key)).thenAnswer(i->{outsideTransaction();entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return claim("CLAIMED");});
        var executor=Executors.newSingleThreadExecutor();
        try {
            var first=executor.submit(()->workflow.resume(key));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            assertPending(()->newWorkflow().resume(key));release.countDown();
            assertThat(first.get(10,TimeUnit.SECONDS).id()).isEqualTo(purchaseId);
            assertThat(workflow.resume(key).id()).isEqualTo(purchaseId);assertThat(localCount()).isEqualTo(1);assertThat(visible()).isTrue();
        } finally {release.countDown();executor.shutdownNow();}
    }
    @Test void definitiveClaimRejectionIsTerminal(){
        when(remote.get(key)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,"CLAIM_NOT_FOUND","missing"));
        when(remote.claim(eq(key),any())).thenThrow(new ApiException(HttpStatus.CONFLICT,"PURCHASE_REMOTE_CLAIM_REJECTED","rejected"));
        assertThatThrownBy(()->workflow.resume(key)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(()->newWorkflow().resume(key)).isInstanceOf(ApiException.class);
        verify(remote,times(1)).claim(eq(key),any());assertThat(localCount()).isZero();
    }

    @Test void migrationHidesOnlyUnconfirmedBridgeOrders(){
        assertThat(jdbc.queryForObject("select bridge_confirmed from purchase_orders where id=12",Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select purchase_order_id from purchase_create_operations where idempotency_key='pre-v13-pending'",Long.class)).isEqualTo(12L);
        assertThat(jdbc.queryForList("select bridge_confirmed from purchase_orders where id in (1,2,3,123) order by id",Boolean.class)).containsExactly(true,true,true,true);
    }

    @Test void databaseFailureDuringCompletionRollsBackVisibility() {
        jdbc.execute("create trigger reject_bridge_completion before update on purchase_create_operations for each row begin if NEW.idempotency_key='"+key+"' and NEW.state='SUCCEEDED' then signal sqlstate '45000' set message_text='test completion failure'; end if; end");
        try {
            assertPending(()->workflow.resume(key));
            assertThat(visible()).isFalse();
            assertThat(jdbc.queryForObject("select state from purchase_create_operations where idempotency_key=?",String.class,key)).isEqualTo("PENDING");
        } finally {jdbc.execute("drop trigger reject_bridge_completion");}
        assertThat(newWorkflow().resume(key).id()).isEqualTo(purchaseId);
        assertThat(visible()).isTrue();assertThat(localCount()).isEqualTo(1);
        verify(local,times(1)).createClaimed(any(),anyString(),anyList());
    }

    @Test void mismatchedConfirmationReferenceCannotOpenOrder(){
        when(remote.confirm(key,reference())).thenReturn(new RemoteProcurementClaims.Result(key,"CONFIRMED","different-purchase"));
        assertPending(()->workflow.resume(key));assertThat(visible()).isFalse();
        assertThat(jdbc.queryForObject("select state from purchase_create_operations where idempotency_key=?",String.class,key)).isEqualTo("PENDING");
    }
}
