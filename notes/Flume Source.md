# Flume Source

### 1.1 source interface

```
public interface Source extends LifecycleAware, NamedComponent {
  public void setChannelProcessor(ChannelProcessor channelProcessor);
  public ChannelProcessor getChannelProcessor();
}
```

 	1. 从代码可以看出source也实现了LifecycleAware和NamedComponent接口，实现了对生命周期的管理和名字的绑定；
 	2. setChannelProcessor设置这个source的channelProcessor，然后source将会调用channel的方法将他产生的event放进channel中，该channel将会处理由该source产生的events；

### 1.2 SourceFactory 

```
public interface SourceFactory {
  Source create(String sourceName, String type)
      throws FlumeException;
  Class<? extends Source> getClass(String type)
      throws FlumeException;
}
```

1. SourceFactory是产生source的工厂方法接口，channel，source，sink都有与之对应的工行方法接口，通过实现该接口可以得到创建这些对象的工厂类；
2. flume-ng-core中只有`DefaultSourceFactory`实现了该接口；

### 1.3 SourceRunner

```
  public static SourceRunner forSource(Source source) {
    SourceRunner runner = null;
    
    if (source instanceof PollableSource) {
      runner = new PollableSourceRunner();
      ((PollableSourceRunner) runner).setSource((PollableSource) source);
    } else if (source instanceof EventDrivenSource) {
      runner = new EventDrivenSourceRunner();
      ((EventDrivenSourceRunner) runner).setSource((EventDrivenSource) source);
    } else {
      throw new IllegalArgumentException("No known runner type for source " + source);
    }
    return runner;
  }
  public Source getSource() {
    return source;
  }
  public void setSource(Source source) {
    this.source = source;
  }
}
```

-> sourcerunner的所用

1. 代码的注释中提到：

   > ```
   > A source runner controls how a source is driven.
   > This is an abstract class used for instantiating derived classes.
   > ```

2. forSource() 是一个静态方法，用来生成对应类型的source的runner；