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
package com.github.dtprj.dongting.dtkv.server;

import com.github.dtprj.dongting.codec.DecodeContext;
import com.github.dtprj.dongting.codec.DecoderCallback;
import com.github.dtprj.dongting.codec.PbCallback;
import com.github.dtprj.dongting.codec.StrEncoder;
import com.github.dtprj.dongting.dtkv.RemoveReq;
import com.github.dtprj.dongting.net.CmdCodes;
import com.github.dtprj.dongting.net.Commands;
import com.github.dtprj.dongting.net.PbIntWritePacket;
import com.github.dtprj.dongting.net.ReadPacket;
import com.github.dtprj.dongting.net.ReqContext;
import com.github.dtprj.dongting.net.WritePacket;
import com.github.dtprj.dongting.raft.server.AbstractRaftBizProcessor;
import com.github.dtprj.dongting.raft.server.RaftCallback;
import com.github.dtprj.dongting.raft.server.RaftInput;
import com.github.dtprj.dongting.raft.server.RaftServer;
import com.github.dtprj.dongting.raft.server.ReqInfo;

import java.nio.ByteBuffer;

/**
 * @author huangli
 */
public class RemoveProcessor extends AbstractRaftBizProcessor<RemoveReq> {

    private static final class RemoveReqDecoderCallback extends PbCallback<RemoveReq> {
        private final RemoveReq result = new RemoveReq();
        @Override
        public boolean readVarNumber(int index, long value) {
            if (index == 1) {
                result.setGroupId((int) value);
            }
            return true;
        }

        @Override
        public boolean readBytes(int index, ByteBuffer buf, int fieldLen, int currentPos) {
            if (index == 2) {
                result.setKey(parseUTF8(buf, fieldLen, currentPos));
            }
            return true;
        }

        @Override
        public RemoveReq getResult() {
            return result;
        }
    }

    public RemoveProcessor(RaftServer raftServer) {
        super(raftServer);
    }

    @Override
    public DecoderCallback<RemoveReq> createDecoderCallback(int cmd, DecodeContext context) {
        return context.toDecoderCallback(new RemoveReqDecoderCallback());
    }

    @Override
    protected int getGroupId(ReadPacket<RemoveReq> frame) {
        return frame.getBody().getGroupId();
    }

    @Override
    protected WritePacket doProcess(ReqInfo<RemoveReq> reqInfo) {
        RemoveReq req = reqInfo.getReqFrame().getBody();
        ReqContext reqContext = reqInfo.getReqContext();
        RaftInput ri = new RaftInput(DtKV.BIZ_TYPE_REMOVE, new StrEncoder(req.getKey()), null,
                reqContext.getTimeout(), false);
        reqInfo.getRaftGroup().submitLinearTask(ri, new RaftCallback() {
            @Override
            public void success(long raftIndex, Object result) {
                PbIntWritePacket resp = new PbIntWritePacket(Commands.DTKV_REMOVE, (Boolean) result ? 1 : 0);
                resp.setRespCode(CmdCodes.SUCCESS);
                writeResp(reqInfo, resp);
            }

            @Override
            public void fail(Throwable ex) {
                processError(reqInfo, ex);
            }
        });
        return null;
    }
}
