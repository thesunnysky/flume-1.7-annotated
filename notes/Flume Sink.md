# Flume Sink

### 1.1 Sink interface

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

1. sink都是要和channel绑定到一起的，Sink接口提供了方法可以来设置sink和channel的关系；
2. 其中最重要的process()方法，在事物的界限内，process（）讲消费channel中的events， 在process()成功“处理/传递“事物中的events，需要对transaction commit(),同事如果在处理过程中发生了异常，就需要rollback事务；
3. 在自己实现flume的sink时，Sink就是要实现的接口之一，Sink接口为实现自定义的sink规范了统一的接口；

### 1.2 SinkFactory

```
public interface SinkFactory {
  Sink create(String name, String type)
      throws FlumeException;
  Class<? extends Sink> getClass(String type)
      throws FlumeException;
}
```

### 1.3 SinkProcessor

```
public interface SinkProcessor extends LifecycleAware, Configurable {
  Status process() throws EventDeliveryException;
  void setSinks(List<Sink> sinks);
}
```

1. 从代码的注释中可以读到：

   ```
    * Interface for a device that allows abstraction of the behavior of multiple sinks, always assigned to a SinkRunner
    * A sink processors {@link SinkProcessor#process()} method will only be accessed by a single runner thread. However configuration method such as {@link Configurable#configure} may be concurrently accessed.
   ```

   -> 说是提供了多sink的 抽象，一个sinkprocessor赋予一个sinkrunner；

2. `Status process() throws EventDeliveryException`

   ```
     /**
      * <p>Handle a request to poll the owned sinks.</p>
      *
      * <p>The processor is expected to call {@linkplain Sink#process()} on whatever sink(s) appropriate, handling failures as appropriate and throwing {@link EventDeliveryException} when there is a failure to deliver any events according to the delivery policy defined by the sink processor implementation. See specific implementations of this
   interface for delivery behavior and policies.
   @return Returns {@code READY} if events were successfully consumed,or {@code BACKOFF} if no events were available in the channel to consume.Status proc
   ```

3. SinkProcessor的processor()方法只允许被被一个SinkRunner访问;

4. 在对sinkprocessor进一步了解之后发现sinkrocessor的主要功能是提供了对multiple sink的抽象，processor将会选举出哪个sink将是被使用的sink；（如何实现的？）

   ### SinkRunner

   ```
   ?
   ```

   ​

   ​