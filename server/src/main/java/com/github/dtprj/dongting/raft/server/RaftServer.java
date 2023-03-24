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
import com.github.dtprj.dongting.common.DtTime;
import com.github.dtprj.dongting.common.IntObjMap;
import com.github.dtprj.dongting.common.ObjUtil;
import com.github.dtprj.dongting.common.Timestamp;
import com.github.dtprj.dongting.log.BugLog;
import com.github.dtprj.dongting.log.DtLog;
import com.github.dtprj.dongting.log.DtLogs;
import com.github.dtprj.dongting.net.Commands;
import com.github.dtprj.dongting.net.HostPort;
import com.github.dtprj.dongting.net.NioClient;
import com.github.dtprj.dongting.net.NioClientConfig;
import com.github.dtprj.dongting.net.NioConfig;
import com.github.dtprj.dongting.net.NioServer;
import com.github.dtprj.dongting.net.NioServerConfig;
import com.github.dtprj.dongting.raft.client.RaftException;
import com.github.dtprj.dongting.raft.impl.ApplyManager;
import com.github.dtprj.dongting.raft.impl.CommitManager;
import com.github.dtprj.dongting.raft.impl.EventBus;
import com.github.dtprj.dongting.raft.impl.GroupComponents;
import com.github.dtprj.dongting.raft.impl.MemberManager;
import com.github.dtprj.dongting.raft.impl.NodeManager;
import com.github.dtprj.dongting.raft.impl.Raft;
import com.github.dtprj.dongting.raft.impl.RaftExecutor;
import com.github.dtprj.dongting.raft.impl.RaftGroupThread;
import com.github.dtprj.dongting.raft.impl.RaftNodeEx;
import com.github.dtprj.dongting.raft.impl.RaftRole;
import com.github.dtprj.dongting.raft.impl.RaftStatus;
import com.github.dtprj.dongting.raft.impl.RaftUtil;
import com.github.dtprj.dongting.raft.impl.ReplicateManager;
import com.github.dtprj.dongting.raft.impl.ShareStatus;
import com.github.dtprj.dongting.raft.impl.VoteManager;
import com.github.dtprj.dongting.raft.rpc.AppendProcessor;
import com.github.dtprj.dongting.raft.rpc.InstallSnapshotProcessor;
import com.github.dtprj.dongting.raft.rpc.NodePingProcessor;
import com.github.dtprj.dongting.raft.rpc.RaftPingProcessor;
import com.github.dtprj.dongting.raft.rpc.VoteProcessor;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author huangli
 */
public class RaftServer extends AbstractLifeCircle {
    private static final DtLog log = DtLogs.getLogger(RaftServer.class);
    private final NioServer raftServer;
    private final NioClient raftClient;

    private final IntObjMap<GroupComponents> groupComponentsMap = new IntObjMap<>();

    private final RaftServerConfig serverConfig;

    private final NodeManager nodeManager;

    private final int maxPendingWrites;
    private final long maxPendingWriteBytes;
    @SuppressWarnings({"unused"})
    private volatile int pendingWrites;
    @SuppressWarnings({"unused"})
    private volatile long pendingWriteBytes;

    private static final VarHandle PENDING_WRITES;
    private static final VarHandle PENDING_WRITE_BYTES;

