# Flume Channel Selector

Channel Selectors:Channel选择器

主要作用：对于一个source发往多个channel的策略设置

Channel Selectors类型：Replicating Channel Selector (default)、Multiplexing Channel Selector

其中：

* Replicating类型的ChannelSelector会针对每一个Event，拷贝到该source配置的所有的Channel中，这是默认的ChannelSelector。
* Multiplexing selector会根据event中某个header对应的value来将event发往不同的channel（header与value就是KV结构）。

**Multiplexing Channel Selector**

Event里面的header类型：Map<String, String>

如果header中有一个key:state,我们在不同的数据源设置不同的stat则通过设置这个key的值来分配不同的channel

a1.sources.r1.channels = c1 c2

a1.sources.r1.selector.type = multiplexing

a1.sources.r1.selector.header = state

a1.sources.r1.selector.mapping.CZ = c1

a1.sources.r1.selector.mapping.US = c2 c3

a1.sources.r1.selector.default = c4

如果source的header的key的值等于state，source的header的value的值等于CZ或者US或者其他

如果等于CZ就发给c1，如果等于US就发给c2和c3，其他情况发给c4

比如source里header有datacenter = NEW_YORK

a1.sources.r1.selector.header = **datacenter**

a1.sources.r1.selector.mapping.china = c1

a1.sources.r1.selector.mapping. **NEW_YORK** = c2 c3

那只发给c2和c3

Channel Selectors:Channel选择器

主要作用：对于一个source发往多个channel的策略设置

Channel Selectors类型：Replicating Channel Selector (default)、Multiplexing Channel Selector

其中：Replicating 会将source过来的events发往所有channel,

Multiplexing selector会根据event中某个header对应的value来将event发往不同的channel（header与value就是KV结构）。

## flume-ng-channel selector

Flume中channel选择器（selector.type配置）必须实现ChannelSelector接口，实现了该接口的类主要作用是告诉Source中接收到的Event应该发送到哪些Channel;

```java
public interface ChannelSelector extends NamedComponent, Configurable {
  public void setChannels(List<Channel> channels);
  # 根绝入参event 获取需要将该event放入到的required channel
  public List<Channel> getRequiredChannels(Event event);
  # 根绝入参event 获取需要将该event放入到的optional channel
  public List<Channel> getOptionalChannels(Event event);
  public List<Channel> getAllChannels();
}
```

同时flume中的channel selector主要可分为两类,对应的功能和区别在最开始的地方已经做了介绍:

* MultiplexingChannelSelector

  Multiplexing selector会根据event中某个header对应的value来将event发往不同的channel（header与value就是KV结构）。

* ReplicatingChannelSelector

  Replicating类型的ChannelSelector会针对每一个Event，拷贝到该source配置的所有的Channel中，这是默认的ChannelSelector。

###ChannelSelector接口两个主要的方法是：

1. //获取必选的Channel列表  

   ```
   public List<Channel> getRequiredChannels(Event event);  
   ```

2. //获取可选的Channel列表  

   ```
   public List<Channel> getOptionalChannels(Event event);  
   ```



###ReplicatingChannelSelector (所有Channel默认的方式)

| 属性名               | 默认          | 描述                |
| ----------------- | ----------- | ----------------- |
| selector.type     | replicating | 组件名：`replicating` |
| selector.optional | –           | 标记哪些Channels是可选的  |

以下例子将c3标记为可选，写入c3失败的话会被忽略，如果写入c1和c2失败的话，这个事务就会失败:

```
a1.sources = r1  
a1.channels = c1 c2 c3  
a1.source.r1.selector.type = replicating  
a1.source.r1.channels = c1 c2 c3  
a1.source.r1.selector.optional = c3  
```

```java
public void configure(Context context) {  
    //获取哪些Channel标记为可选  
    String optionalList = context.getString(CONFIG_OPTIONAL);  
    //将所有Channel都方法必须的Channel列表中  
    requiredChannels = new ArrayList<Channel>(getAllChannels());  
    Map<String, Channel> channelNameMap = getChannelNameMap();  
    if(optionalList != null && !optionalList.isEmpty()) {  
      //下面的操作：如果channel属于可选的，则加入可选的列表中，并从必选的列表中删除  
      for(String optional : optionalList.split("\\s+")) {  
        Channel optionalChannel = channelNameMap.get(optional);  
        requiredChannels.remove(optionalChannel);  
        if (!optionalChannels.contains(optionalChannel)) {  
          //将optional channel 中没有的channel 加入到 optionalChannel中
          optionalChannels.add(optionalChannel);  
        }  
      }  
    }  
  }  
```



