/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.tools.offlineImageViewer;

import java.io.IOException;
import java.util.LinkedList;

import org.apache.hadoop.hdfs.server.namenode.INode;

/**
 * LsImageVisitor displays the blocks of the namespace in a format very similar
 * to the output of ls/lsr.  Entries are marked as directories or not,
 * permissions listed, replication, username and groupname, along with size,
 * modification date and full path.
 *
 * Note: A significant difference between the output of the lsr command
 * and this image visitor is that this class cannot sort the file entries;
 * they are listed in the order they are stored within the fsimage file. 
 * Therefore, the output of this class cannot be directly compared to the
 * output of the lsr command.
 */
public class LsImageVisitor extends TextWriterImageVisitor {
  final private LinkedList<ImageElement> elemQ = new LinkedList<ImageElement>();

  private int numBlocks;
  private String perms;
  private String replication;
  private String username;
  private String group;
  private long filesize;
  private String modTime;
  private String path;
  private String linkTarget;
  private String type;
  private String hardlinkId;
  
  private boolean printHardlinkId = false;

  private boolean inInode = false;
  final private StringBuilder sb = new StringBuilder();

  public LsImageVisitor(String filename) throws IOException {
    super(filename);
  }
  
  public LsImageVisitor(String filename, boolean printToScreen) throws IOException {
    super(filename, printToScreen);
  }

  public LsImageVisitor(String filename, boolean printToScreen,
      int numberOfParts, boolean printHardlinkId) throws IOException {
    super(filename, printToScreen, numberOfParts);
    this.printHardlinkId = printHardlinkId;
  }

  /**
   * Start a new line of output, reset values.
   */
  private void newLine() {
    numBlocks = 0;
    perms = username = group = path = linkTarget = replication = hardlinkId = "";
    filesize = 0l;
    type = INode.INodeType.REGULAR_INODE.toString();
    inInode = true;
  }

  /**
   * All the values have been gathered.  Print them to the console in an
   * ls-style format.
   */
  private final static int widthRepl = 2;  
  private final static int widthUser = 8; 
  private final static int widthGroup = 10; 
  private final static int widthSize = 10;
  private final static int widthHardlinkId = 10;
  private final static int widthMod = 10;
  
  private final static String SPACE = " ";

  private void printLine() throws IOException {
    boolean hardlinktype =
      type.equals(INode.INodeType.HARDLINKED_INODE.toString());
    String file = (numBlocks < 0 ) ? "d" : ( (hardlinktype) ? "h" : "-");
    sb.append(file);
    sb.append(perms);

    if (0 != linkTarget.length()) {
      path = path + " -> " + linkTarget; 
    }

    printString(replication.equals("0") ? "-" : replication, widthRepl, sb);
    if (printHardlinkId) {
      printString(file.equals("h") ? hardlinkId : "-", widthHardlinkId, sb);
    }
    printString(username, widthUser, sb);
    printString(group, widthGroup, sb);
    printString(Long.toString(filesize), widthSize, sb);
    printString(modTime, widthMod, sb);
    printString(path, 0, sb);
    
    sb.append("\n");

    write(sb.toString());
    sb.setLength(0); // clear string builder

    inInode = false;
  }
  
  private void printString(String s, int width, StringBuilder sb) {
    int filler = Math.max(0, width - s.length());
    
    // insert an extra space
    for (int i = 0; i < filler + 1; i++) {
      sb.append(SPACE);
    }
    sb.append(s);
  }

  @Override
  void start() throws IOException {}

  @Override
  void finish() throws IOException {
    super.finish();
  }

  @Override
  void finishAbnormally() throws IOException {
    System.out.println("Input ended unexpectedly.");
    super.finishAbnormally();
  }

  @Override
  void leaveEnclosingElement() throws IOException {
    ImageElement elem = elemQ.pop();

    if(elem == ImageElement.INODE) {
      printLine();
      super.rollIfNeeded();
    }
  }

  @Override
  void visit(ImageElement element, long value) throws IOException {
    if(inInode) {
      switch(element) {
      case NUM_BYTES:
        filesize += value;
        break;
      case MODIFICATION_TIME:
        visit(element, ImageLoaderCurrent.formatDate(value));
        break;
      // these elements are not processed by LsImageVisitor
      // we can discard them prior to converting to String
      case ACCESS_TIME:
      case NS_QUOTA:
      case DS_QUOTA:
      case BLOCK_SIZE:
      case BLOCK_ID:
      case GENERATION_STAMP:
        break;
      default:
        visit(element, Long.toString(value));
      }
    }
  }
  
  // Maintain state of location within the image tree and record
  // values needed to display the inode in ls-style format.
  @Override
  void visit(ImageElement element, String value) throws IOException {
    if(inInode) {
      switch(element) {
      case INODE_PATH:
        if(value.equals("")) path = "/";
        else path = value;
        break;
      case PERMISSION_STRING:
        perms = value;
        break;
      case REPLICATION:
        replication = value;
        break;
      case USER_NAME:
        username = value;
        break;
      case GROUP_NAME:
        group = value;
        break;
      case NUM_BYTES:
        filesize += Long.valueOf(value);
        break;
      case MODIFICATION_TIME:
        modTime = value;
        break;
      case SYMLINK:
        linkTarget = value;
        break;
      case INODE_TYPE:
        type = value;
        break;
      case INODE_HARDLINK_ID:
        hardlinkId = value;
        break;
      default:
        // This is OK.  We're not looking for all the values.
        break;
      }
    }
  }

  @Override
  void visitEnclosingElement(ImageElement element) throws IOException {
    elemQ.push(element);
    if (element == ImageElement.INODE)
      newLine();
  }

  @Override
  void visitEnclosingElement(ImageElement element,
      ImageElement key, String value) throws IOException {
    elemQ.push(element);
    if(element == ImageElement.INODE)
      newLine();
    else if (element == ImageElement.BLOCKS)
      numBlocks = Integer.valueOf(value);
  }
  
  @Override
  void visitEnclosingElement(ImageElement element,
      ImageElement key, int value) throws IOException {
    elemQ.push(element);
    if(element == ImageElement.INODE)
      newLine();
    else if (element == ImageElement.BLOCKS)
      numBlocks = value;
  }
}
