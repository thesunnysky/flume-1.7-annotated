# SinkProcessor

下面是官方配置：

| **Property Name**  | **Default** | **Description**                          |
| ------------------ | ----------- | ---------------------------------------- |
| **sinks**          | –           | Space-separated list of sinks that are participating in the group |
| **processor.type** | default     | The component type name, needs to be default, failover or load_balance |

从参数类型上可以看出有3种Processors类型：default, failover（故障转移）和 load_balance（负载均衡），当然，官网上说目前自定义processors 还不支持。

下面是官网例子

a1.sinkgroups**=**g1

a1.sinkgroups.g1.sinks**=**k1 k2

a1.sinkgroups.g1.processor.type**=**load_balance

 

## 一、Default Sink Processor

DefaultSink Processor 接收单一的Sink，不强制用户为Sink创建Processor，前面举了很多例子。所以这个就不多说了。

 

## 二、Failover Sink Processor（故障转移）

​         FailoverSink Processor会通过配置维护了一个优先级列表。保证每一个有效的事件都会被处理。

​         故障转移的工作原理是将连续失败sink分配到一个池中，在那里被分配一个冷冻期，在这个冷冻期里，这个sink不会做任何事。一旦sink成功发送一个event，sink将被还原到live 池中。

​         在这配置中，要设置sinkgroups processor为failover，需要为所有的sink分配优先级，所有的优先级数字必须是唯一的，这个得格外注意。此外，failover time的上限可以通过maxpenalty 属性来进行设置。

下面是官网配置：

| **Property Name**                 | **Default** | **Description**                          |
| --------------------------------- | ----------- | ---------------------------------------- |
| **sinks**                         | –           | Space-separated list of sinks that are participating in the group |
| **processor.type**                | default     | The component type name, needs to be failover |
| **processor.priority.<sinkName>** | –           | <sinkName> must be one of the sink instances associated with the current sink group |
| processor.maxpenalty              | 30000       | (in millis)                              |

下面是官网例子

a1.sinkgroups**=**g1

a1.sinkgroups.g1.sinks**=**k1 k2

a1.sinkgroups.g1.processor.type**=**failover

a1.sinkgroups.g1.processor.priority.k1**=**5

a1.sinkgroups.g1.processor.priority.k2**=**10

a1.sinkgroups.g1.processor.maxpenalty**=**10000

这里首先要申明一个sinkgroups,然后再设置2个sink ,k1与k2,其中2个优先级是5和10，而processor的maxpenalty被设置为10秒，默认是30秒。‘

下面是测试例子

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40583815#) [copy](http://blog.csdn.net/looklook5/article/details/40583815#)

1. \#配置文件：failover_sink_case13.conf  
2. \#Name the components on this agent  
3. a1.sources= r1  
4. a1.sinks= k1 k2  
5. a1.channels= c1 c2  
6.    
7. a1.sinkgroups= g1  
8. a1.sinkgroups.g1.sinks= k1 k2  
9. a1.sinkgroups.g1.processor.type= failover  
10. a1.sinkgroups.g1.processor.priority.k1= 5  
11. a1.sinkgroups.g1.processor.priority.k2= 10  
12. a1.sinkgroups.g1.processor.maxpenalty= 10000  
13.    
14. \#Describe/configure the source  
15. a1.sources.r1.type= syslogtcp  
16. a1.sources.r1.port= 50000  
17. a1.sources.r1.host= 192.168.233.128  
18. a1.sources.r1.channels= c1 c2  
19.    
20. \#Describe the sink  
21. a1.sinks.k1.type= avro  
22. a1.sinks.k1.channel= c1  
23. a1.sinks.k1.hostname= 192.168.233.129  
24. a1.sinks.k1.port= 50000  
25.    
26. a1.sinks.k2.type= avro  
27. a1.sinks.k2.channel= c2  
28. a1.sinks.k2.hostname= 192.168.233.130  
29. a1.sinks.k2.port= 50000  
30. \# Usea channel which buffers events in memory  
31. a1.channels.c1.type= memory  
32. a1.channels.c1.capacity= 1000  
33. a1.channels.c1.transactionCapacity= 100  

