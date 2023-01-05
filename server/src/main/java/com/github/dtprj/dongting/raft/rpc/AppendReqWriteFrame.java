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
package com.github.dtprj.dongting.raft.rpc;

import com.github.dtprj.dongting.buf.RefCountByteBuffer;
import com.github.dtprj.dongting.net.ZeroCopyWriteFrame;
import com.github.dtprj.dongting.pb.PbUtil;

import java.nio.ByteBuffer;

/**
 * @author huangli
 */
//  uint32 term = 1;
//  uint32 leader_id = 2;
//  fixed64 prev_log_index = 3;
//  uint32 prev_log_term = 4;
//  repeated bytes entries = 5;
//  fixed64 leader_commit = 6;
public class AppendReqWriteFrame extends ZeroCopyWriteFrame {

    private int term;
    private int leaderId;
    private long prevLogIndex;
    private int prevLogTerm;
    // TODO batch
    private boolean empty = false;
    private RefCountByteBuffer log;
    private long leaderCommit;

    @Override
    protected int accurateBodySize() {
        return PbUtil.accurateUnsignedIntSize(1, term)
                + PbUtil.accurateUnsignedIntSize(2, leaderId)
                + PbUtil.accurateFix64Size(3, prevLogIndex)
                + PbUtil.accurateUnsignedIntSize(4, prevLogTerm)
                + (empty ? 0 : PbUtil.accurateLengthDelimitedSize(5, log.getBuffer().remaining()))
                + PbUtil.accurateFix64Size(6, leaderCommit);
    }

    @Override
    protected void encodeBody(ByteBuffer buf) {
        PbUtil.writeUnsignedInt32(buf, 1, term);
        PbUtil.writeUnsignedInt32(buf, 2, leaderId);
        PbUtil.writeFix64(buf, 3, prevLogIndex);
        PbUtil.writeUnsignedInt32(buf, 4, prevLogTerm);
        PbUtil.writeLengthDelimitedPrefix(buf, 5, log.getBuffer().remaining());
        buf.put(log.getBuffer());
        log.release();
        log = null;
        PbUtil.writeFix64(buf, 6, leaderCommit);
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public void setLeaderId(int leaderId) {
        this.leaderId = leaderId;
    }

    public void setPrevLogIndex(long prevLogIndex) {
        this.prevLogIndex = prevLogIndex;
    }

    public void setPrevLogTerm(int prevLogTerm) {
        this.prevLogTerm = prevLogTerm;
    }

    public void setLeaderCommit(long leaderCommit) {
        this.leaderCommit = leaderCommit;
    }

    public void setLog(RefCountByteBuffer log) {
        this.empty = log == null;
        this.log = log;
    }
}
