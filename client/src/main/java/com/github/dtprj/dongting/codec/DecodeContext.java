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
package com.github.dtprj.dongting.codec;

import com.github.dtprj.dongting.buf.RefBufferFactory;
import com.github.dtprj.dongting.log.DtLog;
import com.github.dtprj.dongting.log.DtLogs;

/**
 * @author huangli
 */
public class DecodeContext {
    private static final DtLog log = DtLogs.getLogger(DecodeContext.class);
    static final int THREAD_LOCAL_BUFFER_SIZE = 4 * 1024;
    private static final ThreadLocal<byte[]> THREAD_LOCAL_BUFFER = ThreadLocal.withInitial(() -> new byte[THREAD_LOCAL_BUFFER_SIZE]);

    private final RefBufferFactory heapPool;
    PbParser nestedParser;
    Decoder nestedDecoder;

    // reset in PbParser and Decoder
    Object status;

    private DecodeContext nestedContext;

    private final byte[] threadLocalBuffer = THREAD_LOCAL_BUFFER.get();

    public DecodeContext(RefBufferFactory heapPool) {
        this.heapPool = heapPool;
    }

    public void reset(PbParser root) {
        reset();
        try {
            root.reset();
        } catch (Exception e) {
            log.error("reset error", e);
        }
    }

    public void reset(Decoder root) {
        reset();
        try {
            root.reset();
        } catch (Exception e) {
            log.error("reset error", e);
        }
    }

    private void reset() {
        if (nestedContext != null) {
            nestedContext.reset();
        }
        if (nestedParser != null) {
            nestedParser.reset();
        }
        if (nestedDecoder != null) {
            nestedDecoder.reset();
        }
    }

    public DecodeContext createOrGetNestedContext() {
        if (nestedContext == null) {
            nestedContext = new DecodeContext(heapPool);
        }
        return nestedContext;
    }

    PbParser prepareNestedParser(PbCallback<?> nestedCallback, int len) {
        if (nestedParser == null) {
            nestedParser = new PbParser(createOrGetNestedContext(), nestedCallback, len);
        } else {
            nestedParser.prepareNext(createOrGetNestedContext(), nestedCallback, len);
        }
        return nestedParser;
    }

    Decoder prepareNestedDecoder(DecoderCallback<?> nestedCallback) {
        if (nestedDecoder == null) {
            nestedDecoder = new Decoder(createOrGetNestedContext(), nestedCallback);
        } else {
            nestedDecoder.prepareNext(createOrGetNestedContext(), nestedCallback);
        }
        return nestedDecoder;
    }

    public PbParser getNestedParser() {
        return nestedParser;
    }

    public Decoder getNestedDecoder() {
        return nestedDecoder;
    }

    public RefBufferFactory getHeapPool() {
        return heapPool;
    }

    public byte[] getThreadLocalBuffer() {
        return threadLocalBuffer;
    }
}
