package io.github.miquelo.maven.plugin.packer.example;

import static java.lang.String.format;
import static java.lang.System.out;
import static java.lang.Thread.sleep;
import static java.lang.management.ManagementFactory.getPlatformMBeanServer;
import static java.util.stream.Collectors.toMap;

import java.util.AbstractMap.SimpleEntry;
import java.util.Hashtable;
import java.util.Map.Entry;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

public class JMXService
implements JMXServiceMBean
{
    private static final long TEMINATION_SECONDS = 8L;
    
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
        getPlatformMBeanServer().registerMBean(this, new ObjectName(
            "io.github.miquelo.maven.plugin.packer.example",
            new Hashtable<>(Stream.of(
                new SimpleEntry<>("name", "JMXService"))
                .collect(toMap(Entry::getKey, Entry::getValue)))));
        semaphore.acquire();
    }
    
    private void awaitTermination()
    throws InterruptedException
    {
        semaphore.acquire();
        out.println(format(
            "Waiting %d seconds before termination...",
            TEMINATION_SECONDS));
        sleep(TEMINATION_SECONDS * 1000L);
    }
}
