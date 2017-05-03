package pubsub;

import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.curator.x.async.AsyncCuratorFramework;
import org.apache.curator.x.async.modeled.cached.CachedModeledFramework;
import org.apache.curator.x.async.modeled.cached.ModeledCacheListener;
import pubsub.messages.LocationAvailable;
import pubsub.messages.UserCreated;
import pubsub.models.Group;
import pubsub.models.Instance;
import pubsub.models.InstanceType;
import pubsub.models.Priority;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SubPubTest implements Closeable
{
    private final TestingServer testingServer;
    private final AsyncCuratorFramework client;
    private final Publisher publisher;
    private final ScheduledExecutorService executorService;
    private final List<CachedModeledFramework<Instance>> instanceSubscribers = new ArrayList<>();
    private final List<CachedModeledFramework<LocationAvailable>> locationAvailableSubscribers = new ArrayList<>();
    private final List<CachedModeledFramework<UserCreated>> userCreatedSubscribers = new ArrayList<>();

    private static final AtomicLong id = new AtomicLong(1);

    private static final Group[] groups = {new Group("main"), new Group("admin")};
    private static final String[] hostnames = {"host1", "host2", "host3"};
    private static final Integer[] ports = {80, 443, 9999};
    private static final String[] locations = {"dc1", "dc2", "eu", "us"};
    private static final Duration[] durations = {Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1)};
    private static final String[] positions = {"worker", "manager", "executive"};

    public static void main(String[] args) throws Exception
    {
        try ( SubPubTest subPubTest = new SubPubTest() )
        {
            subPubTest.start();
            TimeUnit.MINUTES.sleep(1);
        }
    }

    public SubPubTest() throws Exception
    {
        this.testingServer = new TestingServer();
        client = AsyncCuratorFramework.wrap(CuratorFrameworkFactory.newClient(testingServer.getConnectString(), new RetryOneTime(1)));
        publisher = new Publisher(client);
        executorService = Executors.newSingleThreadScheduledExecutor();
    }

    public void start()
    {
        client.unwrap().start();

        Subscriber subscriber = new Subscriber(client);
        instanceSubscribers.addAll(
            Arrays.stream(InstanceType.values())
            .map(subscriber::startInstanceSubscriber)
            .collect(Collectors.toList())
        );

        locationAvailableSubscribers.addAll(
            Arrays.stream(Priority.values())
                .flatMap(priority -> Arrays.stream(groups).map(group -> subscriber.startLocationAvailableSubscriber(group, priority)))
                .collect(Collectors.toList())
        );

        userCreatedSubscribers.addAll(
            Arrays.stream(Priority.values())
                .flatMap(priority -> Arrays.stream(groups).map(group -> subscriber.startUserCreatedSubscriber(group, priority)))
                .collect(Collectors.toList())
        );

        instanceSubscribers.forEach(s -> s.getCache().listenable().addListener(generalListener()));
        locationAvailableSubscribers.forEach(s -> s.getCache().listenable().addListener(generalListener()));
        userCreatedSubscribers.forEach(s -> s.getCache().listenable().addListener(generalListener()));

        executorService.scheduleAtFixedRate(this::publishSomething, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws IOException
    {
        executorService.shutdownNow();
        try
        {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
        catch ( InterruptedException ignore )
        {
            Thread.currentThread().interrupt();
        }

        userCreatedSubscribers.forEach(CachedModeledFramework::close);
        locationAvailableSubscribers.forEach(CachedModeledFramework::close);
        instanceSubscribers.forEach(CachedModeledFramework::close);
        client.unwrap().close();
        testingServer.close();
    }

    private void publishSomething()
    {
        switch ( ThreadLocalRandom.current().nextInt(5) )
        {
            case 0:
            {
                Instance instance = new Instance(nextId(), random(InstanceType.values()), random(hostnames), random(ports));
                System.out.println("Publishing instance");
                publisher.publishInstance(instance);
                break;
            }

            case 1:
            {
                List<Instance> instances =  IntStream.range(1, 10)
                    .mapToObj(__ -> new Instance(nextId(), random(InstanceType.values()), random(hostnames), random(ports)))
                    .collect(Collectors.toList());
                System.out.println("Publishing instances");
                publisher.publishInstances(instances);
                break;
            }

            case 2:
            {
                LocationAvailable locationAvailable = new LocationAvailable(nextId(), random(Priority.values()), random(locations), random(durations));
                System.out.println("Publishing locationAvailable");
                publisher.publishLocationAvailable(random(groups), locationAvailable);
                break;
            }

            case 3:
            {
                List<LocationAvailable> locationsAvailable =  IntStream.range(1, 10)
                    .mapToObj(__ -> new LocationAvailable(nextId(), random(Priority.values()), random(locations), random(durations)))
                    .collect(Collectors.toList());
                System.out.println("Publishing locationsAvailable");
                publisher.publishLocationsAvailable(random(groups), locationsAvailable);
                break;
            }

            case 4:
            {
                UserCreated userCreated = new UserCreated(nextId(), random(Priority.values()), random(locations), random(positions));
                System.out.println("Publishing userCreated");
                publisher.publishUserCreated(random(groups), userCreated);
                break;
            }

            case 5:
            {
                List<UserCreated> usersCreated =  IntStream.range(1, 10)
                    .mapToObj(__ -> new UserCreated(nextId(), random(Priority.values()), random(locations), random(positions)))
                    .collect(Collectors.toList());
                System.out.println("Publishing usersCreated");
                publisher.publishUsersCreated(random(groups), usersCreated);
                break;
            }
        }
    }

    private <T> ModeledCacheListener<T> generalListener()
    {
        return (type, path, stat, model) -> System.out.println(String.format("Subscribed %s @ %s", model.getClass().getSimpleName(), path));
    }

    @SafeVarargs
    private final <T> T random(T... tab)
    {
        int index = ThreadLocalRandom.current().nextInt(tab.length);
        return tab[index];
    }

    private String nextId()
    {
        return Long.toString(id.getAndIncrement());
    }
}
