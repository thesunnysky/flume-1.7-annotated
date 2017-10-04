# Sink,AbstractSink,XXXSink

先看一下代码中的注释:

```
/**
 * <p>
 * A sink is connected to a <tt>Channel</tt> and consumes its contents,
 * sending them to a configured destination that may vary according to
 * the sink type.
 * </p>
 * <p>
 * Sinks can be grouped together for various behaviors using <tt>SinkGroup</tt>
 * and <tt>SinkProcessor</tt>. They are polled periodically by a
 * <tt>SinkRunner</tt> via the processor</p>
 *<p>
 * Sinks are associated with unique names that can be used for separating
 * configuration and working namespaces.
 * </p>
 * <p>
 * While the {@link Sink#process()} call is guaranteed to only be accessed
 * by a single thread, other calls may be concurrently accessed and should
 * thus be protected.
 * </p>
 */
```

### Sink接口

```
public interface Sink extends LifecycleAware, NamedComponent {
  public void setChannel(Channel channel);

  public Channel getChannel();

  /**
   * <p>Requests the sink to attempt to consume data from attached channel</p>
   * <p><strong>Note</strong>: This method should be consuming from the channel
   * within the bounds of a Transaction. On successful delivery, the transaction
   * should be committed, and on failure it should be rolled back.
   * @return READY if 1 or more Events were successfully delivered, BACKOFF if
   * no data could be retrieved from the channel feeding this sink
   * @throws EventDeliveryException In case of any kind of failure to
   * deliver data to the next hop destination.
   */
  public Status process() throws EventDeliveryException;

  public static enum Status {
    READY, BACKOFF
  }
}
```

1. Sink接口提供了基本的Sink类方法,由于Sink必须要和Channel(一个或一组)绑定在一起,所以接口中提供了setter和getter方法;

2. 其实是重要的process()方法, process方法将从channel中获取event,然后在处理这些event;

   需要注意的是,代码中的注解也提到:sink的process()方法消费channel中的内容时必须在事务边界内,只有那些成功被处理的event才能被commit,否则就要回滚;

### AbstractSink

1. AbstractSink实现了Sink接口和LifecycleAware接口,但是并没有将Sink的process()方法在这里实现;

2. AbstactSink 类示例话了Channel,这个Channel将和该Sink关联在一起,AbstractSink也实现了一些基本的操作方法,这样在后续的具体Sink类再继承AbstractSink时就不用再去实现这些很基本的方法了;

3. 在自己的一次实践中,实现了自己的一个Sink,实现的功能就是从channel中获取event,然后分析其中的内容,将符合某种规则的日志信息存入到mysql中;

   在实现自定义的sink时简单来说就让自己的Sink类继承AbstractSink即可,实现其中尚未实现的方法即可,其中主要的编码都集中在process()方法中;

   (需要注意的时,如果想让自己的Sink可以由配置文件来配置,同时需要实现Configurable接口)

```
public abstract class AbstractSink implements Sink, LifecycleAware {

  private Channel channel;
  private String name;

  private LifecycleState lifecycleState;

  public AbstractSink() {
    lifecycleState = LifecycleState.IDLE;
  }

  @Override
  public synchronized void start() {
    Preconditions.checkState(channel != null, "No channel configured");

    lifecycleState = LifecycleState.START;
  }

  @Override
  public synchronized void stop() {
    lifecycleState = LifecycleState.STOP;
  }

  @Override
  public synchronized Channel getChannel() {
    return channel;
  }

  @Override
  public synchronized void setChannel(Channel channel) {
    this.channel = channel;
  }

  @Override
  public synchronized LifecycleState getLifecycleState() {
    return lifecycleState;
  }

  @Override
  public synchronized void setName(String name) {
    this.name = name;
  }

  @Override
  public synchronized String getName() {
    return name;
  }

  @Override
  public String toString() {
    return this.getClass().getName() + "{name:" + name + ", channel:" + channel.getName() + "}";
  }
}
```

