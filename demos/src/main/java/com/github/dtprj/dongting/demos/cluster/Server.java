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
package com.github.dtprj.dongting.demos.cluster;

import com.github.dtprj.dongting.dtkv.server.DtKV;
import com.github.dtprj.dongting.dtkv.server.KvConfig;
import com.github.dtprj.dongting.dtkv.server.KvServerUtil;
import com.github.dtprj.dongting.raft.server.DefaultRaftFactory;
import com.github.dtprj.dongting.raft.server.RaftGroupConfig;
import com.github.dtprj.dongting.raft.server.RaftGroupConfigEx;
import com.github.dtprj.dongting.raft.server.RaftServer;
import com.github.dtprj.dongting.raft.server.RaftServerConfig;
import com.github.dtprj.dongting.raft.sm.StateMachine;

import java.util.ArrayList;
import java.util.List;

/**
 * @author huangli
 */
public class Server {

    protected static RaftServer startServer(int nodeId, String servers, String members,
                                            String observers, int[] groupIds) {
        RaftServerConfig serverConfig = new RaftServerConfig();
        serverConfig.setServers(servers);
        serverConfig.setNodeId(nodeId);
        serverConfig.setReplicatePort(4000 + nodeId);
        serverConfig.setServicePort(5000 + nodeId);
        serverConfig.setElectTimeout(3000);
        serverConfig.setHeartbeatInterval(1000);

        List<RaftGroupConfig> groupConfigs = new ArrayList<>();
        for (int groupId : groupIds) {
            RaftGroupConfig groupConfig = RaftGroupConfig.newInstance(groupId, members, observers);
            groupConfig.setDataDir("target/raft_data_" + groupId + "_node" + nodeId);
            groupConfigs.add(groupConfig);
        }

        DefaultRaftFactory raftFactory = new DefaultRaftFactory() {
            @Override
            public StateMachine createStateMachine(RaftGroupConfigEx groupConfig) {
                return new DtKV(groupConfig, new KvConfig());
            }
        };

        RaftServer raftServer = new RaftServer(serverConfig, groupConfigs, raftFactory);
        KvServerUtil.initKvServer(raftServer);

        raftServer.start();
        return raftServer;
    }
}
