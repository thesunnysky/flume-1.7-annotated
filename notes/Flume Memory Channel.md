# Flume Memory Channel

Memory Channle作为flume的最基的channel. 在flume memory channel的代码最开的注释中提到: memory channel是在考虑速度的情况下,同时数据的写盘和持久化不是硬性要求的前提下应该使用的channel,这是因为memory channel 本身的不可靠性决定的,只要一个flume的agent挂掉了 ,memory channel中尚未被sink take的数据也就随之丢失了, 在考虑使用memory channel的时候,需要注意.

下图是Memory的继承关系图,其中主要的组成部分是:

* NamedComponent: 

  基础组件,将Component和某个Name绑定起来;

* LifecycleAware

   基础组件, 用于component的生命周期管理;

* Configurable

   基础组件,所以实现configurable接口的component都是可配置的,都会有一个context阈值相对应;


* Channel: 

  接口,定义了,channel的基本操作:put(), take(), getTransanction();

* AbstractChannel

  接口的abstract类,定了了channel的一下基本的方法;

* BasicChannelSemantics


> 这里有必要提一下XXXChannel 和 ChannelProcessor之间的关系,XXXchannel,这里以MemoryChannel为例,只是定义了这种类型的channel的特性,并暴露出了对这个channel操作的方法,对这些方法的调用是在ChannelProessor中调用的,这也就给出了XXXChannel 和ChannelProessor之间的关系;

![捕获](G:\snap\捕获.PNG)

##Code

```
public class MemoryChannel extends BasicChannelSemantics {
  #	The maximum number of events stored in the channel
  private static final Integer defaultCapacity = 100;
  #	The maximum number of events the channel will take from a source or give to a sink per transaction
  #通过代码分析,这个值好像是用来量化event byte size时的单位,后续有代码分析;
  private static final Integer defaultTransCapacity = 100;
  
  private static final double byteCapacitySlotSize = 100;
  #channel中允许的所有event的自己容量,这里计算的只是event的body部分的大小;, 默认值是jvm在启动的时候通过命令行设定的内存大小的80%,剩下的20%会被当作缓存;
  private static final Long defaultByteCapacity = (long)(Runtime.getRuntime().maxMemory() * .80);
  #ByteCapacityBuffer 所占整个应用内存的百分比,这个内存大小参数是在启动flume的时候在命令行中通过jvm的设定内存大小的参数设定的;
  private static final Integer defaultByteCapacityBufferPercentage = 20;
  #往channel中put 和 take event的默认timeout时间,默认3秒
  private static final Integer defaultKeepAlive = 3;
}
```

## ChannelCounter

在MemoryTransaction的构造器入参中有一个 ChannelCounter类型的入参,在介绍MemoryTransaction之前先来看下这个东西;

## MemoryTransaction

MemoryTransaction 是 MemoryChannel的内部类,这个类继承了BasicTransactionSemantics, 之前的分析中指出,flume channel中对事务的操作都交给了BasicTransactionSemantics来代理完成,这里MemoryTransaction集成BasicTransactionSemantics,同时也实现了doPut,doTake,doRollback等事务相关的几个典型的操作;

可以说MemoryChannel中的大部分的代码都是MemoryTransaction的代码

```
private class MemoryTransaction extends BasicTransactionSemantics {
    private LinkedBlockingDeque<Event> takeList;
    private LinkedBlockingDeque<Event> putList;
    private final ChannelCounter channelCounter;
    private int putByteCounter = 0;
    private int takeByteCounter = 0;

    public MemoryTransaction(int transCapacity, ChannelCounter counter) {
      putList = new LinkedBlockingDeque<Event>(transCapacity);
      takeList = new LinkedBlockingDeque<Event>(transCapacity);

      channelCounter = counter;
    }
```

### putList 和 takeList

putLis和takeList记录了向channel中put的event和take event,事务的处理需要对channel中的event做记录,这里代码中用的是阻塞队列LinkedBlockingDeque来实现的;

###Transaction

