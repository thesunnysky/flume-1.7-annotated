#TailDirSource

![TailDirSource](G:\snap\TailDirSource.PNG)

TaildirSource是我自己项目中实用的source,所以这里分析了一下TaildirSource的代码;

taildirSorurce 针对的是flume在处理系统日志文件的情景,系统的日志文件随着系统的运行是不断产生的,并且很多系统的日志文件是按照日期来归档的,并且这些文件一般都是放在一个固定的文件夹下面的,一天的日志存放到一个文件中,然后按照日期啥的来命名,随着系统的运行,同一天的日志以一种append的方式追加到当天的日志文件中的;

由以上的分析可以得到两点: 

* 日志的存储是在同一个问价夹下的;
* 当前的日志是以追加的方式写入到当天的文件中;

这两天也对应了TaildirSource中的Tail 和 Dir 两个字眼;

好了,下面来分析TaildirSource的代码:

上图是TadidirSource的继承关系图,其中PollableSource和AbstractSource作用之前都已经分析过了,PollableSource是TaildirSource工作的关键之一, 但是在这里我们将主要关注TaildirSource;

### TaildirSource

TaildirSource(下面将以source简写)主要的功能就是读取配置文件中配置的filepath的中的日志文件,有几个关键点:

1. flume支持配置文件中配置的文件名为正则表达式,Source需要处理所有满足正则表达式的文件;
2. source需要将文件中的内容按行为单位读取并生成event;
3. 对于文件的追加模式,source需要判断哪些文件中用新的内容追加进来了,对于有新内容追加进来的文件,需要从该文件中read event,对于一段时间呢没有内容追加的文集,source将关闭这些文件,对于这些idle file/inactive file,source 会起一个定时的任务取关闭这些文件;
4. 对于每一个文件,source都会在positionfile中记录对该文件上次处理的位置,认为处理位置之前的文件内容是已经处理过的,每次处理的时候只会处理上次记录位置之后的内容,并且会记录每个文件的已处理位置,然后会有一个定时任务的线程定期的去更新postionfile中对改文件的处理位置的记录;

以上的这几个队文件的操作TaildirSource都是借由ReliableTaildirEventReader和TailFile辅助来完成的,对TailFile和ReliableTaildirEventReader的介绍会在最后

### idleFIleCheker线程和positionWriter线程

```java
private ScheduledExecutorService idleFileChecker;
  //position file writer 线程, flume会起一个线程定期的更新position file
private ScheduledExecutorService positionWriter;
```

start()方法中对idleFileChecker和positionWriter进行了初始化;

```java
idleFileChecker = Executors.newSingleThreadScheduledExecutor(
	new ThreadFactoryBuilder().setNameFormat("idleFileChecker").build());
idleFileChecker.scheduleWithFixedDelay(new idleFileCheckerRunnable(),
	idleTimeout, checkIdleInterval, TimeUnit.MILLISECONDS);

positionWriter = Executors.newSingleThreadScheduledExecutor(
	new ThreadFactoryBuilder().setNameFormat("positionWriter").build());
positionWriter.scheduleWithFixedDelay(new PositionWriterRunnable(),
	writePosInitDelay, writePosInterval, TimeUnit.MILLISECONDS);
```

```java
  /**
   * Runnable class that checks whether there are files which should be closed.
   */
  private class idleFileCheckerRunnable implements Runnable {
    @Override
    public void run() {
      try {
        long now = System.currentTimeMillis();
        for (TailFile tf : reader.getTailFiles().values()) {
          if (tf.getLastUpdated() + idleTimeout < now && tf.getRaf() != null) {
            idleInodes.add(tf.getInode());
          }
        }
      } catch (Throwable t) {
        logger.error("Uncaught exception in IdleFileChecker thread", t);
      }
    }
  }
```

