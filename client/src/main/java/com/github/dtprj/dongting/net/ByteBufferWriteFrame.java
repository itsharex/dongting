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
package com.github.dtprj.dongting.net;

import com.github.dtprj.dongting.codec.EncodeContext;

import java.nio.ByteBuffer;

/**
 * @author huangli
 */
public class ByteBufferWriteFrame extends WriteFrame {
    private final ByteBuffer data;
    private int readBytes;
    private ByteBuffer srcCopy;

    public ByteBufferWriteFrame(ByteBuffer data) {
        this.data = data;
    }

    @Override
    protected int calcActualBodySize() {
        ByteBuffer body = this.data;
        return body == null ? 0 : body.remaining();
    }

    public static int copyFromHeapBuffer(ByteBuffer src, ByteBuffer dest, int readBytes) {
        int len = Math.min(src.remaining() - readBytes, dest.remaining());
        dest.put(src.array(), src.position() + readBytes, len);
        return readBytes + len;
    }

    public static ByteBuffer copyFromDirectBuffer(ByteBuffer src, ByteBuffer dest, ByteBuffer srcCopy) {
        if (srcCopy == null) {
            srcCopy = src.slice();
        }
        if (srcCopy.remaining() > dest.remaining()) {
            int limit = srcCopy.limit();
            srcCopy.limit(srcCopy.position() + dest.remaining());
            dest.put(srcCopy);
            srcCopy.limit(limit);
        } else {
            dest.put(srcCopy);
        }
        return srcCopy;
    }

    @Override
    protected boolean encodeBody(EncodeContext context, ByteBuffer dest) {
        if (data == null) {
            return true;
        }
        if (data.isDirect()) {
            srcCopy = copyFromDirectBuffer(data, dest, srcCopy);
            return srcCopy.remaining() == 0;
        } else {
            readBytes = copyFromHeapBuffer(data, dest, readBytes);
            return readBytes >= data.remaining();
        }
    }
}
