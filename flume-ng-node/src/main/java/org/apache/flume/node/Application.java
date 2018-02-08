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

package org.apache.flume.node;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.flume.Channel;
import org.apache.flume.Constants;
import org.apache.flume.Context;
import org.apache.flume.SinkRunner;
import org.apache.flume.SourceRunner;
import org.apache.flume.instrumentation.MonitorService;
import org.apache.flume.instrumentation.MonitoringType;
import org.apache.flume.lifecycle.LifecycleAware;
import org.apache.flume.lifecycle.LifecycleState;
import org.apache.flume.lifecycle.LifecycleSupervisor;
import org.apache.flume.lifecycle.LifecycleSupervisor.SupervisorPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

public class Application {

  private static final Logger logger = LoggerFactory
      .getLogger(Application.class);

  public static final String CONF_MONITOR_CLASS = "flume.monitoring.type";
  public static final String CONF_MONITOR_PREFIX = "flume.monitoring.";

  /**
   * 用来管理所有的组件，例如：source，sink，channel
   */
  private final List<LifecycleAware> components;
  /**
   * 核心是通过一个 ScheduledThreadPoolExecutor 实现的一个线程池
   * 来管理Application 各个组件的生命周期
   * 这个类是start和stop flume component的关键
   */
  private final LifecycleSupervisor supervisor;
  private MaterializedConfiguration materializedConfiguration;
  private MonitorService monitorServer;

  public Application() {
    this(new ArrayList<LifecycleAware>(0));
  }

  public Application(List<LifecycleAware> components) {
    this.components = components;
    supervisor = new LifecycleSupervisor();
  }

  public synchronized void start() {
    for (LifecycleAware component : components) {
      //启动所有组件的入口
      supervisor.supervise(component,
          new SupervisorPolicy.AlwaysRestartPolicy(), LifecycleState.START);
    }
  }

  @Subscribe
  public synchronized void handleConfigurationEvent(MaterializedConfiguration conf) {
    /**
     * 感觉是如果发现配置文件发生变化之后所有的compents会先关掉然后再重启
     * 以此来实现“动态”加载配置文件的目的
     */
    stopAllComponents();
    startAllComponents(conf);
  }

  public synchronized void stop() {
    supervisor.stop();
    if (monitorServer != null) {
      monitorServer.stop();
    }
  }

  //关闭source， channel 和sink
  private void stopAllComponents() {
    if (this.materializedConfiguration != null) {
      logger.info("Shutting down configuration: {}", this.materializedConfiguration);

      //关闭source
      for (Entry<String, SourceRunner> entry :
           this.materializedConfiguration.getSourceRunners().entrySet()) {
        try {
          logger.info("Stopping Source " + entry.getKey());
          supervisor.unsupervise(entry.getValue());
        } catch (Exception e) {
          logger.error("Error while stopping {}", entry.getValue(), e);
        }
      }

      //关闭sink
      for (Entry<String, SinkRunner> entry :
           this.materializedConfiguration.getSinkRunners().entrySet()) {
        try {
          logger.info("Stopping Sink " + entry.getKey());
          supervisor.unsupervise(entry.getValue());
        } catch (Exception e) {
          logger.error("Error while stopping {}", entry.getValue(), e);
        }
      }

      //关闭channel
      for (Entry<String, Channel> entry :
           this.materializedConfiguration.getChannels().entrySet()) {
        try {
          logger.info("Stopping Channel " + entry.getKey());
          supervisor.unsupervise(entry.getValue());
        } catch (Exception e) {
          logger.error("Error while stopping {}", entry.getValue(), e);
        }
      }
    }
    if (monitorServer != null) {
      monitorServer.stop();
    }
  }

