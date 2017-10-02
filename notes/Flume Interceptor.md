#Flume Interceptor 介绍

有的时候希望通过Flume将读取的文件再细分存储，比如讲source的数据按照业务类型分开存储，具体一点比如类似：将source中web、wap、media等的内容分开存储；比如丢弃或修改一些数据。这时可以考虑使用拦截器Interceptor。

　　flume通过拦截器实现修改和丢弃事件的功能。拦截器通过定义类继承org.apache.flume.interceptor.Interceptor接口来实现。用户可以通过该节点定义规则来修改或者丢弃事件。Flume支持链式拦截，通过在配置中指定构建的拦截器类的名称。在source的配置中，拦截器被指定为一个以空格为间隔的列表。拦截器按照指定的顺序调用。一个拦截器返回的事件列表被传递到链中的下一个拦截器。当一个拦截器要丢弃某些事件时，拦截器只需要在返回事件列表时不返回该事件即可。若拦截器要丢弃所有事件，则其返回一个空的事件列表即可。

　　先解释一下一个重要对象Event：event是flume传输的最小对象，从source获取数据后会先封装成event，然后将event发送到channel，sink从channel拿event消费。event由头(Map<String, String> headers)和身体(body)两部分组成：Headers部分是一个map，body部分可以是String或者byte[]等。其中body部分是真正存放数据的地方，headers部分用于本节所讲的interceptor。

> Flume中拦截器的作用就是对于event中header的部分可以按需塞入一些属性，当然你如果想要处理event的body内容，也是可以的，但是event的body内容是系统下游阶段真正处理的内容，如果让Flume来修饰body的内容的话，那就是强耦合了，这就违背了当初使用Flume来解耦的初衷了。

### 1.初识拦截器接口

```
public interface Interceptor {
  public void initialize();
  public Event intercept(Event event);
  public List<Event> intercept(List<Event> events);
  public void close();
  public interface Builder extends Configurable {
    public Interceptor build();
  }
}
```

1. public void initialize()运行前的初始化，一般不需要实现（上面的几个都没实现这个方法）；

2. public Event intercept(Event event)处理单个event；

3. public List<Event> intercept(List<Event> events)批量处理event，实际上市循环调用上面的2；

   在 InterceptorChain 类总有体现

4. public void close()可以做一些清理工作，上面几个也都没有实现这个方法；

5. public interface Builder extends Configurable 构建Interceptor对象，外部使用这个Builder来获取Interceptor对象。

**　　如果要自己定制，必须要完成上面的2,3,5。**

这个地方你可能有个很眼熟Configurable   这个拦截器的接口也继承了配置的接口，因为很多东西都是从配置读取出来的，所以你自己开发的之后上来一定脑子里面要有一个必须要实现的方法，即便说你不需要读取配置，你也给我写上！

### 2.TimestampInterceptor拦截器示例分析

#### 1.initialize(）方法解析

```
 @Override
  public void initialize() {
    // no-op
  }
```

这个地方官方的几个拦截器都没有实现，我也没实现过。

#### 2.close()方法解析

```
 @Override
  public void close() {
    // no-op
  }
```

这个地方是一个关闭的方法。

#### 3.Event intercept(Event event) 方法解析

```
    Map<String, String> headers = event.getHeaders();
    if (preserveExisting && headers.containsKey(TIMESTAMP)) {
      // we must preserve the existing timestamp
    } else {
      long now = System.currentTimeMillis();
      headers.put(TIMESTAMP, Long.toString(now));
    }
    return event;
```

简单的循环调用了intercept对event逐一处理

#### 4.public List<Event> intercept(List<Event> events)方法解析

```
    for (Event event : events) {
      intercept(event);
    }
    return events;
```

批量的处理event，和上面的3相结合处理。

#### 5.TimestampInterceptor的具体实现

```
  public static class Builder implements Interceptor.Builder {

    private boolean preserveExisting = PRESERVE_DFLT;

    @Override
    public Interceptor build() {
      return new TimestampInterceptor(preserveExisting);
    }

    @Override
    public void configure(Context context) {
      preserveExisting = context.getBoolean(PRESERVE, PRESERVE_DFLT);
    }
  }
```

该内部类实现了Interceptor的接口Builder，必须得有一个无参的构造方法，通过该构造方法就实例化了一个拦截器对象

#### 6.该方法即拦截器的核心内容

1、如果拿到的event的header中本身包括timestamp这个key并且预留保存属性为true，我们就直接返回该event就行了。

2、否则的话，我们生成一个时间戳，并将这个时间戳放到event的header中，作为一个属性保存，再返回给event。

### 拦截器总结

1.拦截器被指定为一个以空格为间隔的列表，拦截器按照指定的顺序调用。

2.核心是返回一个拦截器对象。

3.实现自己的event处理机制。

### 自己写的event打包拦截器

```
public class EventCompressor extends AbstractFlumeInterceptor {
    //static final String COMPRESS_FORMAT = "gzip";

    @Override
    public void initialize() {
        // NOPE
    }

    @Override
    public Event intercept(Event event) {
        Map<String, String> headers = event.getHeaders();
        
        byte[] body = Compressor.compress(event.getBody());
        
        //headers添加是否打包标志
        
        headers.put(HeaderConstants.DEF_COMPRESS, HeaderConstants.VAL_COMPRESS_GZIP);
        event.setBody(body);
        return event;
    }
    @Override
    public void close() {
        // NOPE
    }

    public static class Builder implements Interceptor.Builder {
        @Override
        public void configure(Context context) {
            // NOPE
        }

        @Override
        public Interceptor build() {
            return new EventCompressor();
        }
    }
```

[![复制代码](http://common.cnblogs.com/images/copycode.gif)](javascript:void(0);)

因为flume 的数据采集到发送到kafka，如果一次一条数据的话很小，因此我把body取出来打包成大约40k左右的包来发送，还有一点kafka官方给出的一条消息大小为10k的时候kafka吞吐量达到最大效果。

原文地址:http://www.cnblogs.com/chushiyaoyue/p/6233825.html