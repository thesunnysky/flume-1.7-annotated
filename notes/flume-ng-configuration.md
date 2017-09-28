# flume-ng-configuration

该model主要提供了flume的配置类



## ComponentConfigurationFactory

该类提供了创建各种类型组件配置类的工厂方法

提供了static 方法，返回的是ComponentConfiguration类

```java
public static ComponentConfiguration create(String name, String type, ComponentType component) throws ConfigurationException {...}
//name 是组件的名称
// type 是组件的具体类型；
// Component指示的时候具体那种的组件类
通过进一步的分析可知：
ComponentType：包含：
    OTHER(null),
    SOURCE("Source"),
    SINK("Sink"),
    SINK_PROCESSOR("SinkProcessor"),
    SINKGROUP("Sinkgroup"),
    CHANNEL("Channel"),
    CHANNELSELECTOR("ChannelSelector");
第二个参数type就是具体每种ComponentType下的某种具体的组件，以SINK为例，
	LOGGER，FILE_ROLL,HDFS,IRC,AVRO等类型
通过flume的项目主页上的介绍可以看到，flume的source，sink和channel都有很多具体的细分类型；
```





## FlumeConfiguration 

这个类是flume-ng-configuration核心的类， 该类验证了各个channels，sinks，sources等合法性和之间的检查三者之间的连接关系，用来判断给出的配置是否是合法的；