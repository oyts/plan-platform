package com.oyts.planplatform.reminders;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ReminderPollLimiterTest {
 @Test void oneKeyIsBoundedAcrossSources(){var l=new ReminderPollLimiter();for(int i=0;i<12;i++)l.acquire("synthetic-"+i,"key");assertThatThrownBy(()->l.acquire("another","key")).hasMessageContaining("频繁");}
 @Test void oneSourceCannotRotateUnlimitedKeys(){var l=new ReminderPollLimiter();for(int i=0;i<120;i++)l.acquire("source","key-"+i);assertThatThrownBy(()->l.acquire("source","next")).hasMessageContaining("频繁");}
 @Test void separateSourcesAndKeysDoNotShareQuota(){var l=new ReminderPollLimiter();for(int i=0;i<12;i++)l.acquire("one","key-one");assertThatCode(()->l.acquire("two","key-two")).doesNotThrowAnyException();}
}
