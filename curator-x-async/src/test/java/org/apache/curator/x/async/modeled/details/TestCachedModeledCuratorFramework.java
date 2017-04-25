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
package org.apache.curator.x.async.modeled.details;

import com.google.common.collect.Sets;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.x.async.CompletableBaseClassForTests;
import org.apache.curator.x.async.modeled.JacksonModelSerializer;
import org.apache.curator.x.async.modeled.ModelSerializer;
import org.apache.curator.x.async.modeled.ModeledCuratorFramework;
import org.apache.curator.x.async.modeled.ZPath;
import org.apache.curator.x.async.modeled.caching.CachingOption;
import org.apache.curator.x.async.modeled.models.TestSimpleModel;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.util.concurrent.atomic.AtomicInteger;

public class TestCachedModeledCuratorFramework extends CompletableBaseClassForTests
{
    private static final ZPath path = ZPath.parse("/test/path");
    private CuratorFramework rawClient;
    private ModeledCuratorFramework<TestSimpleModel> client;

    @BeforeMethod
    @Override
    public void setup() throws Exception
    {
        super.setup();

        rawClient = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1));
        rawClient.start();

        ModelSerializer<TestSimpleModel> serializer = new JacksonModelSerializer<>(TestSimpleModel.class);
        client = ModeledCuratorFramework.builder(rawClient, path, serializer).cached().build();
    }

    @AfterMethod
    @Override
    public void teardown() throws Exception
    {
        CloseableUtils.closeQuietly(rawClient);
        super.teardown();
    }

    @Test
    public void testBasic() throws InterruptedException
    {
        client.caching().start();

        AtomicInteger counter = new AtomicInteger();
        ((ModeledCuratorFrameworkImpl)client).debugCachedReadCount = counter;

        complete(client.read());
        Assert.assertEquals(counter.get(), 0);

        complete(client.create(new TestSimpleModel("test", 10)));
        Assert.assertEquals(counter.get(), 0);

        timing.sleepABit();

        complete(client.read());
        Assert.assertEquals(counter.get(), 1);
        counter.set(0);

        complete(client.create(new TestSimpleModel("test2", 20)));
        Assert.assertEquals(counter.get(), 0);

        timing.sleepABit();

        complete(client.read(), (model, e) -> Assert.assertEquals(model, new TestSimpleModel("test2", 20)));
        Assert.assertEquals(counter.get(), 1);

        client.caching().close();
    }
}
