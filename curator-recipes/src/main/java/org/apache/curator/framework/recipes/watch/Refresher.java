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

import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.atomic.AtomicInteger;

class Refresher
{
    private final CuratorCacheBase cacheBase;
    private final String refreshPath;
    private final SettableFuture<Boolean> task;
    private final AtomicInteger count = new AtomicInteger(0);

    public Refresher(CuratorCacheBase cacheBase, String refreshPath)
    {
        this(cacheBase, refreshPath, null);
    }

    Refresher(CuratorCacheBase cacheBase, String refreshPath, SettableFuture<Boolean> task)
    {

        this.cacheBase = cacheBase;
        this.refreshPath = refreshPath;
        this.task = task;
    }

    void increment()
    {
        count.incrementAndGet();
    }

    void decrement()
    {
        if ( count.decrementAndGet() <= 0 )
        {
            cacheBase.notifyListeners(CacheEvent.CACHE_REFRESHED, refreshPath);
            if ( task != null )
            {
                task.set(true);
            }
        }
    }

    boolean isCancelled()
    {
        return (task != null) && task.isCancelled();
    }
}
