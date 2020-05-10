package io.github.miquelo.tools.packer;

import static java.time.Duration.between;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

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

class RelevantTimeoutHandler
implements TimeoutHandler
{
    private final long timeout;
    private final TimeUnit unit;
    private final Instant begin;
    private final Supplier<Instant> checkItBeginSupplier;
    
    RelevantTimeoutHandler(long timeout, TimeUnit unit)
    {
        this(timeout, unit, Instant::now, Instant::now);
    }
    
    RelevantTimeoutHandler(
        long timeout,
        TimeUnit unit,
        Supplier<Instant> beginSupplier,
        Supplier<Instant> checkItBeginSupplier)
    {
        this.timeout = timeout;
        this.unit = unit;
        this.begin = beginSupplier.get();
        this.checkItBeginSupplier = checkItBeginSupplier;
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
        Instant currentBegin = checkItBeginSupplier.get();
        Instant timedout = begin.plus(timeout, toTemporalUnit(unit));
        if (currentBegin.isBefore(timedout))
            return new RelevantTimeoutHandler(
                between(currentBegin, timedout).toNanos(),
                NANOSECONDS);
      
        throw new TimeoutException();
    }

    static TemporalUnit toTemporalUnit(TimeUnit unit)
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

class IrrelevantTimeoutHandler
implements TimeoutHandler
{
    static final TimeoutHandler INSTANCE = new IrrelevantTimeoutHandler();
    
    private IrrelevantTimeoutHandler()
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
