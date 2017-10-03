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

package org.apache.flume.source;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.flume.CounterGroup;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.PollableSource;
import org.apache.flume.Source;
import org.apache.flume.SourceRunner;
import org.apache.flume.channel.ChannelProcessor;
import org.apache.flume.lifecycle.LifecycleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * An implementation of {@link SourceRunner} that can drive a
 * {@link PollableSource}.
 * </p>
 * <p>
 * A {@link PollableSourceRunner} wraps a {@link PollableSource} in the required
 * run loop in order for it to operate. Internally, metrics and counters are
 * kept such that a source that returns a {@link PollableSource.Status} of
 * {@code BACKOFF} causes the run loop to do exactly that. There's a maximum
 * backoff period of 500ms. A source that returns {@code READY} is immediately
 * invoked. Note that {@code BACKOFF} is merely a hint to the runner; it need
 * not be strictly adhered to.
 * </p>
 */
/**
 * PollableSourceRunner 是对PollableSource的主要作用管理source工作线程;
 * source的工作线程是在PollableSourceRunner中创建,该线程的声明周期的管理也有该类来完成;
 * 同时由于flume有BACKOFF机制,就是说source在某次运行中无法从source中crate event,则根据响应的BACKOFF设置
 * 来sleep一段时间,实现Flume BACKOFF的机制也在该类中;
 */
public class PollableSourceRunner extends SourceRunner {

  private static final Logger logger = LoggerFactory.getLogger(PollableSourceRunner.class);

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

  @Override
  public String toString() {
    return "PollableSourceRunner: { source:" + getSource() + " counterGroup:"
        + counterGroup + " }";
  }

  @Override
  public LifecycleState getLifecycleState() {
    return lifecycleState;
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
