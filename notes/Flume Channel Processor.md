#Flume Channel Processor

> 搞懂channel processor, channel, 和 channel selector之间的关系;

* channel processor  暴露出了向channel中put Event的操作;
* channel Processor的主要功能时间event(单个的event)/events(list, 批量的操作)放入到对应的channel中,其中channel 可分为required channel 和 optional channel,
*  为了区分 required channel 和 optional channel, channel processor中还实例化了channel selector来确定那些channel是必选的,哪些channel是可选的;
* channel processor 实例化了interceptorChain, channelProcessor的操作中有对interceptorChain的基本操作操作;

### 配置InterceptorChain

```java
  /* 初始化 interceptor chain */
  public void initialize() {
    interceptorChain.initialize();
  }

  /*  关闭 interceptor chain */
  public void close() {
    interceptorChain.close();
  }

  /* channelProcessor的configure方法只configure了自己的InterceptorChain对象 */
  @Override
  public void configure(Context context) {
    configureInterceptors(context);
  }

  // WARNING: throws FlumeException (is that ok?)
  /* 从 context 中配置Interceptor, 入参为 Context 对象 */
  private void configureInterceptors(Context context) {
    List<Interceptor> interceptors = Lists.newLinkedList();
    String interceptorListStr = context.getString("interceptors", "");
    if (interceptorListStr.isEmpty()) {
      return;
    }
    String[] interceptorNames = interceptorListStr.split("\\s+");
    Context interceptorContexts =
        new Context(context.getSubProperties("interceptors."));
    // run through and instantiate all the interceptors specified in the Context
    InterceptorBuilderFactory factory = new InterceptorBuilderFactory();
    for (String interceptorName : interceptorNames) {
      Context interceptorContext = new Context(
          interceptorContexts.getSubProperties(interceptorName + "."));
      String type = interceptorContext.getString("type");
      if (type == null) {
        LOG.error("Type not specified for interceptor " + interceptorName);
        throw new FlumeException("Interceptor.Type not specified for " +
            interceptorName);
      }
      try {
        Interceptor.Builder builder = factory.newInstance(type);
        builder.configure(interceptorContext);
        interceptors.add(builder.build());
      } catch (ClassNotFoundException e) {
        LOG.error("Builder class not found. Exception follows.", e);
        throw new FlumeException("Interceptor.Builder not found.", e);
      } catch (InstantiationException e) {
        LOG.error("Could not instantiate Builder. Exception follows.", e);
        throw new FlumeException("Interceptor.Builder not constructable.", e);
      } catch (IllegalAccessException e) {
        LOG.error("Unable to access Builder. Exception follows.", e);
        throw new FlumeException("Unable to access Interceptor.Builder.", e);
      }
    }
    interceptorChain.setInterceptors(interceptors);
  }
```

### 将Event/Events put到channel中

```java
# 将 events(list) 批量的放入到channel中
public void processEventBatch(List<Event> events);
# 将单个 events 的放入到channel中
public void processEvent(Event event);
```

在将event放入到channel中时, channel的属性可分为required channel和 optional channel, 通过channel processor 中实例化的channel Selector来区分;

