package io.github.miquelo.tools.packer;

import static io.github.miquelo.tools.packer.IrrelevantTimeoutHandler.INSTANCE;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

public class IrrelevantTimeoutHandlerTest
{
    public IrrelevantTimeoutHandlerTest()
    {
    }
    
    @Test
    public void isIrrelevant()
    {
        boolean relevant = INSTANCE.isRelevant();
        
        assertThat(relevant).isFalse();
    }
    
    @Test
    public void getZeroTimeout()
    {
        long timeout = INSTANCE.getTimeout();
        
        assertThat(timeout).isZero();
    }
    
    @Test
    public void getNanosecondsUnit()
    {
        TimeUnit unit = INSTANCE.getUnit();
        
        assertThat(unit).isEqualTo(NANOSECONDS);
    }
    
    @Test
    public void checkItWithoutTimeoutAndReturningItself()
    throws Exception
    {
        TimeoutHandler timeoutHandler = INSTANCE.checkIt();
        
        assertThat(timeoutHandler).isEqualTo(INSTANCE);
    }
}
