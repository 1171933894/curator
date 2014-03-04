/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.curator.x.rest.api;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.test.KillSession;
import org.apache.curator.test.TestingServer;
import org.apache.curator.test.Timing;
import org.apache.curator.x.rest.entities.Status;
import org.apache.curator.x.rest.entities.StatusMessage;
import org.apache.curator.x.rest.support.BaseClassForTests;
import org.apache.curator.x.rest.support.InterProcessLockBridge;
import org.apache.curator.x.rest.support.StatusListener;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class TestLocks extends BaseClassForTests
{
    @Test
    public void test2Clients() throws Exception
    {
        final InterProcessLock mutexForClient1 = new InterProcessLockBridge(restClient, sessionManager, uriMaker);
        final InterProcessLock mutexForClient2 = new InterProcessLockBridge(restClient, sessionManager, uriMaker);

        final CountDownLatch latchForClient1 = new CountDownLatch(1);
        final CountDownLatch latchForClient2 = new CountDownLatch(1);
        final CountDownLatch acquiredLatchForClient1 = new CountDownLatch(1);
        final CountDownLatch acquiredLatchForClient2 = new CountDownLatch(1);

        final AtomicReference<Exception> exceptionRef = new AtomicReference<Exception>();

        ExecutorService service = Executors.newCachedThreadPool();
        Future<Object> future1 = service.submit
            (
                new Callable<Object>()
                {
                    @Override
                    public Object call() throws Exception
                    {
                        try
                        {
                            if ( !mutexForClient1.acquire(10, TimeUnit.SECONDS) )
                            {
                                throw new Exception("mutexForClient1.acquire timed out");
                            }
                            acquiredLatchForClient1.countDown();
                            if ( !latchForClient1.await(10, TimeUnit.SECONDS) )
                            {
                                throw new Exception("latchForClient1 timed out");
                            }
                            mutexForClient1.release();
                        }
                        catch ( Exception e )
                        {
                            exceptionRef.set(e);
                        }
                        return null;
                    }
                }
            );
        Future<Object> future2 = service.submit
            (
                new Callable<Object>()
                {
                    @Override
                    public Object call() throws Exception
                    {
                        try
                        {
                            if ( !mutexForClient2.acquire(10, TimeUnit.SECONDS) )
                            {
                                throw new Exception("mutexForClient2.acquire timed out");
                            }
                            acquiredLatchForClient2.countDown();
                            if ( !latchForClient2.await(10, TimeUnit.SECONDS) )
                            {
                                throw new Exception("latchForClient2 timed out");
                            }
                            mutexForClient2.release();
                        }
                        catch ( Exception e )
                        {
                            exceptionRef.set(e);
                        }
                        return null;
                    }
                }
            );

        while ( !mutexForClient1.isAcquiredInThisProcess() && !mutexForClient2.isAcquiredInThisProcess() )
        {
            Thread.sleep(1000);
            Exception exception = exceptionRef.get();
            if ( exception != null )
            {
                throw exception;
            }
            Assert.assertFalse(future1.isDone() && future2.isDone());
        }

        Assert.assertTrue(mutexForClient1.isAcquiredInThisProcess() != mutexForClient2.isAcquiredInThisProcess());
        Thread.sleep(1000);
        Assert.assertTrue(mutexForClient1.isAcquiredInThisProcess() || mutexForClient2.isAcquiredInThisProcess());
        Assert.assertTrue(mutexForClient1.isAcquiredInThisProcess() != mutexForClient2.isAcquiredInThisProcess());

        Exception exception = exceptionRef.get();
        if ( exception != null )
        {
            throw exception;
        }

        if ( mutexForClient1.isAcquiredInThisProcess() )
        {
            latchForClient1.countDown();
            Assert.assertTrue(acquiredLatchForClient2.await(10, TimeUnit.SECONDS));
            Assert.assertTrue(mutexForClient2.isAcquiredInThisProcess());
        }
        else
        {
            latchForClient2.countDown();
            Assert.assertTrue(acquiredLatchForClient1.await(10, TimeUnit.SECONDS));
            Assert.assertTrue(mutexForClient1.isAcquiredInThisProcess());
        }
    }

    @Test
    public void testWaitingProcessKilledServer() throws Exception
    {
        final Timing timing = new Timing();
        final CountDownLatch latch = new CountDownLatch(1);
        StatusListener statusListener = new StatusListener()
        {
            @Override
            public void statusUpdate(List<StatusMessage> messages)
            {
                // NOP
            }

            @Override
            public void errorState(Status status)
            {
                if ( status.getState().equals("lost") )
                {
                    latch.countDown();
                }
            }
        };
        sessionManager.setStatusListener(statusListener);

        final AtomicBoolean isFirst = new AtomicBoolean(true);
        ExecutorCompletionService<Object> service = new ExecutorCompletionService<Object>(Executors.newFixedThreadPool(2));
        for ( int i = 0; i < 2; ++i )
        {
            service.submit
                (
                    new Callable<Object>()
                    {
                        @Override
                        public Object call() throws Exception
                        {
                            InterProcessLock lock = new InterProcessLockBridge(restClient, sessionManager, uriMaker);
                            lock.acquire();
                            try
                            {
                                if ( isFirst.compareAndSet(true, false) )
                                {
                                    timing.sleepABit();

                                    server.stop();
                                    Assert.assertTrue(timing.awaitLatch(latch));
                                    server = new TestingServer(server.getPort(), server.getTempDirectory());
                                }
                            }
                            finally
                            {
                                try
                                {
                                    lock.release();
                                }
                                catch ( Exception e )
                                {
                                    // ignore
                                }
                            }
                            return null;
                        }
                    }
                );
        }

        for ( int i = 0; i < 2; ++i )
        {
            service.take().get(timing.forWaiting().milliseconds(), TimeUnit.MILLISECONDS);
        }
    }

    @Test
    public void testKilledSession() throws Exception
    {
        final Timing timing = new Timing();

        final InterProcessLock mutex1 = new InterProcessLockBridge(restClient, sessionManager, uriMaker);
        final InterProcessLock mutex2 = new InterProcessLockBridge(restClient, sessionManager, uriMaker);

        final Semaphore semaphore = new Semaphore(0);
        ExecutorCompletionService<Object> service = new ExecutorCompletionService<Object>(Executors.newFixedThreadPool(2));
        service.submit
            (
                new Callable<Object>()
                {
                    @Override
                    public Object call() throws Exception
                    {
                        mutex1.acquire();
                        semaphore.release();
                        Thread.sleep(1000000);
                        return null;
                    }
                }
            );

        service.submit
            (
                new Callable<Object>()
                {
                    @Override
                    public Object call() throws Exception
                    {
                        mutex2.acquire();
                        semaphore.release();
                        Thread.sleep(1000000);
                        return null;
                    }
                }
            );

        Assert.assertTrue(timing.acquireSemaphore(semaphore, 1));
        KillSession.kill(getCuratorRestContext().getClient().getZookeeperClient().getZooKeeper(), server.getConnectString());
        Assert.assertTrue(timing.acquireSemaphore(semaphore, 1));
    }
}