由于继承了BasicTransactionSemantics , MemoryTransaction也实现了其中的doPut,doTake,doCommit和doRollback操作,这几个事务操作的方法也消耗掉了MemoryChannel中大部分的代码;

直接上代码吧,注释都在代码中:

```
private class MemoryTransaction extends BasicTransactionSemantics {
    //用来缓存从channel的queue中take掉的Event
    private LinkedBlockingDeque<Event> takeList;
    //用来缓存put 到 channel的queue中的Event
    private LinkedBlockingDeque<Event> putList;
    private final ChannelCounter channelCounter;
    //用来记录从put channel queue 的event的slot数,这里使用了slot(槽)的概念,每个槽100byte,每个event至少占用1个slot
    private int putByteCounter = 0;
    //用来记录从channel的queue中take掉的Event的slot数 以100为单位
    private int takeByteCounter = 0;

    public MemoryTransaction(int transCapacity, ChannelCounter counter) {
      putList = new LinkedBlockingDeque<Event>(transCapacity);
      takeList = new LinkedBlockingDeque<Event>(transCapacity);

      channelCounter = counter;
    }
    
    /**
     * MemoryTransaction中并没有重写doBegin()方法,调用的还是父类的方法
     */

    @Override
    protected void doPut(Event event) throws InterruptedException {
      channelCounter.incrementEventPutAttemptCount();
      int eventByteSize = (int) Math.ceil(estimateEventSize(event) / byteCapacitySlotSize);

      /* 这里使用的是offer方法,该方法不会阻塞,如果发现此时队列不可用(既不能立即成功将数据插入到队列中)则返回;
       * 这里可以关注一下该队列的add(),put()和offer()的区别
       */
      if (!putList.offer(event)) {
        // 队列已满,抛出异常
        throw new ChannelException(
            "Put queue for MemoryTransaction of capacity " +
            putList.size() + " full, consider committing more frequently, " +
            "increasing capacity or increasing thread count");
      }
      //更新putByteCounter
      putByteCounter += eventByteSize;
    }

    @Override
    protected Event doTake() throws InterruptedException {
      channelCounter.incrementEventTakeAttemptCount();
      if (takeList.remainingCapacity() == 0) {
        throw new ChannelException("Take list for MemoryTransaction, capacity " +
            takeList.size() + " full, consider committing more frequently, " +
            "increasing capacity, or increasing thread count");
      }
      //先获取信号量
      if (!queueStored.tryAcquire(keepAlive, TimeUnit.SECONDS)) {
        return null;
      }
      Event event;
      //获取队列的锁, 然后从队列中取出Event
      synchronized (queueLock) {
        event = queue.poll();
      }
      Preconditions.checkNotNull(event, "Queue.poll returned NULL despite semaphore " +
          "signalling existence of entry");
      //将从channel杜列中取出的event再放入到takeList中
      takeList.put(event);

      //更新eventByteSize 和 takeByteCounter
      int eventByteSize = (int) Math.ceil(estimateEventSize(event) / byteCapacitySlotSize);
      takeByteCounter += eventByteSize;

      return event;
    }

    /**
     * 向channel的queue中commit数据(的变化)
     * @throws InterruptedException
     */
    @Override
    protected void doCommit() throws InterruptedException {
      //计算此次commit时queue中event byte的变化量
      int remainingChange = takeList.size() - putList.size();
      if (remainingChange < 0) {
        /* 表明此时put的size是大于take的size的,需要往队里中增加响应byte的容量;
         * 在增加的时候首先需要先获得对应byte容量的信号量,表明此时还有足够的byteCapacity用来增加数据
         */
        if (!bytesRemaining.tryAcquire(putByteCounter, keepAlive, TimeUnit.SECONDS)) {
          throw new ChannelException("Cannot commit transaction. Byte capacity " +
              "allocated to store event body " + byteCapacity * byteCapacitySlotSize +
              "reached. Please increase heap space/byte capacity allocated to " +
              "the channel as the sinks may not be keeping up with the sources");
        }
        /* -remainingChange是最终queue中增加的size,最终往queue中增加的size需要先获取对应的信号量
         * 表示queue中有对应的容量允许你增加
         */
        if (!queueRemaining.tryAcquire(-remainingChange, keepAlive, TimeUnit.SECONDS)) {
          bytesRemaining.release(putByteCounter);
          throw new ChannelFullException("Space for commit to queue couldn't be acquired." +
              " Sinks are likely not keeping up with sources, or the buffer size is too tight");
        }
      }
      int puts = putList.size();
      int takes = takeList.size();
      //将putList中的数据放入到Channel的queue中
      synchronized (queueLock) {
        if (puts > 0) {
          while (!putList.isEmpty()) {
            if (!queue.offer(putList.removeFirst())) {
              throw new RuntimeException("Queue add failed, this shouldn't be able to happen");
            }
          }
        }
        //清空putList和takeList
        putList.clear();
        takeList.clear();
      }
      //将take掉的byte对应的信号量释放
      bytesRemaining.release(takeByteCounter);
      //清空计数器
      takeByteCounter = 0;
      putByteCounter = 0;

      //queue中新增加event,在queueStored中增加对应的信号量,表明此时有更多的event可以从queue中取出来
      queueStored.release(puts);
      /* 此时表示take size是大于 put的size的,queue中增加了元素(有更多的元素Remaining),需要释放(增加)相应的信号量
       * 同时表示上面的if判断不成功
       */
      if (remainingChange > 0) {
        queueRemaining.release(remainingChange);
      }
      //更新channelCounter中的计数
      if (puts > 0) {
        channelCounter.addToEventPutSuccessCount(puts);
      }
      if (takes > 0) {
        channelCounter.addToEventTakeSuccessCount(takes);
      }

      //更新channel size
      channelCounter.setChannelSize(queue.size());
    }

    /**
     * 事务回滚操作,也就是将之前从queue中take到takeList中的数据全部恢复到queue中
     */
    @Override
    protected void doRollback() {
      int takes = takeList.size();
      synchronized (queueLock) {
        Preconditions.checkState(queue.remainingCapacity() >= takeList.size(),
            "Not enough space in memory channel " +
            "queue to rollback takes. This should never happen, please report");
        while (!takeList.isEmpty()) {
          //takeList 的倒序取出插入到queue的头部
          queue.addFirst(takeList.removeLast());
        }
        /*将putList中数据清空,putList中的数据都是还没有来的及commit到queue中的数据
         */
        putList.clear();
      }
      /* putByteCounter是RollBack时往queue中新写入的数据,此时表示queue中有了更多的数据
       * 需要增加相应的信号量来与之对应
       */
      bytesRemaining.release(putByteCounter);
      putByteCounter = 0;
      takeByteCounter = 0;

      //更新queueStored的信号量
      queueStored.release(takes);
      //更新channel size
      channelCounter.setChannelSize(queue.size());
    }
  }
```

