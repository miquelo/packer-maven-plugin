package io.github.miquelo.tools.packer;

import static io.github.miquelo.tools.packer.RelevantTimeoutHandler
    .toTemporalUnit;
import static java.time.Instant.ofEpochMilli;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

public class RelevantTimeoutHandlerTest
{
    private static final TimeUnit DAYS_UNIT = DAYS;
    private static final TimeUnit HOURS_UNIT = HOURS;
    private static final TimeUnit MINUTES_UNIT = MINUTES;
    private static final TimeUnit SECONDS_UNIT = SECONDS;
    private static final TimeUnit MILLISECONDS_UNIT = MILLISECONDS;
    private static final TimeUnit MICROSECONDS_UNIT = MICROSECONDS;
    private static final TimeUnit NANOSECONDS_UNIT = NANOSECONDS;
    
    private static final long ANY_TIMEOUT = -1L;
    private static final TimeUnit ANY_UNIT = SECONDS;
    
    private static final long SOME_TIMEOUT = 2000L;
    
    private static final long INITIAL_TIMEOUT = 2000L;
    private static final Instant INITIAL_BEGIN = ofEpochMilli(0L);
    
    private static final Instant NON_TIMEDOUT_BEGIN = ofEpochMilli(600L);
    private static final Instant TIMEDOUT_BEGIN = ofEpochMilli(2100L);
    private static final long REMAINING_TIMEOUT = 1400_000_000L;

    public RelevantTimeoutHandlerTest()
    {
    }
    
    @Test
    public void isRelevant()
    {
        TimeoutHandler timeoutHandler = new RelevantTimeoutHandler(
            ANY_TIMEOUT,
            ANY_UNIT);
        
        boolean relevant = timeoutHandler.isRelevant();
        
        assertThat(relevant).isTrue();
    }
    
    @Test
    public void getItsTimeout()
    {
        TimeoutHandler timeoutHandler = new RelevantTimeoutHandler(
            SOME_TIMEOUT,
            ANY_UNIT);
        
        long timeout = timeoutHandler.getTimeout();
        
        assertThat(timeout).isEqualTo(SOME_TIMEOUT);
    }
    
    @Test
    public void getItsUnit()
    {
        TimeoutHandler timeoutHandler = new RelevantTimeoutHandler(
            ANY_TIMEOUT,
            MILLISECONDS_UNIT);
        
        TimeUnit unit = timeoutHandler.getUnit();
        
        assertThat(unit).isEqualTo(MILLISECONDS_UNIT);
    }
    
    @Test
    public void checkItWithoutTimeout()
    throws Exception
    {
        TimeoutHandler timeoutHandler = new RelevantTimeoutHandler(
            INITIAL_TIMEOUT,
            MILLISECONDS_UNIT,
            () -> INITIAL_BEGIN,
            () -> NON_TIMEDOUT_BEGIN);
        
        TimeoutHandler newTimeoutHandler = timeoutHandler.checkIt();
        
        assertThat(newTimeoutHandler.getTimeout())
            .isEqualTo(REMAINING_TIMEOUT);
        assertThat(newTimeoutHandler.getUnit())
            .isEqualTo(NANOSECONDS_UNIT);
    }
    
    @Test
    public void checkItWithTimeout()
    throws Exception
    {
        TimeoutHandler timeoutHandler = new RelevantTimeoutHandler(
            INITIAL_TIMEOUT,
            MILLISECONDS_UNIT,
            () -> INITIAL_BEGIN,
            () -> TIMEDOUT_BEGIN);
        
        Throwable exception = catchThrowable(() -> timeoutHandler.checkIt());
        
        assertThat(exception).isInstanceOf(TimeoutException.class);
    }
    
    @Test
    public void worksWithDays()
    {
        TemporalUnit unit = toTemporalUnit(DAYS_UNIT);
        
        assertThat(unit).isEqualTo(ChronoUnit.DAYS);
    }
    
    @Test
    public void worksWithHours()
    {
        TemporalUnit unit = toTemporalUnit(HOURS_UNIT);
        
        assertThat(unit).isEqualTo(ChronoUnit.HOURS);
    }
    
    @Test
    public void worksWithMinutes()
    {
        TemporalUnit unit = toTemporalUnit(MINUTES_UNIT);
        
        assertThat(unit).isEqualTo(ChronoUnit.MINUTES);
    }
    
    @Test
    public void worksWithSeconds()
    {
        TemporalUnit unit = toTemporalUnit(SECONDS_UNIT);
        
        assertThat(unit).isEqualTo(ChronoUnit.SECONDS);
    }
    
    @Test
    public void worksWithMilliseconds()
    {
        TemporalUnit unit = toTemporalUnit(MILLISECONDS_UNIT);
        
        assertThat(unit).isEqualTo(ChronoUnit.MILLIS);
    }
    
    @Test
    public void worksWithMicroseconds()
    {
        TemporalUnit unit = toTemporalUnit(MICROSECONDS_UNIT);
        
        assertThat(unit).isEqualTo(ChronoUnit.MICROS);
    }
    
    @Test
    public void worksWithNanoseconds()
    {
        TemporalUnit unit = toTemporalUnit(NANOSECONDS_UNIT);
        
        assertThat(unit).isEqualTo(ChronoUnit.NANOS);
    }
}
