package com.oyts.planplatform.auth;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MailRequestLimiterTest {
    static class Time extends Clock {Instant now=Instant.parse("2026-10-22T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}void plus(long seconds){now=now.plusSeconds(seconds);}}
    @Test void userCooldownAndWindowAreSharedAcrossAddressesButSeparateByPurpose(){
        Time time=new Time();var limiter=new MailRequestLimiter(time);assertTrue(limiter.acquire("email-change","user","synthetic-a"));
        assertFalse(limiter.acquire("email-change","user","synthetic-b"));assertEquals(60,limiter.retryAfterSeconds("email-change","user","synthetic-b"));
        assertTrue(limiter.acquire("email-change-verify","user","synthetic-b"));
        for(int i=0;i<2;i++){time.plus(61);assertTrue(limiter.acquire("email-change","user","synthetic-b"));}
        time.plus(61);assertFalse(limiter.acquire("email-change","user","synthetic-c"));time.plus(901);assertTrue(limiter.acquire("email-change","user","synthetic-a"));
    }
    @Test void publicVerificationSourceCannotAllocateUnlimitedRequestBuckets(){
        Time time=new Time();var limiter=new MailRequestLimiter(time);
        for(int i=0;i<10;i++)assertTrue(limiter.acquire("email-change-verify","request-"+i,"synthetic-address"));
        assertFalse(limiter.acquire("email-change-verify","request-next","synthetic-address"));assertEquals(900,limiter.retryAfterSeconds("email-change-verify","request-next","synthetic-address"));
    }
}