```java
  /**
   * Runnable class that writes a position file which has the last read position
   * of each file.
   */
  private class PositionWriterRunnable implements Runnable {
    @Override
    public void run() {
      writePosition();
    }
  }

  private void writePosition() {
    File file = new File(positionFilePath);
    FileWriter writer = null;
    try {
      writer = new FileWriter(file);
      if (!existingInodes.isEmpty()) {
        String json = toPosInfoJson();
        writer.write(json);
      }
    } catch (Throwable t) {
      logger.error("Failed writing positionFile", t);
    } finally {
      try {
        if (writer != null) writer.close();
      } catch (IOException e) {
        logger.error("Error: " + e.getMessage(), e);
      }
    }
  }
```

### 将events 放入channel中

上面提到,taildirsource队文件的操作都是借由ReliableTaildirEventReader和TailFile来完成的,会直接调用ReliableTaildirEventReader 的readEvents()方法来获取events, 而TaildirSource.java 的作用主要是

TaildirSoure的和核心的一个方法是

```java
public Status process();
private void tailFileProcess(TailFile tf, boolean backoffWithoutNL);
```

1. process()方法是PollableSource借口定义的方法,PollableSourceRunner中的工作线程会循环调用process()方法;
2. TaildirSource 的process()方法的作用就是针对已经检测到的文件索引通过tailFileProcess来调用ReliableTaildirEventReader的tailFileProcess(TailFile tf, boolean backoffWithoutNL)方法获取events,并将envnet 放入该source 对应的channel中;

```java
//source 的核心方法, 从file中 readEvent然后放入到channel中
  @Override
  public Status process() {
    Status status = Status.READY;
    try {
      existingInodes.clear();
      existingInodes.addAll(reader.updateTailFiles());
      //遍历已经检测到的所有文件
      for (long inode : existingInodes) {
        TailFile tf = reader.getTailFiles().get(inode);
        if (tf.needTail()) {
          //处理文件
          tailFileProcess(tf, true);
        }
      }
      closeTailFiles();
      try {
        TimeUnit.MILLISECONDS.sleep(retryInterval);
      } catch (InterruptedException e) {
        logger.info("Interrupted while sleeping");
      }
    } catch (Throwable t) {
      logger.error("Unable to tail files", t);
      status = Status.BACKOFF;
    }
    return status;
  }
```

```java
private void tailFileProcess(TailFile tf, boolean backoffWithoutNL)
      throws IOException, InterruptedException {
    while (true) {
      //设置readEvent的文件
      reader.setCurrentFile(tf);
      //尝试读取batchSize个event
      List<Event> events = reader.readEvents(batchSize, backoffWithoutNL);
      if (events.isEmpty()) {
        break;
      }
      sourceCounter.addToEventReceivedCount(events.size());
      sourceCounter.incrementAppendBatchReceivedCount();
      try {
        //将读取event放入到channel中
        getChannelProcessor().processEventBatch(events);
        reader.commit();
      } catch (ChannelException ex) {
        //对于像channel 放入event失败的情况,source会在间隔一段时候后一直重试
        logger.warn("The channel is full or unexpected failure. " +
            "The source will try again after " + retryInterval + " ms");
        TimeUnit.MILLISECONDS.sleep(retryInterval);
        retryInterval = retryInterval << 1;
        retryInterval = Math.min(retryInterval, maxRetryInterval);
        continue;
      }
      retryInterval = 1000;
      sourceCounter.addToEventAcceptedCount(events.size());
      sourceCounter.incrementAppendBatchAcceptedCount();
      //表示event已经读取完了,可以结束对改文件的处理了
      if (events.size() < batchSize) {
        break;
      }
    }
  }
```

### TailFile

TailFile中细节的东西太多了,这里只做一下简要的概述:

 * TailFile 主要是判断file中是否有新的数据,如果有的新的数据并能够一行时,就将改行数据读取差转换成Event;
 * TailDirSource在读取数据生成event的时候是按行来读取的,文件中的每行(根据换行符来判断)都将生成一个event,在source生成的event中,并不会去读换行符;
* 在读取文件的时候,tailfile每次读取的最大缓存容量是8M;
* 由于是按照buffer 容量的方式在读取,所以tailFIle不能做到每次读取的都是正行,而source 生成的event却是正行来读取的,所以tailFile使用buffer和oldBuffer来处理数据保证每次生成的event都对应文件中的一行;

