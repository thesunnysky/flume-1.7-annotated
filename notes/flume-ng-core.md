# flume-ng-core-Channel

Flume的架构主要有一下几个核心概念：

- Event：一个数据单元，带有一个可选的消息头
- Flow：Event从源点到达目的点的迁移的抽象
- Client：操作位于源点处的Event，将其发送到Flume Agent
- Agent：一个独立的Flume进程，包含组件Source、Channel、Sink
- Source：用来消费传递到该组件的Event
- Channel：中转Event的一个临时存储，保存有Source组件传递过来的Event
- Sink：从Channel中读取并移除Event，将Event传递到Flow Pipeline中的下一个Agent（如果有的话）

# 1. Channel

先从channel讲起，感觉channel作为连接source和sink的的桥梁，感觉channel是最关键的；

### 1.1 LifecycleAware  & NamedComponent

```
public interface LifecycleAware {
  public void start();
  public void stop();
  public LifecycleState getLifecycleState();
}

public interface NamedComponent {
  public void setName(String name);
  public String getName();
}
```

 LifecycleAware 是flume 的基本借口之一，主要的功能是实现了对Component生命周期的管理;

NamedComponent 同样是flume的基本接口之一，它给每一个Component都绑定了一个名字，这样就可以通过组件的名字来管理组件；

### 1.2  channel

```
public interface Channel extends LifecycleAware, NamedComponent{
    public void put(Event event) throws ChannelException;
    public Event take() throws ChannelException;
    public Transaction getTransaction();
}
```

1. 由interface channel的代码可以看出，channel实现了LifecycleAware 和 NamedCompoment 接口；

2. channel的 put () 是见Event放进channel的方法，其中源码中有这么一段话：

   > ```
   >  * <p>Puts the given event into the channel.</p>
   >  * <p><strong>Note</strong>: This method must be invoked within an active  {@link Transaction} boundary. Failure to do so can lead to unpredictable
   > ```

   所以感觉channel对event的操作都是和transaction绑定在一起的；

3.  take()方法是从channel中取出下一个event的方法 ，如果channel为空，则该方法会返回null；源码中也有相应的一段话表示take方法也是和transaction绑定在一块的；

4. getTransaction() 中从channel中取出一个transaction；

   ```
   // 需要搞清楚channel， event 和 transaction的对应关系
   ```

   ### ChannelFactory

   ```
   public interface ChannelFactory {
     Channel create(String name, String type) throws FlumeException;
     Class<? extends Channel> getClass(String type) throws FlumeException;
   }
   ```

   1. 接口ChannelFactory提供创建channel的工厂方法，通过实现该接口，可以通过name和type参数来创建对应的channel类；
   2. 需要指出的是flume-ng-core中只有DefaultChannelFactory实现了该借口；

   ### ChannelSelector

   ```
   public interface ChannelSelector extends NamedComponent, Configurable {
     public void setChannels(List<Channel> channels);
     public List<Channel> getRequiredChannels(Event event);
     public List<Channel> getOptionalChannels(Event event);
     public List<Channel> getAllChannels();
   }
   ```

   1. >```
      >/**
      > * Allows the selection of a subset of channels from the given set based on its implementation policy. Different implementations of this interface embody different policies that affect the choice of channels that a source will push the incoming events to.
      > */
      >```

   2. channelselector 也应该是flume的基本的接口之一，标识的是和channel selector相关的东西；

   ### CounterGroup

   > ```
   > Used for counting events, collecting metrics, etc.
   > ```