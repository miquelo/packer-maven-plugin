package io.github.miquelo.maven.plugin.packer.example;

import static java.lang.String.format;
import static java.lang.System.out;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Semaphore;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

public class JMXService
implements JMXServiceMBean
{
    private transient Semaphore semaphore;
    
    public JMXService()
    {
        semaphore = new Semaphore(1);
    }
    
    @Override
    public void terminate()
    {
        semaphore.release();
    }
    
    public static void main(String[] args)
    throws Exception
    {
        JMXService service = new JMXService();
        service.start();
        out.println("Server started");
        service.awaitTermination();
        out.println("Server terminated");
    }
    
    private void start()
    throws
        InterruptedException,
        NotCompliantMBeanException,
        MBeanRegistrationException,
        InstanceAlreadyExistsException,
        MalformedObjectNameException
    {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        server.registerMBean(
            this,
            new ObjectName(format(
                "%s:type=basic,name=%s",
                "io.github.miquelo.maven.plugin.packer.example",
                "packer-maven-plugin-example-jmx-server")));
        semaphore.acquire();
    }
    
    private void awaitTermination()
    throws InterruptedException
    {
        semaphore.acquire();
    }
}
