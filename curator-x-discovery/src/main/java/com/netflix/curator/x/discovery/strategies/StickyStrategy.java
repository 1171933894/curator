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

package com.netflix.curator.x.discovery.strategies;

import com.netflix.curator.x.discovery.ProviderStrategy;
import com.netflix.curator.x.discovery.ServiceInstance;
import com.netflix.curator.x.discovery.details.InstanceProvider;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class StickyStrategy<T> implements ProviderStrategy<T>
{
    private final ProviderStrategy<T>                   masterStrategy;
    private final AtomicReference<ServiceInstance<T>>   ourInstance = new AtomicReference<ServiceInstance<T>>(null);
    private final AtomicInteger                         instanceNumber = new AtomicInteger(-1);

    public StickyStrategy(ProviderStrategy<T> masterStrategy)
    {
        this.masterStrategy = masterStrategy;
    }

    @Override
    public ServiceInstance<T> getInstance(InstanceProvider<T> instanceProvider) throws Exception
    {
        final List<ServiceInstance<T>>    instances = instanceProvider.getInstances();

        {
            ServiceInstance<T>                localOurInstance = ourInstance.get();
            if ( !instances.contains(localOurInstance) )
            {
                ourInstance.compareAndSet(localOurInstance, null);
            }
        }
        
        if ( ourInstance.get() == null )
        {
            ServiceInstance<T> instance = masterStrategy.getInstance
            (
                new InstanceProvider<T>()
                {
                    @Override
                    public List<ServiceInstance<T>> getInstances() throws Exception
                    {
                       return instances;
                    }
                }
            );
            if ( ourInstance.compareAndSet(null, instance) )
            {
                instanceNumber.incrementAndGet();
            }
        }
        return ourInstance.get();
    }

    public int getInstanceNumber()
    {
        return instanceNumber.get();
    }
}