###Channel

这里需要说明的几点:

1. MemoryChannel中使用LinkedBlockingDeque<Event>作为自己的queue;
2. 对LinkedBlockingDeque<Event> queue的操作都用锁queueLock来同步对queue的操作;
3. configure()方法是用来配置MemoryChannel()的,由于默认flume的每30s重新去读取配置文件所以在configure()方法中可看到更改memoryChannel的配置代码;
4. 同时还重写了AbstractChannel的start() , stop()代码等;

```
  // lock to guard queue, mainly needed to keep it locked down during resizes
  // it should never be held through a blocking operation
  //queue的锁,所有对channel queue的操作都需要先获得这个锁
  private Object queueLock = new Object();

  //channel的queue,这个也是Memory Channel的核心的数据结构,这个queue中存储了所有在channel event
  @GuardedBy(value = "queueLock")
  private LinkedBlockingDeque<Event> queue;

  // invariant that tracks the amount of space remaining in the queue(with all uncommitted takeLists deducted)
  // we maintain the remaining permits = queue.remaining - takeList.size()
  // this allows local threads waiting for space in the queue to commit without denying access to the
  // shared lock to threads that would make more space on the queue
  //queue中剩余容量的信号量,该信号量和queue剩余的容量是相同的,表明当前queue中还有多少剩余容量
  private Semaphore queueRemaining;

  // used to make "reservations" to grab data from the queue.
  // by using this we can block for a while to get data without locking all other threads out
  // like we would if we tried to use a blocking call on queue
  //queue中当前存储的event的数量,
  private Semaphore queueStored;

  // maximum items in a transaction queue
  private volatile Integer transCapacity;
  private volatile int keepAlive;
  private volatile int byteCapacity;
  //用来记录上一次配置中byteCapacity配置的大小
  private volatile int lastByteCapacity;
  private volatile int byteCapacityBufferPercentage;
  //针对byteCapacity设置的信号量
  private Semaphore bytesRemaining;
  private ChannelCounter channelCounter;

  public MemoryChannel() {
    super();
  }

  /**
   * Read parameters from context
   * <li>capacity = type long that defines the total number of events allowed at one time in the queue.
   * <li>transactionCapacity = type long that defines the total number of events allowed in one transaction.
   * <li>byteCapacity = type long that defines the max number of bytes used for events in the queue.
   * <li>byteCapacityBufferPercentage = type int that defines the percent of buffer between byteCapacity and the estimated event size.
   * <li>keep-alive = type int that defines the number of second to wait for a queue permit
   */
  /**
   * 在flume运行过程中是有可能channel等component的配置发生改变的,此时需要重新修改component的配置
   * 这就可能需要判断之前的配置和最新的配置发生了那些变化,并做出相应的调整
   */
  @Override
  public void configure(Context context) {
    Integer capacity = null;
    try {
      capacity = context.getInteger("capacity", defaultCapacity);
    } catch (NumberFormatException e) {
      capacity = defaultCapacity;
      LOGGER.warn("Invalid capacity specified, initializing channel to "
          + "default capacity of {}", defaultCapacity);
    }

    if (capacity <= 0) {
      capacity = defaultCapacity;
      LOGGER.warn("Invalid capacity specified, initializing channel to "
          + "default capacity of {}", defaultCapacity);
    }
    try {
      transCapacity = context.getInteger("transactionCapacity", defaultTransCapacity);
    } catch (NumberFormatException e) {
      transCapacity = defaultTransCapacity;
      LOGGER.warn("Invalid transation capacity specified, initializing channel"
          + " to default capacity of {}", defaultTransCapacity);
    }

    if (transCapacity <= 0) {
      transCapacity = defaultTransCapacity;
      LOGGER.warn("Invalid transation capacity specified, initializing channel"
          + " to default capacity of {}", defaultTransCapacity);
    }
    Preconditions.checkState(transCapacity <= capacity,
        "Transaction Capacity of Memory Channel cannot be higher than " +
            "the capacity.");

    try {
      byteCapacityBufferPercentage = context.getInteger("byteCapacityBufferPercentage",
                                                        defaultByteCapacityBufferPercentage);
    } catch (NumberFormatException e) {
      byteCapacityBufferPercentage = defaultByteCapacityBufferPercentage;
    }

    try {
      byteCapacity = (int) ((context.getLong("byteCapacity", defaultByteCapacity).longValue() *
          (1 - byteCapacityBufferPercentage * .01)) / byteCapacitySlotSize);
      if (byteCapacity < 1) {
        byteCapacity = Integer.MAX_VALUE;
      }
    } catch (NumberFormatException e) {
      byteCapacity = (int) ((defaultByteCapacity * (1 - byteCapacityBufferPercentage * .01)) /
          byteCapacitySlotSize);
    }

    try {
      keepAlive = context.getInteger("keep-alive", defaultKeepAlive);
    } catch (NumberFormatException e) {
      keepAlive = defaultKeepAlive;
    }

    if (queue != null) {
      try {
        resizeQueue(capacity);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    } else {
      synchronized (queueLock) {
        queue = new LinkedBlockingDeque<Event>(capacity);
        queueRemaining = new Semaphore(capacity);
        queueStored = new Semaphore(0);
      }
    }

    if (bytesRemaining == null) {
      bytesRemaining = new Semaphore(byteCapacity);
      lastByteCapacity = byteCapacity;
    } else {
      /* 新配置中的byteCapacity 大于上一次配置的lastByteCapacity,则新的bytCapacity增大了,需要在
       * byteRemaining中增加相应的差值才能保证byteRemaining中的容量和最新的byteCapacity相等;
       * Semaphore.release() 是在信号量中增加许可的操作,Semaphore可以同台修改日permits的大小,不用重新new
       */
      if (byteCapacity > lastByteCapacity) {
        bytesRemaining.release(byteCapacity - lastByteCapacity);
        lastByteCapacity = byteCapacity;
      } else {
        try {
          /* 新配置的byteCapacity小于之前的lastByteCapacity,需要缩减信号量,这里并没有重新new 一个Semaphore,
           * 而是从之前的信号量固定的Acquired信号量之间的差值;
           * 这里用的tryAcquire()方法,如果在规定的时间内无法获取所要缩减的信号量,该方法将会抛出异常,线程就会中断
           */
          if (!bytesRemaining.tryAcquire(lastByteCapacity - byteCapacity, keepAlive,
                                         TimeUnit.SECONDS)) {
            LOGGER.warn("Couldn't acquire permits to downsize the byte capacity, resizing has been aborted");
          } else {
            // 更新lastByteCapacity
            lastByteCapacity = byteCapacity;
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }

    if (channelCounter == null) {
      channelCounter = new ChannelCounter(getName());
    }
  }

  // resize LinkedBlockingDeque, 修改channel的容量后需要修改队列的容量
  private void resizeQueue(int capacity) throws InterruptedException {
    int oldCapacity;
    synchronized (queueLock) {
      //计算就的queue的capacity,queue.size(),当前queue中有多少元素, remainingCapacity(), queue剩余的量
      oldCapacity = queue.size() + queue.remainingCapacity();
    }

    if (oldCapacity == capacity) {
      //queue的容量大小不变,不需要做任何操作,直接返回
      return;
    } else if (oldCapacity > capacity) {
      //queue的容量缩小了,首先需要先把信号量的permits降低,然后再改变queue的capacity
      if (!queueRemaining.tryAcquire(oldCapacity - capacity, keepAlive, TimeUnit.SECONDS)) {
        LOGGER.warn("Couldn't acquire permits to downsize the queue, resizing has been aborted");
      } else {
        synchronized (queueLock) {
          LinkedBlockingDeque<Event> newQueue = new LinkedBlockingDeque<Event>(capacity);
          newQueue.addAll(queue);
          queue = newQueue;
        }
      }
    } else {
      synchronized (queueLock) {
        //capacity的容量增加了,新建一个新的queue,然后将其中的剩余的event加入到newQueue中;
        LinkedBlockingDeque<Event> newQueue = new LinkedBlockingDeque<Event>(capacity);
        newQueue.addAll(queue);
        queue = newQueue;
      }
      //增加信号量permits,要保持permits的数量和queue的容量大小相等
      queueRemaining.release(capacity - oldCapacity);
    }
  }

  @Override
  public synchronized void start() {
    channelCounter.start();
    channelCounter.setChannelSize(queue.size());
    channelCounter.setChannelCapacity(Long.valueOf(
            queue.size() + queue.remainingCapacity()));
    super.start();
  }

  @Override
  public synchronized void stop() {
    channelCounter.setChannelSize(queue.size());
    channelCounter.stop();
    super.stop();
  }

  /**
   * 重写的父类的方法
   * @return
   */
  @Override
  protected BasicTransactionSemantics createTransaction() {
    return new MemoryTransaction(transCapacity, channelCounter);
  }

  private long estimateEventSize(Event event) {
    byte[] body = event.getBody();
    if (body != null && body.length != 0) {
      return body.length;
    }
    //Each event occupies at least 1 slot, so return 1.
    return 1;
  }
}
```

