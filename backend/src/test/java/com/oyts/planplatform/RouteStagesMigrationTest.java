package com.oyts.planplatform;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RouteStagesMigrationTest {
    @Test void existingRoutesRetainOrderProgressAndVersions() throws Exception {
        String url = "jdbc:h2:mem:route_upgrade_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("11").load().migrate();
        try (var connection = DriverManager.getConnection(url,"sa",""); var sql = connection.createStatement()) {
            sql.executeUpdate("insert into users(id,email,password_hash,display_name,role,created_at,updated_at) values('u','route@example.test','hash','测试','USER',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            sql.executeUpdate("insert into mainlines(id,owner_id,name,color,version,created_at,updated_at) values('l','u','原路线','#002FA7',4,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            sql.executeUpdate("insert into mainline_items(id,mainline_id,title,acceptance_json,planned_minutes,sort_order,status,accumulated_seconds,version,created_at,updated_at) values('i','l','原步骤','[]',20,0,'COMPLETED',125,8,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            Flyway.configure().dataSource(url,"sa","").load().migrate();
            try (var rows = sql.executeQuery("select execution_mode,version from mainlines where id='l'")) { assertTrue(rows.next()); assertEquals("SEQUENTIAL",rows.getString(1)); assertEquals(4,rows.getLong(2)); }
            try (var rows = sql.executeQuery("select stage_id,dependency_mode,prerequisite_ids_json,status,accumulated_seconds,version from mainline_items where id='i'")) {
                assertTrue(rows.next()); assertNull(rows.getString(1)); assertEquals("INHERIT",rows.getString(2)); assertEquals("[]",rows.getString(3)); assertEquals("COMPLETED",rows.getString(4)); assertEquals(125,rows.getLong(5)); assertEquals(8,rows.getLong(6));
            }
        }
    }
}
