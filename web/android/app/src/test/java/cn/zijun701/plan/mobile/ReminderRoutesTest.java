package cn.zijun701.plan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReminderRoutesTest {
    @Test public void dayAndWeekUseOnlyKnownKinds(){assertTrue(ReminderRoutes.valid("/reflection?kind=DAY&date=2026-10-20"));assertTrue(ReminderRoutes.valid("/reflection?kind=WEEK&date=2026-10-19"));assertFalse(ReminderRoutes.valid("/reflection?kind=OTHER&date=2026-10-20"));}
    @Test public void invalidCalendarDateIsRejected(){assertFalse(ReminderRoutes.valid("/reflection?kind=DAY&date=2026-02-30"));assertFalse(ReminderRoutes.valid("/todos?date=2026-13-01"));assertTrue(ReminderRoutes.valid("/todos?date=2024-02-29"));assertFalse(ReminderRoutes.valid("/todos?date=2100-02-29"));assertTrue(ReminderRoutes.valid("/todos?date=2000-02-29"));}
    @Test public void existingTaskAndTodoDestinationsRemainBounded(){assertTrue(ReminderRoutes.valid("/plans/20000000-0000-4000-8000-000000000001?date=2026-10-20"));assertTrue(ReminderRoutes.valid("/todos?date=2026-10-20"));assertFalse(ReminderRoutes.valid("/plans/------------------------------------?date=2026-10-20"));}
    @Test public void extraDataAndExternalDestinationsAreRejected(){for(String p:new String[]{"https://example.test/reflection?kind=DAY&date=2026-10-20","/reflection?kind=DAY&date=2026-10-20&token=x","/reflection?kind=DAY&date=2026-10-20#x","/reflection?date=2026-10-20&kind=DAY"})assertFalse(ReminderRoutes.valid(p));}
    @Test public void emptyDestinationIsRejected(){assertFalse(ReminderRoutes.valid(null));assertFalse(ReminderRoutes.valid(""));}
}
