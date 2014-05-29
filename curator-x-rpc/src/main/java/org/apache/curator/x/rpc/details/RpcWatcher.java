package org.apache.curator.x.rpc.details;

import org.apache.curator.x.rpc.idl.event.RpcCuratorEvent;
import org.apache.curator.x.rpc.idl.projection.CuratorProjection;
import org.apache.curator.x.rpc.idl.projection.CuratorProjectionService;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

public class RpcWatcher implements Watcher
{
    private final CuratorProjection projection;
    private final CuratorProjectionService projectionService;

    public RpcWatcher(CuratorProjectionService projectionService, CuratorProjection projection)
    {
        this.projection = projection;
        this.projectionService = projectionService;
    }

    @Override
    public void process(WatchedEvent event)
    {
        projectionService.addEvent(projection, new RpcCuratorEvent(event));
    }
}