下面贴上含有注释的代码;

```java
public class TailFile {
  private static final Logger logger = LoggerFactory.getLogger(TailFile.class);

  //换行符的byte表示,由于在读取文件的时候都采用的是byte[],所以在插入换行符和回车符的时候就用这两个final byte来表示就可以了
  private static final byte BYTE_NL = (byte) 10;
  //回车符的byte表示
  private static final byte BYTE_CR = (byte) 13;

  //最大没从从文件中读取8M大小的数据
  private static final int BUFFER_SIZE = 8192;
  private static final int NEED_READING = -1;

  private RandomAccessFile raf;
  private final String path;
  //索引值,flume为taildir中的每一个文件(它缓存过的)都设置了一个索引值
  private final long inode;
  private long pos;
  //记录文件最后一次被flume readEvent的时间
  private long lastUpdated;
  //表示是否是需要读取文件,意味着文件中有尚未被source读取的新的内容
  private boolean needTail;
  private final Map<String, String> headers;
  //buffer 和 oldBuffer 配合使得再用byte[]读取文件的同时也保证了最终文件内容读取时是按行读取的
  private byte[] buffer;
  private byte[] oldBuffer;
  private int bufferPos;
  //记录当前文件读取的位置
  private long lineReadPos;

  public TailFile(File file, Map<String, String> headers, long inode, long pos)
      throws IOException {
    this.raf = new RandomAccessFile(file, "r");
    //在新建TailFile的时候直接就将file的文件指针指到了要读取的文件的位置
    if (pos > 0) {
      raf.seek(pos);
      lineReadPos = pos;
    }
    this.path = file.getAbsolutePath();
    this.inode = inode;
    this.pos = pos;
    this.lastUpdated = 0L;
    this.needTail = true;
    this.headers = headers;
    this.oldBuffer = new byte[0];
    this.bufferPos = NEED_READING;
  }

  public RandomAccessFile getRaf() {
    return raf;
  }

  public String getPath() {
    return path;
  }

  public long getInode() {
    return inode;
  }

  public long getPos() {
    return pos;
  }

  public long getLastUpdated() {
    return lastUpdated;
  }

  public boolean needTail() {
    return needTail;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public long getLineReadPos() {
    return lineReadPos;
  }

  public void setPos(long pos) {
    this.pos = pos;
  }

  public void setLastUpdated(long lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  public void setNeedTail(boolean needTail) {
    this.needTail = needTail;
  }

  public void setLineReadPos(long lineReadPos) {
    this.lineReadPos = lineReadPos;
  }

  public boolean updatePos(String path, long inode, long pos) throws IOException {
    if (this.inode == inode && this.path.equals(path)) {
      setPos(pos);
      updateFilePos(pos);
      logger.info("Updated position, file: " + path + ", inode: " + inode + ", pos: " + pos);
      return true;
    }
    return false;
  }
  public void updateFilePos(long pos) throws IOException {
    raf.seek(pos);
    lineReadPos = pos;
    bufferPos = NEED_READING;
    oldBuffer = new byte[0];
  }
  
  /**
   * 这些readEvents方法会由ReliableTaildirEventReader来调用从currentFile中读取events
   */
  public List<Event> readEvents(int numEvents, boolean backoffWithoutNL,
      boolean addByteOffset) throws IOException {
    List<Event> events = Lists.newLinkedList();
    for (int i = 0; i < numEvents; i++) {
      Event event = readEvent(backoffWithoutNL, addByteOffset);
      if (event == null) {
        break;
      }
      events.add(event);
    }
    return events;
  }

  private Event readEvent(boolean backoffWithoutNL, boolean addByteOffset) throws IOException {
    Long posTmp = getLineReadPos();
    LineResult line = readLine();
    if (line == null) {
      return null;
    }
    if (backoffWithoutNL && !line.lineSepInclude) {
      logger.info("Backing off in file without newline: "
          + path + ", inode: " + inode + ", pos: " + raf.getFilePointer());
      updateFilePos(posTmp);
      return null;
    }
    //将line数据创建event
    Event event = EventBuilder.withBody(line.line);
    if (addByteOffset == true) {
      event.getHeaders().put(BYTE_OFFSET_HEADER_KEY, posTmp.toString());
    }
    return event;
  }

  private void readFile() throws IOException {
    /* 如果剩余的文件内容大小不够8M,则按需读取剩余的内容
     * 如果剩余的文件内容大于8M, 则只读取8M大小的内容
     */
    if ((raf.length() - raf.getFilePointer()) < BUFFER_SIZE) {
      buffer = new byte[(int) (raf.length() - raf.getFilePointer())];
    } else {
      buffer = new byte[BUFFER_SIZE];
    }
    raf.read(buffer, 0, buffer.length);
    bufferPos = 0;
  }

  private byte[] concatByteArrays(byte[] a, int startIdxA, int lenA,
                                  byte[] b, int startIdxB, int lenB) {
    byte[] c = new byte[lenA + lenB];
    System.arraycopy(a, startIdxA, c, 0, lenA);
    System.arraycopy(b, startIdxB, c, lenA, lenB);
    return c;
  }

  public LineResult readLine() throws IOException {
    LineResult lineResult = null;
    while (true) {
      //说明buffer是空的
      if (bufferPos == NEED_READING) {
        //说明有新的数据需要读取
        if (raf.getFilePointer() < raf.length()) {
          //readFile()会更新bufferPos的值
          readFile();
        } else {
          //已经是文件末尾了,并且是oldBuffer中还有数据
          if (oldBuffer.length > 0) {
            //将oldBuffer中的数据转换成lineResult,并清空oldBuffer
            lineResult = new LineResult(false, oldBuffer);
            oldBuffer = new byte[0];
            setLineReadPos(lineReadPos + lineResult.line.length);
          }
          break;
        }
      }
      //到这里bufferPos已经不会是-1,在前面readFile()的时候已经更新为0了
      for (int i = bufferPos; i < buffer.length; i++) {
        if (buffer[i] == BYTE_NL) {
          int oldLen = oldBuffer.length;
          // Don't copy last byte(NEW_LINE)
          int lineLen = i - bufferPos;
          // For windows, check for CR
          /* 检查上一个字符是不是回车符,windows下的换行符<回车><换行>,比linux的多一个字节,
           * 如果检查上一个字符是回车符的话,拷贝的时候不拷贝这个回车符
           */
          if (i > 0 && buffer[i - 1] == BYTE_CR) {
            lineLen -= 1;
          } else if (oldBuffer.length > 0 && oldBuffer[oldBuffer.length - 1] == BYTE_CR) {
            oldLen -= 1;
          }
          //将buffer中的第一个换行符前面的数据和oldBuffer中剩余的数据拼接成一个新的一行,生活一个LineResult
          lineResult = new LineResult(true,
              concatByteArrays(oldBuffer, 0, oldLen, buffer, bufferPos, lineLen));
          setLineReadPos(lineReadPos + (oldBuffer.length + (i - bufferPos + 1)));
          oldBuffer = new byte[0];
          //检查buffer中是否还有缓存的数据,如果有的话就更新buffer的位置到下一个byte的数据位置,如果没有将bufferPos 置位-1;
          if (i + 1 < buffer.length) {
            bufferPos = i + 1;
          } else {
            bufferPos = NEED_READING;
          }
          break;
        }
      }
      if (lineResult != null) {
        break;
      }
      // NEW_LINE not showed up at the end of the buffer
      // 将buffer中剩余的数据和oldBuffer中剩余的数据拼接成欣的oldBuffer;
      oldBuffer = concatByteArrays(oldBuffer, 0, oldBuffer.length,
                                   buffer, bufferPos, buffer.length - bufferPos);
      bufferPos = NEED_READING;
    }
    return lineResult;
  }

  public void close() {
    try {
      raf.close();
      raf = null;
      long now = System.currentTimeMillis();
      setLastUpdated(now);
    } catch (IOException e) {
      logger.error("Failed closing file: " + path + ", inode: " + inode, e);
    }
  }

  private class LineResult {
    //应该表示的是数据中是否包含了行分隔符
    final boolean lineSepInclude;
    final byte[] line;

    public LineResult(boolean lineSepInclude, byte[] line) {
      super();
      this.lineSepInclude = lineSepInclude;
      this.line = line;
    }
  }
}
```