这里设置了2个channels与2个sinks ，关于故障转移的设置直接复制官网的例子。我们还要配置2个sinks对于的代理。这里的2个接受代理我们沿用之前第六章复制的2个sink代理配置。

下面是第一个接受复制事件代理配置

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40583815#) [copy](http://blog.csdn.net/looklook5/article/details/40583815#)

1. \#配置文件：replicate_sink1_case11.conf  
2. \# Name the components on this agent  
3. a2.sources = r1  
4. a2.sinks = k1  
5. a2.channels = c1  
6.    
7. \# Describe/configure the source  
8. a2.sources.r1.type = avro  
9. a2.sources.r1.channels = c1  
10. a2.sources.r1.bind = 192.168.233.129  
11. a2.sources.r1.port = 50000  
12.    
13. \# Describe the sink  
14. a2.sinks.k1.type = logger  
15. a2.sinks.k1.channel = c1  
16.    
17. \# Use a channel which buffers events inmemory  
18. a2.channels.c1.type = memory  
19. a2.channels.c1.capacity = 1000  
20. a2.channels.c1.transactionCapacity = 100  

下面是第二个接受复制事件代理配置：

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40583815#) [copy](http://blog.csdn.net/looklook5/article/details/40583815#)

1. \#配置文件：replicate_sink2_case11.conf  
2. \# Name the components on this agent  
3. a3.sources = r1  
4. a3.sinks = k1  
5. a3.channels = c1  
6.    
7. \# Describe/configure the source  
8. a3.sources.r1.type = avro  
9. a3.sources.r1.channels = c1  
10. a3.sources.r1.bind = 192.168.233.130  
11. a3.sources.r1.port = 50000  
12.    
13. \# Describe the sink  
14. a3.sinks.k1.type = logger  
15. a3.sinks.k1.channel = c1  
16.    
17. \# Use a channel which buffers events inmemory  
18. a3.channels.c1.type = memory  
19. a3.channels.c1.capacity = 1000  
20. a3.channels.c1.transactionCapacity = 100  

**#敲命令**

首先先启动2个接受复制事件代理，如果先启动源发送的代理，会报他找不到sinks的绑定，因为2个接事件的代理还未起来。

flume-ng agent -cconf -f conf/replicate_sink1_case11.conf -n a1 -Dflume.root.logger=INFO,console

flume-ng agent -cconf -f conf/replicate_sink2_case11.conf -n a1 -Dflume.root.logger=INFO,console

在启动源发送的代理

flume-ng agent -cconf -f conf/failover_sink_case13.conf -n a1 -Dflume.root.logger=INFO,console

启动成功后

打开另一个终端输入，往侦听端口送数据

echo "hello failoversink" | nc 192.168.233.128 50000

**#在启动源发送的代理终端查看console输出**

因为k1的优先级是5，K2是10因此当K2正常运行的时候，是发送到K2的。下面数据正常输出。

