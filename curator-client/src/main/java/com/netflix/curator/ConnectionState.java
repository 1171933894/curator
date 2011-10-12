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
package com.netflix.curator;

import com.netflix.curator.drivers.LoggingDriver;
import com.netflix.curator.drivers.TracerDriver;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import java.io.Closeable;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class ConnectionState implements Watcher, Closeable
{
    private volatile long connectionStartMs = 0;

    private final HandleHolder zooKeeper;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final int connectionTimeoutMs;
    private final AtomicReference<LoggingDriver> log;
    private final AtomicReference<TracerDriver> tracer;
    private final AtomicReference<Watcher> parentWatcher = new AtomicReference<Watcher>(null);
    private final Queue<Exception> backgroundExceptions = new ConcurrentLinkedQueue<Exception>();

    private static final int        MAX_BACKGROUND_EXCEPTIONS = 10;

    ConnectionState(String connectString, int sessionTimeoutMs, int connectionTimeoutMs, Watcher parentWatcher, AtomicReference<LoggingDriver> log, AtomicReference<TracerDriver> tracer) throws IOException
    {
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.log = log;
        this.tracer = tracer;
        this.parentWatcher.set(parentWatcher);
        zooKeeper = new HandleHolder(this, connectString, sessionTimeoutMs);
    }

    ZooKeeper getZooKeeper() throws Exception
    {
        Exception exception = backgroundExceptions.poll();
        if ( exception != null )
        {
            log.get().error("Background exception caught", exception);
            tracer.get().addCount("background-exceptions", 1);
            throw exception;
        }

        boolean localIsConnected = isConnected.get();
        if ( !localIsConnected )
        {
            long        elapsed = System.currentTimeMillis() - connectionStartMs;
            if ( elapsed >= connectionTimeoutMs )
            {
                KeeperException.ConnectionLossException connectionLossException = new KeeperException.ConnectionLossException();
                log.get().error("Connection timed out", connectionLossException);
                tracer.get().addCount("connections-timed-out", 1);
                throw connectionLossException;
            }
        }

        return zooKeeper.getZooKeeper();
    }

    boolean isConnected()
    {
        return isConnected.get();
    }

    Watcher substituteParentWatcher(Watcher newWatcher)
    {
        return parentWatcher.getAndSet(newWatcher);
    }

    void        start() throws Exception
    {
        log.get().debug("Starting");
        reset();
    }

    @Override
    public void        close() throws IOException
    {
        log.get().debug("Closing");

        try
        {
            zooKeeper.closeAndClear();
        }
        catch ( Exception e )
        {
            throw new IOException(e);
        }
        finally
        {
            isConnected.set(false);
        }
    }

    private void reset() throws Exception
    {
        isConnected.set(false);
        connectionStartMs = System.currentTimeMillis();
        zooKeeper.closeAndReset();
        zooKeeper.getZooKeeper();   // initiate connection
    }

    @Override
    public void process(WatchedEvent event)
    {
        boolean wasConnected = isConnected.get();
        boolean newIsConnected = wasConnected;
        if ( event.getType() == Watcher.Event.EventType.None )
        {
            newIsConnected = (event.getState() == Event.KeeperState.SyncConnected);
            if ( event.getState() == Event.KeeperState.Expired )
            {
                handleExpiredSession();
            }
        }

        if ( newIsConnected != wasConnected )
        {
            isConnected.set(newIsConnected);
            connectionStartMs = System.currentTimeMillis();
        }

        Watcher localParentWatcher = parentWatcher.get();
        if ( localParentWatcher != null )
        {
            TimeTrace timeTrace = new TimeTrace("connection-state-parent-process", tracer.get());
            localParentWatcher.process(event);
            timeTrace.commit();
        }
    }

    private void handleExpiredSession()
    {
        log.get().warn("Session expired event received");
        tracer.get().addCount("session-expired", 1);

        try
        {
            reset();
        }
        catch ( Exception e )
        {
            queueBackgroundException(e);
        }
    }

    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
    private void queueBackgroundException(Exception e)
    {
        while ( backgroundExceptions.size() >= MAX_BACKGROUND_EXCEPTIONS )
        {
            backgroundExceptions.poll();
        }
        backgroundExceptions.offer(e);
    }
}
