package com.szr.crosserp.orderservice.order.service;

import com.szr.crosserp.orderservice.events.OrderOutbox;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintenance-only copy. Both production writers must be stopped by the operator first. */
@Service
public class OrderDataMigrationService {
    private static final List<String> TABLES=List.of("sales_channels","sales_stores","sales_orders",
        "sales_order_items","order_procurement_requests","order_status_events",
        "order_import_batches","order_import_rows","order_import_errors");
    private final JdbcTemplate target;
    private final OrderOutbox outbox;
    public OrderDataMigrationService(JdbcTemplate target,OrderOutbox outbox) {
        this.target=target; this.outbox=outbox;
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Long> migrate(DataSource source) throws SQLException {
        for (String table:TABLES) {
            if (target.queryForObject("select count(*) from "+table,Long.class)!=0)
                throw new IllegalStateException("Migration requires an empty target owned table: "+table);
        }
        if (target.queryForObject("select count(*) from outbox_events",Long.class)!=0)
            throw new IllegalStateException("Migration requires an empty target outbox");
        Map<String,Long> counts=new LinkedHashMap<>();
        try (Connection connection=source.getConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true); connection.setAutoCommit(false);
            try {
                try (var statement=connection.createStatement(); var users=statement.executeQuery(
                    "select id,login_name,display_name,status from app_users order by id")) {
                    while (users.next()) {
                        target.update("insert ignore into user_display_projections(id,login_name,display_name,status,source_version) values(?,?,?,?,0)",
                            users.getLong(1),users.getString(2),users.getString(3),users.getString(4));
                    }
                }
                for (String table:TABLES) {
                    long copied=0;
                    try (var statement=connection.createStatement(); var rows=statement.executeQuery("select * from "+table+" order by id")) {
                        var metadata=rows.getMetaData();
                        List<String> columns=new ArrayList<>();
                        for (int i=1;i<=metadata.getColumnCount();i++) {
                            String name=metadata.getColumnName(i);
                            if (!name.matches("[a-z_][a-z0-9_]*")) throw new IllegalStateException("Invalid migration column");
                            columns.add("`"+name+"`");
                        }
                        String insert="insert into "+table+" ("+String.join(",",columns)+") values ("
                            +String.join(",",java.util.Collections.nCopies(columns.size(),"?"))+")";
                        while (rows.next()) {
                            Object[] values=new Object[columns.size()];
                            for (int i=0;i<values.length;i++) values[i]=rows.getObject(i+1);
                            target.update(insert,values); copied++;
                        }
                    }
                    if (target.queryForObject("select count(*) from "+table,Long.class)!=copied)
                        throw new IllegalStateException("Migration count mismatch: "+table);
                    counts.put(table,copied);
                }
                // Outbox uses the copied IDs and versions; consumers perform verified legacy handoff.
                for (Long id:target.queryForList("select id from sales_orders order by id",Long.class)) outbox.changed(id);
                for (Long id:target.queryForList("select id from order_procurement_requests order by id",Long.class)) outbox.procurementSnapshot(id);
            } finally { connection.rollback(); }
        }
        return counts;
    }
}
