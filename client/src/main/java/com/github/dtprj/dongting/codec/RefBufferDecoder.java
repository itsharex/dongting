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

import com.github.dtprj.dongting.buf.RefBuffer;

import java.nio.ByteBuffer;

/**
 * @author huangli
 */
public class RefBufferDecoder extends Decoder<RefBuffer> {

    public static final RefBufferDecoder INSTANCE = new RefBufferDecoder(false);
    public static final RefBufferDecoder PLAIN_INSTANCE = new RefBufferDecoder(true);

    private final boolean plain;

    private RefBufferDecoder(boolean plain) {
        this.plain = plain;
    }

    @Override
    public RefBuffer doDecode(DecodeContext context, ByteBuffer buffer, int bodyLen, int currentPos) {
        RefBuffer result;
        boolean end = buffer.remaining() >= bodyLen - currentPos;
        if (currentPos == 0) {
            if (plain) {
                result = context.getHeapPool().createPlain(bodyLen);
            } else {
                result = context.getHeapPool().create(bodyLen);
            }
            context.setStatus(result);
        } else {
            result = (RefBuffer) context.getStatus();
        }
        ByteBuffer bb = result.getBuffer();
        bb.put(buffer);
        if (end) {
            bb.flip();
            context.setStatus(null);
            // release by user code
            return result;
        }
        return null;
    }

    @Override
    public void finish(DecodeContext context) {
        Object s = context.getStatus();
        if (s instanceof RefBuffer) {
            ((RefBuffer) s).release();
        }
    }
}