![img](http://img.blog.csdn.net/20141029113049250?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvbG9va2xvb2s1/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/Center)

然后我们中断K2的代理进程。

再尝试往侦听端口送数据

echo "hello close k2"| nc 192.168.233.128 50000

我们发现源代理发生事件到K2失败，然后他将K2放入到failover list（故障列表）

因为K1还是正常运行的，因此这个时候他会接收到数据。

然后我们再打开K2的大理进程，我们继续往侦听端口送数据

echo " hello open k2 again" | nc192.168.233.128 50000

数据正常发生，Failover SinkProcessor测试完毕。

## 三、Load balancing SinkProcessor

负载均衡片处理器提供在多个Sink之间负载平衡的能力。实现支持通过round_robin（轮询）或者random（随机）参数来实现负载分发，默认情况下使用round_robin，但可以通过配置覆盖这个默认值。还可以通过集成AbstractSinkSelector类来实现用户自己的选择机制。

当被调用的时候，这选择器通过配置的选择规则选择下一个sink来调用。

下面是官网配置

 

| **Property Name**             | **Default** | **Description**                          |
| ----------------------------- | ----------- | ---------------------------------------- |
| **processor.sinks**           | –           | Space-separated list of sinks that are participating in the group |
| **processor.type**            | default     | The component type name, needs to be load_balance |
| processor.backoff             | false       | Should failed sinks be backed off exponentially. |
| processor.selector            | round_robin | Selection mechanism. Must be either round_robin, random or FQCN of custom class that inherits from AbstractSinkSelector |
| processor.selector.maxTimeOut | 30000       | Used by backoff selectors to limit exponential backoff (in milliseconds) |

下面是官网的例子

a1.sinkgroups**=**g1

a1.sinkgroups.g1.sinks**=**k1 k2

a1.sinkgroups.g1.processor.type**=**load_balance

a1.sinkgroups.g1.processor.backoff**=**true

a1.sinkgroups.g1.processor.selector**=**random

这个与故障转移的设置差不多。

下面是测试例子

**[html]** [view plain](http://blog.csdn.net/looklook5/article/details/40583815#) [copy](http://blog.csdn.net/looklook5/article/details/40583815#)

1. \#配置文件：load_sink_case14.conf  
2. \# Name the components on this agent  
3. a1.sources = r1  
4. a1.sinks = k1 k2  
5. a1.channels = c1  
6.    
7. a1.sinkgroups = g1  
8. a1.sinkgroups.g1.sinks = k1 k2  
9. a1.sinkgroups.g1.processor.type =load_balance  
10. a1.sinkgroups.g1.processor.backoff = true  
11. a1.sinkgroups.g1.processor.selector =round_robin  
12.    
13. \# Describe/configure the source  
14. a1.sources.r1.type = syslogtcp  
15. a1.sources.r1.port = 50000  
16. a1.sources.r1.host = 192.168.233.128  
17. a1.sources.r1.channels = c1  
18.    
19. \# Describe the sink  
20. a1.sinks.k1.type = avro  
21. a1.sinks.k1.channel = c1  
22. a1.sinks.k1.hostname = 192.168.233.129  
23. a1.sinks.k1.port = 50000  
24.    
25. a1.sinks.k2.type = avro  
26. a1.sinks.k2.channel = c1  
27. a1.sinks.k2.hostname = 192.168.233.130  
28. a1.sinks.k2.port = 50000  
29. \# Use a channel which buffers events inmemory  
30. a1.channels.c1.type = memory  
31. a1.channels.c1.capacity = 1000  
32. a1.channels.c1.transactionCapacity = 100  

这里要说明的是，因此测试的是负载均衡的例子，因此这边使用一个channel来作为数据传输通道。这里sinks的对应的接收数据的代理配置，我们沿用故障转移的接收代理配置。

**#敲命令**

首先先启动2个接受复制事件代理，如果先启动源发送的代理，会报他找不到sinks的绑定，因为2个接事件的代理还未起来。

flume-ng agent -cconf -f conf/replicate_sink1_case11.conf -n a1

-Dflume.root.logger=INFO,console

flume-ng agent -cconf -f conf/replicate_sink2_case11.conf -n a1

-Dflume.root.logger=INFO,console

在启动源发送的代理

flume-ng agent -cconf -f conf/load_sink_case14.conf -n a1

-Dflume.root.logger=INFO,console

 

启动成功后

 

打开另一个终端输入，往侦听端口送数据

echo "loadbanlancetest1" | nc 192.168.233.128 50000

echo "loadbantest2" | nc 192.168.233.128 50000

echo "loadban test3"| nc 192.168.233.128 50000

echo "loadbantest4" | nc 192.168.233.128 50000

echo "loadbantest5" | nc 192.168.233.128 50000

**#在启动源发送的代理终端查看console输出**

其中K1收到3条数据

其中K1收到2条数据

因为我们负载均衡选择的类型是轮询，因此可以看出flume 让代理每次向一个sink发送2次事件数据后就换另一个sinks 发送。

Sink Processors测试完毕



原文地址：http://blog.csdn.net/looklook5/article/details/40583815