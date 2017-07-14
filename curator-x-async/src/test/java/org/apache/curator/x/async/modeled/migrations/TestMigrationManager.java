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
package org.apache.curator.x.async.modeled.migrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.transaction.CuratorOp;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.x.async.AsyncCuratorFramework;
import org.apache.curator.x.async.CompletableBaseClassForTests;
import org.apache.curator.x.async.modeled.JacksonModelSerializer;
import org.apache.curator.x.async.modeled.ModelSpec;
import org.apache.curator.x.async.modeled.ModeledFramework;
import org.apache.curator.x.async.modeled.ZPath;
import org.apache.curator.x.async.modeled.migrations.models.ModelV1;
import org.apache.curator.x.async.modeled.migrations.models.ModelV2;
import org.apache.curator.x.async.modeled.migrations.models.ModelV3;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

public class TestMigrationManager extends CompletableBaseClassForTests
{
    private AsyncCuratorFramework client;
    private MigrationSet migrationSet;
    private ModelSpec<ModelV1> v1Spec;
    private ModelSpec<ModelV2> v2Spec;
    private ModelSpec<ModelV3> v3Spec;
    private ExecutorService executor;

    @BeforeMethod
    @Override
    public void setup() throws Exception
    {
        super.setup();

        CuratorFramework rawClient = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(100));
        rawClient.start();

        this.client = AsyncCuratorFramework.wrap(rawClient);

        ObjectMapper mapper = new ObjectMapper();
        UnaryOperator<byte[]> from1to2 = bytes -> {
            try
            {
                ModelV1 v1 = mapper.readerFor(ModelV1.class).readValue(bytes);
                ModelV2 v2 = new ModelV2(v1.getName(), 64);
                return mapper.writeValueAsBytes(v2);
            }
            catch ( IOException e )
            {
                throw new RuntimeException(e);
            }
        };

        UnaryOperator<byte[]> from2to3 = bytes -> {
            try
            {
                ModelV2 v2 = mapper.readerFor(ModelV2.class).readValue(bytes);
                String[] nameParts = v2.getName().split("\\s");
                ModelV3 v3 = new ModelV3(nameParts[0], nameParts[1], v2.getAge());
                return mapper.writeValueAsBytes(v3);
            }
            catch ( IOException e )
            {
                throw new RuntimeException(e);
            }
        };

        ZPath modelPath = ZPath.parse("/test/it");

        v1Spec = ModelSpec.builder(modelPath, JacksonModelSerializer.build(ModelV1.class)).build();
        v2Spec = ModelSpec.builder(modelPath, JacksonModelSerializer.build(ModelV2.class)).build();
        v3Spec = ModelSpec.builder(modelPath, JacksonModelSerializer.build(ModelV3.class)).build();

        CuratorOp v1op = ModeledFramework.wrap(client, v1Spec).createOp(new ModelV1("Test"));
        CuratorOp v2op = ModeledFramework.wrap(client, v2Spec).updateOp(new ModelV2("Test 2", 10));
        CuratorOp v3op = ModeledFramework.wrap(client, v3Spec).updateOp(new ModelV3("One", "Two", 30));

        Migration m1 = Migration.build("1",1, () -> Collections.singletonList(v1op));
        Migration m2 = Migration.build("2",1, () -> Collections.singletonList(v2op));
        Migration m3 = Migration.build("3",1, () -> Collections.singletonList(v3op));
        migrationSet = MigrationSet.build("1", ZPath.parse("/metadata"), Arrays.asList(m1, m2, m3));

        executor = Executors.newCachedThreadPool();
    }

    @AfterMethod
    @Override
    public void teardown() throws Exception
    {
        CloseableUtils.closeQuietly(client.unwrap());
        executor.shutdownNow();
        super.teardown();
    }

    @Test
    public void testBasic() throws Exception
    {
        ModeledFramework<ModelV1> v1Client = ModeledFramework.wrap(client, v1Spec);
        ModelV1 v1 = new ModelV1("John Galt");
        complete(v1Client.child("1").set(v1));

        MigrationManager manager = new MigrationManager(client, ZPath.parse("/locks"), JacksonModelSerializer.build(MetaData.class), executor, Duration.ofMinutes(10));
        complete(manager.migrate(migrationSet));

        ModeledFramework<ModelV3> v3Client = ModeledFramework.wrap(client, v3Spec);
        complete(v3Client.child("1").read(), (m, e) -> {
            Assert.assertEquals(m.getAge(), 64);
            Assert.assertEquals(m.getFirstName(), "John");
            Assert.assertEquals(m.getLastName(), "Galt");
        });
    }
}