    private final Timestamp readTimestamp = new Timestamp();


    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            PENDING_WRITES = lookup.findVarHandle(RaftServer.class, "pendingWrites", int.class);
            PENDING_WRITE_BYTES = lookup.findVarHandle(RaftServer.class, "pendingWriteBytes", long.class);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    public RaftServer(RaftServerConfig serverConfig, List<RaftGroupConfig> groupConfig,
                      List<RaftLog> raftLogs, List<StateMachine> stateMachines) {
        Objects.requireNonNull(serverConfig);
        Objects.requireNonNull(groupConfig);
        Objects.requireNonNull(raftLogs);
        Objects.requireNonNull(stateMachines);
        Objects.requireNonNull(serverConfig.getServers());

        if (groupConfig.size() != raftLogs.size() || groupConfig.size() != stateMachines.size()) {
            throw new IllegalArgumentException("groupConfig, raftLogs, stateMachines size not match");
        }

        ObjUtil.checkPositive(serverConfig.getNodeId(), "id");
        ObjUtil.checkPositive(serverConfig.getRaftPort(), "port");
        this.serverConfig = serverConfig;
        this.maxPendingWrites = serverConfig.getMaxPendingWrites();
        this.maxPendingWriteBytes = serverConfig.getMaxPendingWriteBytes();

        List<RaftNode> allRaftServers = RaftUtil.parseServers(serverConfig.getNodeId(), serverConfig.getServers());
        HashSet<Integer> allNodeIds = new HashSet<>();
        HashSet<HostPort> allNodeHosts = new HashSet<>();
        for (RaftNode rn : allRaftServers) {
            if (!allNodeIds.add(rn.getNodeId())) {
                throw new IllegalArgumentException("duplicate server id: " + rn.getNodeId());
            }
            if (!allNodeHosts.add(rn.getHostPort())) {
                throw new IllegalArgumentException("duplicate server host: " + rn.getHostPort());
            }
        }
        if (!allNodeIds.contains(serverConfig.getNodeId())) {
            throw new IllegalArgumentException("self id not found in servers list: " + serverConfig.getNodeId());
        }

        NioClientConfig nioClientConfig = new NioClientConfig();
        nioClientConfig.setName("RaftClient");
        setupNioConfig(nioClientConfig);
        raftClient = new NioClient(nioClientConfig);

        createRaftGroups(serverConfig, groupConfig, raftLogs, stateMachines, allNodeIds);
        nodeManager = new NodeManager(serverConfig, allRaftServers, raftClient, groupComponentsMap);
        groupComponentsMap.forEach((id, gc) -> {
            gc.getEventBus().register(nodeManager);
            return true;
        });

        NioServerConfig nioServerConfig = new NioServerConfig();
        nioServerConfig.setPort(serverConfig.getRaftPort());
        nioServerConfig.setName("RaftServer");
        nioServerConfig.setBizThreads(0);
        nioServerConfig.setIoThreads(1);
        setupNioConfig(nioServerConfig);
        raftServer = new NioServer(nioServerConfig);
        raftServer.register(Commands.NODE_PING, new NodePingProcessor(serverConfig.getNodeId(), nodeManager.getUuid()));
        raftServer.register(Commands.RAFT_PING, new RaftPingProcessor(groupComponentsMap));
        raftServer.register(Commands.RAFT_APPEND_ENTRIES, new AppendProcessor(groupComponentsMap));
        raftServer.register(Commands.RAFT_REQUEST_VOTE, new VoteProcessor(groupComponentsMap));
        raftServer.register(Commands.RAFT_INSTALL_SNAPSHOT, new InstallSnapshotProcessor(groupComponentsMap));
    }

    private void createRaftGroups(RaftServerConfig serverConfig, List<RaftGroupConfig> groupConfig,
                                  List<RaftLog> raftLogs, List<StateMachine> stateMachines,
                                  HashSet<Integer> allNodeIds) {
        for (int i = 0; i < groupConfig.size(); i++) {
            RaftGroupConfig rgc = groupConfig.get(i);
            StateMachine stateMachine = stateMachines.get(i);
            RaftLog raftLog = raftLogs.get(i);

            Objects.requireNonNull(rgc.getNodeIdOfMembers());

            EventBus eventBus = new EventBus();

            HashSet<Integer> nodeIdOfMembers = new HashSet<>();
            parseMemberIds(allNodeIds, nodeIdOfMembers, rgc.getNodeIdOfMembers(), rgc.getGroupId());
            if (nodeIdOfMembers.size() == 0 && serverConfig.isStaticConfig()) {
                throw new IllegalArgumentException("no member in group: " + rgc.getGroupId());
            }

            Set<Integer> nodeIdOfObservers;
            if (rgc.getNodeIdOfObservers() != null && rgc.getNodeIdOfObservers().trim().length() > 0) {
                nodeIdOfObservers = new HashSet<>();
                parseMemberIds(allNodeIds, nodeIdOfObservers, rgc.getNodeIdOfObservers(), rgc.getGroupId());
                for (int id : nodeIdOfMembers) {
                    if (nodeIdOfObservers.contains(id)) {
                        throw new IllegalArgumentException("member and observer has same node: " + id);
                    }
                }
            } else {
                nodeIdOfObservers = Collections.emptySet();
            }

            boolean isMember = nodeIdOfMembers.contains(serverConfig.getNodeId());
            boolean isObserver = nodeIdOfObservers.contains(serverConfig.getNodeId());
            if (!isMember && !isObserver && serverConfig.isStaticConfig()) {
                throw new IllegalArgumentException("self id not found in group members/observers list: " + serverConfig.getNodeId());
            }

            int electQuorum = RaftUtil.getElectQuorum(nodeIdOfMembers.size());
            int rwQuorum = RaftUtil.getRwQuorum(nodeIdOfMembers.size());
            RaftStatus raftStatus = new RaftStatus(electQuorum, rwQuorum, isMember ? RaftRole.follower : RaftRole.observer);
            RaftExecutor raftExecutor = new RaftExecutor();
            raftStatus.setRaftExecutor(raftExecutor);
            raftStatus.setNodeIdOfMembers(nodeIdOfMembers);
            raftStatus.setNodeIdOfObservers(nodeIdOfObservers);
            raftStatus.setGroupId(rgc.getGroupId());

            MemberManager memberManager = new MemberManager(serverConfig, raftClient, raftExecutor,
                    raftStatus, eventBus);
            ApplyManager applyManager = new ApplyManager(serverConfig.getNodeId(), raftLog, stateMachine, raftStatus, eventBus);
            CommitManager commitManager = new CommitManager(raftStatus, raftLog, applyManager);
            ReplicateManager replicateManager = new ReplicateManager(serverConfig, rgc, raftStatus, raftLog,
                    stateMachine, raftClient, raftExecutor, commitManager);

            Raft raft = new Raft(rgc, raftStatus, raftLog, applyManager, commitManager, replicateManager);
            VoteManager voteManager = new VoteManager(serverConfig, rgc, raftStatus, raftClient, raftExecutor, raft);
            RaftGroupThread raftGroupThread = new RaftGroupThread(serverConfig, rgc, raftStatus, raftLog, stateMachine, raftExecutor,
                    raft, memberManager, voteManager);

            eventBus.register(voteManager);

            GroupComponents gc = new GroupComponents();
            gc.setServerConfig(serverConfig);
            gc.setGroupConfig(rgc);
            gc.setRaftLog(raftLog);
            gc.setStateMachine(stateMachine);
            gc.setRaftGroupThread(raftGroupThread);
            gc.setRaftStatus(raftStatus);
            gc.setMemberManager(memberManager);
            gc.setVoteManager(voteManager);
            gc.setRaftExecutor(raftExecutor);
            gc.setApplyManager(applyManager);
            gc.setCommitManager(commitManager);
            gc.setReplicateManager(replicateManager);
            gc.setEventBus(eventBus);

            groupComponentsMap.put(rgc.getGroupId(), gc);
        }
    }

