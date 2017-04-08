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
package org.apache.curator.x.async.modeled;

import com.google.common.collect.ImmutableSet;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.api.UnhandledErrorListener;
import org.apache.curator.x.async.WatchMode;
import org.apache.curator.x.async.api.CreateOption;
import org.apache.curator.x.async.api.DeleteOption;
import org.apache.curator.x.async.modeled.details.ModeledAsyncCuratorFrameworkImpl;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.data.ACL;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

public class ModeledAsyncCuratorFrameworkBuilder<T>
{
    private final CuratorFramework client;
    private final ZPath path;
    private final ModelSerializer<T> serializer;
    private WatchMode watchMode;
    private UnaryOperator<WatchedEvent> watcherFilter;
    private UnhandledErrorListener unhandledErrorListener;
    private UnaryOperator<CuratorEvent> resultFilter;
    private CreateMode createMode;
    private List<ACL> aclList;
    private Set<CreateOption> createOptions;
    private Set<DeleteOption> deleteOptions;

    public ModeledAsyncCuratorFramework<T> build()
    {
        return new ModeledAsyncCuratorFrameworkImpl<>(
            client,
            path.fullPath(),
            serializer,
            watchMode,
            watcherFilter,
            unhandledErrorListener,
            resultFilter,
            createMode,
            aclList,
            createOptions,
            deleteOptions
        );
    }

    public ModeledAsyncCuratorFrameworkBuilder<T> watched()
    {
        this.watchMode = WatchMode.stateChangeAndSuccess;
        return this;
    }

    public ModeledAsyncCuratorFrameworkBuilder<T> watched(WatchMode watchMode)
    {
        this.watchMode = watchMode;
        return this;
    }

    public ModeledAsyncCuratorFrameworkBuilder<T> watched(WatchMode watchMode, UnaryOperator<WatchedEvent> watcherFilter)
    {
        this.watchMode = watchMode;
        this.watcherFilter = watcherFilter;
        return this;
    }

    public ModeledAsyncCuratorFrameworkBuilder<T> withUnhandledErrorListener(UnhandledErrorListener unhandledErrorListener)
    {
        this.unhandledErrorListener = unhandledErrorListener;
        return this;
    }

    public ModeledAsyncCuratorFrameworkBuilder<T> withResultFilter(UnaryOperator<CuratorEvent> resultFilter)
    {
        this.resultFilter = resultFilter;
        return this;
    }

    public ModeledAsyncCuratorFrameworkBuilder<T> withCreateMode(CreateMode createMode)
    {
        this.createMode = createMode;
        return this;
    }

    public ModeledAsyncCuratorFrameworkBuilder<T> withAclList(List<ACL> aclList)
    {
        this.aclList = aclList;
        return this;
    }

    public ModeledAsyncCuratorFrameworkBuilder<T> withCreateOptions(Set<CreateOption> createOptions)
    {
        this.createOptions = (createOptions != null) ? ImmutableSet.copyOf(createOptions) : null;
        return this;
    }

    public ModeledAsyncCuratorFrameworkBuilder<T> withDeleteOptions(Set<DeleteOption> deleteOptions)
    {
        this.deleteOptions = (deleteOptions != null) ? ImmutableSet.copyOf(deleteOptions) : null;
        return this;
    }

    ModeledAsyncCuratorFrameworkBuilder(CuratorFramework client, ZPath path, ModelSerializer<T> serializer)
    {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer cannot be null");
    }
}