### ReliableTaildirEventReader

* ReliableTaildirEventReader 帮TaildirSource做掉了对指定目录下所有文件的监控, 监控是否有文件有新的内容写入,

* ReliableTaildirEventReader 通过正则表达式匹配文件名依赖TaildirMatcher类来实现该功能;

* 定义了private TailFile currentFile,  ReliableTaildirEventReader从给currentFile指定的文件中读取event;

* `public void loadPositionFile(String filePath)` 方法是用来从positionfile中读取文件的访问位置的;

* ReliableTaildirEventReader 提供了readEvent方法,该方法是用来从currentFile中读取event的,该方法将会在TaildirSouce的process()方法部分调用;

* ReliableTaildirEventReader 也提供了commit()方法, 用来标记commit最后被读取的一行,更新时相关的变量;

* `public List<Long> updateTailFiles(boolean skipToEnd) throws IOException` 方法是ReliableTaildirEventReader的核心方法,该方法将会遍历所有检测到的文件,判断文件是否有新的内容追加进来,  同事标记哪些问价需要Tail(也就是说问价中有新的内容了,需要source读取event)

* ReliableTaildirEventReader 入参比较多, 并没有public的构造器,而是采用了builder的方式来构造对象;

  代码细节和相应的注释都在代码中,下面直接贴上来:

```java
/**
 * ReliableTaildirEventReader 帮TaildirSource做掉了对指定目录下所有文件的监控, 监控是否有文件有新的内容写入;
 * 从给定文件中读取event;
 */
public class ReliableTaildirEventReader implements ReliableEventReader {
  private static final Logger logger = LoggerFactory.getLogger(ReliableTaildirEventReader.class);

  //本地缓存的TaildirMatcher
  private final List<TaildirMatcher> taildirCache;
  private final Table<String, String, String> headerTable;

  /* TailFile的引用
   * ReliadbleTaildirEventReader 中有对TailFile 的readEvent方法,这些方法就是从currentFile所引用的TailFile中
   * 读取event的
   */
  private TailFile currentFile = null;
  /* 本地TailFile的Map,key是file的inode,value是文件对应的TailFile类
   * tailFiles标记的是每次处理时要处理的file
   */
  private Map<Long, TailFile> tailFiles = Maps.newHashMap();
  private long updateTime;
  private boolean addByteOffset;
  private boolean cachePatternMatching;
  private boolean committed = true;
  private final boolean annotateFileName;
  private final String fileNameHeader;

  /**
   * Create a ReliableTaildirEventReader to watch the given directory.
   */
  private ReliableTaildirEventReader(Map<String, String> filePaths,
      Table<String, String, String> headerTable, String positionFilePath,
      boolean skipToEnd, boolean addByteOffset, boolean cachePatternMatching,
      boolean annotateFileName, String fileNameHeader) throws IOException {
    // Sanity checks
    Preconditions.checkNotNull(filePaths);
    Preconditions.checkNotNull(positionFilePath);

    if (logger.isDebugEnabled()) {
      logger.debug("Initializing {} with directory={}, metaDir={}",
          new Object[] { ReliableTaildirEventReader.class.getSimpleName(), filePaths });
    }

    /* 讲配置文件中配置的filegroup配置项转换成TaildirMatcher并存入到taildirCache中;
     * 然后后续的操作都会taildirCache来进行;
     * 补充:后续对taildirCache的更新操作是在检测到filePath的父目录更新后才更新的,
     * 以为只有父目录更新后才以为着目录下有文件的增加或者删除,单单对文件的内容的修改不会引起父文件夹的更新
     */
    List<TaildirMatcher> taildirCache = Lists.newArrayList();
    for (Entry<String, String> e : filePaths.entrySet()) {
      taildirCache.add(new TaildirMatcher(e.getKey(), e.getValue(), cachePatternMatching));
    }
    logger.info("taildirCache: " + taildirCache.toString());
    logger.info("headerTable: " + headerTable.toString());

    this.taildirCache = taildirCache;
    this.headerTable = headerTable;
    this.addByteOffset = addByteOffset;
    this.cachePatternMatching = cachePatternMatching;
    this.annotateFileName = annotateFileName;
    this.fileNameHeader = fileNameHeader;
    updateTailFiles(skipToEnd);

    logger.info("Updating position from position file: " + positionFilePath);
    loadPositionFile(positionFilePath);
  }

  /**
   * Load a position file which has the last read position of each file.
   * If the position file exists, update tailFiles mapping.
   */
  /**
   * flume position文件的示例:
   * [
   *    {"inode":245854,"pos":1856,"file":"/home/gpadmin/data/master/gpseg-1/pg_log/adb_29.csv"},
   *    {"inode":245836,"pos":0,"file":"/home/gpadmin/data/master/gpseg-1/pg_log/adb_30.csv"}
   * ]
   */
  public void loadPositionFile(String filePath){}

  @Override
  public Event readEvent() throws IOException {
    List<Event> events = readEvents(1);
    if (events.isEmpty()) {
      return null;
    }
    return events.get(0);
  }

  @Override
  public List<Event> readEvents(int numEvents) throws IOException {
    return readEvents(numEvents, false);
  }

  @VisibleForTesting
  public List<Event> readEvents(TailFile tf, int numEvents) throws IOException {
    setCurrentFile(tf);
    return readEvents(numEvents, true);
  }

  public List<Event> readEvents(int numEvents, boolean backoffWithoutNL)
      throws IOException {
    if (!committed) {
      if (currentFile == null) {
        throw new IllegalStateException("current file does not exist. " + currentFile.getPath());
      }
      logger.info("Last read was never committed - resetting position");
      long lastPos = currentFile.getPos();
      currentFile.updateFilePos(lastPos);
    }
    List<Event> events = currentFile.readEvents(numEvents, backoffWithoutNL, addByteOffset);
    if (events.isEmpty()) {
      return events;
    }

    Map<String, String> headers = currentFile.getHeaders();
    if (annotateFileName || (headers != null && !headers.isEmpty())) {
      for (Event event : events) {
        if (headers != null && !headers.isEmpty()) {
          event.getHeaders().putAll(headers);
        }
        if (annotateFileName) {
          event.getHeaders().put(fileNameHeader, currentFile.getPath());
        }
      }
    }
    committed = false;
    return events;
  }

  @Override
  public void close() throws IOException {
    for (TailFile tf : tailFiles.values()) {
      if (tf.getRaf() != null) tf.getRaf().close();
    }
  }

  /** Commit the last lines which were read. */
  @Override
  public void commit() throws IOException {
    if (!committed && currentFile != null) {
      long pos = currentFile.getLineReadPos();
      currentFile.setPos(pos);
      //更新TailFile对flme readEvent(commit)(commit)的时间
      currentFile.setLastUpdated(updateTime);
      committed = true;
    }
  }

  /**
   * Update tailFiles mapping if a new file is created or appends are detected
   * to the existing file.
   */
  /* 检查taildirCache中符合要求的source文件,将这些文件的索引放到tailFiles(map)中,
   * 同时检查文件是否更新或者修改过,如果是,设置标志位,下次在处理的时候会将从更新的文件中读取event
   */
  public List<Long> updateTailFiles(boolean skipToEnd) throws IOException {
    updateTime = System.currentTimeMillis();
    List<Long> updatedInodes = Lists.newArrayList();

    for (TaildirMatcher taildir : taildirCache) {
      Map<String, String> headers = headerTable.row(taildir.getFileGroup());

      for (File f : taildir.getMatchingFiles()) {
        long inode = getInode(f);
        TailFile tf = tailFiles.get(inode);
        if (tf == null || !tf.getPath().equals(f.getAbsolutePath())) {
          long startPos = skipToEnd ? f.length() : 0;
          tf = openFile(f, headers, inode, startPos);
        } else {
          /* 判断文件是是否已经被修改过
           * tf.getLastUpdated()记录的是flume最后一次对该file处理的时间
           * 这里的处理应该是从文件中read event
           */
          boolean updated = tf.getLastUpdated() < f.lastModified();
          if (updated) {
            if (tf.getRaf() == null) {
              tf = openFile(f, headers, inode, tf.getPos());
            }
            //position文件红记录的位置比文件本身的大小都要大,说明有地方出错了,flume的处理是从头开始读取文件
            if (f.length() < tf.getPos()) {
              logger.info("Pos " + tf.getPos() + " is larger than file size! "
                  + "Restarting from pos 0, file: " + tf.getPath() + ", inode: " + inode);
              tf.updatePos(tf.getPath(), inode, 0);
            }
          }
          //设置文件已经更新的标志位
          tf.setNeedTail(updated);
        }
        //将文件以<inode,TailFile>的格式存储到Map中
        tailFiles.put(inode, tf);
        updatedInodes.add(inode);
      }
    }
    return updatedInodes;
  }

  public List<Long> updateTailFiles() throws IOException {
    return updateTailFiles(false);
  }

  //得到文件的inode
  private long getInode(File file) throws IOException {
    long inode = (long) Files.getAttribute(file.toPath(), "unix:ino");
    return inode;
  }

  private TailFile openFile(File file, Map<String, String> headers, long inode, long pos) {
    try {
      logger.info("Opening file: " + file + ", inode: " + inode + ", pos: " + pos);
      return new TailFile(file, headers, inode, pos);
    } catch (IOException e) {
      throw new FlumeException("Failed opening file: " + file, e);
    }
  }

  /**
   * Special builder class for ReliableTaildirEventReader
   */
  /**
   * ReliableTaildirEventReader并没有public的构造器,而是采用的builder的形式
   */
  public static class Builder {
    private Map<String, String> filePaths;
    private Table<String, String, String> headerTable;
    private String positionFilePath;
    private boolean skipToEnd;
    private boolean addByteOffset;
    private boolean cachePatternMatching;
    private Boolean annotateFileName =
            TaildirSourceConfigurationConstants.DEFAULT_FILE_HEADER;
    private String fileNameHeader =
            TaildirSourceConfigurationConstants.DEFAULT_FILENAME_HEADER_KEY;

    public Builder filePaths(Map<String, String> filePaths) {
      this.filePaths = filePaths;
      return this;
    }

    public Builder headerTable(Table<String, String, String> headerTable) {
      this.headerTable = headerTable;
      return this;
    }

    public Builder positionFilePath(String positionFilePath) {
      this.positionFilePath = positionFilePath;
      return this;
    }

    public Builder skipToEnd(boolean skipToEnd) {
      this.skipToEnd = skipToEnd;
      return this;
    }

    public Builder addByteOffset(boolean addByteOffset) {
      this.addByteOffset = addByteOffset;
      return this;
    }

    public Builder cachePatternMatching(boolean cachePatternMatching) {
      this.cachePatternMatching = cachePatternMatching;
      return this;
    }

    public Builder annotateFileName(boolean annotateFileName) {
      this.annotateFileName = annotateFileName;
      return this;
    }

    public Builder fileNameHeader(String fileNameHeader) {
      this.fileNameHeader = fileNameHeader;
      return this;
    }

    public ReliableTaildirEventReader build() throws IOException {
      return new ReliableTaildirEventReader(filePaths, headerTable, positionFilePath, skipToEnd,
                                            addByteOffset, cachePatternMatching,
                                            annotateFileName, fileNameHeader);
    }
  }
}
```