    private static void parseMemberIds(Set<Integer> allNodeIds, Set<Integer> nodeIdOfMembers, String str, int groupId) {
        String[] membersStr = str.split(",");
        for (String idStr : membersStr) {
            int id = Integer.parseInt(idStr.trim());
            if (!allNodeIds.contains(id)) {
                throw new IllegalArgumentException("member id " + id + " not in server list: groupId=" + groupId);
            }
            if (!nodeIdOfMembers.add(id)) {
                throw new IllegalArgumentException("duplicated raft member id " + id + ".  groupId=" + groupId);
            }
        }
    }

    private void setupNioConfig(NioConfig nc) {
        nc.setFinishPendingImmediatelyWhenChannelClose(true);
        nc.setMaxOutRequests(0);
        nc.setMaxInRequests(0);
        nc.setMaxInBytes(0);
        nc.setMaxBodySize(Integer.MAX_VALUE);
        nc.setMaxFrameSize(Integer.MAX_VALUE);
    }

    @Override
    protected void doStart() {
        groupComponentsMap.forEach((groupId, gc) -> {
            gc.getRaftGroupThread().init();
            return true;
        });

        raftServer.start();
        raftClient.start();
        raftClient.waitStart();

        nodeManager.start();
        nodeManager.waitReady(RaftUtil.getElectQuorum(nodeManager.getAllNodesEx().size()));
        log.info("nodeManager is ready");

        groupComponentsMap.forEach((groupId, gc) -> {
            gc.getMemberManager().init(nodeManager.getAllNodesEx());
            gc.getRaftGroupThread().start();
            return true;
        });

        groupComponentsMap.forEach((groupId, gc) -> {
            gc.getRaftGroupThread().waitReady(gc.getRaftStatus().getElectQuorum());
            log.info("raft group {} is ready", groupId);
            return true;
        });
    }

    @Override
    protected void doStop() {
        raftServer.stop();
        raftClient.stop();

        groupComponentsMap.forEach((groupId, gc) -> {
            RaftGroupThread raftGroupThread = gc.getRaftGroupThread();
            raftGroupThread.requestShutdown();
            raftGroupThread.interrupt();
            return true;
        });
    }

    private GroupComponents getGroupComponents(int groupId) {
        GroupComponents gc = groupComponentsMap.get(groupId);
        if (gc == null) {
            throw new RaftException("group not found: " + groupId);
        }
        return gc;
    }

