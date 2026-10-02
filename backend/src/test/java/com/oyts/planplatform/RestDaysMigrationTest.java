package com.oyts.planplatform;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RestDaysMigrationTest {
    @Test
    void upgradesAnExistingV10PlanWithoutChangingItsData() throws Exception {
        String url = "jdbc:h2:mem:rest_migration_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("10").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            sql.executeUpdate("insert into users(id,email,password_hash,display_name,role,created_at,updated_at) values('u','migration@example.test','hash','测试','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            sql.executeUpdate("insert into plans(id,owner_id,name,start_date,end_date,version,created_at,updated_at) values('p','u','保留计划','2026-10-01','2026-10-31',3,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            try (var rows = sql.executeQuery("select name,version,rest_days_mask from plans where id='p'")) {
                assertTrue(rows.next()); assertEquals("保留计划", rows.getString(1)); assertEquals(3, rows.getLong(2)); assertEquals(0, rows.getInt(3));
            }
        }
    }
}
