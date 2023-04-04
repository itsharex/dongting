/*
 * Copyright The Dongting Project
 *
 * The Dongting Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.github.dtprj.dongting.raft.file;

import com.github.dtprj.dongting.common.BitUtil;
import com.github.dtprj.dongting.common.ObjUtil;
import com.github.dtprj.dongting.raft.client.RaftException;
import com.github.dtprj.dongting.raft.server.LogItem;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.zip.CRC32C;

/**
 * @author huangli
 */
public class LogFileQueue extends FileQueue {
    private static final int FILE_MAGIC = 0x7C3FA7B6;

    private static final int LOG_FILE_SIZE = 1024 * 1024 * 1024;
    private static final int FILE_LEN_MASK = LOG_FILE_SIZE - 1;
    private static final int FILE_LEN_SHIFT_BITS = BitUtil.zeroCountOfBinary(LOG_FILE_SIZE);

    private static final int FILE_HEADER_SIZE = 512;
    // crc32c 4 bytes, len 4 bytes, type 1 byte, term 4 bytes, prevLogTerm 4 bytes, index 8 bytes
    private static final int ITEM_HEADER_SIZE = 4 + 4 + 1 + 4 + 4 + 8;

    private final ByteBuffer buffer = ByteBuffer.allocateDirect(128 * 1024);
    private final CRC32C crc32c = new CRC32C();
    private long writePos;

    public LogFileQueue(File dir, Executor ioExecutor) {
        super(dir, ioExecutor);
    }

    @Override
    protected long getFileSize() {
        return LOG_FILE_SIZE;
    }

    @Override
    public int getFileLenShiftBits() {
        return FILE_LEN_SHIFT_BITS;
    }

    @Override
    protected long getWritePos() {
        return writePos;
    }

    @Override
    protected void afterFileAllocated(File f, FileChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocateDirect(8);
        header.putInt(FILE_MAGIC);
        // file version
        header.putInt(1);
        header.flip();
        channel.write(header);
        channel.force(false);
    }

    public void init(long nextPos) throws IOException {
        super.init();
        for (int i = 0; i < queue.size(); i++) {
            LogFile lf = queue.get(i);
        }
    }

    public void append(List<LogItem> logs) throws IOException {
        ensureWritePosReady();
        ByteBuffer buffer = this.buffer;
        buffer.clear();
        long pos = writePos;
        LogFile file = getLogFile(pos);
        for (LogItem log : logs) {
            ByteBuffer dataBuffer = log.getBuffer();
            long posOfFile = (pos + buffer.position()) & FILE_LEN_MASK;
            // if posOfFile == 0, it means last item exactly fill the file
            if (posOfFile == 0 || LOG_FILE_SIZE - posOfFile < ITEM_HEADER_SIZE + dataBuffer.remaining()) {
                pos = writeAndClearBuffer(buffer, file, pos);
                if (posOfFile != 0) {
                    pos = ((pos >>> FILE_LEN_SHIFT_BITS) + 1) << FILE_LEN_SHIFT_BITS;
                }
                ensureWritePosReady(pos);
                file = getLogFile(pos);
                pos += FILE_HEADER_SIZE;
            }
            if (buffer.remaining() < ITEM_HEADER_SIZE) {
                pos = writeAndClearBuffer(buffer, file, pos);
            }

            int crcPos = buffer.position();
            buffer.putInt(0);
            buffer.putInt(dataBuffer.remaining() + ITEM_HEADER_SIZE);
            buffer.put((byte) log.getType());
            buffer.putInt(log.getTerm());
            buffer.putInt(log.getPrevLogTerm());
            buffer.putLong(log.getIndex());
            updateCrc(buffer, dataBuffer, crcPos);

            while (dataBuffer.hasRemaining()) {
                buffer.put(dataBuffer);
                if (!buffer.hasRemaining()) {
                    pos = writeAndClearBuffer(buffer, file, pos);
                }
            }
        }
        pos = writeAndClearBuffer(buffer, file, pos);
        file.channel.force(false);
        this.writePos = pos;
    }

    private void updateCrc(ByteBuffer buffer, ByteBuffer dataBuffer, int crcPos) {
        CRC32C crc32c = this.crc32c;
        crc32c.reset();

        // backup position and limit
        int pos = buffer.position();
        int limit = buffer.limit();

        buffer.position(crcPos + 4);
        buffer.limit(pos);
        crc32c.update(buffer);

        // restore position and limit
        buffer.limit(limit);
        buffer.position(pos);

        // backup position, the data buffer is a read-only buffer, so we don't need to change its limit
        pos = dataBuffer.position();
        crc32c.update(dataBuffer);
        dataBuffer.position(pos);
    }

    private long writeAndClearBuffer(ByteBuffer buffer, LogFile file, long filePos) throws IOException {
        if (buffer.position() == 0) {
            return filePos;
        }
        buffer.flip();
        int x = buffer.remaining();
        while (buffer.hasRemaining()) {
            file.channel.write(buffer, filePos);
        }
        buffer.clear();
        return filePos + x;
    }

    public void truncateTail(long dataPosition) {
        ObjUtil.checkNotNegative(dataPosition, "dataPosition");
        writePos = dataPosition;
    }
}