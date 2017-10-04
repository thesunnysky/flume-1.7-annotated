# SinkProcessor

Sink groups允许组织多个sink到一个实体上。 Sink processors能够提供在组内所有Sink之间实现负载均衡的能力，而且在失败的情况下能够进行故障转移从一个Sink到另一个Sink。

简单的说就是一个source 对应一个Sinkgroups，即多个sink,这里实际上与第六节的复用/复制情况差不多，只是这里考虑的是可靠性与性能，即故障转移与负载均衡的设置。

下面是官方配置：

| **Property Name**  | **Default** | **Description**                          |
| ------------------ | ----------- | ---------------------------------------- |
| **sinks**          | –           | Space-separated list of sinks that are participating in the group |
| **processor.type** | default     | The component type name, needs to be default, failover or load_balance |

从参数类型上可以看出有3种Processors类型：default, failover（故障转移）和 load_balance（负载均衡），当然，官网上说目前自定义processors 还不支持。

下面是官网例子

a1.sinkgroups**=**g1

a1.sinkgroups.g1.sinks**=**k1 k2

a1.sinkgroups.g1.processor.type**=**load_balance

 

## 一、Default Sink Processor

DefaultSink Processor 接收单一的Sink，不强制用户为Sink创建Processor，前面举了很多例子。所以这个就不多说了。

 

## 二、Failover Sink Processor（故障转移）

​         FailoverSink Processor会通过配置维护了一个优先级列表。保证每一个有效的事件都会被处理。

​         故障转移的工作原理是将连续失败sink分配到一个池中，在那里被分配一个冷冻期，在这个冷冻期里，这个sink不会做任何事。一旦sink成功发送一个event，sink将被还原到live 池中。

​         在这配置中，要设置sinkgroups processor为failover，需要为所有的sink分配优先级，所有的优先级数字必须是唯一的，这个得格外注意。此外，failover time的上限可以通过maxpenalty 属性来进行设置。

下面是官网配置：

| **Property Name**                 | **Default** | **Description**                          |
| --------------------------------- | ----------- | ---------------------------------------- |
| **sinks**                         | –           | Space-separated list of sinks that are participating in the group |
| **processor.type**                | default     | The component type name, needs to be failover |
| **processor.priority.<sinkName>** | –           | <sinkName> must be one of the sink instances associated with the current sink group |
| processor.maxpenalty              | 30000       | (in millis)                              |

下面是官网例子

a1.sinkgroups**=**g1

a1.sinkgroups.g1.sinks**=**k1 k2

a1.sinkgroups.g1.processor.type**=**failover

a1.sinkgroups.g1.processor.priority.k1**=**5

a1.sinkgroups.g1.processor.priority.k2**=**10

a1.sinkgroups.g1.processor.maxpenalty**=**10000

这里首先要申明一个sinkgroups,然后再设置2个sink ,k1与k2,其中2个优先级是5和10，而processor的maxpenalty被设置为10秒，默认是30秒。‘

下面是测试例子

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40583815#) [copy](http://blog.csdn.net/looklook5/article/details/40583815#)

1. \#配置文件：failover_sink_case13.conf  
2. \#Name the components on this agent  
3. a1.sources= r1  
4. a1.sinks= k1 k2  
5. a1.channels= c1 c2  
6. ​
7. a1.sinkgroups= g1  
8. a1.sinkgroups.g1.sinks= k1 k2  
9. a1.sinkgroups.g1.processor.type= failover  
10. a1.sinkgroups.g1.processor.priority.k1= 5  
11. a1.sinkgroups.g1.processor.priority.k2= 10  
12. a1.sinkgroups.g1.processor.maxpenalty= 10000  
13. ​
14. \#Describe/configure the source  
15. a1.sources.r1.type= syslogtcp  
16. a1.sources.r1.port= 50000  
17. a1.sources.r1.host= 192.168.233.128  
18. a1.sources.r1.channels= c1 c2  
19. ​
20. \#Describe the sink  
21. a1.sinks.k1.type= avro  
22. a1.sinks.k1.channel= c1  
23. a1.sinks.k1.hostname= 192.168.233.129  
24. a1.sinks.k1.port= 50000  
25. ​
26. a1.sinks.k2.type= avro  
27. a1.sinks.k2.channel= c2  
28. a1.sinks.k2.hostname= 192.168.233.130  
29. a1.sinks.k2.port= 50000  
30. \# Usea channel which buffers events in memory  
31. a1.channels.c1.type= memory  
32. a1.channels.c1.capacity= 1000  
33. a1.channels.c1.transactionCapacity= 100  

