#ChannelCounter

在分析MemoryTransaction的代码的时候发现MemoryTransaction的构造器入参中有一个 ChannelCounter类型的入参,这里就看一下这个ChannelCounter到底是个什么东西.

下图是ChannelCounter的继承关系图:![ChannelCounter](G:\snap\ChannelCounter.PNG)

## MonitoredCounterGroup 

先看一下代码中的注释部分,提到说MonitoredCounterGroup主要是用来对channel,source,sink等跟踪内部度量标准的(这里不知道怎么翻译metrics),在对代码的分析中可以发现,在memorychannel中,这个类主要用来对memorychannel中的MemoryTransaction中的putList 和 takeList( LinkedBlockingDeque<Event>)的size进行跟踪的;

```
/**
 * Used for keeping track of internal metrics using atomic integers</p>
 *
 * This is used by a variety of component types such as Sources, Channels,
 * Sinks, SinkProcessors, ChannelProcessors, Interceptors and Serializers.
 */
```

支持的计数类型有:

```java
  public static enum Type {
    SOURCE,
    CHANNEL_PROCESSOR,
    CHANNEL,
    SINK_PROCESSOR,
    SINK,
    INTERCEPTOR,
    SERIALIZER,
    OTHER
  }
```

```java
public abstract class MonitoredCounterGroup {

  private static final Logger logger =
      LoggerFactory.getLogger(MonitoredCounterGroup.class);

  // Key for component's start time in MonitoredCounterGroup.counterMap
  private static final String COUNTER_GROUP_START_TIME = "start.time";

  // key for component's stop time in MonitoredCounterGroup.counterMap
  private static final String COUNTER_GROUP_STOP_TIME = "stop.time";

  private final Type type;
  private final String name;
  private final Map<String, AtomicLong> counterMap;

  private AtomicLong startTime;
  private AtomicLong stopTime;
  protected MonitoredCounterGroup(Type type, String name, String... attrs) {
    this.type = type;
    this.name = name;

    Map<String, AtomicLong> counterInitMap = new HashMap<String, AtomicLong>();

    // Initialize the counters
    for (String attribute : attrs) {
      counterInitMap.put(attribute, new AtomicLong(0L));
    }
    counterMap = Collections.unmodifiableMap(counterInitMap);
    startTime = new AtomicLong(0L);
    stopTime = new AtomicLong(0L);
  }
```

* 在MonitoredCounterGroup通过一个counterMap来记录各种key对应的计数,这里计数用的类型是AtomicLong;
* MonitoredCounterGroup 提供了一些基本的技术类型,比如比较重要的starTime,stopTime用来记录component的启动时间和停止时间;
* 同时构造器的入参中也提供了自定义的计数值,通过这些计数值可以实现针对某种特例的计数值的入参;

```java
  /**
   * Starts the component
   *
   * Initializes the values for the stop time as well as all the keys in the
   * internal map to zero and sets the start time to the current time in
   * milliseconds since midnight January 1, 1970 UTC
   */
  //初始化各个计数器
  public void start() {

    register();
    stopTime.set(0L);
    for (String counter : counterMap.keySet()) {
      counterMap.get(counter).set(0L);
    }
    startTime.set(System.currentTimeMillis());
    logger.info("Component type: " + type + ", name: " + name + " started");
  }

  /**
   * Registers the counter.
   * This method is exposed only for testing, and there should be no need for
   * any implementations to call this method directly.
   */
   //测试的时候使用的;
  @VisibleForTesting
  void register()
  public void stop()
  public long getStartTime() {
    return startTime.get();
  }
  public long getStopTime() {
    return stopTime.get();
  }
  protected long get(String counter) {
    return counterMap.get(counter).get();
  }
  protected void set(String counter, long value) {
    counterMap.get(counter).set(value);
  }
  protected long addAndGet(String counter, long delta) {
    return counterMap.get(counter).addAndGet(delta);
  }
  protected long increment(String counter) {
    return counterMap.get(counter).incrementAndGet();
  }
  public String getType() 
```

###ChannelCounterMBean

ChannelCounter同时也实现了ChannelCounterMBean, 可以说ChannelCounterMBean规定了一些对(ChannelCounter)最基本的方法, 然后ChannelCounterMBean 结合MonitoredCounterGroup 同时实现了这些最基本的方法;

