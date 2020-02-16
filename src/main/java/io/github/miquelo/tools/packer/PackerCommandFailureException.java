package io.github.miquelo.tools.packer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.beans.ConstructorProperties;

public class PackerCommandFailureException
extends Exception
{
    private static final long serialVersionUID = 1L;
    
    private final PackerCommandFailureCode failureCode;
    
    @ConstructorProperties(
        "failureCode"
    )
    public PackerCommandFailureException(PackerCommandFailureCode failureCode)
    {
        super(format("Packer execution failed: %s", failureCode));
        this.failureCode = requireNonNull(failureCode);
    }

    public PackerCommandFailureCode getFailureCode()
    {
        return failureCode;
    }
}
