# FileChannel

* Flume File Channel 是一个通过文件来达到持久化目的的channel,它将持久化所有的Event,将其存储到磁盘上.因此当JVM宕机,或者操作系统崩溃或者重启,再或者没有在channel成功传递到下一个Agent,这一切都不会造成数据丢失.


* File Channel比Memory Channel提供更好的可靠性和可恢复性，不过要操作本地文件，性能要差一些
* 来自网上的一些信息:
  1. 由于FileChannel提供了更好的可靠性,所以一般生产环境中多使用FileChannel;
  2. 使用FileChannel往HDFS中写入数据,写入的数据是:2W4/s [链接][http://boylook.blog.51cto.com/7934327/1298624]

### FileChannle的配置

Required properties are in **bold**.

| Property Name Default                    | Description                      |                                          |
| ---------------------------------------- | -------------------------------- | ---------------------------------------- |
| **type**                                 | –                                | The component type name, needs to be `file`. |
| checkpointDir                            | ~/.flume/file-channel/checkpoint | The directory where checkpoint file will be stored |
| useDualCheckpoints                       | false                            | Backup the checkpoint. If this is set to `true`, `backupCheckpointDir` **must** be set |
| backupCheckpointDir                      | –                                | The directory where the checkpoint is backed up to. This directory **must not** be the same as the data directories or the checkpoint directory |
| dataDirs                                 | ~/.flume/file-channel/data       | Comma separated list of directories for storing log files. Using multiple directories on separate disks can improve file channel peformance |
| transactionCapacity                      | 10000                            | The maximum size of transaction supported by the channel |
| checkpointInterval                       | 30000                            | Amount of time (in millis) between checkpoints |
| maxFileSize                              | 2146435071                       | Max size (in bytes) of a single log file |
| minimumRequiredSpace                     | 524288000                        | Minimum Required free space (in bytes). To avoid data corruption, File Channel stops accepting take/put requests when free space drops below this value |
| capacity                                 | 1000000                          | Maximum capacity of the channel          |
| keep-alive                               | 3                                | Amount of time (in sec) to wait for a put operation |
| use-log-replay-v1                        | false                            | Expert: Use old replay logic             |
| use-fast-replay                          | false                            | Expert: Replay without using queue       |
| checkpointOnClose                        | true                             | Controls if a checkpoint is created when the channel is closed. Creating a checkpoint on close speeds up subsequent startup of the file channel by avoiding replay. |
| encryption.activeKey                     | –                                | Key name used to encrypt new data        |
| encryption.cipherProvider                | –                                | Cipher provider type, supported types: AESCTRNOPADDING |
| encryption.keyProvider                   | –                                | Key provider type, supported types: JCEKSFILE |
| encryption.keyProvider.keyStoreFile      | –                                | Path to the keystore file                |
| encrpytion.keyProvider.keyStorePasswordFile | –                                | Path to the keystore password file       |
| encryption.keyProvider.keys              | –                                | List of all keys (e.g. history of the activeKey setting) |
| encyption.keyProvider.keys.*.passwordFile | –                                | Path to the optional key password file   |

> Note: By default the File Channel uses paths for checkpoint and data directories that are within the user home as specified above. As a result if you have more than one File Channel instances active within the agent, only one will be able to lock the directories and cause the other channel initialization to fail. It is therefore necessary that you provide explicit paths to all the configured channels, preferably on different disks. Furthermore, as file channel will sync to disk after every commit, coupling it with a sink/source that batches events together may be necessary to provide good performance where multiple disks are not available for checkpoint and data directories.

来自官方的配置示例:

Example for agent named a1:

```
a1.channels = c1
a1.channels.c1.type = file
a1.channels.c1.checkpointDir = /mnt/flume/checkpoint
a1.channels.c1.dataDirs = /mnt/flume/data

```

**Encryption**

Below is a few sample configurations:

Generating a key with a password seperate from the key store password:

```
keytool -genseckey -alias key-0 -keypass keyPassword -keyalg AES \
  -keysize 128 -validity 9000 -keystore test.keystore \
  -storetype jceks -storepass keyStorePassword

```

Generating a key with the password the same as the key store password:

```
keytool -genseckey -alias key-1 -keyalg AES -keysize 128 -validity 9000 \
  -keystore src/test/resources/test.keystore -storetype jceks \
  -storepass keyStorePassword

```

```
a1.channels.c1.encryption.activeKey = key-0
a1.channels.c1.encryption.cipherProvider = AESCTRNOPADDING
a1.channels.c1.encryption.keyProvider = key-provider-0
a1.channels.c1.encryption.keyProvider = JCEKSFILE
a1.channels.c1.encryption.keyProvider.keyStoreFile = /path/to/my.keystore
a1.channels.c1.encryption.keyProvider.keyStorePasswordFile = /path/to/my.keystore.password
a1.channels.c1.encryption.keyProvider.keys = key-0

```

Let’s say you have aged key-0 out and new files should be encrypted with key-1:

```
a1.channels.c1.encryption.activeKey = key-1
a1.channels.c1.encryption.cipherProvider = AESCTRNOPADDING
a1.channels.c1.encryption.keyProvider = JCEKSFILE
a1.channels.c1.encryption.keyProvider.keyStoreFile = /path/to/my.keystore
a1.channels.c1.encryption.keyProvider.keyStorePasswordFile = /path/to/my.keystore.password
a1.channels.c1.encryption.keyProvider.keys = key-0 key-1

```

The same scenerio as above, however key-0 has its own password:

```
a1.channels.c1.encryption.activeKey = key-1
a1.channels.c1.encryption.cipherProvider = AESCTRNOPADDING
a1.channels.c1.encryption.keyProvider = JCEKSFILE
a1.channels.c1.encryption.keyProvider.keyStoreFile = /path/to/my.keystore
a1.channels.c1.encryption.keyProvider.keyStorePasswordFile = /path/to/my.keystore.password
a1.channels.c1.encryption.keyProvider.keys = key-0 key-1
a1.channels.c1.encryption.keyProvider.keys.key-0.passwordFile = /path/to/key-0.password
```
### FileChannel 结构

![FileChannel](G:\snap\FileChannel.PNG)

