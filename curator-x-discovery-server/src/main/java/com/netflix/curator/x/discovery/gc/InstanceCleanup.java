/*
 *
 *  Copyright 2011 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.netflix.curator.x.discovery.gc;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.curator.x.discovery.ServiceDiscovery;
import com.netflix.curator.x.discovery.ServiceInstance;
import com.netflix.curator.x.discovery.ServiceType;
import com.netflix.curator.x.discovery.config.DiscoveryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InstanceCleanup implements Closeable
{
    private static final Logger         log = LoggerFactory.getLogger(InstanceCleanup.class);

    private final ServiceDiscovery<Object>  discovery;
    private final DiscoveryConfig           config;
    private final ExecutorService           service = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("InstanceCleanup-%d").build());

    public InstanceCleanup(ServiceDiscovery<Object> discovery, DiscoveryConfig config)
    {
        this.discovery = discovery;
        this.config = config;
    }

    public void     start()
    {
        Preconditions.checkArgument(!service.isShutdown());

        service.submit
        (
            new Runnable()
            {
                @Override
                public void run()
                {
                    doWork();
                }
            }
        );
    }

    @Override
    public void close() throws IOException
    {
        Preconditions.checkArgument(!service.isShutdown());
        service.shutdownNow();
    }

    private void doWork()
    {
        while ( !Thread.currentThread().isInterrupted() )
        {
            try
            {
                Thread.sleep(config.getInstanceRefreshMs());
            }
            catch ( InterruptedException e )
            {
                Thread.currentThread().interrupt();
                break;
            }

            try
            {
                for ( String name : discovery.queryForNames() )
                {
                    checkService(name);
                }
            }
            catch ( Exception e )
            {
                log.error("GC for service names", e);
            }
        }
    }

    private void checkService(String name)
    {
        try
        {
            Collection<ServiceInstance<Object>>     instances = discovery.queryForInstances(name);
            for ( ServiceInstance<Object> instance : instances )
            {
                if ( instance.getServiceType() != ServiceType.PERMANENT )
                {
                    if ( (System.currentTimeMillis() - instance.getRegistrationTimeUTC()) > config.getInstanceRefreshMs() )
                    {
                        discovery.unregisterService(instance);
                    }
                }
            }
        }
        catch ( Exception e )
        {
            log.error(String.format("GC for service: %s", name), e);
        }
    }
}
