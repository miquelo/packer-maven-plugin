package io.github.miquelo.tools.packer;

import static java.time.Instant.now;
import static java.time.Instant.ofEpochMilli;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

public class PackerOutputMessageTest
{
    private static final String NULL_TARGET = null;
    
    private static final Instant ANY_TIMESTAMP = ofEpochMilli(0L);
    private static final String ANY_TARGET = "any-target";
    private static final String ANY_TYPE = "any-type";
    private static final String[] ANY_DATA = {};
    
    private static final Instant SOME_TIMESTAMP = now();
    private static final String SOME_TARGET = "some-target";
    private static final String SOME_TYPE = "some-type";

    private static final String[] SOME_DATA = { "some-data-part" };

    public PackerOutputMessageTest()
    {
    }
    
    @Test
    public void getItsTimestamp()
    {
        PackerOutputMessage outputMessage = new PackerOutputMessageImpl(
            SOME_TIMESTAMP,
            ANY_TARGET,
            ANY_TYPE,
            ANY_DATA);
        
        assertThat(outputMessage.getTimestamp())
            .isEqualTo(SOME_TIMESTAMP);
    }
    
    @Test
    public void getItsTargetEmpty()
    {
        PackerOutputMessage outputMessage = new PackerOutputMessageImpl(
            ANY_TIMESTAMP,
            NULL_TARGET,
            ANY_TYPE,
            ANY_DATA);
        
        assertThat(outputMessage.getTarget())
            .isEmpty();
    }
    
    @Test
    public void getItsTargetNonEmpty()
    {
        PackerOutputMessage outputMessage = new PackerOutputMessageImpl(
            ANY_TIMESTAMP,
            SOME_TARGET,
            ANY_TYPE,
            ANY_DATA);
        
        assertThat(outputMessage.getTarget())
            .hasValue(SOME_TARGET);
    }
    
    @Test
    public void getItsType()
    {
        PackerOutputMessage outputMessage = new PackerOutputMessageImpl(
            ANY_TIMESTAMP,
            ANY_TARGET,
            SOME_TYPE,
            ANY_DATA);
        
        assertThat(outputMessage.getType())
            .isEqualTo(SOME_TYPE);
    }
    
    @Test
    public void getItsData()
    {
        PackerOutputMessage outputMessage = new PackerOutputMessageImpl(
            ANY_TIMESTAMP,
            ANY_TARGET,
            ANY_TYPE,
            SOME_DATA);
        
        assertThat(outputMessage.getData())
            .isEqualTo(SOME_DATA);
    }
}
