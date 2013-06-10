package org.apache.curator.framework.recipes.nodes;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.BaseClassForTests;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.KillSession;
import org.apache.curator.test.Timing;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.*;

public class TestPersistentEphemeralNode extends BaseClassForTests
{
    private static final String DIR = "/test";
    private static final String PATH = ZKPaths.makePath(DIR, "/foo");

    private final Collection<CuratorFramework> curatorInstances = Lists.newArrayList();
    private final Collection<PersistentEphemeralNode> createdNodes = Lists.newArrayList();

    @AfterMethod
    public void teardown() throws Exception
    {
        for ( PersistentEphemeralNode node : createdNodes )
        {
            node.close();
        }

        for ( CuratorFramework curator : curatorInstances )
        {
            curator.close();
        }

        super.teardown();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullCurator() throws Exception
    {
        new PersistentEphemeralNode(null, PersistentEphemeralNode.Mode.EPHEMERAL, PATH, new byte[0]);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullPath() throws Exception
    {
        CuratorFramework curator = newCurator();
        new PersistentEphemeralNode(curator, PersistentEphemeralNode.Mode.EPHEMERAL, null, new byte[0]);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullData() throws Exception
    {
        CuratorFramework curator = newCurator();
        new PersistentEphemeralNode(curator, PersistentEphemeralNode.Mode.EPHEMERAL, PATH, null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testNullMode() throws Exception
    {
        CuratorFramework curator = newCurator();
        new PersistentEphemeralNode(curator, null, PATH, new byte[0]);
    }

    @Test
    public void testDeletesNodeWhenClosed() throws Exception
    {
        CuratorFramework curator = newCurator();

        PersistentEphemeralNode node = new PersistentEphemeralNode(curator, PersistentEphemeralNode.Mode.EPHEMERAL, PATH, new byte[0]);
        node.start();
        String path = null;
        try
        {
            node.waitForInitialCreate(5, TimeUnit.SECONDS);
            path = node.getActualPath();
            assertNodeExists(curator, path);
        }
        finally
        {
            node.close();  // After closing the path is set to null...
        }

        assertNodeDoesNotExist(curator, path);
    }

    @Test
    public void testClosingMultipleTimes() throws Exception
    {
        CuratorFramework curator = newCurator();

        PersistentEphemeralNode node = new PersistentEphemeralNode(curator, PersistentEphemeralNode.Mode.EPHEMERAL, PATH, new byte[0]);
        node.start();
        node.waitForInitialCreate(5, TimeUnit.SECONDS);

        String path = node.getActualPath();
        node.close();
        assertNodeDoesNotExist(curator, path);

        node.close();
        assertNodeDoesNotExist(curator, path);
    }

    @Test
    public void testDeletesNodeWhenSessionDisconnects() throws Exception
    {
        CuratorFramework curator = newCurator();
        CuratorFramework observer = newCurator();

        PersistentEphemeralNode node = new PersistentEphemeralNode(curator, PersistentEphemeralNode.Mode.EPHEMERAL, PATH, new byte[0]);
        node.start();
        try
        {
            node.waitForInitialCreate(5, TimeUnit.SECONDS);
            assertNodeExists(observer, node.getActualPath());

            // Register a watch that will fire when the node is deleted...
            Trigger deletedTrigger = Trigger.deleted();
            observer.checkExists().usingWatcher(deletedTrigger).forPath(node.getActualPath());

            killSession(curator);

            // Make sure the node got deleted
            assertTrue(deletedTrigger.firedWithin(10, TimeUnit.SECONDS));
        }
        finally
        {
            node.close();
        }
    }

    @Test
    public void testRecreatesNodeWhenSessionReconnects() throws Exception
    {
        CuratorFramework curator = newCurator();
        CuratorFramework observer = newCurator();

        PersistentEphemeralNode node = new PersistentEphemeralNode(curator, PersistentEphemeralNode.Mode.EPHEMERAL, PATH, new byte[0]);
        node.start();
        try
        {
            node.waitForInitialCreate(5, TimeUnit.SECONDS);
            assertNodeExists(observer, node.getActualPath());

            Trigger deletedTrigger = Trigger.deleted();
            observer.checkExists().usingWatcher(deletedTrigger).forPath(node.getActualPath());

            killSession(curator);

            // Make sure the node got deleted...
            assertTrue(deletedTrigger.firedWithin(10, TimeUnit.SECONDS));

            // Check for it to be recreated...
            Trigger createdTrigger = Trigger.created();
            Stat stat = observer.checkExists().usingWatcher(createdTrigger).forPath(node.getActualPath());
            assertTrue(stat != null || createdTrigger.firedWithin(10, TimeUnit.SECONDS));
        }
        finally
        {
            node.close();
        }
    }

    @Test
    public void testRecreatesNodeWhenSessionReconnectsMultipleTimes() throws Exception
    {
        CuratorFramework curator = newCurator();
        CuratorFramework observer = newCurator();

        PersistentEphemeralNode node = new PersistentEphemeralNode(curator, PersistentEphemeralNode.Mode.EPHEMERAL, PATH, new byte[0]);
        node.start();
        try
        {
            node.waitForInitialCreate(5, TimeUnit.SECONDS);
            String path = node.getActualPath();
            assertNodeExists(observer, path);

            // We should be able to disconnect multiple times and each time the node should be recreated.
            for ( int i = 0; i < 5; i++ )
            {
                Trigger deletionTrigger = Trigger.deleted();
                observer.checkExists().usingWatcher(deletionTrigger).forPath(path);

                // Kill the session, thus cleaning up the node...
                killSession(curator);

                // Make sure the node ended up getting deleted...
                assertTrue(deletionTrigger.firedWithin(10, TimeUnit.SECONDS));

                // Now put a watch in the background looking to see if it gets created...
                Trigger creationTrigger = Trigger.created();
                Stat stat = observer.checkExists().usingWatcher(creationTrigger).forPath(path);
                assertTrue(stat != null || creationTrigger.firedWithin(10, TimeUnit.SECONDS));
            }
        }
        finally
        {
            node.close();
        }
    }

    @Test
    public void testRecreatesNodeWhenItGetsDeleted() throws Exception
    {
        CuratorFramework curator = newCurator();

        PersistentEphemeralNode node = new PersistentEphemeralNode(curator, PersistentEphemeralNode.Mode.EPHEMERAL, PATH, new byte[0]);
        node.start();
        try
        {
            node.waitForInitialCreate(5, TimeUnit.SECONDS);
            String originalNode = node.getActualPath();
            assertNodeExists(curator, originalNode);

            // Delete the original node...
            curator.delete().forPath(originalNode);

            // Since we're using an ephemeral node, and the original session hasn't been interrupted the name of the new
            // node that gets created is going to be exactly the same as the original.
            Trigger createdWatchTrigger = Trigger.created();
            Stat stat = curator.checkExists().usingWatcher(createdWatchTrigger).forPath(originalNode);
            assertTrue(stat != null || createdWatchTrigger.firedWithin(10, TimeUnit.SECONDS));
        }
        finally
        {
            node.close();
        }
    }

    @Test
    public void testNodesCreateUniquePaths() throws Exception
    {
        CuratorFramework curator = newCurator();

        PersistentEphemeralNode node1 = new PersistentEphemeralNode(curator, PersistentEphemeralNode.Mode.EPHEMERAL_SEQUENTIAL, PATH, new byte[0]);
        node1.start();
        try
        {
            node1.waitForInitialCreate(5, TimeUnit.SECONDS);
            String path1 = node1.getActualPath();

            PersistentEphemeralNode node2 = new PersistentEphemeralNode(curator, PersistentEphemeralNode.Mode.EPHEMERAL_SEQUENTIAL, PATH, new byte[0]);
            node2.start();
            try
            {
                node2.waitForInitialCreate(5, TimeUnit.SECONDS);
                String path2 = node2.getActualPath();

                assertFalse(path1.equals(path2));
            }
            finally
            {
                node2.close();
            }
        }
        finally
        {
            node1.close();
        }
    }

    @Test
    public void testData() throws Exception
    {
        CuratorFramework curator = newCurator();
        byte[] data = "Hello World".getBytes();

        PersistentEphemeralNode node = new PersistentEphemeralNode(curator, PersistentEphemeralNode.Mode.EPHEMERAL, PATH, data);
        node.start();
        try
        {
            node.waitForInitialCreate(5, TimeUnit.SECONDS);
            assertTrue(Arrays.equals(curator.getData().forPath(node.getActualPath()), data));
        }
        finally
        {
            node.close();
        }
    }

    private void assertNodeExists(CuratorFramework curator, String path) throws Exception
    {
        assertNotNull(path);
        assertTrue(curator.checkExists().forPath(path) != null);
    }

    private void assertNodeDoesNotExist(CuratorFramework curator, String path) throws Exception
    {
        assertTrue(curator.checkExists().forPath(path) == null);
    }

    private CuratorFramework newCurator() throws IOException
    {
        Timing timing = new Timing();
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1));
        client.start();

        curatorInstances.add(client);
        return client;
    }

    public void killSession(CuratorFramework curator) throws Exception
    {
        KillSession.kill(curator.getZookeeperClient().getZooKeeper(), curator.getZookeeperClient().getCurrentConnectionString());
    }

    private static final class Trigger implements Watcher
    {
        private final Event.EventType type;
        private final CountDownLatch latch;

        public Trigger(Event.EventType type)
        {
            assertNotNull(type);

            this.type = type;
            this.latch = new CountDownLatch(1);
        }

        @Override
        public void process(WatchedEvent event)
        {
            if ( type == event.getType() )
            {
                latch.countDown();
            }
        }

        public boolean firedWithin(long duration, TimeUnit unit)
        {
            try
            {
                return latch.await(duration, unit);
            } catch ( InterruptedException e )
            {
                throw Throwables.propagate(e);
            }
        }

        private static Trigger created()
        {
            return new Trigger(Event.EventType.NodeCreated);
        }

        private static Trigger deleted()
        {
            return new Trigger(Event.EventType.NodeDeleted);
        }
    }
}