这里需要注意的是并不是MonitoredCounterGroup实现了ChannelCounterMBean, 而是channelcounter 在 集成 MonitoredCounterGroup的同时 implements了ChannelCounterMBean, 感觉这样在某种程度上减小了MonitoredCounterGroup和ChannelCounterMBean直接的耦合,二者在最终需要实现的地方(channelCounter)又结合到了一起,感觉这样的结合提供了更多的灵活性,同时也不显得过于的独立;

```java
public interface ChannelCounterMBean {
  long getChannelSize();
  long getEventPutAttemptCount();
  long getEventTakeAttemptCount();
  long getEventPutSuccessCount();
  long getEventTakeSuccessCount();
  long getStartTime();
  long getStopTime();
  long getChannelCapacity();
  String getType();
  double getChannelFillPercentage();
}
```

### ChannelCounter

在分析完MonitoredCounterGroup和ChannelCounterMBean之后再来看ChannelCounter的代码就显得简单多了

```
public class ChannelCounter extends MonitoredCounterGroup implements
    ChannelCounterMBean {

  private static final String COUNTER_CHANNEL_SIZE = "channel.current.size";

  private static final String COUNTER_EVENT_PUT_ATTEMPT =
      "channel.event.put.attempt";

  private static final String COUNTER_EVENT_TAKE_ATTEMPT =
      "channel.event.take.attempt";

  private static final String COUNTER_EVENT_PUT_SUCCESS =
      "channel.event.put.success";

  private static final String COUNTER_EVENT_TAKE_SUCCESS =
      "channel.event.take.success";

  private static final String COUNTER_CHANNEL_CAPACITY =
          "channel.capacity";

  private static final String[] ATTRIBUTES = {
    COUNTER_CHANNEL_SIZE, COUNTER_EVENT_PUT_ATTEMPT,
    COUNTER_EVENT_TAKE_ATTEMPT, COUNTER_EVENT_PUT_SUCCESS,
    COUNTER_EVENT_TAKE_SUCCESS, COUNTER_CHANNEL_CAPACITY
  };

  public ChannelCounter(String name) {
    super(MonitoredCounterGroup.Type.CHANNEL, name, ATTRIBUTES);
  }

  public ChannelCounter(String name, String[] attributes) {
    super(MonitoredCounterGroup.Type.CHANNEL, name,
        (String[])ArrayUtils.addAll(attributes,ATTRIBUTES));
  }

  @Override
  public long getChannelSize() {
    return get(COUNTER_CHANNEL_SIZE);
  }

  public void setChannelSize(long newSize) {
    set(COUNTER_CHANNEL_SIZE, newSize);
  }

  @Override
  public long getEventPutAttemptCount() {
    return get(COUNTER_EVENT_PUT_ATTEMPT);
  }

  public long incrementEventPutAttemptCount() {
    return increment(COUNTER_EVENT_PUT_ATTEMPT);
  }

  @Override
  public long getEventTakeAttemptCount() {
    return get(COUNTER_EVENT_TAKE_ATTEMPT);
  }

  public long incrementEventTakeAttemptCount() {
    return increment(COUNTER_EVENT_TAKE_ATTEMPT);
  }

  @Override
  public long getEventPutSuccessCount() {
    return get(COUNTER_EVENT_PUT_SUCCESS);
  }

  public long addToEventPutSuccessCount(long delta) {
    return addAndGet(COUNTER_EVENT_PUT_SUCCESS, delta);
  }

  @Override
  public long getEventTakeSuccessCount() {
    return get(COUNTER_EVENT_TAKE_SUCCESS);
  }

  public long addToEventTakeSuccessCount(long delta) {
    return addAndGet(COUNTER_EVENT_TAKE_SUCCESS, delta);
  }

  public void setChannelCapacity(long capacity) {
    set(COUNTER_CHANNEL_CAPACITY, capacity);
  }

  @Override
  public long getChannelCapacity() {
    return get(COUNTER_CHANNEL_CAPACITY);
  }

  @Override
  public double getChannelFillPercentage() {
    long capacity = getChannelCapacity();
    if (capacity != 0L) {
      return (getChannelSize() / (double)capacity) * 100;
    }
    return Double.MAX_VALUE;
  }

```

ChannelCounter的对MonitoredCounterGroup和ChannelCounterMBean的封装使得其代码的实现很简答,一些初始化操作之外就是一些setter和getter方法, 同时这些setter和getter方法实现了对channel metrics的计数;