这里设置了2个channels与2个sinks ，关于故障转移的设置直接复制官网的例子。我们还要配置2个sinks对于的代理。这里的2个接受代理我们沿用之前第六章复制的2个sink代理配置。

下面是第一个接受复制事件代理配置

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40583815#) [copy](http://blog.csdn.net/looklook5/article/details/40583815#)

1. \#配置文件：replicate_sink1_case11.conf  
2. \# Name the components on this agent  
3. a2.sources = r1  
4. a2.sinks = k1  
5. a2.channels = c1  
6. ​
7. \# Describe/configure the source  
8. a2.sources.r1.type = avro  
9. a2.sources.r1.channels = c1  
10. a2.sources.r1.bind = 192.168.233.129  
11. a2.sources.r1.port = 50000  
12. ​
13. \# Describe the sink  
14. a2.sinks.k1.type = logger  
15. a2.sinks.k1.channel = c1  
16. ​
17. \# Use a channel which buffers events inmemory  
18. a2.channels.c1.type = memory  
19. a2.channels.c1.capacity = 1000  
20. a2.channels.c1.transactionCapacity = 100  

下面是第二个接受复制事件代理配置：

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40583815#) [copy](http://blog.csdn.net/looklook5/article/details/40583815#)

1. \#配置文件：replicate_sink2_case11.conf  
2. \# Name the components on this agent  
3. a3.sources = r1  
4. a3.sinks = k1  
5. a3.channels = c1  
6. ​
7. \# Describe/configure the source  
8. a3.sources.r1.type = avro  
9. a3.sources.r1.channels = c1  
10. a3.sources.r1.bind = 192.168.233.130  
11. a3.sources.r1.port = 50000  
12. ​
13. \# Describe the sink  
14. a3.sinks.k1.type = logger  
15. a3.sinks.k1.channel = c1  
16. ​
17. \# Use a channel which buffers events inmemory  
18. a3.channels.c1.type = memory  
19. a3.channels.c1.capacity = 1000  
20. a3.channels.c1.transactionCapacity = 100  

**#敲命令**

首先先启动2个接受复制事件代理，如果先启动源发送的代理，会报他找不到sinks的绑定，因为2个接事件的代理还未起来。

flume-ng agent -cconf -f conf/replicate_sink1_case11.conf -n a1 -Dflume.root.logger=INFO,console

flume-ng agent -cconf -f conf/replicate_sink2_case11.conf -n a1 -Dflume.root.logger=INFO,console

在启动源发送的代理

flume-ng agent -cconf -f conf/failover_sink_case13.conf -n a1 -Dflume.root.logger=INFO,console

启动成功后

打开另一个终端输入，往侦听端口送数据

echo "hello failoversink" | nc 192.168.233.128 50000

**#在启动源发送的代理终端查看console输出**

因为k1的优先级是5，K2是10因此当K2正常运行的时候，是发送到K2的。下面数据正常输出。

