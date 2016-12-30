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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * General interface for client-cached nodes. Create instances
 * using {@link CuratorCacheBuilder}
 */
public interface CuratorCache extends Closeable
{
    /**
     * Start the cache
     */
    void start();

    @Override
    void close();

    /**
     * Get listenable container used to add/remove listeners
     *
     * @return listener container
     */
    Listenable<CacheListener> getListenable();

    /**
     * force-fill the cache by getting all applicable nodes. The returned future
     * can be used to check/block for completion.
     *
     * @return a future that signals when the refresh is complete
     */
    Future<Boolean> refreshAll();

    /**
     * Refresh the given cached node
     *
     * @param path node full path
     * @return a future that signals when the refresh is complete
     */
    Future<Boolean> refresh(String path);

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
     * Return true if there is a cached node at the given path
     *
     * @param path node full path
     * @return true/false
     */
    boolean exists(String path);

    /**
     * Returns the set of paths in the cache. The returned set behaves in the same manner
     * as {@link ConcurrentHashMap#keySet()}
     *
     * @return set of paths
     */
    Set<String> paths();

    /**
     * Return the node data stored for the path in the cache or null
     *
     * @param path node full path
     * @return node data or null
     */
    CachedNode get(String path);

    /**
     * Returns the collection of node values in the cache. The returned set behaves in the same manner
     * as {@link ConcurrentHashMap#values()}
     *
     * @return node values
     */
    Collection<CachedNode> getAll();

    /**
     * Returns the collection of node entries in the cache. The returned set behaves in the same manner
     * as {@link ConcurrentHashMap#entrySet()}
     *
     * @return node entries
     */
    Set<Map.Entry<String, CachedNode>> entries();

    /**
     * Returns true if the cache is currently empty. Use the result only as a reference. Concurrent
     * changes may not be immediately reflected.
     *
     * @return true/false
     */
    boolean isEmpty();

    /**
     * Returns the number of nodes int the cache. Use the result only as a reference. Concurrent
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
}
