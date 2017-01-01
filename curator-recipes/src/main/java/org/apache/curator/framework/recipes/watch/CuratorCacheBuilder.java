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
package org.apache.curator.framework.recipes.watch;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import org.apache.curator.framework.CuratorFramework;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class CuratorCacheBuilder
{
    private final CuratorFramework client;
    private final String path;
    private CacheBuilder<Object, Object> cacheBuilder = CacheBuilder.newBuilder();
    private CacheAction singleNodeCacheAction = null;
    private boolean sendRefreshEvents = true;
    private boolean refreshOnStart = true;
    private boolean sortChildren = true;
    private CachedNodeComparator nodeComparator = CachedNodeComparators.dataAndType();
    private CacheSelector cacheSelector = CacheSelectors.statAndData();

    public static CuratorCacheBuilder builder(CuratorFramework client, String path)
    {
        return new CuratorCacheBuilder(client, path);
    }

    public CuratorCache build()
    {
        if ( singleNodeCacheAction != null )
        {
            Preconditions.checkState(cacheSelector == null, "Single node mode does not support CacheSelectors");
            return new InternalNodeCache(client, path, singleNodeCacheAction, nodeComparator, cacheBuilder.<String, CachedNode>build(), sendRefreshEvents, refreshOnStart);
        }

        return new InternalCuratorCache(client, path, cacheSelector, nodeComparator, cacheBuilder.<String, CachedNode>build(), sendRefreshEvents, refreshOnStart, sortChildren);
    }

    public CuratorCacheBuilder forSingleNode()
    {
        return forSingleNode(CacheAction.STAT_AND_DATA);
    }

    public CuratorCacheBuilder forSingleNode(CacheAction cacheAction)
    {
        cacheSelector = null;
        singleNodeCacheAction = Objects.requireNonNull(cacheAction, "cacheAction cannot be null");
        return this;
    }

    public CuratorCacheBuilder usingWeakValues()
    {
        cacheBuilder = cacheBuilder.weakValues();
        return this;
    }

    public CuratorCacheBuilder usingSoftValues()
    {
        cacheBuilder = cacheBuilder.softValues();
        return this;
    }

    public CuratorCacheBuilder thatExpiresAfterWrite(long duration, TimeUnit unit)
    {
        cacheBuilder = cacheBuilder.expireAfterWrite(duration, unit);
        return this;
    }

    public CuratorCacheBuilder thatExpiresAfterAccess(long duration, TimeUnit unit)
    {
        cacheBuilder = cacheBuilder.expireAfterAccess(duration, unit);
        return this;
    }

    public CuratorCacheBuilder sendingRefreshEvents(boolean sendRefreshEvents)
    {
        this.sendRefreshEvents = sendRefreshEvents;
        return this;
    }

    public CuratorCacheBuilder refreshingWhenStarted(boolean refreshOnStart)
    {
        this.refreshOnStart = refreshOnStart;
        return this;
    }

    public CuratorCacheBuilder sortingChildren(boolean sortChildren)
    {
        this.sortChildren = sortChildren;
        return this;
    }

    public CuratorCacheBuilder withNodeComparator(CachedNodeComparator nodeComparator)
    {
        this.nodeComparator = Objects.requireNonNull(nodeComparator, "nodeComparator cannot be null");
        return this;
    }

    public CuratorCacheBuilder withCacheSelector(CacheSelector cacheSelector)
    {
        this.cacheSelector = Objects.requireNonNull(cacheSelector, "cacheSelector cannot be null");
        return this;
    }

    private CuratorCacheBuilder(CuratorFramework client, String path)
    {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.path = Objects.requireNonNull(path, "path cannot be null");
    }
}