![img](http://img.blog.csdn.net/20141029113049250?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvbG9va2xvb2s1/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/Center)

然后我们中断K2的代理进程。

再尝试往侦听端口送数据

echo "hello close k2"| nc 192.168.233.128 50000

我们发现源代理发生事件到K2失败，然后他将K2放入到failover list（故障列表）

因为K1还是正常运行的，因此这个时候他会接收到数据。

然后我们再打开K2的大理进程，我们继续往侦听端口送数据

echo " hello open k2 again" | nc192.168.233.128 50000

数据正常发生，Failover SinkProcessor测试完毕。

## 三、Load balancing SinkProcessor

负载均衡片处理器提供在多个Sink之间负载平衡的能力。实现支持通过round_robin（轮询）或者random（随机）参数来实现负载分发，默认情况下使用round_robin，但可以通过配置覆盖这个默认值。还可以通过集成AbstractSinkSelector类来实现用户自己的选择机制。

当被调用的时候，这选择器通过配置的选择规则选择下一个sink来调用。

下面是官网配置

 

| **Property Name**             | **Default** | **Description**                          |
| ----------------------------- | ----------- | ---------------------------------------- |
| **processor.sinks**           | –           | Space-separated list of sinks that are participating in the group |
| **processor.type**            | default     | The component type name, needs to be load_balance |
| processor.backoff             | false       | Should failed sinks be backed off exponentially. |
| processor.selector            | round_robin | Selection mechanism. Must be either round_robin, random or FQCN of custom class that inherits from AbstractSinkSelector |
| processor.selector.maxTimeOut | 30000       | Used by backoff selectors to limit exponential backoff (in milliseconds) |

下面是官网的例子

a1.sinkgroups**=**g1

a1.sinkgroups.g1.sinks**=**k1 k2

a1.sinkgroups.g1.processor.type**=**load_balance

a1.sinkgroups.g1.processor.backoff**=**true

a1.sinkgroups.g1.processor.selector**=**random

这个与故障转移的设置差不多。

下面是测试例子

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40583815#) [copy](http://blog.csdn.net/looklook5/article/details/40583815#)

1. \#配置文件：load_sink_case14.conf  
2. \# Name the components on this agent  
3. a1.sources = r1  
4. a1.sinks = k1 k2  
5. a1.channels = c1  
6. ​
7. a1.sinkgroups = g1  
8. a1.sinkgroups.g1.sinks = k1 k2  
9. a1.sinkgroups.g1.processor.type =load_balance  
10. a1.sinkgroups.g1.processor.backoff = true  
11. a1.sinkgroups.g1.processor.selector =round_robin  
12. ​
13. \# Describe/configure the source  
14. a1.sources.r1.type = syslogtcp  
15. a1.sources.r1.port = 50000  
16. a1.sources.r1.host = 192.168.233.128  
17. a1.sources.r1.channels = c1  
18. ​
19. \# Describe the sink  
20. a1.sinks.k1.type = avro  
21. a1.sinks.k1.channel = c1  
22. a1.sinks.k1.hostname = 192.168.233.129  
23. a1.sinks.k1.port = 50000  
24. ​
25. a1.sinks.k2.type = avro  
26. a1.sinks.k2.channel = c1  
27. a1.sinks.k2.hostname = 192.168.233.130  
28. a1.sinks.k2.port = 50000  
29. \# Use a channel which buffers events inmemory  
30. a1.channels.c1.type = memory  
31. a1.channels.c1.capacity = 1000  
32. a1.channels.c1.transactionCapacity = 100  

这里要说明的是，因此测试的是负载均衡的例子，因此这边使用一个channel来作为数据传输通道。这里sinks的对应的接收数据的代理配置，我们沿用故障转移的接收代理配置。

**#敲命令**

首先先启动2个接受复制事件代理，如果先启动源发送的代理，会报他找不到sinks的绑定，因为2个接事件的代理还未起来。

flume-ng agent -cconf -f conf/replicate_sink1_case11.conf -n a1

-Dflume.root.logger=INFO,console

flume-ng agent -cconf -f conf/replicate_sink2_case11.conf -n a1

-Dflume.root.logger=INFO,console

在启动源发送的代理

flume-ng agent -cconf -f conf/load_sink_case14.conf -n a1

-Dflume.root.logger=INFO,console

 

启动成功后

打开另一个终端输入，往侦听端口送数据

echo "loadbanlancetest1" | nc 192.168.233.128 50000

echo "loadbantest2" | nc 192.168.233.128 50000

echo "loadban test3"| nc 192.168.233.128 50000

echo "loadbantest4" | nc 192.168.233.128 50000

echo "loadbantest5" | nc 192.168.233.128 50000

**#在启动源发送的代理终端查看console输出**

其中K1收到3条数据

其中K1收到2条数据

因为我们负载均衡选择的类型是轮询，因此可以看出flume 让代理每次向一个sink发送2次事件数据后就换另一个sinks 发送。

Sink Processors测试完毕

原文地址：http://blog.csdn.net/looklook5/article/details/40583815



---

#SinkProcessor代码

### SinkProcessor

- flume提供了一个对于一个agent配置多个sink的功能,SinkProcessor接口就提供了对这种多sink的功能;
- SinkProcessor一般来说是在SinkRunner中实例化并调用的;
- 根据自己的了解,如果自己实现的Sink不准备支持多Sink的情况下,可以不用考虑SinkProcessor接口,直接实现AbstractSink类就可以了;

```
public interface SinkProcessor extends LifecycleAware, Configurable {
  /**
   * <p>Handle a request to poll the owned sinks.</p>
   *
   * <p>The processor is expected to call {@linkplain Sink#process()} on
   *  whatever sink(s) appropriate, handling failures as appropriate and
   *  throwing {@link EventDeliveryException} when there is a failure to
   *  deliver any events according to the delivery policy defined by the
   *  sink processor implementation. See specific implementations of this
   *  interface for delivery behavior and policies.</p>
   *
   * @return Returns {@code READY} if events were successfully consumed,
   * or {@code BACKOFF} if no events were available in the channel to consume.
   * @throws EventDeliveryException if the behavior guaranteed by the processor
   * couldn't be carried out.
   */
  Status process() throws EventDeliveryException;

  /**
   * <p>Set all sinks to work with.</p>
   *
   * <p>Sink specific parameters are passed to the processor via configure</p>
   *
   * @param sinks A non-null, non-empty list of sinks to be chosen from by the
   * processor
   */
  void setSinks(List<Sink> sinks);
```

- SinkProcessor中的process()方法,将直接调用各个sink的process()方法;

### AbstractSinkProcessor

AbstractSinkProcessor 实现了SinkProcessor接口,实现了一些基本的操作:

- 实例化了sinkList;
- 实现了些基本的方法,给子类的实现提供了一定的方便;

```
public abstract class AbstractSinkProcessor implements SinkProcessor {

  private LifecycleState state;

  // List of sinks as specified
  private List<Sink> sinkList;

  @Override
  public void start() {
    for (Sink s : sinkList) {
      s.start();
    }

    state = LifecycleState.START;
  }

  @Override
  public void stop() {
    for (Sink s : sinkList) {
      s.stop();
    }
    state = LifecycleState.STOP;
  }

  @Override
  public LifecycleState getLifecycleState() {
    return state;
  }

  @Override
  public void setSinks(List<Sink> sinks) {
    List<Sink> list = new ArrayList<Sink>();
    list.addAll(sinks);
    sinkList = Collections.unmodifiableList(list);
  }

  protected List<Sink> getSinks() {
    return sinkList;
  }
}
```

参考文献: http://holynull.leanote.com/post/4-2

下面来看一下flume中定义的三种SinkProcessor

### DefaultSinkProcessor

DefaultSinkProcessor 是flume的磨人的SinkProcessor,如果配置文件中没有配置processors, 那起作用的就是DefaultSinkProcessor;

 默认的sink processor仅接受单独一个sink。不必对单个sink使用processor。对单个sink可以使用source-channel-sink的方式。

代码的注释中也提到了, DefaultSinkProcessor只支持单Sink的情况, 并且没有做任何额外其他的处理;

```java
/**
 * Default sink processor that only accepts a single sink, passing on process
 * results without any additional handling. Suitable for all sinks that aren't
 * assigned to a group.
 */
public class DefaultSinkProcessor implements SinkProcessor, ConfigurableComponent {
  private Sink sink;
  private LifecycleState lifecycleState;

  @Override
  public void start() {
    Preconditions.checkNotNull(sink, "DefaultSinkProcessor sink not set");
    sink.start();
    lifecycleState = LifecycleState.START;
  }

  @Override
  public void stop() {
    Preconditions.checkNotNull(sink, "DefaultSinkProcessor sink not set");
    sink.stop();
    lifecycleState = LifecycleState.STOP;
  }

  @Override
  public LifecycleState getLifecycleState() {
    return lifecycleState;
  }

  @Override
  public void configure(Context context) {
  }

  //单纯的调用了sink的process()方法,没有实现任何额外其他的功能
  @Override
  public Status process() throws EventDeliveryException {
    return sink.process();
  }

  @Override
  public void setSinks(List<Sink> sinks) {
    Preconditions.checkNotNull(sinks);
    Preconditions.checkArgument(sinks.size() == 1, "DefaultSinkPolicy can "
        + "only handle one sink, "
        + "try using a policy that supports multiple sinks");
    sink = sinks.get(0);
  }

  @Override
  public void configure(ComponentConfiguration conf) {

  }
}
```

### FailOverSinkProcessor

摘自前面的话:

> FailoverSink Processor会通过配置维护了一个优先级列表，保证只要所配置的各个Sink只要有一个是可用的，events就可以被正确的处理；
>
> ​    容错机制将失败的sink放入一个冷却池中，并给他设置一个冷却时间，如果重试中不断失败，冷却时间将不断增加。一旦sink成功的发送event，sink将被重新保存到一个可用sink池中。在这个可用sink池中，每一个sink都有一个关联优先级值，值越大优先级越高。当一个sink发送event失败时，剩下的sink中优先级最高的sink将试着发送event。例如：在选择发送event的sink时，优先级100的sink将优先于优先级80的sink。如果没有设置sink的优先级，那么优先级将按照设置的顺序从左至右，由高到低来决定。
>
> ​    设置sink组的processor为failover，并且为每个独立的sink配置优先级，优先级不能重复。通过设置参数maxpenalty，来设置冷却池中的sink的最大冷却时间。

```
public class FailoverSinkProcessor extends AbstractSinkProcessor {
  private static final int FAILURE_PENALTY = 1000;
  private static final int DEFAULT_MAX_PENALTY = 30000;

  private class FailedSink implements Comparable<FailedSink> {
    //failsink重试的时间点，超过这个时间点的认为已经过了cooldown时间，可以重新尝试用这个sink去process数据
    private Long refresh;
    //sink的优先级
    private Integer priority;
    //对sink的引用
    private Sink sink;
    //标识连续失败的次数
    private Integer sequentialFailures;
  }

  private Map<String, Sink> sinks;
  //记录当前存活sinks中优先级最高的sink
  private Sink activeSink;
  //存活的sinks
  private SortedMap<Integer, Sink> liveSinks;
  //失效的sinks
  private Queue<FailedSink> failedSinks;
  private int maxPenalty;

  @Override
  public void configure(Context context) {
    liveSinks = new TreeMap<Integer, Sink>();
    failedSinks = new PriorityQueue<FailedSink>();
    Integer nextPrio = 0;
    String maxPenaltyStr = context.getString(MAX_PENALTY_PREFIX);
    if (maxPenaltyStr == null) {
      maxPenalty = DEFAULT_MAX_PENALTY;
    } else {
      try {
        maxPenalty = Integer.parseInt(maxPenaltyStr);
      } catch (NumberFormatException e) {
        logger.warn("{} is not a valid value for {}",
                    new Object[] { maxPenaltyStr, MAX_PENALTY_PREFIX });
        maxPenalty = DEFAULT_MAX_PENALTY;
      }
    }
    //从配置文件中读取sink的priority
    for (Entry<String, Sink> entry : sinks.entrySet()) {
      String priStr = PRIORITY_PREFIX + entry.getKey();
      Integer priority;
      try {
        priority =  Integer.parseInt(context.getString(priStr));
      } catch (Exception e) {
        /* 1.从配置文件中解析sink的priority失败
         * 2.或者文件中没有配置sink的priority
         * 这两种情况将按照sink在配置文件中的排序来排列优先级，排在前面的优先级越高
         */
        priority = --nextPrio;
      }
      if (!liveSinks.containsKey(priority)) {
        liveSinks.put(priority, sinks.get(entry.getKey()));
      } else {
        logger.warn("Sink {} not added to FailverSinkProcessor as priority" +
            "duplicates that of sink {}", entry.getKey(),
            liveSinks.get(priority));
      }
    }
    //获取liveSink中优先级最高的sink
    activeSink = liveSinks.get(liveSinks.lastKey());
  }
  
  @Override
  public Status process() throws EventDeliveryException {
    // Retry any failed sinks that have gone through their "cooldown" period
    Long now = System.currentTimeMillis();
    //peek()只是从队列中获取，但是不remove
    while (!failedSinks.isEmpty() && failedSinks.peek().getRefresh() < now) {
      //retrieves and remove the head of the queue
      FailedSink cur = failedSinks.poll();
      Status s;
      try {
        //尝试用failoversink来process 数据
        s = cur.getSink().process();
        if (s  == Status.READY) {
          liveSinks.put(cur.getPriority(), cur.getSink());
          //得到当前存活的优先级最高的sink
          activeSink = liveSinks.get(liveSinks.lastKey());
          logger.debug("Sink {} was recovered from the fail list",
                  cur.getSink().getName());
        } else {
          // if it's a backoff it needn't be penalized.
          failedSinks.add(cur);
        }
        return s;
      } catch (Exception e) {
        cur.incFails();
        failedSinks.add(cur);
      }
    }

    //调用用当前优先级最高的存活sink
    Status ret = null;
    while (activeSink != null) {
      try {
        ret = activeSink.process();
        return ret;
      } catch (Exception e) {
        logger.warn("Sink {} failed and has been sent to failover list",
                activeSink.getName(), e);
        activeSink = moveActiveToDeadAndGetNext();
      }
    }

    throw new EventDeliveryException("All sinks failed to process, " +
        "nothing left to failover to");
  }

  //将fail的sink放入到failedSinks中
  private Sink moveActiveToDeadAndGetNext() {
    Integer key = liveSinks.lastKey();
    failedSinks.add(new FailedSink(key, activeSink, 1));
    liveSinks.remove(key);
    if (liveSinks.isEmpty()) return null;
    if (liveSinks.lastKey() != null) {
      return liveSinks.get(liveSinks.lastKey());
    } else {
      return null;
    }
  }
}
```

###LoadBalancingSinkProcessor

   Load balancing Sink processor（负载均衡处理器）在多个sink间实现负载均衡。数据分发到多个活动的sink，处理器用一个索引化的列表来存储这些sink的信息。处理器实现两种数据分发机制，轮循选择机制和随机选择机制。默认的分发机制是轮循选择机制，可以通过配置修改。同时我们可以通过继承AbstractSinkSelector来实现自定义数据分发选择机制。

​    选择器按照我们配置的选择机制执行选择sink。当sink失败时，处理器将根据我们配置的选择机制，选择下一个可用的sink。这种方式中没有黑名单，而是主动常识每一个可用的sink。如果所有的sink都失败了，选择器将把这些失败传递给sink的执行者。

​    如果设置backoff为true，处理器将会把失败的sink放进黑名单中，并且为失败的sink设置一个在黑名单驻留的时间，在这段时间内，sink将不会被选择接收数据。当超过黑名单驻留时间，如果该sink仍然没有应答或者应答迟缓，黑名单驻留时间将以指数的方式增加，以避免长时间等待sink应答而阻塞。如果设置backoff为false，在轮循的方式下，失败的数据将被顺序的传递给下一个sink，因此数据分发就变成非均衡的了。

   如果flume轮训完了所有的sink都失败了，那么flume会抛出一个EventDeliveryException异常

1. flume 实现了轮训selector和随机selector来选择下一次使用哪个sink；

```
private static class RoundRobinSinkSelector extends AbstractSinkSelector；
private static class RandomOrderSinkSelector extends AbstractSinkSelector；
```

2. LoadBalancingSinkProcessor process()

```java
  //当前使用的selector的引用
  private SinkSelector selector;

@Override
  public Status process() throws EventDeliveryException {
    Status status = null;

    Iterator<Sink> sinkIterator = selector.createSinkIterator();
    while (sinkIterator.hasNext()) {
      Sink sink = sinkIterator.next();
      try {
        status = sink.process();
        break;
      } catch (Exception ex) {
        selector.informSinkFailed(sink);
        LOGGER.warn("Sink failed to consume event. "
            + "Attempting next sink if available.", ex);
      }
    }

    if (status == null) {
      throw new EventDeliveryException("All configured sinks have failed");
    }

    return status;
  }
```