### MultiplexingChannelSelector

| 属性名                | 默认                    | Description          |
| ------------------ | --------------------- | -------------------- |
| selector.type      | replicating           | 组件名：`multiplexing`   |
| selector.optional  | –                     | 标记哪些Channels是可选的     |
| selector.header    | flume.selector.header |                      |
| selector.default   | –                     | 默认的channel,如果mapping |
| selector.mapping.* | –                     |                      |

示例:

```
a1.sources = r1  
a1.channels = c1 c2 c3 c4 c5
a1.sources.r1.selector.type = multiplexing  
a1.sources.r1.selector.header = state  
a1.sources.r1.selector.mapping.CZ = c1 
a1.sources.r1.selector.mapping.US = c2 c3  
a1.sources.r1.selector.optional = c5
a1.sources.r1.selector.default = c4  
根据header中key为state的值，决定将数据写入那个channel中，如上示例将state=CZ写入到c1中，将state=US写入到c2，c3中，默认情况下写入c4,
```

MultiplexingChannelSelector的初始化过程：

flume 在configure的时候会一次配置selector的required, optional channel,配置好了就可以轻而易举的获取channel了.

```java
@Override
  public void configure(Context context) {
    this.headerName = context.getString(CONFIG_MULTIPLEX_HEADER_NAME,
        DEFAULT_MULTIPLEX_HEADER);

    //获取所有channel的 name:Channel的 map
    Map<String, Channel> channelNameMap = getChannelNameMap();

    //获取default channel
    defaultChannels = getChannelListFromNames(
        context.getString(CONFIG_DEFAULT_CHANNEL), channelNameMap);

    /* 获取Mapping的值
     * 个人理解是先获取所有配置文件中配置的所有的mapping的值(有待进一步验证)
     * 搞定这个mapping的具体内容是理解下面代码前提
     */
    Map<String, String> mapConfig =
        context.getSubProperties(CONFIG_PREFIX_MAPPING);
    //channelMapping变量存放了header变量中必须的Channel列表
    channelMapping = new HashMap<String, List<Channel>>();

    //填充channelMapping
    for (String headerValue : mapConfig.keySet()) {
      //? 应该是配置文件中配置的 channel
      List<Channel> configuredChannels = getChannelListFromNames(
          mapConfig.get(headerValue),
          channelNameMap);

      //This should not go to default channel(s)
      //because this seems to be a bad way to configure.
      if (configuredChannels.size() == 0) {
        throw new FlumeException("No channel configured for when "
            + "header value is: " + headerValue);
      }

      if (channelMapping.put(headerValue, configuredChannels) != null) {
        throw new FlumeException("Selector channel configured twice");
      }
    }
    //If no mapping is configured, it is ok.
    //All events will go to the default channel(s).
    //? 从配置文件获取配置为optional 的channel?
    Map<String, String> optionalChannelsMapping =
        context.getSubProperties(CONFIG_PREFIX_OPTIONAL + ".");

    //填充optional channel
    optionalChannels = new HashMap<String, List<Channel>>();
    for (String hdr : optionalChannelsMapping.keySet()) {
      List<Channel> confChannels = getChannelListFromNames(
              optionalChannelsMapping.get(hdr), channelNameMap);
      if (confChannels.isEmpty()) {
        confChannels = EMPTY_LIST;
      }

      //Remove channels from optional channels, which are already
      //configured to be required channels.

      //从optional channel中移除已经配置为required channel 的channel
      //这样做是不是为了排除配置文件配错时造成同一个channel 同时出现在mapping字段和optional字段的情况 ?
      List<Channel> reqdChannels = channelMapping.get(hdr);

      //Check if there are required channels, else defaults to default channels
      //如果header对应的必选Channel列表为空，那么default就作为它的必选Channel
      if (reqdChannels == null || reqdChannels.isEmpty()) {
        reqdChannels = defaultChannels;
      }
      //如果header对应的Channel是必选的，那么就在可选的列表中删除。
      for (Channel c : reqdChannels) {
        if (confChannels.contains(c)) {
          confChannels.remove(c);
        }
      }

      if (optionalChannels.put(hdr, confChannels) != null) {
        throw new FlumeException("Selector channel configured twice");
      }
    }
  }
```

