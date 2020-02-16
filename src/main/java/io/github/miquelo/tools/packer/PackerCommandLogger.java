package io.github.miquelo.tools.packer;

/**
 * Logging bridge for Packer command tasks.
 * 
 * It gives a clean integration with client logging policies and capabilities.
 */
public interface PackerCommandLogger
{
    /**
     * Log with INFO level.
     * 
     * @param message
     *     Log message.
     */
    void info(String message);
    
    /**
     * Log with DEBUG level.
     * 
     * @param message
     *     Log message.
     */
    void debug(String message);
    
    /**
     * Log with WARN level.
     * 
     * @param message
     *     Log message.
     */
    void warn(String message);
    
    /**
     * Log with WARN level associated to a cause.
     * 
     * @param message
     *     Log message.
     * @param cause
     *     Associated cause.
     */
    void warn(String message, Throwable cause);
    
    /**
     * Log with ERROR level.
     * 
     * @param message
     *     Log message.
     */
    void error(String message);
    
    /**
     * Log with ERROR level associated to a cause.
     * 
     * @param message
     *     Log message.
     * @param cause
     *     Associated cause.
     */
    void error(String message, Throwable cause);
}
