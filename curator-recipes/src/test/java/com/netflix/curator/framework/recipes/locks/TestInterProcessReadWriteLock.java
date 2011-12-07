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

package com.netflix.curator.framework.recipes.locks;

import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.framework.recipes.BaseClassForTests;
import com.netflix.curator.retry.RetryOneTime;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestInterProcessReadWriteLock extends BaseClassForTests
{
    @Test
    public void     testBasic() throws Exception
    {
        final int               CONCURRENCY = 8;
        final int               ITERATIONS = 100;

        final CuratorFramework        client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try
        {
            client.start();

            final Random            random = new Random();
            final AtomicInteger     concurrentCount = new AtomicInteger(0);
            final AtomicInteger     maxConcurrentCount = new AtomicInteger(0);
            final AtomicInteger     writeCount = new AtomicInteger(0);
            final AtomicInteger     readCount = new AtomicInteger(0);

            List<Future<Void>>  futures = Lists.newArrayList();
            ExecutorService     service = Executors.newCachedThreadPool();
            for ( int i = 0; i < CONCURRENCY; ++i )
            {
                Future<Void>    future = service.submit
                (
                    new Callable<Void>()
                    {
                        @Override
                        public Void call() throws Exception
                        {
                            InterProcessReadWriteLock   lock = new InterProcessReadWriteLock(client, "/lock");
                            for ( int i = 0; i < ITERATIONS; ++i )
                            {
                                if ( random.nextInt(100) < 10 )
                                {
                                    doLocking(lock.writeLock(), concurrentCount, maxConcurrentCount, random, 1);
                                    writeCount.incrementAndGet();
                                }
                                else
                                {
                                    doLocking(lock.readLock(), concurrentCount, maxConcurrentCount, random, Integer.MAX_VALUE);
                                    readCount.incrementAndGet();
                                }
                            }
                            return null;
                        }
                    }
                );
                futures.add(future);
            }

            for ( Future<Void> future : futures )
            {
                future.get();
            }

            System.out.println("Writes: " + writeCount.get() + " - Reads: " + readCount.get() + " - Max Reads: " + maxConcurrentCount.get());

            Assert.assertTrue(writeCount.get() > 0);
            Assert.assertTrue(readCount.get() > 0);
            Assert.assertTrue(maxConcurrentCount.get() > 1);
        }
        finally
        {
            Closeables.closeQuietly(client);
        }
    }

    private void doLocking(InterProcessLock lock, AtomicInteger concurrentCount, AtomicInteger maxConcurrentCount, Random random, int maxAllowed) throws Exception
    {
        try
        {
            Assert.assertTrue(lock.acquire(10, TimeUnit.SECONDS));
            int     localConcurrentCount;
            synchronized(this)
            {
                localConcurrentCount = concurrentCount.incrementAndGet();
                if ( localConcurrentCount > maxConcurrentCount.get() )
                {
                    maxConcurrentCount.set(localConcurrentCount);
                }
            }

            Assert.assertTrue(localConcurrentCount <= maxAllowed, "" + localConcurrentCount);

            Thread.sleep(random.nextInt(9) + 1);
        }
        finally
        {
            synchronized(this)
            {
                concurrentCount.decrementAndGet();
                lock.release();
            }
        }
    }
}
