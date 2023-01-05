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
package com.github.dtprj.dongting.raft.server;

import com.github.dtprj.dongting.common.AbstractLifeCircle;
import com.github.dtprj.dongting.common.ObjUtil;
import com.github.dtprj.dongting.net.Commands;
import com.github.dtprj.dongting.net.HostPort;
import com.github.dtprj.dongting.net.NioClient;
import com.github.dtprj.dongting.net.NioClientConfig;
import com.github.dtprj.dongting.net.NioServer;
import com.github.dtprj.dongting.net.NioServerConfig;
import com.github.dtprj.dongting.raft.client.RaftException;
import com.github.dtprj.dongting.raft.impl.GroupConManager;
import com.github.dtprj.dongting.raft.impl.Raft;
import com.github.dtprj.dongting.raft.impl.RaftExecutor;
import com.github.dtprj.dongting.raft.impl.RaftStatus;
import com.github.dtprj.dongting.raft.impl.RaftThread;
import com.github.dtprj.dongting.raft.rpc.AppendProcessor;
import com.github.dtprj.dongting.raft.rpc.VoteProcessor;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author huangli
 */
public class RaftServer<I, O> extends AbstractLifeCircle {
    private final NioServer raftServer;
    private final NioClient raftClient;
    private final GroupConManager groupConManager;
    private final RaftThread raftThread;
    private final RaftStatus raftStatus;
    private final RaftLog raftLog;
    private final StateMachine stateMachine;

    public RaftServer(RaftServerConfig config, RaftLog raftLog, StateMachine<I, O> stateMachine) {
        this.raftLog = raftLog;
        this.stateMachine = stateMachine;
        Objects.requireNonNull(config.getServers());
        ObjUtil.checkPositive(config.getId(), "id");
        ObjUtil.checkPositive(config.getRaftPort(), "port");

        Set<HostPort> raftServers = GroupConManager.parseServers(config.getServers());

        int electQuorum = raftServers.size() / 2 + 1;
        int rwQuorum = raftServers.size() % 2 == 0 ? raftServers.size() / 2 : electQuorum;
        raftStatus = new RaftStatus(electQuorum, rwQuorum);

        NioClientConfig nioClientConfig = new NioClientConfig();
        nioClientConfig.setName("RaftClient");
        raftClient = new NioClient(nioClientConfig);

        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        RaftExecutor raftExecutor = new RaftExecutor(queue);
        groupConManager = new GroupConManager(config, raftClient, raftExecutor, raftStatus);

        NioServerConfig nioServerConfig = new NioServerConfig();
        nioServerConfig.setPort(config.getRaftPort());
        nioServerConfig.setName("RaftServer");
        nioServerConfig.setBizThreads(0);
        nioServerConfig.setIoThreads(1);
        raftServer = new NioServer(nioServerConfig);
        raftServer.register(Commands.RAFT_PING, this.groupConManager.getProcessor(), raftExecutor);
        raftServer.register(Commands.RAFT_APPEND_ENTRIES, new AppendProcessor(raftStatus), raftExecutor);
        raftServer.register(Commands.RAFT_REQUEST_VOTE, new VoteProcessor(raftStatus), raftExecutor);

        Raft raft = new Raft(config, raftExecutor, raftLog, raftStatus, raftClient);
        raftThread = new RaftThread(config, raftExecutor, raftStatus, raft, groupConManager);
    }

    @Override
    protected void doStart() {
        raftLog.init(stateMachine);
        // TODO
        //raftStatus.setLastLogIndex(raftLog.getLastLogIndex());
        //raftStatus.setLastLogTerm(raftLog.getLastLogTerm());
        raftServer.start();
        raftClient.start();
        raftClient.waitStart();
        raftThread.start();
        raftThread.waitInit();
    }

    @Override
    protected void doStop() {
        raftServer.stop();
        raftClient.stop();
        raftThread.requestShutdown();
        raftThread.interrupt();
        try {
            raftThread.join(100);
        } catch (InterruptedException e) {
            throw new RaftException(e);
        }
    }

}
