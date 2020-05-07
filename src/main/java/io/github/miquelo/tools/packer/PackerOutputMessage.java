package io.github.miquelo.tools.packer;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Optional;

public interface PackerOutputMessage
{
    public static final String TYPE_UI = "ui";
    public static final String TYPE_ARTIFACT_COUNT = "artifact-count";
    
    public static final String DATA_UI_SAY = "say";
    public static final String DATA_UI_MESSAGE = "message";
    public static final String DATA_UI_ERROR = "error";
    
    Instant getTimestamp();

    Optional<String> getTarget();

    String getType();

    String[] getData();
}

class PackerOutputMessageImpl
implements PackerOutputMessage
{
    private final Instant timestamp;
    private final String target;
    private final String type;
    private final String data[];
    
    public PackerOutputMessageImpl(
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

    @Override
    public Instant getTimestamp()
    {
        return timestamp;
    }

    @Override
    public Optional<String> getTarget()
    {
        return Optional.ofNullable(target);
    }

    @Override
    public String getType()
    {
        return type;
    }

    @Override
    public String[] getData()
    {
        return data;
    }
}
