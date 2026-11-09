package db.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V6__allow_released_quarantine_inventory extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        String database = context.getConnection().getMetaData().getDatabaseProductName();
        try (Statement statement = context.getConnection().createStatement()) {
            if ("H2".equals(database)) {
                statement.execute("ALTER TABLE inventory_lots DROP CONSTRAINT ck_inventory_lots_quality_balance");
            } else {
                statement.execute("ALTER TABLE inventory_lots DROP CHECK ck_inventory_lots_quality_balance");
            }
            statement.execute("""
                ALTER TABLE inventory_lots ADD CONSTRAINT ck_inventory_lots_quality_balance CHECK (
                    (quality_status = 'ACCEPTED' AND quarantined_quantity = 0)
                    OR quality_status = 'DAMAGED'
                )
                """);
        }
    }
}
