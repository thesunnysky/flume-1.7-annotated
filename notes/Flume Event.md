# Flume Event

Event是Flume中的一个基本概念,可以认为,Flume Channel中数据的传输是以event为单位来传输的;

同时为了拓展flume的特性,比如说flume支持channel selector,  Interceptor,等操作,在event中有增加了header字段,通过对header字段的不同的配置,可是使flume实现更多更灵活的配置;

###Event interface

flume 提供的event的 interface,所有类型的event都需要implements 这个接口;

从代码可以看出event主要有一下两部分数据组成:

* event的header 是一个Map<String, String> ;
* body 是一个byte数组;

```java
public interface Event {
  public Map<String, String> getHeaders();
  public void setHeaders(Map<String, String> headers);
  public byte[] getBody();
  public void setBody(byte[] body);
}
```