    @SuppressWarnings("unused")
    public CompletableFuture<RaftOutput> submitLinearTask(int groupId, RaftInput input) throws RaftException {
        GroupComponents gc = getGroupComponents(groupId);
        RaftStatus raftStatus = gc.getRaftStatus();
        if (raftStatus.isError()) {
            throw new RaftException("raft status is error");
        }
        int size = input.size();
        if (size > serverConfig.getMaxBodySize()) {
            throw new RaftException("request size too large, size=" + size + ", maxBodySize=" + serverConfig.getMaxBodySize());
        }
        int currentPendingWrites = (int) PENDING_WRITES.getAndAddRelease(this, 1);
        if (currentPendingWrites >= maxPendingWrites) {
            String msg = "submitRaftTask failed: too many pending writes, currentPendingWrites=" + currentPendingWrites;
            log.warn(msg);
            PENDING_WRITES.getAndAddRelease(this, -1);
            throw new RaftException(msg);
        }
        long currentPendingWriteBytes = (long) PENDING_WRITE_BYTES.getAndAddRelease(this, size);
        if (currentPendingWriteBytes >= maxPendingWriteBytes) {
            String msg = "submitRaftTask failed: too many pending write bytes,currentPendingWriteBytes="
                    + currentPendingWriteBytes + ", currentRequestBytes=" + size;
            log.warn(msg);
            PENDING_WRITE_BYTES.getAndAddRelease(this, -size);
            throw new RaftException(msg);
        }
        CompletableFuture<RaftOutput> f = gc.getRaftGroupThread().submitRaftTask(input);
        registerCallback(f, size);
        return f;
    }

    private void registerCallback(CompletableFuture<?> f, int size) {
        f.whenComplete((o, ex) -> {
            PENDING_WRITES.getAndAddRelease(this, -1);
            PENDING_WRITE_BYTES.getAndAddRelease(this, -size);
        });
    }

    @SuppressWarnings("unused")
    public long getLogIndexForRead(int groupId, DtTime deadline)
            throws RaftException, InterruptedException, TimeoutException {
        GroupComponents gc = getGroupComponents(groupId);
        RaftStatus raftStatus = gc.getRaftStatus();
        if (raftStatus.isError()) {
            throw new RaftException("raft status error");
        }
        ShareStatus ss = raftStatus.getShareStatus();
        readTimestamp.refresh(1);
        if (ss.role != RaftRole.leader) {
            throw new NotLeaderException(RaftUtil.getLeader(ss.currentLeader));
        }
        long t = readTimestamp.getNanoTime();
        if (ss.leaseEndNanos - t < 0) {
            throw new NotLeaderException(null);
        }
        if (ss.firstCommitOfApplied != null) {
            try {
                ss.firstCommitOfApplied.get(deadline.rest(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS);
            } catch (ExecutionException e) {
                BugLog.log(e);
                throw new RaftException(e);
            }
        }
        return ss.lastApplied;
    }

    /**
     * ADMIN API. This method is idempotent. When future complete the new node is connected.
     * If the node is already in node list and connected, the future complete normally immediately.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<RaftNodeEx> addNode(RaftNode node) {
        return nodeManager.addNode(node);
    }

    /**
     * ADMIN API. If the node is node in node list, the future complete normally immediately.
     * If the reference count of the node is not 0, the future complete exceptionally.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> removeNode(int nodeId) {
        return nodeManager.removeNode(nodeId);
    }

    /**
     * ADMIN API. This method is idempotent.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> leaderPrepareJointConsensus(int groupId, Set<Integer> members, Set<Integer> observers) {
        Objects.requireNonNull(members);
        Objects.requireNonNull(observers);
        // node state change in scheduler thread, member state change in raft thread
        CompletableFuture<Void> f = new CompletableFuture<>();
        RaftUtil.SCHEDULED_SERVICE.execute(() -> nodeManager.leaderPrepareJointConsensus(f, groupId, members, observers));
        return f;
    }

    /**
     * ADMIN API. This method is idempotent.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> leaderAbortJointConsensus(int groupId) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        RaftUtil.SCHEDULED_SERVICE.execute(() -> nodeManager.leaderAbortJointConsensus(f, groupId));
        return f;
    }

    /**
     * ADMIN API. This method is idempotent.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> leaderCommitJointConsensus(int groupId) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        RaftUtil.SCHEDULED_SERVICE.execute(() -> nodeManager.leaderCommitJointConsensus(f, groupId));
        return f;
    }

    /**
     * ADMIN API.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> transferLeadership(int groupId, int nodeId, long timeoutMillis) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        DtTime deadline = new DtTime(timeoutMillis, TimeUnit.MILLISECONDS);
        GroupComponents gc = getGroupComponents(groupId);
        gc.getRaftStatus().setHoldRequest(true);
        gc.getMemberManager().transferLeadership(nodeId, f, deadline);
        return f;
    }

}
