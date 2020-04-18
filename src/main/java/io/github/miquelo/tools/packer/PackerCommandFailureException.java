package io.github.miquelo.tools.packer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.beans.ConstructorProperties;

import io.github.miquelo.tools.packer.PackerCommand.FailureCode;

public class PackerCommandFailureException
extends Exception
{
    private static final long serialVersionUID = 1L;
    
    private final FailureCode failureCode;
    
    @ConstructorProperties(
        "failureCode"
    )
    public PackerCommandFailureException(FailureCode failureCode)
    {
        super(format("Packer execution failed: %s", failureCode));
        this.failureCode = requireNonNull(failureCode);
    }

    public FailureCode getFailureCode()
    {
        return failureCode;
    }
}
