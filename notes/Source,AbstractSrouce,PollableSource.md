# Source,AbstractSrouce

Source 是flume的基本的组件执之一,source的主要作用的就是产生event,然后调用ChannelProcessor的方法将产生的event放入到配置的channel中;

###Source Interface

Source接口是source的一个基本接口,从代码中可以看出,除了继承LifecycleAware, NamedComponent之外,它自己的两法方法主要是ChannelProcessor的setter和getter方法;

```
public interface Source extends LifecycleAware, NamedComponent {
  /**
   * Specifies which channel processor will handle this source's events.
   *
   * @param channelProcessor
   */
  public void setChannelProcessor(ChannelProcessor channelProcessor);

  /**
   * Returns the channel processor that will handle this source's events.
   */
  public ChannelProcessor getChannelProcessor();
```

### AbstractSource

AbbstactSrouce 是implements Source接口的抽象类,在该类中实现了LifecycleAware, NamedComponent, Source中的所有方法,也就意味着这个抽象类将flume source 一些基本的方法统一实现了,对于任何一种source,都需要继承这个类;

```
public abstract class AbstractSource implements Source {

  private ChannelProcessor channelProcessor;
  private String name;

  private LifecycleState lifecycleState;

  public AbstractSource() {
    lifecycleState = LifecycleState.IDLE;
  }

  @Override
  public synchronized void start() {
    Preconditions.checkState(channelProcessor != null,
        "No channel processor configured");

    lifecycleState = LifecycleState.START;
  }

  @Override
  public synchronized void stop() {
    lifecycleState = LifecycleState.STOP;
  }

  @Override
  public synchronized void setChannelProcessor(ChannelProcessor cp) {
    channelProcessor = cp;
  }

  @Override
  public synchronized ChannelProcessor getChannelProcessor() {
    return channelProcessor;
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

  public String toString() {
    return this.getClass().getName() + "{name:" + name + ",state:" + lifecycleState + "}";
  }  
}
```

### SourceRunner

sourcerunner决定了一个source是如何被驱动的, SourceRunner是一个抽象类,该类是被用来实例化具体的SourceRunner类的;

在flume中共有两种的sourcerunner:

* PollableSourceRunner 
* EventDrivenSourceRunner

SourceRunner 的 forceSource()方法将根据不同的Source返回对应类型的SourceRunner;

```
public abstract class SourceRunner implements LifecycleAware {
  private Source source;
  /**
   * Static factory method to instantiate a source runner implementation that
   * corresponds to the type of {@link Source} specified.
   *
   * @param source The source to run
   * @return A runner that can run the specified source
   * @throws IllegalArgumentException if the specified source does not implement
   * a supported derived interface of {@link SourceRunner}.
   */
  public static SourceRunner forSource(Source source) {
    SourceRunner runner = null;
    if (source instanceof PollableSource) {
      runner = new PollableSourceRunner();
      ((PollableSourceRunner) runner).setSource((PollableSource) source);
    } else if (source instanceof EventDrivenSource) {
      runner = new EventDrivenSourceRunner();
      ((EventDrivenSourceRunner) runner).setSource((EventDrivenSource) source);
    } else {
      throw new IllegalArgumentException("No known runner type for source "
          + source);
    }
    return runner;
  }
  public Source getSource;
  public void setSource(Source source);
}
```

###PollableSourceRunner

```
/**
 * PollableSourceRunner 是对PollableSource的主要作用管理source工作线程;
 * source的工作线程是在PollableSourceRunner中创建,该线程的声明周期的管理也有该类来完成;
 * 同时由于flume有BACKOFF机制,就是说source在某次运行中无法从source中crate event,则根据响应的BACKOFF设置
 * 来sleep一段时间,实现Flume BACKOFF的机制也在该类中;
 */
```

