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

import org.apache.curator.framework.listen.Listenable;
import java.io.Closeable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * General interface for client-cached nodes. Create instances
 * using {@link CuratorCacheBuilder}
 */
public interface CuratorCache extends Closeable
{
    /**
     * Start the cache
     *
     * @return a latch that can be used to block until the initial refresh has completed
     */
    CountDownLatch start();

    /**
     * Close the cache, release resources, etc.
     */
    @Override
    void close();

    /**
     * Get listenable container used to add/remove listeners
     *
     * @return listener container
     */
    Listenable<CacheListener> getListenable();

    /**
     * force-fill the cache by getting all applicable nodes. The returned latch
     * can be used to check/block for completion. If the cache is set to send refresh
     * events, one will be posted when this refresh has completed.
     *
     * @return a latch that signals when the refresh is complete
     */
    CountDownLatch refreshAll();

    /**
     * Refresh the given cached node. The returned latch
     * can be used to check/block for completion. If the cache is set to send refresh
     * events, one will be posted when this refresh has completed.
     *
     * @param path node full path
     * @return a latch that signals when the refresh is complete
     */
    CountDownLatch refresh(String path);

    /**
     * Remove the given path from the cache.
     *
     * @param path node full path
     * @return true if the node was in the cache
     */
    boolean clear(String path);

    /**
     * Remove all nodes from the cache
     */
    void clearAll();

    /**
     * Return true if there is a cached node at the given path.
     * Concurrent changes may not be immediately reflected.
     *
     * @param path node full path
     * @return true/false
     */
    boolean exists(String path);

    /**
     * Returns an immutable view of paths in the cache.
     *
     * @return set of paths
     */
    Collection<String> paths();

    /**
     * Returns an immutable map of child node names of the given node and the data at that node.
     * Concurrent changes may not be immediately reflected.
     *
     * @param path node full path
     * @return child nodes
     */
    Map<String, CachedNode> childrenAtPath(String path);

    /**
     * Return the node data stored for the main path (the path passed to the builder) in the cache or null.
     * Concurrent changes may not be immediately reflected.
     *
     * @return node data or null
     */
    CachedNode getMain();

    /**
     * Return the node data stored for the path in the cache or null.
     * Concurrent changes may not be immediately reflected.
     *
     * @param path node full path
     * @return node data or null
     */
    CachedNode get(String path);

    /**
     * Return an unmodifiable map of the node entries in the cache.
     * Concurrent changes may not be immediately reflected.
     *
     * @return map
     */
    Map<String, CachedNode> view();

    /**
     * Returns true if the cache is currently empty. Concurrent
     * changes may not be immediately reflected.
     *
     * @return true/false
     */
    boolean isEmpty();

    /**
     * Returns the number of nodes in the cache. Use the result only as a reference. Concurrent
     * changes may not be immediately reflected.
     *
     * @return true/false
     */
    int size();

    /**
     * As a memory optimization, you can clear the cached data bytes for a node. Subsequent
     * calls to {@link CachedNode#getData()} for this node will return <code>null</code>.
     *
     * @param path the path of the node to clear
     */
    void clearDataBytes(String path);

    /**
     * As a memory optimization, you can clear the cached data bytes for a node. Subsequent
     * calls to {@link CachedNode#getData()} for this node will return <code>null</code>.
     *
     * @param path  the path of the node to clear
     * @param ifVersion if non-negative, only clear the data if the data's version matches this version
     * @return true if the data was cleared
     */
    boolean clearDataBytes(String path, int ifVersion);

    /**
     * Returns the number of times this cache has been refreshed (manually via one of the refresh()
     * methods, from starting, from connection problems, etc.).
     *
     * @return number of refreshes
     */
    long refreshCount();
}
