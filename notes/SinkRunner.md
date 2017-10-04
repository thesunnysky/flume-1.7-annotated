# SinkRunner

* sinkRunner中启动了Sink的工作线程，worker thread将会循环调用sinkProcessor的proess()方法
* 同时我们也应该清楚，SinkProcessor的Process方法核心也是调用Sink的process()方法；
* 同时SinkRunner中也实现了在调用process方法失败时的backoff机制；

```java
 @Override
  public void start() {
    SinkProcessor policy = getPolicy();

    policy.start();

    runner = new PollingRunner();

    runner.policy = policy;
    runner.counterGroup = counterGroup;
    runner.shouldStop = new AtomicBoolean();
    //启动工作线程
    runnerThread = new Thread(runner);
    runnerThread.setName("SinkRunner-PollingRunner-" +
        policy.getClass().getSimpleName());
    runnerThread.start();

    lifecycleState = LifecycleState.START;
  }
```

```java
 @Override
  public void start() {
    SinkProcessor policy = getPolicy();

    policy.start();

    runner = new PollingRunner();

    runner.policy = policy;
    runner.counterGroup = counterGroup;
    runner.shouldStop = new AtomicBoolean();
    //启动工作线程
    runnerThread = new Thread(runner);
    runnerThread.setName("SinkRunner-PollingRunner-" +
        policy.getClass().getSimpleName());
    runnerThread.start();

    lifecycleState = LifecycleState.START;
  }
```

```java
   /* 线程的run方法，就是不停的调用SinkProcessor的process()方法
     * 并在失败的时候启动backoff机制
     */
    @Override
    public void run() {
      logger.debug("Polling sink runner starting");

      while (!shouldStop.get()) {
        try {
          if (policy.process().equals(Sink.Status.BACKOFF)) {
            counterGroup.incrementAndGet("runner.backoffs");

            Thread.sleep(Math.min(
                counterGroup.incrementAndGet("runner.backoffs.consecutive")
                * backoffSleepIncrement, maxBackoffSleep));
          } else {
            counterGroup.set("runner.backoffs.consecutive", 0L);
          }
        } catch (InterruptedException e) {
          logger.debug("Interrupted while processing an event. Exiting.");
          counterGroup.incrementAndGet("runner.interruptions");
        } catch (Exception e) {
          logger.error("Unable to deliver event. Exception follows.", e);
          if (e instanceof EventDeliveryException) {
            counterGroup.incrementAndGet("runner.deliveryErrors");
          } else {
            counterGroup.incrementAndGet("runner.errors");
          }
          try {
            Thread.sleep(maxBackoffSleep);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        }
      }
      logger.debug("Polling runner exiting. Metrics:{}", counterGroup);
    }
```







