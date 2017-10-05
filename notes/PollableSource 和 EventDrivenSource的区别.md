#PollableSource 和 EventDrivenSource的区别

以下这段话来自Quora上的回答:

```
When a source is event-driven, it acts as a push source. In other words, it controls how and when events are added to the underlying channel. The most obvious example of an event-driven source is the Avro source. When it receives an RPC with one or more events, it immediately adds them to the channel. The initiator of the event is the RPC client. Event-driven sources are generally preferable in that they control the rate at which events are added to channels. This affords much greater control over concurrency and latency.

Pollable sources, on the other hand, are "passive." They have no way of knowing when new data arrives, so Flume has to poll them at a regular interval. The sequence source (generates a monotonically incrementing integer for testing) is a pollable source. Pollable sources are easier to write, but provide far less control. They basically look like iterators with a process() method (that acts like Iterator#next(), give or take). Specifically, a pollable source is always singly threaded (i.e. there's only one polling thread).

The shorter, and slightly more technical answer, is that an event-driven source has no polling thread where as the pollable source does.
```

原文地址:https://www.quora.com/What-is-the-difference-between-Apache-Flume-event-driven-and-pollable-source