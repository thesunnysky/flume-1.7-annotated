# Flume Transaction

> Transaction，事务，这是一个很常见的概念，但是又怎么理解它真正的含义呢?

### Flume中的事务

先看一段flume代码注解中对flume transaction的定义：

>```
>/**
> * <p>Provides the transaction boundary while accessing a channel.</p>
> * <p>A <tt>Transaction</tt> instance is used to encompass channel access
> * via the following idiom:</p>
> * <pre><code>
> * Channel ch = ...
> * Transaction tx = ch.getTransaction();
> * try {
> *   tx.begin();
> *   ...
> *   // ch.put(event) or ch.take()
> *   ...
> *   tx.commit();
> * } catch (ChannelException ex) {
> *   tx.rollback();
> *   ...
> * } finally {
> *   tx.close();
> * }
> * </code></pre>
> * <p>Depending upon the implementation of the channel, the transaction
> * semantics may be strong, or best-effort only.</p>
> *
> * <p>
> * Transactions must be thread safe. To provide  a guarantee of thread safe
> * access to Transactions, see {@link BasicChannelSemantics} and
> * {@link  BasicTransactionSemantics}.
> *
> * @see org.apache.flume.Channel
> */
>```

之前在其他的地方看到说事务提供了对channel操作的事务boundary，从注解中的实例代码中可以看出，所有对channel的操作都是放在事务之中的,事物的begin()和close()方法组成了对channel操作的代码的边界；

也就是说除了对channel的基本操作之外，flume又在这些操作之外引入了事务机制，保证了数据在channel传递的过程中的可靠；	

### flume 事务支持的操作

> 代码注释一块上，代码中的注释是理解代码逻辑的key

```
public interface Transaction {

  enum TransactionState { Started, Committed, RolledBack, Closed }

  /**
   * <p>Starts a transaction boundary for the current channel operation. If a
   * transaction is already in progress, this method will join that transaction
   * using reference counting.</p>
   * <p><strong>Note</strong>: For every invocation of this method there must
   * be a corresponding invocation of {@linkplain #close()} method. Failure
   * to ensure this can lead to dangling transactions and unpredictable results.
   * </p>
   */
  void begin();

  /**
   * Indicates that the transaction can be successfully committed. It is
   * required that a transaction be in progress when this method is invoked.
   */
  void commit();

  /**
   * Indicates that the transaction can must be aborted. It is
   * required that a transaction be in progress when this method is invoked.
   */
  void rollback();

  /**
   * <p>Ends a transaction boundary for the current channel operation. If a
   * transaction is already in progress, this method will join that transaction
   * using reference counting. The transaction is completed only if there
   * are no more references left for this transaction.</p>
   * <p><strong>Note</strong>: For every invocation of this method there must
   * be a corresponding invocation of {@linkplain #begin()} method. Failure
   * to ensure this can lead to dangling transactions and unpredictable results.
   * </p>
   */
  void close();
}
```

1. 事物的状态包括：Started，Committed，RolledBack，Closed
2. begin()方法为当前的channel操作开启一个事物边界，如果当前有事物正在进行，那么该方法将会等到上一个事物结束才开始执行（否则可能会造成一个dangling（悬空）事物）
3. commit()，标识当前事物可以被成功提交了，调用该方法需要保证当前有一个事物正在你被执行；
4. rollback(), 表示当前的事物可以被丢弃掉了（感觉这里用丢弃来标识略有不妥，应该用更常见的回滚来描述）
5. close() , 为当前的channel操作结束一个事物边界，如果当前有一个事务正在进行，对该方法的调用将会join 事物的引用计数，只有事物中已经没有reference了，这个事务才能真正的结束，close()方法的调用需要配合begin()方法的调用，否则就有可能造成dangling事务；

> 事务是由某个component全局管理的一个东西？ 
>
> 当前事物，begin()和close()的对应？

