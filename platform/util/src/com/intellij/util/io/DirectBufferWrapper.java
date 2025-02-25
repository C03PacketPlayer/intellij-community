// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

@ApiStatus.Internal
public final class DirectBufferWrapper {
  @NotNull
  private static final ByteOrder ourNativeByteOrder = ByteOrder.nativeOrder();
  private static final int RELEASED_CODE = -1;

  private static final AtomicIntegerFieldUpdater<DirectBufferWrapper> REF_UPDATER =
    AtomicIntegerFieldUpdater.newUpdater(DirectBufferWrapper.class, "myReferences");

  private final @NotNull PagedFileStorage myFile;
  private final long myPosition;

  private volatile ByteBuffer myBuffer;
  private volatile boolean myDirty;
  private volatile int myReferences;
  private volatile int myBufferDataEndPos;

  //private final Stack<Throwable> myReferenceTraces = new Stack<>();

  DirectBufferWrapper(@NotNull PagedFileStorage file, long offset) throws IOException {
    myFile = file;
    myPosition = offset;
    myBuffer = DirectByteBufferAllocator.allocate(() -> create());
    myFile.getStorageLockContext().assertUnderSegmentAllocationLock();
  }

  private void markDirty() throws IOException {
    if (!myDirty) {
      if (myFile.isReadOnly()) {
        throw new IOException("Read-only byte buffer can't be modified. File: " + myFile);
      }
      myDirty = true;
      myFile.markDirty();
    }
  }

  private void fileSizeMayChanged(int bufferDataEndPos) {
    if (bufferDataEndPos > myBufferDataEndPos) {
      myBufferDataEndPos = bufferDataEndPos;
      myFile.ensureCachedSizeAtLeast(myPosition + myBufferDataEndPos);
    }
  }

  boolean isDirty() {
    return myDirty;
  }

  public ByteBuffer copy() {
    try {
      return DirectByteBufferAllocator.allocate(() -> {
        ByteBuffer duplicate = myBuffer.duplicate();
        duplicate.order(myBuffer.order());
        return duplicate;
      });
    }
    catch (IOException e) {
      // not expected there
      throw new RuntimeException(e);
    }
  }

  public byte get(int index) {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkReadAccess();

    return myBuffer.get(index);
  }

  public long getLong(int index) {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkReadAccess();

    return myBuffer.getLong(index);
  }

  public void putLong(int index, long value) throws IOException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    markDirty();
    myBuffer.putLong(index, value);
    fileSizeMayChanged(index + 8);
  }

  public int getInt(int index) {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkReadAccess();

    return myBuffer.getInt(index);
  }

  public void putInt(int index, int value) throws IOException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    markDirty();
    myBuffer.putInt(index, value);
    fileSizeMayChanged(index + 4);
  }

  public void position(int newPosition) {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    myBuffer.position(newPosition);
  }

  public int position() {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkReadAccess();

    return myBuffer.position();
  }

  public void put(ByteBuffer src) throws IOException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    markDirty();
    myBuffer.put(src);
    fileSizeMayChanged(myBuffer.position());
  }

  public void put(int index, byte b) throws IOException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    markDirty();
    myBuffer.put(index, b);
    fileSizeMayChanged(index + 1);
  }

  public void readToArray(byte[] dst, int o, int page_offset, int page_len) throws IllegalArgumentException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkReadAccess();

    ByteBuffer buf = myBuffer.duplicate();
    buf.position(page_offset);
    buf.get(dst, o, page_len);
  }

  public void putFromArray(byte[] src, int o, int page_offset, int page_len) throws IOException, IllegalArgumentException {
    StorageLockContext context = myFile.getStorageLockContext();
    context.checkWriteAccess();

    markDirty();
    ByteBuffer buf = myBuffer.duplicate();
    buf.position(page_offset);
    buf.put(src, o, page_len);
    fileSizeMayChanged(buf.position());
  }


  private ByteBuffer create() throws IOException {
    ByteBuffer buffer = ByteBuffer.allocateDirect(myFile.myPageSize);
    assert buffer.capacity() > 0;
    return myFile.useChannel(ch -> {
      ch.read(buffer, myPosition);
      return buffer;
    }, myFile.isReadOnly());
  }

  boolean tryRelease(boolean force) throws IOException {
    boolean releaseState = REF_UPDATER.updateAndGet(this, operand -> operand == 0 ? RELEASED_CODE : operand) == RELEASED_CODE;
    if (releaseState || force) {
      myFile.getStorageLockContext().assertUnderSegmentAllocationLock();

      if (isDirty()) force();
      if (myBuffer != null) {
        ByteBufferUtil.cleanBuffer(myBuffer);
        myBuffer = null;
      }

      if (force && !releaseState) {
        PagedFileStorage.LOG.error("Page buffer is referenced but was forcibly released for file " + myFile.getFile());
      }

      return true;
    }
    return false;
  }

  boolean isReleased() {
    return myReferences == RELEASED_CODE;
  }

  void force() throws IOException {
    myFile.getStorageLockContext().assertUnderSegmentAllocationLock();

    assert !myFile.isReadOnly();
    if (isDirty()) {
      ByteBuffer buffer = myBuffer.duplicate();
      buffer.rewind();
      buffer.limit(myBufferDataEndPos);

      myFile.useChannel(ch -> {
        ch.write(buffer, myPosition);
        return null;
      }, myFile.isReadOnly());

      myDirty = false;
    }
  }

  int getLength() {
    return myFile.myPageSize;
  }

  @Override
  public String toString() {
    return "Buffer for " + myFile + ", offset:" + myPosition + ", size: " + myFile.myPageSize;
  }

  public void useNativeByteOrder() {
    if (myBuffer.order() != ourNativeByteOrder) {
      myBuffer.order(ourNativeByteOrder);
    }
  }

  boolean belongs(@NotNull StorageLockContext context) {
    return myFile.getStorageLockContext() == context;
  }

  public boolean tryLock() {
    //myReferenceTraces.add(new Throwable());
    //assert !isReleased();
    return REF_UPDATER.updateAndGet(this, operand -> operand >= 0 ? operand + 1 : operand) >= 0;
  }

  public void unlock() {
    //myReferenceTraces.pop();
    int currentRefs = REF_UPDATER.decrementAndGet(this);
    assert currentRefs >= 0;
  }

  @NotNull PagedFileStorage getFile() {
    return myFile;
  }
}