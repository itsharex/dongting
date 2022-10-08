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

import java.nio.ByteBuffer;

/**
 * @author huangli
 */
public class ByteBufferDecoder extends Decoder {
    public static final ByteBufferDecoder INSTANCE = new ByteBufferDecoder();

    @Override
    public boolean supportHalfPacket() {
        return true;
    }

    @Override
    public Object decode(ByteBuffer buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object decode(Object status, ByteBuffer buffer, int frameLen, boolean start, boolean end) {
        ByteBuffer result;
        if (start) {
            result = ByteBuffer.allocate(frameLen);
        } else {
            result = (ByteBuffer) status;
        }
        result.put(buffer);
        if (end) {
            result.flip();
        }
        return result;
    }
}