  private void startAllComponents(MaterializedConfiguration materializedConfiguration) {
    logger.info("Starting new configuration:{}", materializedConfiguration);

    this.materializedConfiguration = materializedConfiguration;

    for (Entry<String, Channel> entry :
        materializedConfiguration.getChannels().entrySet()) {
      try {
        logger.info("Starting Channel " + entry.getKey());
        supervisor.supervise(entry.getValue(),
            new SupervisorPolicy.AlwaysRestartPolicy(), LifecycleState.START);
      } catch (Exception e) {
        logger.error("Error while starting {}", entry.getValue(), e);
      }
    }

    /*
     * Wait for all channels to start.
     */
    for (Channel ch : materializedConfiguration.getChannels().values()) {
      while (ch.getLifecycleState() != LifecycleState.START
          && !supervisor.isComponentInErrorState(ch)) {
        try {
          logger.info("Waiting for channel: " + ch.getName() +
              " to start. Sleeping for 500 ms");
          Thread.sleep(500);
        } catch (InterruptedException e) {
          logger.error("Interrupted while waiting for channel to start.", e);
          Throwables.propagate(e);
        }
      }
    }

    for (Entry<String, SinkRunner> entry : materializedConfiguration.getSinkRunners().entrySet()) {
      try {
        logger.info("Starting Sink " + entry.getKey());
        supervisor.supervise(entry.getValue(),
            new SupervisorPolicy.AlwaysRestartPolicy(), LifecycleState.START);
      } catch (Exception e) {
        logger.error("Error while starting {}", entry.getValue(), e);
      }
    }

    for (Entry<String, SourceRunner> entry :
         materializedConfiguration.getSourceRunners().entrySet()) {
      try {
        logger.info("Starting Source " + entry.getKey());
        supervisor.supervise(entry.getValue(),
            new SupervisorPolicy.AlwaysRestartPolicy(), LifecycleState.START);
      } catch (Exception e) {
        logger.error("Error while starting {}", entry.getValue(), e);
      }
    }

    this.loadMonitoring();
  }

  @SuppressWarnings("unchecked")
  private void loadMonitoring() {
    Properties systemProps = System.getProperties();
    Set<String> keys = systemProps.stringPropertyNames();
    try {
      if (keys.contains(CONF_MONITOR_CLASS)) {
        String monitorType = systemProps.getProperty(CONF_MONITOR_CLASS);
        Class<? extends MonitorService> klass;
        try {
          //Is it a known type?
          klass = MonitoringType.valueOf(
              monitorType.toUpperCase(Locale.ENGLISH)).getMonitorClass();
        } catch (Exception e) {
          //Not a known type, use FQCN
          klass = (Class<? extends MonitorService>) Class.forName(monitorType);
        }
        //反射
        this.monitorServer = klass.newInstance();
        Context context = new Context();
        for (String key : keys) {
          if (key.startsWith(CONF_MONITOR_PREFIX)) {
            context.put(key.substring(CONF_MONITOR_PREFIX.length()),
                systemProps.getProperty(key));
          }
        }
        monitorServer.configure(context);
        monitorServer.start();
      }
    } catch (Exception e) {
      logger.warn("Error starting monitoring. "
          + "Monitoring might not be available.", e);
    }

  }

