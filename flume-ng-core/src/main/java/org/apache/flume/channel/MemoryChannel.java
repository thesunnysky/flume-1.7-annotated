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
package org.apache.flume.channel;

import com.google.common.base.Preconditions;
import org.apache.flume.ChannelException;
import org.apache.flume.ChannelFullException;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.annotations.InterfaceAudience;
import org.apache.flume.annotations.InterfaceStability;
import org.apache.flume.annotations.Recyclable;
import org.apache.flume.instrumentation.ChannelCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * MemoryChannel is the recommended channel to use when speeds which
 * writing to disk is impractical is required or durability of data is not
 * required.
 * </p>
 * <p>
 * Additionally, MemoryChannel should be used when a channel is required for
 * unit testing purposes.
 * </p>
 */
@InterfaceAudience.Public
@InterfaceStability.Stable
@Recyclable
public class MemoryChannel extends BasicChannelSemantics {
  private static Logger LOGGER = LoggerFactory.getLogger(MemoryChannel.class);
  private static final Integer defaultCapacity = 100;
  private static final Integer defaultTransCapacity = 100;
  private static final double byteCapacitySlotSize = 100;
  private static final Long defaultByteCapacity = (long)(Runtime.getRuntime().maxMemory() * .80);
  private static final Integer defaultByteCapacityBufferPercentage = 20;

  private static final Integer defaultKeepAlive = 3;

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

    @Override
    protected void doPut(Event event) throws InterruptedException {
      channelCounter.incrementEventPutAttemptCount();
      int eventByteSize = (int) Math.ceil(estimateEventSize(event) / byteCapacitySlotSize);

      if (!putList.offer(event)) {
        throw new ChannelException(
            "Put queue for MemoryTransaction of capacity " +
            putList.size() + " full, consider committing more frequently, " +
            "increasing capacity or increasing thread count");
      }
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
      if (!queueStored.tryAcquire(keepAlive, TimeUnit.SECONDS)) {
        return null;
      }
      Event event;
      synchronized (queueLock) {
        event = queue.poll();
      }
      Preconditions.checkNotNull(event, "Queue.poll returned NULL despite semaphore " +
          "signalling existence of entry");
      takeList.put(event);

      int eventByteSize = (int) Math.ceil(estimateEventSize(event) / byteCapacitySlotSize);
      takeByteCounter += eventByteSize;

      return event;
    }

    @Override
    protected void doCommit() throws InterruptedException {
      int remainingChange = takeList.size() - putList.size();
      if (remainingChange < 0) {
        if (!bytesRemaining.tryAcquire(putByteCounter, keepAlive, TimeUnit.SECONDS)) {
          throw new ChannelException("Cannot commit transaction. Byte capacity " +
              "allocated to store event body " + byteCapacity * byteCapacitySlotSize +
              "reached. Please increase heap space/byte capacity allocated to " +
              "the channel as the sinks may not be keeping up with the sources");
        }
        if (!queueRemaining.tryAcquire(-remainingChange, keepAlive, TimeUnit.SECONDS)) {
          bytesRemaining.release(putByteCounter);
          throw new ChannelFullException("Space for commit to queue couldn't be acquired." +
              " Sinks are likely not keeping up with sources, or the buffer size is too tight");
        }
      }
      int puts = putList.size();
      int takes = takeList.size();
      synchronized (queueLock) {
        if (puts > 0) {
          while (!putList.isEmpty()) {
            if (!queue.offer(putList.removeFirst())) {
              throw new RuntimeException("Queue add failed, this shouldn't be able to happen");
            }
          }
        }
        putList.clear();
        takeList.clear();
      }
      bytesRemaining.release(takeByteCounter);
      takeByteCounter = 0;
      putByteCounter = 0;

      queueStored.release(puts);
      if (remainingChange > 0) {
        queueRemaining.release(remainingChange);
      }
      if (puts > 0) {
        channelCounter.addToEventPutSuccessCount(puts);
      }
      if (takes > 0) {
        channelCounter.addToEventTakeSuccessCount(takes);
      }

      channelCounter.setChannelSize(queue.size());
    }

    @Override
    protected void doRollback() {
      int takes = takeList.size();
      synchronized (queueLock) {
        Preconditions.checkState(queue.remainingCapacity() >= takeList.size(),
            "Not enough space in memory channel " +
            "queue to rollback takes. This should never happen, please report");
        while (!takeList.isEmpty()) {
          queue.addFirst(takeList.removeLast());
        }
        putList.clear();
      }
      bytesRemaining.release(putByteCounter);
      putByteCounter = 0;
      takeByteCounter = 0;

      queueStored.release(takes);
      channelCounter.setChannelSize(queue.size());
    }

  }

  // lock to guard queue, mainly needed to keep it locked down during resizes
  // it should never be held through a blocking operation
  private Object queueLock = new Object();

  @GuardedBy(value = "queueLock")
  private LinkedBlockingDeque<Event> queue;

  // invariant that tracks the amount of space remaining in the queue(with all uncommitted takeLists deducted)
  // we maintain the remaining permits = queue.remaining - takeList.size()
  // this allows local threads waiting for space in the queue to commit without denying access to the
  // shared lock to threads that would make more space on the queue
  private Semaphore queueRemaining;

  // used to make "reservations" to grab data from the queue.
  // by using this we can block for a while to get data without locking all other threads out
  // like we would if we tried to use a blocking call on queue
  private Semaphore queueStored;

  // maximum items in a transaction queue
  private volatile Integer transCapacity;
  private volatile int keepAlive;
  private volatile int byteCapacity;
  //用来记录上一次配置中byteCapacity配置的大小
  private volatile int lastByteCapacity;
  private volatile int byteCapacityBufferPercentage;
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
