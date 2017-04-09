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
package org.apache.curator.x.async.modeled.recipes;

import org.apache.curator.framework.listen.Listenable;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.x.async.modeled.ModeledDetails;
import org.apache.curator.x.async.modeled.details.recipes.ModeledNodeCacheImpl;
import java.io.Closeable;
import java.util.Optional;

/**
 * Wraps a {@link org.apache.curator.framework.recipes.cache.NodeCache} so that
 * node data can be viewed as strongly typed models.
 */
public interface ModeledNodeCache<T> extends Closeable
{
    /**
     * Return a newly wrapped cache
     *
     * @param modeled modeling options
     * @param cache the cache to wrap
     * @return new wrapped cache
     */
    static <T> ModeledNodeCache wrap(ModeledDetails<T> modeled, NodeCache cache)
    {
        return new ModeledNodeCacheImpl<>(modeled, cache);
    }

    /**
     * Return the original cache that was wrapped
     *
     * @return cache
     */
    NodeCache unwrap();

    /**
     * Forwards to {@link org.apache.curator.framework.recipes.cache.NodeCache#start()}
     */
    void start();

    /**
     * Forwards to {@link org.apache.curator.framework.recipes.cache.NodeCache#start(boolean)}
     */
    void start(boolean buildInitial);

    /**
     * Forwards to {@link org.apache.curator.framework.recipes.cache.NodeCache#rebuild()}
     */
    void rebuild();

    /**
     * Forwards to {@link org.apache.curator.framework.recipes.cache.NodeCache#getListenable()}
     */
    Listenable<NodeCacheListener> getListenable();

    /**
     * Return the modeled current data. There are no guarantees of accuracy. This is
     * merely the most recent view of the data. If the node does not exist,
     * this returns {@link java.util.Optional#empty()} is returned
     *
     * @return node data
     */
    Optional<ModeledCachedNode<T>> getCurrentData();

    /**
     * Forwards to {@link org.apache.curator.framework.recipes.cache.NodeCache#close()}
     */
    void close();
}