  public static void main(String[] args) {

    try {

      boolean isZkConfigured = false;

      /*
       * 首先是解析命令行中的各个参数
       * flume启动的时候所带的 -n， -f， -h 等参数都会在这里解析
       * flume 首先会通过脚本启动的脚本把启动的参数解析整理一遍，然后再在脚本中通过命令
       * 启动Application 的main方法， 同时会把解析整理后的参数再传过来
       */

      Options options = new Options();

      /**
       * opt: 参数名的缩写
       * longOpt：参数名的全程
       * hasArg：参数是否带值
       * description：参数的描述信息
       */
      /**
       * -n agent name,是一个必须带有的参数
       */
      Option option = new Option("n", "name", true, "the name of this agent");
      /*
       * 表示参数是否是必须的
       */
      option.setRequired(true);
      options.addOption(option);

      /**
       * 指定配置配置文件, 如果没有 -z 参数，则 -f 参数是必须的
       * -z 是关于zookeeper相关的配置
       */
      option = new Option("f", "conf-file", true,
          "specify a config file (required if -z missing)");
      option.setRequired(false);
      options.addOption(option);

      /**
       * 该参数没有简写，只有全称，如果配置了该参数标识的如果配置文件发生了改变不会去重新去加载配置文件的
       */
      option = new Option(null, "no-reload-conf", false,
          "do not reload config file if changed");
      options.addOption(option);

      /**
       *zookeeper 相关的配置
       */
      // Options for Zookeeper
      option = new Option("z", "zkConnString", true,
          "specify the ZooKeeper connection to use (required if -f missing)");
      option.setRequired(false);
      options.addOption(option);

      option = new Option("p", "zkBasePath", true,
          "specify the base path in ZooKeeper for agent configs");
      option.setRequired(false);
      options.addOption(option);

      option = new Option("h", "help", false, "display help text");
      options.addOption(option);

      /**
       * 这里使用的是apache的两个关于命令行参数解析的包来帮助完成参数解析
       */
      CommandLineParser parser = new GnuParser();
      CommandLine commandLine = parser.parse(options, args);

      if (commandLine.hasOption('h')) {
        new HelpFormatter().printHelp("flume-ng agent", options, true);
        return;
      }

      String agentName = commandLine.getOptionValue('n');
      boolean reload = !commandLine.hasOption("no-reload-conf");

      if (commandLine.hasOption('z') || commandLine.hasOption("zkConnString")) {
        isZkConfigured = true;
      }
      Application application = null;
      if (isZkConfigured) {
        // get options
        String zkConnectionStr = commandLine.getOptionValue('z');
        String baseZkPath = commandLine.getOptionValue('p');

        if (reload) {
          EventBus eventBus = new EventBus(agentName + "-event-bus");
          List<LifecycleAware> components = Lists.newArrayList();
          PollingZooKeeperConfigurationProvider zookeeperConfigurationProvider =
              new PollingZooKeeperConfigurationProvider(
                  agentName, zkConnectionStr, baseZkPath, eventBus);
          components.add(zookeeperConfigurationProvider);
          application = new Application(components);
          eventBus.register(application);
        } else {
          StaticZooKeeperConfigurationProvider zookeeperConfigurationProvider =
              new StaticZooKeeperConfigurationProvider(
                  agentName, zkConnectionStr, baseZkPath);
          application = new Application();
          application.handleConfigurationEvent(zookeeperConfigurationProvider.getConfiguration());
        }
      } else {
        File configurationFile = new File(commandLine.getOptionValue('f'));

        /*
         * The following is to ensure that by default the agent will fail on
         * startup if the file does not exist.
         */
        if (!configurationFile.exists()) {
          // If command line invocation, then need to fail fast
          if (System.getProperty(Constants.SYSPROP_CALLED_FROM_SERVICE) ==
              null) {
            String path = configurationFile.getPath();
            try {
              //获取文件的绝对路径
              path = configurationFile.getCanonicalPath();
            } catch (IOException ex) {
              logger.error("Failed to read canonical path for file: " + path,
                  ex);
            }
            throw new ParseException(
                "The specified configuration file does not exist: " + path);
          }
        }
        //? 为什么使用google的Lists, 那就去查查google的集合类相比于java原生的类的好处
        List<LifecycleAware> components = Lists.newArrayList();
        /**
         * 一、没有此参数，会动态加载配置文件，默认每30秒加载一次配置文件，因此可以动态修改配置文件；
         * 二、有此参数，则只在启动时加载一次配置文件。实现动态加载功能采用了发布订阅模式，
         * 使用guava中的EventBus实现。
         * 三、PropertiesFileConfigurationProvider这个类是配置文件加载类
         */

        if (reload) {
          //一般都是走这个代码分支启动flume
          EventBus eventBus = new EventBus(agentName + "-event-bus");
          PollingPropertiesFileConfigurationProvider configurationProvider =
              new PollingPropertiesFileConfigurationProvider(
                  agentName, configurationFile, eventBus, 30); //30s默认每30秒读取一次配置文件
          components.add(configurationProvider);
          application = new Application(components);
          eventBus.register(application);
        } else {
          PropertiesFileConfigurationProvider configurationProvider =
              new PropertiesFileConfigurationProvider(agentName, configurationFile);
          application = new Application();
          application.handleConfigurationEvent(configurationProvider.getConfiguration());
        }
      }
      //启动所有组件
      application.start();

      final Application appReference = application;
      /**
       * flume的退出机制，通过注册JVM的ShutdownHook()来再jvm退出时调用application.stop
       * 来停止flume的components,jvm在某些情况下退出时会调用ShutdownHook函数，但是
       * kill -9 是不会执行ShutdownHook函数的
       */
      Runtime.getRuntime().addShutdownHook(new Thread("agent-shutdown-hook") {
        @Override
        public void run() {
          appReference.stop();
        }
      });

    } catch (Exception e) {
      logger.error("A fatal error occurred while running. Exception follows.", e);
    }
  }
}