/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.flume.channel;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.FlumeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiplexingChannelSelector extends AbstractChannelSelector {

  public static final String CONFIG_MULTIPLEX_HEADER_NAME = "header";
  public static final String DEFAULT_MULTIPLEX_HEADER =
      "flume.selector.header";
  public static final String CONFIG_PREFIX_MAPPING = "mapping.";
  public static final String CONFIG_DEFAULT_CHANNEL = "default";
  public static final String CONFIG_PREFIX_OPTIONAL = "optional";

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(MultiplexingChannelSelector.class);

  private static final List<Channel> EMPTY_LIST =
      Collections.emptyList();

  private String headerName;

  //配置文件中明确了配置了Mapping规则的channel
  private Map<String, List<Channel>> channelMapping;
  //配置文件中配置为optional的channel
  private Map<String, List<Channel>> optionalChannels;
  //配置文件中配置为default的channel
  private List<Channel> defaultChannels;

  @Override
  public List<Channel> getRequiredChannels(Event event) {
    String headerValue = event.getHeaders().get(headerName);
    if (headerValue == null || headerValue.trim().length() == 0) {
      return defaultChannels;
    }

    List<Channel> channels = channelMapping.get(headerValue);

    //This header value does not point to anything
    //Return default channel(s) here.
    if (channels == null) {
      channels = defaultChannels;
    }

    return channels;
  }

  @Override
  public List<Channel> getOptionalChannels(Event event) {
    String hdr = event.getHeaders().get(headerName);
    List<Channel> channels = optionalChannels.get(hdr);

    if (channels == null) {
      channels = EMPTY_LIST;
    }
    return channels;
  }

  /*
   * context 中的内容到底包括什么?
   * context 中是如何定义required channel 和 optional channel 的?
   * 还有就是下面的这段代码看的并不是很懂
   */
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
      //检查如果header对应的必选Channel列表为空，那么default就作为它的必选Channel
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

}
