package io.github.miquelo.tools.packer;

import static java.util.Objects.requireNonNull;

import java.time.Instant;

public class PackerOutputMessage
{
    public static final String TYPE_UI = "ui";
    public static final String TYPE_ARTIFACT_COUNT = "artifact-count";
    
    public static final String DATA_UI_SAY = "say";
    public static final String DATA_UI_MESSAGE = "message";
    public static final String DATA_UI_ERROR = "error";
    
    private final Instant timestamp;
    private final String target;
    private final String type;
    private final String data[];
    
    public PackerOutputMessage(
        Instant timestamp,
        String target,
        String type,
        String data[])
    {
        this.timestamp = requireNonNull(timestamp);
        this.target = target;
        this.type = requireNonNull(type);
        this.data = requireNonNull(data);
    }

    public Instant getTimestamp()
    {
        return timestamp;
    }

    public String getTarget()
    {
        return target;
    }

    public String getType()
    {
        return type;
    }

    public String[] getData()
    {
        return data;
    }
}
