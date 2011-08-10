package com.netflix.curator.framework.recipes.atomic;

import com.netflix.curator.RetryPolicy;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.CuratorFrameworkFactory;
import com.netflix.curator.framework.recipes.BaseClassForTests;
import com.netflix.curator.retry.ExponentialBackoffRetry;
import com.netflix.curator.retry.RetryOneTime;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.collections.Lists;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class TestDistributedAtomicLong extends BaseClassForTests
{
    @Test
    public void     testSimulation() throws Exception
    {
        final int           threadQty = 200;
        final int           executionQty = 100;

        final AtomicInteger optimisticTries = new AtomicInteger();
        final AtomicInteger promotedLockTries = new AtomicInteger();
        final AtomicInteger failures = new AtomicInteger();
        final AtomicInteger errors = new AtomicInteger();

        final SummaryStatistics timingStats = new SynchronizedSummaryStatistics();
        List<Future<Void>>      procs = Lists.newArrayList();
        ExecutorService         executorService = Executors.newFixedThreadPool(threadQty);
        for ( int i = 0; i < threadQty; ++i )
        {
            Callable<Void>          proc = new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    doSimulation(executionQty, timingStats, optimisticTries, promotedLockTries, failures, errors);
                    return null;
                }
            };
            procs.add(executorService.submit(proc));
        }

        for ( Future<Void> f : procs )
        {
            f.get();
        }

        System.out.println("OptimisticTries: " + optimisticTries.get());
        System.out.println("PromotedLockTries: " + promotedLockTries.get());
        System.out.println("Failures: " + failures.get());
        System.out.println("Errors: " + errors.get());
        System.out.println();

        System.out.println("Avg time: " + timingStats.getMean());
        System.out.println("Max time: " + timingStats.getMax());
        System.out.println("Min time: " + timingStats.getMin());
        System.out.println("Qty: " + timingStats.getN());

        double             failurePercentage = (double)failures.get() / (double)(executionQty * threadQty);

        Assert.assertTrue(failurePercentage <= 0.02);   // this is more of a hope than a hard fact
        Assert.assertEquals(errors.get(), 0);
        Assert.assertTrue(optimisticTries.get() > 0);
        Assert.assertTrue(promotedLockTries.get() > 0);
    }

    @Test
    public void     testBasic() throws Exception
    {
        CuratorFramework        client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
        try
        {
            DistributedAtomicCounter dal = new DistributedAtomicCounter(client, "/counter", new RetryOneTime(1));

            AtomicValue<Long>           value = dal.increment();
            Assert.assertTrue(value.succeeded());
            Assert.assertEquals(value.getStats().getOptimisticTries(), 1);
            Assert.assertEquals(value.getStats().getPromotedLockTries(), 0);
            Assert.assertEquals(value.preValue().longValue(), 0L);
            Assert.assertEquals(value.postValue().longValue(), 1L);

            value = dal.decrement();
            Assert.assertTrue(value.succeeded());
            Assert.assertEquals(value.getStats().getOptimisticTries(), 1);
            Assert.assertEquals(value.getStats().getPromotedLockTries(), 0);
            Assert.assertEquals(value.preValue().longValue(), 1L);
            Assert.assertEquals(value.postValue().longValue(), 0L);

            value = dal.add(10);
            Assert.assertTrue(value.succeeded());
            Assert.assertEquals(value.getStats().getOptimisticTries(), 1);
            Assert.assertEquals(value.getStats().getPromotedLockTries(), 0);
            Assert.assertEquals(value.preValue().longValue(), 0L);
            Assert.assertEquals(value.postValue().longValue(), 10L);

            value = dal.add(-5);
            Assert.assertTrue(value.succeeded());
            Assert.assertEquals(value.getStats().getOptimisticTries(), 1);
            Assert.assertEquals(value.getStats().getPromotedLockTries(), 0);
            Assert.assertEquals(value.preValue().longValue(), 10L);
            Assert.assertEquals(value.postValue().longValue(), 5L);
        }
        finally
        {
            client.close();
        }
    }

    private void doSimulation(int executionQty, SummaryStatistics timingStats, AtomicInteger optimisticTries, AtomicInteger promotedLockTries, AtomicInteger failures, AtomicInteger errors) throws Exception
    {
        Random          random = new Random();
        long            previousValue = -1;
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        client.start();
        try
        {
            RetryPolicy             retryPolicy = new ExponentialBackoffRetry(3, 3);
            PromotedToLock.Builder  builder = PromotedToLock.builder().lockPath("/lock").retryPolicy(retryPolicy);

            DistributedAtomicCounter dal = new DistributedAtomicCounter(client, "/counter", retryPolicy, builder.build());
            for ( int i = 0; i < executionQty; ++i )
            {
                Thread.sleep(random.nextInt(100));

                long                start = System.currentTimeMillis();
                AtomicValue<Long>   value = dal.increment();
                long                elapsed = System.currentTimeMillis() - start;
                timingStats.addValue(elapsed);

                if ( value.succeeded() )
                {
                    if ( value.postValue() <= previousValue )
                    {
                        errors.incrementAndGet();
                    }

                    previousValue = value.postValue();
                }
                else
                {
                    failures.incrementAndGet();
                }

                optimisticTries.addAndGet(value.getStats().getOptimisticTries());
                promotedLockTries.addAndGet(value.getStats().getPromotedLockTries());
            }
        }
        finally
        {
            client.close();
        }
    }
}
