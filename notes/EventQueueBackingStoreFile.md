#EventQueueBackingStoreFile(草稿)

### startBackupThread()

```
/**
 * This method starts backing up the checkpoint in the background.
 * 使用一个thread来完成的
 */
```

flume提供了checkpoint的backup机制,如果开启了backup, flume会在每次chieckpoint之后backup一下checkpoint个对应的meta file;



###backupCheckpoint(File backupDirectory)

```
/**
 * This method backs up the checkpoint and its metadata files. This method
 * is called once the checkpoint is completely written and is called
 * from a separate thread which runs in the background while the file channel
 * continues operation.
 *
 * @param backupDirectory - the directory to which the backup files should be
 *                        copied.
 * @throws IOException - if the copy failed, or if there is not enough disk
 *                     space to copy the checkpoint files over.
 */
protected void backupCheckpoint(File backupDirectory) throws IOException {
```

是在`startBackupThread()` 中调用的backup方法;

###restoreBackup(File checkpointDir, File backupDir)

```
/**
 * Restore the checkpoint, if it is found to be bad.
 *
 * 如果发现checkpoint是错误的,调用次函数可以用来恢复checkpoint 
```

操作中会删掉checkpoint目录下的文件,然后把backup 目录下的文件copy一份过来

### checkpoint(boolean force)

```
backingStore.beginCheckpoint();
//将inflightPuts中的events写入对应的文件中
inflightPuts.serializeAndWrite();
//将inflightTakes中的events写入对应的文件中
inflightTakes.serializeAndWrite();
backingStore.checkpoint();
```

  

