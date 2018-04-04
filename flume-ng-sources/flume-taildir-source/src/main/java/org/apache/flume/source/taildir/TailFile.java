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

package org.apache.flume.source.taildir;

import com.google.common.collect.Lists;
import org.apache.flume.Event;
import org.apache.flume.event.EventBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;

import static org.apache.flume.source.taildir.TaildirSourceConfigurationConstants.BYTE_OFFSET_HEADER_KEY;

/**
 * TailFile 主要是判断file中是否有新的数据,如果有的新的数据并能够一行时,就将改行数据读取差转换成Event;
 * TailDirSource在读取数据生成event的时候是按行来读取的,文件中的每行(根据换行符来判断)都将生成一个event;
 */

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
          /*
           * 从文件中读取数据，放到buffer中
           * readFile()会更新bufferPos的值
           */
          readFile();
        } else {
          //已经是文件末尾了,并且是oldBuffer中还有数据
          if (oldBuffer.length > 0) {
            //将oldBuffer中的数据转换成lineResult,并清空oldBuffer
            lineResult = new LineResult(false, oldBuffer);
            oldBuffer = new byte[0];
            //更新文件读取的位置
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
            /* 更新bufferPos
             * 这种情况是由于在buffer中存在很多行,而在一次循环中每次只会读取（文件中的一行，以换行符来区别),这样需要
             * 经过很多次循环才能处理完buffer中的数据
             */
            bufferPos = i + 1;
          } else {
            //buffer中的数据已经处理完了，后续需要重新从文件中读取数据放到buffer中处理
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
