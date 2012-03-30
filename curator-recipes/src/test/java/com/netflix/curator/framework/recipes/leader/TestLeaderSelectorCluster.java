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
package com.netflix.curator.framework.recipes.leader;

import com.google.common.io.Closeables;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.framework.api.UnhandledErrorListener;
import com.netflix.curator.framework.state.ConnectionState;
import com.netflix.curator.retry.ExponentialBackoffRetry;
import com.netflix.curator.test.TestingCluster;
import com.netflix.curator.test.Timing;
import com.netflix.curator.utils.ZKPaths;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class TestLeaderSelectorCluster
{
    @Test
    public void     testRestart() throws Exception
    {
        final Timing        timing = new Timing();

        CuratorFramework    client = null;
        TestingCluster      cluster = new TestingCluster(3);
        cluster.start();
        try
        {
            client = CuratorFrameworkFactory.newClient(cluster.getConnectString(), timing.session(), timing.connection(), new ExponentialBackoffRetry(100, 3));
            client.start();

            final AtomicInteger     errors = new AtomicInteger(0);
            client.getUnhandledErrorListenable().addListener
            (
                new UnhandledErrorListener()
                {
                    @Override
                    public void unhandledError(String message, Throwable e)
                    {
                        errors.incrementAndGet();
                    }
                }
            );
            
            final Semaphore             semaphore = new Semaphore(0);
            final CountDownLatch        reconnectedLatch = new CountDownLatch(1);
            LeaderSelectorListener      listener = new LeaderSelectorListener()
            {
                @Override
                public void takeLeadership(CuratorFramework client) throws Exception
                {
                    List<String>        names = client.getChildren().forPath("/leader");
                    Assert.assertTrue(names.size() > 0);
                    semaphore.release();
                }

                @Override
                public void stateChanged(CuratorFramework client, ConnectionState newState)
                {
                    if ( newState == ConnectionState.RECONNECTED )
                    {
                        reconnectedLatch.countDown();
                    }
                }
            };
            LeaderSelector      selector = new LeaderSelector(client, "/leader", listener);
            selector.start();
            Assert.assertTrue(timing.acquireSemaphore(semaphore));

            TestingCluster.InstanceSpec     connectionInstance = cluster.findConnectionInstance(client.getZookeeperClient().getZooKeeper());
            cluster.killServer(connectionInstance);

            Assert.assertTrue(timing.awaitLatch(reconnectedLatch));
            selector.requeue();
            Assert.assertTrue(timing.acquireSemaphore(semaphore));
            
            Assert.assertEquals(errors.get(), 0);
        }
        finally
        {
            Closeables.closeQuietly(client);
            Closeables.closeQuietly(cluster);
        }
    }

    @Test
    public void     testLostRestart() throws Exception
    {
        final Timing        timing = new Timing();

        CuratorFramework    client = null;
        TestingCluster      cluster = new TestingCluster(3);
        cluster.start();
        try
        {
            client = CuratorFrameworkFactory.newClient(cluster.getConnectString(), timing.session(), timing.connection(), new ExponentialBackoffRetry(timing.milliseconds(), 3));
            client.start();
            client.sync("/", null);

            final AtomicReference<Exception>        error = new AtomicReference<Exception>(null);
            final AtomicReference<String>           lockNode = new AtomicReference<String>(null);
            final Semaphore                         semaphore = new Semaphore(0);
            final CountDownLatch                    lostLatch = new CountDownLatch(1);
            final CountDownLatch                    internalLostLatch = new CountDownLatch(1);
            LeaderSelectorListener                  listener = new LeaderSelectorListener()
            {
                @Override
                public void takeLeadership(CuratorFramework client) throws Exception
                {
                    try
                    {
                        List<String>        names = client.getChildren().forPath("/leader");
                        if ( names.size() != 1 )
                        {
                            semaphore.release();
                            Exception exception = new Exception("Names size isn't 1: " + names.size());
                            error.set(exception);
                            return;
                        }
                        lockNode.set(names.get(0));

                        semaphore.release();
                        if ( !timing.multiple(4).awaitLatch(internalLostLatch) )
                        {
                            error.set(new Exception("internalLostLatch await failed"));
                        }
                    }
                    finally
                    {
                        lostLatch.countDown();
                    }
                }

                @Override
                public void stateChanged(CuratorFramework client, ConnectionState newState)
                {
                    if ( newState == ConnectionState.LOST )
                    {
                        internalLostLatch.countDown();
                    }
                }
            };
            LeaderSelector      selector = new LeaderSelector(client, "/leader", listener);
            selector.start();
            Assert.assertTrue(timing.multiple(4).acquireSemaphore(semaphore));
            if ( error.get() != null )
            {
                throw new AssertionError(error.get());
            }

            Collection<TestingCluster.InstanceSpec>    instances = cluster.getInstances();
            cluster.stop();

            Assert.assertTrue(timing.multiple(4).awaitLatch(lostLatch));
            timing.sleepABit();
            Assert.assertFalse(selector.hasLeadership());

            Assert.assertNotNull(lockNode.get());
            
            cluster = new TestingCluster(instances.toArray(new TestingCluster.InstanceSpec[instances.size()]));
            cluster.start();

            try
            {
                client.delete().forPath(ZKPaths.makePath("/leader", lockNode.get()));   // simulate the lock deleting due to session expiration
            }
            catch ( Exception ignore )
            {
                // ignore
            }

            Assert.assertTrue(semaphore.availablePermits() == 0);
            Assert.assertFalse(selector.hasLeadership());

            selector.requeue();
            Assert.assertTrue(timing.multiple(4).acquireSemaphore(semaphore));
            if ( error.get() != null )
            {
                throw new AssertionError(error.get());
            }
        }
        finally
        {
            Closeables.closeQuietly(client);
            Closeables.closeQuietly(cluster);
        }
    }
}