## MemoryChannel 的隐患

[参考][http://www.cnblogs.com/lxf20061900/p/3638604.html]

该类有一个内部类MemoryTransaction是mem-channel从source取（put）数据、给（take）sink的操作类。其初始化时会创建两个LinkedBlockingDeque，一个是takeList用于sink的take；一个是putList用于source的put，两个队列的容量都是事务的event最大容量transCapacity。两个队列是用于事务回滚rollback和提交commit的。

Source交给channel处理的一般是调用ChannelProcessor类的processEventBatch(List<Event> events)方法或者processEvent(Event event)方法；在sink端可以直接使用channel.take()方法获取其中的一条event数据。这俩方法在将event提交至channel时，都需要：

```
一、获取channel列表。List<Channel> requiredChannels = selector.getRequiredChannels(event)；

二、通过channel获取Transaction。Transaction tx = reqChannel.getTransaction()；

三、tx.begin()；

四、reqChannel.put(event)(在sink中这是take(event)方法)；

五、tx.commit();

六、tx.rollback();

七、tx.close();
```

* 上面的三~七中的方法，最终调用的是MemoryTransaction的doBegin(未重写，默认空方法)、doPut、doCommit、doRollback、doClose(未重写，默认空方法)方法。
* 其中doPut方法，先计算event的大小可以占用bytesRemaining的多大空间，然后在有限的时间内等待获取写入空间，获取之后写入putList暂存。
* doTake方法，先检查takeList的剩余容量；再检查是否有许可进行取操作(queueStored使得可以不用阻塞其它线程获取许可信息)；然后同步的从queue中取一个event，再放入takeList，并返回此event。
* doCommit方法，不管在sink还是source端都会调用。首先检查queue队列中是否有足够空间放得下从source过来的数据，依据就是queueRemaining是否有remainingChange = takeList.size-putList.size个许可。然后是将putList中的所有数据提交到内存队列queue之中，并将putList和takeList清空。清空表明：运行到这步说明takeList中的数据无需再保留，putList中的数据可以放入queue中。由于在doTake中从queue取数据，所以queueStored在减，但在doCommit中会把putList中的数据放入queue所以需要增加queueStored：queueStored.release(puts)；bytesRemaining在doPut中获得了一些许可会减少，在doCommit中由于takeList会清空所有会增加bytesRemaining：bytesRemaining.release(takeByteCounter);而queueRemaining在doPut和doTake中并未进行操作，而且doCommit方法在sink和source中都会调用，故而在此方法中修改takeList和putList的差值即可：queueRemaining.release(remainingChange)(在此有个细节，在doCommit的开始remainingChange如果小于0，说明剩余空间不足以放入整个putList,要么超时报错退出；要么获得足够的许可，如果是后者的话就不需要再调整queueRemaining因为是在现在的基础之上减，如果remainingChange大于0，说明去除takeList大小后不仅足以放入整个putList，而且还有剩余，queueRemaining需要释放remainingChange)。其他就是修改计数器。
* doRollback方法是在上面三、四、五出现异常的时候调用的，用于事务回滚。不管是在sink还是source中，都会调用。将takeList中的所有数据重新放回queue中：

```
while(!takeList.isEmpty()) {
	queue.addFirst(takeList.removeLast());
	//回滚时，重新放回queue中。可能会重复（commit阶段出错，已经take的数据需要回滚，批量的情况）
 }
```

然后清空putList:

* putList.clear(); //这个方法可能发生在put中，也可能发生在take中，所以需要同步清空。可能会丢数据（还在put的阶段，没到commit阶段，出错会导致回滚，导致已经put还未放入queue中的数据会丢失）由于putList清空了，所以bytesRemaining.release(putByteCounter)；由于takeList又返回给了queue所以queue的量增加了：queueStored.release(takes)。
* 在分层的分布式flume中，一旦汇总节点中断，而采集节点使用mem，则采集会大量的丢失数据，因为channel会因为put而快速的填满，填满之后再调用put会迸发异常，致使出现异常引起事务回滚，回滚会直接清空putList，使数据丢失，只留下channel中的数据(这些数据是一开始放入进去的后来的会丢失)。putList.offer会因为填满数据返回false，add方法如果队列满了则会爆异常。