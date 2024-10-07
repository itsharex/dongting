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

import com.github.dtprj.dongting.codec.ByteArrayEncoder;
import com.github.dtprj.dongting.codec.DecodeContext;
import com.github.dtprj.dongting.codec.DecoderCallback;
import com.github.dtprj.dongting.dtkv.KvReq;
import com.github.dtprj.dongting.dtkv.KvResp;
import com.github.dtprj.dongting.dtkv.KvResult;
import com.github.dtprj.dongting.net.CmdCodes;
import com.github.dtprj.dongting.net.Commands;
import com.github.dtprj.dongting.net.EmptyBodyRespPacket;
import com.github.dtprj.dongting.net.EncodableBodyWritePacket;
import com.github.dtprj.dongting.net.ReadPacket;
import com.github.dtprj.dongting.net.ReqContext;
import com.github.dtprj.dongting.net.WritePacket;
import com.github.dtprj.dongting.raft.RaftException;
import com.github.dtprj.dongting.raft.impl.DecodeContextEx;
import com.github.dtprj.dongting.raft.server.AbstractRaftBizProcessor;
import com.github.dtprj.dongting.raft.server.RaftCallback;
import com.github.dtprj.dongting.raft.server.RaftGroup;
import com.github.dtprj.dongting.raft.server.RaftInput;
import com.github.dtprj.dongting.raft.server.RaftServer;
import com.github.dtprj.dongting.raft.server.ReqInfo;

import java.nio.charset.StandardCharsets;

/**
 * @author huangli
 */
public class KvProcessor extends AbstractRaftBizProcessor<KvReq> {
    public KvProcessor(RaftServer raftServer) {
        super(raftServer);
    }

    @Override
    public DecoderCallback<KvReq> createDecoderCallback(int cmd, DecodeContext context) {
        DecodeContextEx e = (DecodeContextEx) context;
        return context.toDecoderCallback(e.kvReqCallback());
    }

    @Override
    protected int getGroupId(ReadPacket<KvReq> frame) {
        return frame.getBody().getGroupId();
    }

    /**
     * run in io thread.
     */
    @Override
    protected WritePacket doProcess(ReqInfo<KvReq> reqInfo) {
        ReadPacket<KvReq> frame = reqInfo.getReqFrame();
        switch (frame.getCommand()) {
            case Commands.DTKV_GET:
                return doGet(reqInfo, frame);
            case Commands.DTKV_PUT:
                return doPut(reqInfo);
            case Commands.DTKV_REMOVE:
                return doRemove(reqInfo);
            default:
                throw new RaftException("unknown command: " + frame.getCommand());
        }
    }

    private WritePacket doGet(ReqInfo<KvReq> reqInfo, ReadPacket<KvReq> frame) {
        ReqContext reqContext = reqInfo.getReqContext();
        RaftGroup group = reqInfo.getRaftGroup();
        group.getLeaseReadIndex(reqContext.getTimeout()).whenComplete((logIndex, ex) -> {
            if (ex != null) {
                processError(reqInfo, ex);
            } else {
                DtKV dtKV = (DtKV) group.getStateMachine();
                byte[] bs = frame.getBody().getKey();
                KvResult r = dtKV.get(logIndex, new String(bs, StandardCharsets.UTF_8));
                KvResp resp = new KvResp(r.getData(), null, null);
                EncodableBodyWritePacket wf = new EncodableBodyWritePacket(resp);
                wf.setRespCode(CmdCodes.SUCCESS);
                wf.setBizCode(r.getBizCode());
                writeResp(reqInfo, wf);
            }
        });
        return null;
    }

    private WritePacket doRemove(ReqInfo<KvReq> reqInfo) {
        KvReq req = reqInfo.getReqFrame().getBody();
        RaftInput ri = new RaftInput(DtKV.BIZ_TYPE_REMOVE, new ByteArrayEncoder(req.getKey()), null,
                reqInfo.getReqContext().getTimeout(), false);
        reqInfo.getRaftGroup().submitLinearTask(ri, new RC(reqInfo));
        return null;
    }

    protected WritePacket doPut(ReqInfo<KvReq> reqInfo) {
        KvReq req = reqInfo.getReqFrame().getBody();
        RaftInput ri = new RaftInput(DtKV.BIZ_TYPE_PUT, new ByteArrayEncoder(req.getKey()), req.getValue(),
                reqInfo.getReqContext().getTimeout(), false);
        reqInfo.getRaftGroup().submitLinearTask(ri, new RC(reqInfo));
        return null;
    }

    private class RC implements RaftCallback {

        private final ReqInfo<KvReq> reqInfo;

        private RC(ReqInfo<KvReq> reqInfo) {
            this.reqInfo = reqInfo;
        }

        @Override
        public void success(long raftIndex, Object result) {
            KvResult r = (KvResult) result;
            EmptyBodyRespPacket resp = new EmptyBodyRespPacket(CmdCodes.SUCCESS);
            resp.setBizCode(r.getBizCode());
            writeResp(reqInfo, resp);
        }

        @Override
        public void fail(Throwable ex) {
            processError(reqInfo, ex);
        }
    }

}