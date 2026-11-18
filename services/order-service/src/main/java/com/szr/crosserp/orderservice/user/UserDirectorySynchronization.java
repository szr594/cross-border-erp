package com.szr.crosserp.orderservice.user;

import com.szr.crosserp.orderservice.common.exception.ApiException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class UserDirectorySynchronization {
    public record Entry(long id, String loginName, String displayName, String status, long sourceVersion) {}
    private final String url;
    private final String token;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public UserDirectorySynchronization(@Value("${IDENTITY_INTERNAL_URL:}") String url,
        @Value("${ORDER_INTERNAL_SERVICE_TOKEN:}") String token, JdbcTemplate jdbc,
        PlatformTransactionManager transactions) {
        this.url=url; this.token=token; this.jdbc=jdbc; this.tx=new TransactionTemplate(transactions);
    }
    public void refresh() {
        // Existing isolated deployments can seed a directory. Cutover Compose requires a live owner URL.
        if (url.isBlank()) return;
        if (token.length() < 32) throw unavailable();
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); factory.setReadTimeout(5000);
        List<Entry> entries;
        try {
            entries=RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Service-Token",token)
                .build().get().uri("/internal/identity/user-displays").retrieve()
                .body(new ParameterizedTypeReference<List<Entry>>() {});
        } catch (RestClientException exception) { throw unavailable(); }
        if (entries==null) throw unavailable();
        for (Entry entry:entries) {
            if (entry.id()<=0 || entry.loginName()==null || entry.loginName().isBlank()
                || entry.displayName()==null || !List.of("ACTIVE","INACTIVE").contains(entry.status())
                || entry.sourceVersion()<0) throw unavailable();
        }
        tx.executeWithoutResult(status -> {
            for (Entry entry:entries.stream().sorted(java.util.Comparator.comparingLong(Entry::id)).toList()) {
                jdbc.update("""
                    insert into user_display_projections(id,login_name,display_name,status,source_version)
                    values(?,?,?,?,?) on duplicate key update
                    login_name=case when source_version<=values(source_version) then values(login_name) else login_name end,
                    display_name=case when source_version<=values(source_version) then values(display_name) else display_name end,
                    status=case when source_version<=values(source_version) then values(status) else status end,
                    source_version=greatest(source_version,values(source_version))
                    """,entry.id(),entry.loginName(),entry.displayName(),entry.status(),entry.sourceVersion());
            }
        });
    }
    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ORDER_DIRECTORY_UNAVAILABLE","员工目录暂不可用，请稍后重试");
    }
}
