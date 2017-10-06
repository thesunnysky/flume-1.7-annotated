<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# 写在前面

这份代码是Apache Flume-NG v1.7的代码,自己想要读一下Flume的代码就fork了一份,同时在读的过程中就直接在代码里添加注释,感觉直接在代码里加注释,想到什么就写下什么是一种很Nice的做法,能够增加自己代码阅读乐趣,另外还有一些自己从网上搜集的资料和自己的一些总结的文档放在了notes文件件下.

不得不说的是这仅仅是自己在阅读的过程中个人对代码的理解,里面的严谨和缜密肯定有很多做的不到位,对代码的理解也未必是准确到位的,如果发现有任何的纰漏的地方,欢迎指出,谢谢.

本人邮箱:jianyungsun@gmail.com



# Welcome to Apache Flume!

Apache Flume is a distributed, reliable, and available service for efficiently
collecting, aggregating, and moving large amounts of log data. It has a simple
and flexible architecture based on streaming data flows. It is robust and fault
tolerant with tunable reliability mechanisms and many failover and recovery
mechanisms. The system is centrally managed and allows for intelligent dynamic
management. It uses a simple extensible data model that allows for online
analytic application.

The Apache Flume 1.x (NG) code line is a refactoring of the first generation
Flume to solve certain known issues and limitations of the original design.

Apache Flume is open-sourced under the Apache Software Foundation License v2.0.

## Documentation

Documentation is included in the binary distribution under the docs directory.
In source form, it can be found in the flume-ng-doc directory.

The Flume 1.x guide and FAQ are available here:

* https://cwiki.apache.org/FLUME
* https://cwiki.apache.org/confluence/display/FLUME/Getting+Started

## Contact us!

* Mailing lists: https://cwiki.apache.org/confluence/display/FLUME/Mailing+Lists
* IRC channel #flume on irc.freenode.net

Bug and Issue tracker.

* https://issues.apache.org/jira/browse/FLUME

## Compiling Flume

Compiling Flume requires the following tools:

* Oracle Java JDK 1.7
* Apache Maven 3.x

Note: The Apache Flume build requires more memory than the default configuration.
We recommend you set the following Maven options:

export MAVEN_OPTS="-Xms512m -Xmx1024m -XX:PermSize=256m -XX:MaxPermSize=512m"

To compile Flume and build a distribution tarball, run `mvn install` from the
top level directory. The artifacts will be placed under `flume-ng-dist/target/`.
