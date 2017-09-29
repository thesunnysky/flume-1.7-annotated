# Flume NG 学习笔记（三）流配置

![img](http://img.blog.csdn.net/20141023104857062?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvbG9va2xvb2s1/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/Center)

在通过flume采集日志数据的时候，一般都是通过flume 代理从日志源或者日志客户端采集数据到flume代理中，然后再由flume代理送到目标存储.上图中就是每个一级flume代理负责从webserv采集数据,然后再由一个二级flume代理进行日志汇总。

Flume支持从一个源发送事件到多个通道中，这被称为事件流的复用。这里需要在配置中定义事件流的复制/复用，选择1个或者多个通道进行数据流向。

下面的内容主要介绍flume 流配置，这节比较水，因为都比较简单。

## 一、单一代理流配置

下面的配置例子是外部数据源通过avro客户端发送数据到HDFS上。下面无节操的直接拷官网

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40393281#) [copy](http://blog.csdn.net/looklook5/article/details/40393281#)

1. agent_foo.sources= avro-AppSrv-source  
2. agent_foo.sinks= hdfs-Cluster1-sink  
3. agent_foo.channels= mem-channel-1  
4.    
5. \# set channel for sources, sinks  
6.    
7. \# properties of avro-AppSrv-source  
8. agent_foo.sources.avro-AppSrv-source.type= avro  
9. agent_foo.sources.avro-AppSrv-source.bind= localhost  
10. agent_foo.sources.avro-AppSrv-source.port= 10000  
11.    
12. \# properties of mem-channel-1  
13. agent_foo.channels.mem-channel-1.type= memory  
14. agent_foo.channels.mem-channel-1.capacity= 1000  
15. agent_foo.channels.mem-channel-1.transactionCapacity= 100  
16.    
17. \# properties of hdfs-Cluster1-sink  
18. agent_foo.sinks.hdfs-Cluster1-sink.type= hdfs  
19. agent_foo.sinks.hdfs-Cluster1-sink.hdfs.path= hdfs://namenode/flume/webdata  

## 二、单代理多流配置

单代理多流配置是上面的加强版，相当于一个代理两个流，一个是从外部avro客户端到HDFS，另一个是linux命令（tail）的输出到Avro接受代理，2个做成配置。继续无节操的直接拷官网

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40393281#) [copy](http://blog.csdn.net/looklook5/article/details/40393281#)

1. \# list the sources, sinks and channelsin the agent  
2. agent_foo.sources= avro-AppSrv-source1 exec-tail-source2  
3. agent_foo.sinks= hdfs-Cluster1-sink1 avro-forward-sink2  
4. agent_foo.channels= mem-channel-1 file-channel-2  
5.    
6. \# flow #1 configuration  
7. agent_foo.sources.avro-AppSrv-source1.channels= mem-channel-1  
8. agent_foo.sinks.hdfs-Cluster1-sink1.channel= mem-channel-1  
9.    
10. \# flow #2 configuration  
11. agent_foo.sources.exec-tail-source2.channels= file-channel-2  
12. agent_foo.sinks.avro-forward-sink2.channel= file-channel-2  

## 三、配置多代理流程

这个配置就是学习（二）的第二个例子，简单的讲就是数据源发送的事件由第一个Flume代理发送到下一个Flume代理中。下面是官网：

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40393281#) [copy](http://blog.csdn.net/looklook5/article/details/40393281#)

1. \# list sources, sinks and channels inthe agent  
2. agent_foo.sources= avro-AppSrv-source  
3. agent_foo.sinks= avro-forward-sink  
4. agent_foo.channels= file-channel  
5.    
6. \# define the flow  
7. agent_foo.sources.avro-AppSrv-source.channels= file-channel  
8. agent_foo.sinks.avro-forward-sink.channel= file-channel  
9.    
10. \# avro sink properties  
11. agent_foo.sources.avro-forward-sink.type= avro  
12. agent_foo.sources.avro-forward-sink.hostname= 10.1.1.100  
13. agent_foo.sources.avro-forward-sink.port= 10000  
14.    
15. \# configure other pieces  
16. \#...  

例子都不难理解

## 四、多路复用流

Flume支持从一个源到多个通道和sinks,叫做fan out。有两种模式的fan out，复制和复用。复制就是流的事件被发送到所有的配置通道去。

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40393281#) [copy](http://blog.csdn.net/looklook5/article/details/40393281#)

1. \# List the sources, sinks and channelsfor the agent  
2. <Agent>.sources= <Source1>  
3. <Agent>.sinks= <Sink1> <Sink2>  
4. <Agent>.channels= <Channel1> <Channel2>  
5.    
6. \# set list of channels for source(separated by space)  
7. <Agent>.sources.<Source1>.channels= <Channel1> <Channel2>  
8.    
9. \# set channel for sinks  
10. <Agent>.sinks.<Sink1>.channel= <Channel1>  
11. <Agent>.sinks.<Sink2>.channel= <Channel2>  
12.    
13. <Agent>.sources.<Source1>.selector.type= replicating  

其中，<Agent>.sources.<Source1>.selector.type= replicating 这个源的选择类型为复制。这个参数不指定一个选择的时候，

默认情况下它复制

复用则是麻烦一下，流的事情是被筛选的发生到不同的渠道，需要指定源和扇出通道的规则，感觉与case when 类似。

复用的参数为：<Agent>.sources.<Source1>.selector.type = multiplexing

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40393281#) [copy](http://blog.csdn.net/looklook5/article/details/40393281#)

1. \# Mapping for multiplexing selector  
2. <Agent>.sources.<Source1>.selector.type= multiplexing  
3. <Agent>.sources.<Source1>.selector.header= <someHeader>  
4. <Agent>.sources.<Source1>.selector.mapping.<Value1>= <Channel1>  
5. <Agent>.sources.<Source1>.selector.mapping.<Value2>= <Channel1> <Channel2>  
6. <Agent>.sources.<Source1>.selector.mapping.<Value3>= <Channel2>  
7. \#...  
8.    
9. <Agent>.sources.<Source1>.selector.default= <Channel2>  

官网中给出例子，可以看出流的事件要声明一个头部，然后我们检查头部对应的值，这里我们可以认为是事件属性，如果指定的值与设定的通道相匹配，那么就将该事件发送到被匹配到的通道中去。这个参数就是默认通道<Agent>.sources.<Source1>.selector.default =<Channel2>

下面是官网中复用的详细配置例子

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40393281#) [copy](http://blog.csdn.net/looklook5/article/details/40393281#)

1. \# list the sources, sinks and channelsin the agent  
2. agent_foo.sources= avro-AppSrv-source1  
3. agent_foo.sinks= hdfs-Cluster1-sink1 avro-forward-sink2  
4. agent_foo.channels= mem-channel-1 file-channel-2  
5.    
6. \# set channels for source  
7. agent_foo.sources.avro-AppSrv-source1.channels= mem-channel-1 file-channel-2  
8.    
9. \# set channel for sinks  
10. agent_foo.sinks.hdfs-Cluster1-sink1.channel= mem-channel-1  
11. agent_foo.sinks.avro-forward-sink2.channel= file-channel-2  
12.    
13. \# channel selector configuration  
14. agent_foo.sources.avro-AppSrv-source1.selector.type= multiplexing  
15. agent_foo.sources.avro-AppSrv-source1.selector.header= State  
16. agent_foo.sources.avro-AppSrv-source1.selector.mapping.CA= mem-channel-1  
17. agent_foo.sources.avro-AppSrv-source1.selector.mapping.AZ= file-channel-2  
18. agent_foo.sources.avro-AppSrv-source1.selector.mapping.NY= mem-channel-1 file-channel-2  
19. agent_foo.sources.avro-AppSrv-source1.selector.default= mem-channel-1  

上面例子中，设置事件的头属性Header 为“State”作为的选择检查。剩下的就是与case when 基本一样。其中，例子中的配置

agent_foo.sources.avro-AppSrv-source1.selector.mapping.NY= mem-channel-1 file-channel-2 从这里可以看出映射允许每个值通道可以重叠。默认值可以包含任意数量的通道。



原文地址：http://blog.csdn.net/looklook5/article/details/40393281