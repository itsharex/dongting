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
package com.github.dtprj.dongting.dtkv;

import com.github.dtprj.dongting.codec.ByteArrayEncoder;
import com.github.dtprj.dongting.codec.DecoderCallbackCreator;
import com.github.dtprj.dongting.common.AbstractLifeCircle;
import com.github.dtprj.dongting.common.DtTime;
import com.github.dtprj.dongting.net.Commands;
import com.github.dtprj.dongting.net.EncodableBodyWritePacket;
import com.github.dtprj.dongting.net.NetBizCodeException;
import com.github.dtprj.dongting.net.NioClientConfig;
import com.github.dtprj.dongting.net.RpcCallback;
import com.github.dtprj.dongting.raft.RaftClient;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * @author huangli
 */
@SuppressWarnings("Convert2Diamond")
public class KvClient extends AbstractLifeCircle {
    private final RaftClient raftClient;

    public KvClient(NioClientConfig nioClientConfig) {
        this.raftClient = new RaftClient(nioClientConfig);
    }

    public CompletableFuture<Void> put(int groupId, String key, byte[] value, DtTime timeout) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        KvReq r = new KvReq(groupId, key.getBytes(StandardCharsets.UTF_8),
                new ByteArrayEncoder(value), null, null, null);
        EncodableBodyWritePacket wf = new EncodableBodyWritePacket(r);
        wf.setCommand(Commands.DTKV_PUT);
        CompletableFuture<Void> f = new CompletableFuture<>();
        RpcCallback<Void> c = RpcCallback.create(f, p -> {
            int bc = p.getBizCode();
            if (bc != KvCodes.CODE_SUCCESS && bc != KvCodes.CODE_SUCCESS_OVERWRITE) {
                f.completeExceptionally(new NetBizCodeException(bc, p.getMsg()));
            }
            return null;
        });
        raftClient.sendRequest(groupId, wf, DecoderCallbackCreator.VOID_DECODE_CALLBACK_CREATOR, timeout, c);
        return f;
    }

    public CompletableFuture<KvNode> get(int groupId, String key, DtTime timeout) {
        Objects.requireNonNull(key);
        KvReq r = new KvReq(groupId, key.getBytes(StandardCharsets.UTF_8),
                null, null, null, null);
        EncodableBodyWritePacket wf = new EncodableBodyWritePacket(r);
        wf.setCommand(Commands.DTKV_GET);
        CompletableFuture<KvNode> f = new CompletableFuture<>();
        RpcCallback<KvResp> c = RpcCallback.create(f, p -> {
            int bc = p.getBizCode();
            if (bc != KvCodes.CODE_SUCCESS && bc != KvCodes.CODE_NOT_FOUND) {
                f.completeExceptionally(new NetBizCodeException(bc, p.getMsg()));
            }
            return p.getBody().getResult();
        });
        raftClient.sendRequest(groupId, wf, ctx -> ctx.toDecoderCallback(ctx.kvRespCallback()), timeout, c);
        return f;
    }

    public CompletableFuture<Void> remove(int groupId, String key, DtTime timeout) {
        Objects.requireNonNull(key);
        KvReq r = new KvReq(groupId, key.getBytes(StandardCharsets.UTF_8),
                null, null, null, null);
        EncodableBodyWritePacket wf = new EncodableBodyWritePacket(r);
        wf.setCommand(Commands.DTKV_REMOVE);
        CompletableFuture<Void> f = new CompletableFuture<>();
        RpcCallback<Void> c = RpcCallback.create(f, p -> {
            int bc = p.getBizCode();
            if (bc != KvCodes.CODE_SUCCESS && bc != KvCodes.CODE_NOT_FOUND) {
                f.completeExceptionally(new NetBizCodeException(bc, p.getMsg()));
            }
            return null;
        });
        raftClient.sendRequest(groupId, wf, DecoderCallbackCreator.VOID_DECODE_CALLBACK_CREATOR, timeout, c);
        return f;
    }

    @Override
    protected void doStart() {
        raftClient.start();
    }

    protected void doStop(DtTime timeout, boolean force) {
        raftClient.stop(timeout);
    }

    public RaftClient getRaftClient() {
        return raftClient;
    }
}