```java
public class PollableSourceRunner extends SourceRunner {
  //原子类,用来表示当前source线程是否应该stop
  private AtomicBoolean shouldStop;
  //用来计数的辅助类
  private CounterGroup counterGroup;
  private PollingRunner runner;
  //source的工作线程
  private Thread runnerThread;
  private LifecycleState lifecycleState;

  public PollableSourceRunner() {
    shouldStop = new AtomicBoolean();
    counterGroup = new CounterGroup();
    lifecycleState = LifecycleState.IDLE;
  }

  //启动source的工作线程
  @Override
  public void start() {
    //父类方法,获取当前runner的source
    PollableSource source = (PollableSource) getSource();
    //获取该source的channel processor
    ChannelProcessor cp = source.getChannelProcessor();
    //初始化 channel processor的拦截器
    cp.initialize();
    //设值source的生命周期为start状态
    source.start();

    //实例化Runnable类初始化并启动线程
    runner = new PollingRunner();

    runner.source = source;
    runner.counterGroup = counterGroup;
    runner.shouldStop = shouldStop;

    runnerThread = new Thread(runner);
    runnerThread.setName(getClass().getSimpleName() + "-" +
        source.getClass().getSimpleName() + "-" + source.getName());
    runnerThread.start();

    //设置当前SourceRunner的声明周期状态
    lifecycleState = LifecycleState.START;
  }

  /**
   * 停止当前sourceRunner;
   * 包括对停止工作线程,source的声明周期和该source对应的channel processor
   */
  @Override
  public void stop() {

    /* 设置sourceStop状态,设置后工作线程会在循环中检测这个变量的值,如果是shouldStop=True,在线程会自行退出
     */
    runner.shouldStop.set(true);

    try {
      //中断工作线程
      runnerThread.interrupt();
      //等待工作工作线程退出
      runnerThread.join();
    } catch (InterruptedException e) {
      logger.warn("Interrupted while waiting for polling runner to stop. Please report this.", e);
      Thread.currentThread().interrupt();
    }

    Source source = getSource();
    //设值source 的生命周期为stop状体
    source.stop();
    ChannelProcessor cp = source.getChannelProcessor();
    //关闭channel processor的拦截器
    cp.close();

    lifecycleState = LifecycleState.STOP;
  }
  /**
   * PoolingRunner 主要是实现了Runnable接口,在run()中会循环的调用Source.Process()方法;
   * PoolingRunner 相当于统一了对Source.process()方法的调用;
   */
   public static class PollingRunner implements Runnable {
      private PollableSource source;
      private AtomicBoolean shouldStop;
      private CounterGroup counterGroup;
      @Override
      public void run() {
        logger.debug("Polling runner starting. Source:{}", source);

        while (!shouldStop.get()) {
          counterGroup.incrementAndGet("runner.polls");

          try {
            //循环调用source.process()方法
            if (source.process().equals(PollableSource.Status.BACKOFF)) {
              counterGroup.incrementAndGet("runner.backoffs");

              //BACKOFF 机制
              Thread.sleep(Math.min(
                  counterGroup.incrementAndGet("runner.backoffs.consecutive")
                  * source.getBackOffSleepIncrement(), source.getMaxBackOffSleepInterval()));
            } else {
              counterGroup.set("runner.backoffs.consecutive", 0L);
            }
          } catch (InterruptedException e) {
            logger.info("Source runner interrupted. Exiting");
            counterGroup.incrementAndGet("runner.interruptions");
          } catch (EventDeliveryException e) {
            logger.error("Unable to deliver event. Exception follows.", e);
            counterGroup.incrementAndGet("runner.deliveryErrors");
          } catch (Exception e) {
            counterGroup.incrementAndGet("runner.errors");
            logger.error("Unhandled exception, logging and sleeping for " +
                source.getMaxBackOffSleepInterval() + "ms", e);
            try {
              Thread.sleep(source.getMaxBackOffSleepInterval());
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            }
          }
        }

        logger.debug("Polling runner exiting. Metrics:{}", counterGroup);
      }

    }

}
```





