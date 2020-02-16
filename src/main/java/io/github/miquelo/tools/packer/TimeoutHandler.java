package io.github.miquelo.tools.packer;

import static java.time.Duration.between;
import static java.time.Instant.now;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handler for propagating and controlling execution timeouts.
 * 
 * @see PackerCommand#init(PackerCommandLogger, TimeoutHandler)
 */
public interface TimeoutHandler
{
    /**
     * Whether this handler is associated to a timeout that must be taken into
     * account.
     */
    boolean isRelevant();
    
    /**
     * Associated timeout.
     * 
     * It is {@code 0L} when this handler is not relevant.
     */
    long getTimeout();
    
    /**
     * Unit of the associated timeout.
     * 
     * It is {@code NANOSECONDS} when this handler is not relevant.
     */
    TimeUnit getUnit();
    
    /**
     * Check this handler does not timed-out and returns a new instance with
     * the remaining timeout.
     * 
     * @return
     *     The new handler with the remaining timeout.
     *     
     * @throws TimeoutException
     *     When this handler timed-out.
     */
    TimeoutHandler checkIt()
    throws TimeoutException;
}

class TimeoutHandlerRelevant
implements TimeoutHandler
{
    private final Instant begin;
    private final long timeout;
    private final TimeUnit unit;
    
    TimeoutHandlerRelevant(long timeout, TimeUnit unit)
    {
        this(now(), timeout, unit);
    }
    
    private TimeoutHandlerRelevant(Instant begin, long timeout, TimeUnit unit)
    {
        this.begin = begin;
        this.timeout = timeout;
        this.unit = unit;
    }

    @Override
    public boolean isRelevant()
    {
        return true;
    }
    
    @Override
    public long getTimeout()
    {
        return timeout;
    }
    
    @Override
    public TimeUnit getUnit()
    {
        return unit;
    }

    @Override
    public TimeoutHandler checkIt()
    throws TimeoutException
    {
        Instant currentBegin = now();
        Instant timedout = begin.plus(timeout, toTemporalUnit(unit));
        if (currentBegin.isBefore(timedout))
            return new TimeoutHandlerRelevant(
                currentBegin,
                between(currentBegin, timedout).toNanos(),
                NANOSECONDS);
        throw new TimeoutException();
    }

    private static TemporalUnit toTemporalUnit(TimeUnit unit)
    {
        switch (unit)
        {
            case DAYS:
            return ChronoUnit.DAYS;
            
            case HOURS:
            return ChronoUnit.HOURS;
            
            case MICROSECONDS:
            return ChronoUnit.MICROS;
            
            case MILLISECONDS:
            return ChronoUnit.MILLIS;
            
            case MINUTES:
            return ChronoUnit.MINUTES;
            
            case NANOSECONDS:
            return ChronoUnit.NANOS;
            
            default:
            return ChronoUnit.SECONDS;
        }
    }
}

class TimeoutHandlerIrrelevant
implements TimeoutHandler
{
    static final TimeoutHandler INSTANCE = new TimeoutHandlerIrrelevant();
    
    private TimeoutHandlerIrrelevant()
    {
    }
    
    @Override
    public boolean isRelevant()
    {
        return false;
    }
    
    @Override
    public long getTimeout()
    {
        return 0L;
    }
    
    @Override
    public TimeUnit getUnit()
    {
        return TimeUnit.NANOSECONDS;
    }

    @Override
    public TimeoutHandler checkIt()
    throws TimeoutException
    {
        return INSTANCE;
    }
}
