# Flume Memory Channel

下图是Memory的继承关系图,其中主要的组成部分是:

* NamedComponent: 

  基础组件,将Component和某个Name绑定起来;

* LifecycleAware

   基础组件, 用于component的生命周期管理;

* Configurable

   基础组件,所以实现configurable接口的component都是可配置的,都会有一个context阈值相对应;


* Channel: 

  接口,定义了,channel的基本操作:put(), take(), getTransanction();

* AbstractChannel

  接口的abstract类,定了了channel的一下基本的方法;

* BasicChannelSemantics

  ​

![捕获](G:\snap\捕获.PNG)

