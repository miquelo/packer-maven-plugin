package io.github.miquelo.tools.packer;

import java.beans.ConstructorProperties;

public class PackerCommandException
extends Exception
{
    private static final long serialVersionUID = 1L;
    
    @ConstructorProperties(
        "message"
    )
    public PackerCommandException(String message)
    {
        super(message);
    }
    
    @ConstructorProperties({
        "message",
        "cause"
    })
    public PackerCommandException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
