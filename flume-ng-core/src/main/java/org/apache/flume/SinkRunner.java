/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.flume;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.flume.lifecycle.LifecycleAware;
import org.apache.flume.lifecycle.LifecycleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * A driver for {@linkplain Sink sinks} that polls them, attempting to
 * {@linkplain Sink#process() process} events if any are available in the
 * {@link Channel}.
 * </p>
 *
 * <p>
 * Note that, unlike {@linkplain Source sources}, all sinks are polled.
 * </p>
 *
 * @see org.apache.flume.Sink
 * @see org.apache.flume.SourceRunner
 */

/**
 * sinkRunner中启动了Sink的工作线程，worker thread将会循环调用sinkProcessor的proess()方法
 * 同时我们也应该清楚，SinkProcessor的Process方法核心也是调用Sink的process()方法；
 * 同时SinkRunner中也实现了在调用process方法失败时的backoff机制
 */
public class SinkRunner implements LifecycleAware {

  private static final Logger logger = LoggerFactory
      .getLogger(SinkRunner.class);
  private static final long backoffSleepIncrement = 1000;
  private static final long maxBackoffSleep = 5000;

  private CounterGroup counterGroup;
  //run 方法
  private PollingRunner runner;
  //对工作线程的引用
  private Thread runnerThread;
  //SinkRunner生命周期管理
  private LifecycleState lifecycleState;

  //SinkProcessor的实例引用
  private SinkProcessor policy;

  public SinkRunner() {
    counterGroup = new CounterGroup();
    lifecycleState = LifecycleState.IDLE;
  }

  public SinkRunner(SinkProcessor policy) {
    this();
    setSink(policy);
  }

  public SinkProcessor getPolicy() {
    return policy;
  }

  public void setSink(SinkProcessor policy) {
    this.policy = policy;
  }

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

  @Override
  public void stop() {

    //关闭工作线程
    if (runnerThread != null) {
      runner.shouldStop.set(true);
      runnerThread.interrupt();

      while (runnerThread.isAlive()) {
        try {
          logger.debug("Waiting for runner thread to exit");
          runnerThread.join(500);
        } catch (InterruptedException e) {
          logger.debug("Interrupted while waiting for runner thread to exit. Exception follows.",
                       e);
        }
      }
    }

    getPolicy().stop();
    lifecycleState = LifecycleState.STOP;
  }

  @Override
  public String toString() {
    return "SinkRunner: { policy:" + getPolicy() + " counterGroup:"
        + counterGroup + " }";
  }

  @Override
  public LifecycleState getLifecycleState() {
    return lifecycleState;
  }

  /**
   * {@link Runnable} that {@linkplain SinkProcessor#process() polls} a
   * {@link SinkProcessor} and manages event delivery notification,
   * {@link Sink.Status BACKOFF} delay handling, etc.
   */
  public static class PollingRunner implements Runnable {

    private SinkProcessor policy;
    private AtomicBoolean shouldStop;
    private CounterGroup counterGroup;

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

  }
}
