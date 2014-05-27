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
package org.apache.curator.x.rpc;

import com.facebook.swift.codec.ThriftCodecManager;
import com.facebook.swift.service.ThriftEventHandler;
import com.facebook.swift.service.ThriftServer;
import com.facebook.swift.service.ThriftServiceProcessor;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationLoader;
import io.airlift.configuration.ConfigurationMetadata;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import org.apache.curator.x.rpc.idl.event.EventService;
import org.apache.curator.x.rpc.idl.projection.CuratorProjectionService;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class CuratorProjectionServer
{
    private final RpcManager rpcManager;
    private final ThriftServer server;
    private final AtomicReference<State> state = new AtomicReference<State>(State.LATENT);

    private enum State
    {
        LATENT,
        STARTED,
        STOPPED
    }

    public static void main(String[] args) throws IOException
    {
        if ( (args.length == 0) || args[0].equalsIgnoreCase("?") || args[0].equalsIgnoreCase("-h") || args[0].equalsIgnoreCase("--help") )
        {
            printHelp();
            return;
        }

        Map<String, String> options;
        File f = new File(args[0]);
        if ( f.exists() )
        {
            options = new ConfigurationLoader().loadPropertiesFrom(f.getPath());
        }
        else
        {
            System.out.println("First argument is not a file. Treating the command line as a list of field/values");
            options = buildOptions(args);
        }

        ConfigurationFactory configurationFactory = new ConfigurationFactory(options);
        Configuration configuration = configurationFactory.build(Configuration.class);

        final CuratorProjectionServer server = new CuratorProjectionServer(configuration);
        server.start();

        Runnable shutdown = new Runnable()
        {
            @Override
            public void run()
            {
                server.stop();
            }
        };
        Thread hook = new Thread(shutdown);
        Runtime.getRuntime().addShutdownHook(hook);
    }

    public CuratorProjectionServer(Configuration configuration)
    {
        rpcManager = new RpcManager(configuration.getProjectionExpiration().toMillis());
        EventService eventService = new EventService(rpcManager, configuration.getPingTime().toMillis());
        CuratorProjectionService projectionService = new CuratorProjectionService(rpcManager);
        ThriftServiceProcessor processor = new ThriftServiceProcessor(new ThriftCodecManager(), Lists.<ThriftEventHandler>newArrayList(), projectionService, eventService);
        server = new ThriftServer(processor, configuration);
    }

    public void start()
    {
        Preconditions.checkState(state.compareAndSet(State.LATENT, State.STARTED), "Already started");

        server.start();
    }

    public void stop()
    {
        if ( state.compareAndSet(State.STARTED, State.STOPPED) )
        {
            rpcManager.close();
            server.close();
        }
    }

    private static void printHelp()
    {
        System.out.println("Curator RPC - an RPC server for using Apache Curator APIs and recipes from non JVM languages.");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("\t<none>              show this help");
        System.out.println("\t<path>              path to a properties configuration file");
        System.out.println("\t<field value> ...   list of values that would be in the JSON configuration file");
        System.out.println();

        System.out.println("Values:");
        ConfigurationMetadata<Configuration> metadata = ConfigurationMetadata.getConfigurationMetadata(Configuration.class);
        for ( ConfigurationMetadata.AttributeMetadata attributeMetadata : metadata.getAttributes().values() )
        {
            ConfigurationMetadata.InjectionPointMetaData injectionPoint = attributeMetadata.getInjectionPoint();
            System.out.println("\t" + injectionPoint.getProperty() + ": " + attributeMetadata.getGetter().getReturnType().getSimpleName());
            if ( attributeMetadata.getDescription() != null )
            {
                System.out.println("\t\t" + attributeMetadata.getDescription());
            }
            System.out.println();
        }

        System.out.println("Special Types Examples:");
        System.out.println("\t" + Duration.class.getSimpleName());
        System.out.println("\t\t" + new Duration(10, TimeUnit.MINUTES));
        System.out.println("\t\t" + new Duration(5, TimeUnit.MILLISECONDS));
        System.out.println("\t\t" + new Duration(1.5, TimeUnit.HOURS));
        System.out.println("\t" + DataSize.class.getSimpleName());
        System.out.println("\t\t" + new DataSize(1.5, DataSize.Unit.GIGABYTE));
        System.out.println("\t\t" + new DataSize(10, DataSize.Unit.BYTE));
        System.out.println("\t\t" + new DataSize(.4, DataSize.Unit.MEGABYTE));
        System.out.println();
    }

    private static Map<String, String> buildOptions(String[] args) throws IOException
    {
        Map<String, String> options = Maps.newHashMap();
        for ( int i = 0; i < args.length; i += 2 )
        {
            if ( (i + 1) >= args.length )
            {
                throw new IOException("Bad command line. Must be list of fields and values of the form: \"field1 value1 ... fieldN valueN\"");
            }
            options.put(args[i], args[i + 1]);
        }
        return options;
    }
}
