#BasicChannelSemantics

下图是BasicChannelSemantics的继承关系图,其中主要的组成部分是:

BasicChannelSemantics 继承了AbstractChannel,其中AbstractChannel中实现几个基本的component接口,BasicChannelSemantics在AbstractChannel的基础上引进了事务操作;

BasicChannelSemantics是channel实现事务的关键之一

![捕获](G:\snap\捕获.PNG)

### ThreadLocal\<BasicTransactionSemantics>

BasicChannelSemantics 中通过将所有对事务的操作都代理给 BasicTransactionSemantics来实现,(BasicTransactionSemantics 是flume中事务的操作关键),

同时BasicChannelSemantics 通过ThreadLocal\<BasicTransactionSemantics>来保证了所有的事务都是和单个现线程绑定的,这样保证了事务直接的隔离性;

```java
private ThreadLocal<BasicTransactionSemantics> currentTransaction = 
	new ThreadLocal<BasicTransactionSemantics>();
```

###创建事务

```
protected abstract BasicTransactionSemantics createTransaction();
```

BasicChannelSemantics  通过方法createTransaction()

###getTransaction

```java
protected void initialize() {}  

@Override
  public Transaction getTransaction() {
    if (!initialized) {
      synchronized (this) {
        if (!initialized) {
          initialize();
          initialized = true;
        }
      }
    }

    BasicTransactionSemantics transaction = currentTransaction.get();
    if (transaction == null || transaction.getState().equals(
            BasicTransactionSemantics.State.CLOSED)) {
      transaction = createTransaction();
      currentTransaction.set(transaction);
    }
    return transaction;
  }
}
```

1. BasicChannelSemantics   通过getTransaction()方法来获取当前执行线程的事务;在getTransaction()的时候会调用initialize()方法,这里channel采用了延迟初始化的操作,初始化的操作在开始第一个事务之前才进行初始化操作;
2. getTransaction() 首先会从threadlocal中尝试去获取BasicTransactionSemantics,如果存在并且这个事务开启的,那么将会创建一个新的事务;

### BasicChannelSemantics   

BasicChannelSemantics   只是创建事务, 所有对事务的操作都将交由BasicTransactionSemantics来代理完成;

```java
  public void put(Event event) throws ChannelException {
    BasicTransactionSemantics transaction = currentTransaction.get();
    Preconditions.checkState(transaction != null,
        "No transaction exists for this thread");
    transaction.put(event);
  }
  public Event take() throws ChannelException {
    BasicTransactionSemantics transaction = currentTransaction.get();
    Preconditions.checkState(transaction != null,
        "No transaction exists for this thread");
    return transaction.take();
  }
```

