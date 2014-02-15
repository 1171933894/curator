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
package org.apache.curator.x.rest.api;

import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.x.rest.CuratorRestContext;
import org.apache.curator.x.rest.details.Closer;
import org.apache.curator.x.rest.details.Session;
import org.apache.curator.x.rest.entities.NodeCacheSpec;
import org.apache.curator.x.rest.entities.StatusMessage;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

@Path("/curator/v1/recipes/node-cache/{session-id}")
public class NodeCacheResource
{
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final CuratorRestContext context;

    public NodeCacheResource(@Context CuratorRestContext context)
    {
        this.context = context;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response newCache(@PathParam("session-id") String sessionId, final NodeCacheSpec spec) throws Exception
    {
        Session session = Constants.getSession(context, sessionId);

        NodeCache cache = new NodeCache(context.getClient(), spec.getPath(), spec.isDataIsCompressed());
        cache.start(spec.isBuildInitial());

        Closer<NodeCache> closer = new Closer<NodeCache>()
        {
            @Override
            public void close(NodeCache cache)
            {
                try
                {
                    cache.close();
                }
                catch ( IOException e )
                {
                    log.error("Could not close left-over NodeCache for path: " + spec.getPath(), e);
                }
            }
        };
        final String id = session.addThing(cache, closer);

        NodeCacheListener listener = new NodeCacheListener()
        {
            @Override
            public void nodeChanged() throws Exception
            {
                context.pushMessage(new StatusMessage(Constants.NODE_CACHE, id, "", ""));
            }
        };
        cache.getListenable().addListener(listener);

        ObjectNode node = Constants.makeIdNode(context, id);
        return Response.ok(context.getWriter().writeValueAsString(node)).build();
    }

    @DELETE
    @Path("/{cache-id}")
    public Response deleteCache(@PathParam("session-id") String sessionId, @PathParam("cache-id") String cacheId)
    {
        Session session = Constants.getSession(context, sessionId);
        NodeCache cache = Constants.deleteThing(session, cacheId, NodeCache.class);
        try
        {
            cache.close();
        }
        catch ( IOException e )
        {
            log.error("Could not close NodeCache id: " + cacheId, e);
        }
        return Response.ok().build();
    }

    @GET
    @Path("/{cache-id}")
    public Response getCacheData(@PathParam("session-id") String sessionId, @PathParam("cache-id") String cacheId) throws Exception
    {
        Session session = Constants.getSession(context, sessionId);
        NodeCache cache = Constants.getThing(session, cacheId, NodeCache.class);
        return Response.ok(Constants.toNodeData(cache.getCurrentData())).build();
    }
}
