package io.github.miquelo.tools.packer;

public interface PackerCommandLogger
{
    void info(String message);
    
    void warn(String message);
    
    void debug(String message);
}
