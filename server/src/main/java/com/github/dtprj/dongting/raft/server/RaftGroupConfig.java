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

/**
 * @author huangli
 */
public class RaftGroupConfig {
    private final int groupId;
    private final String nodeIdOfMembers;
    private final String nodeIdOfObservers;
    private String dataDir = "./data";
    private String statusFile = "raft.status";
    private long[] ioRetryInterval = new long[]{100, 1000, 3000, 5000, 10000, 20000};
    private boolean syncForce = true;
    private boolean staticConfig = true;

    private int maxReplicateItems = 3000;
    private long maxReplicateBytes = 16 * 1024 * 1024;
    private int singleReplicateLimit = 1800 * 1024;

    private int maxPendingWrites = 10000;
    private long maxPendingWriteBytes = 256 * 1024 * 1024;

    private int idxCacheSize = 64 * 1024;
    private int idxFlushThreshold = 8 * 1024;

    private boolean ioCallbackUseGroupExecutor = false;

    public RaftGroupConfig(int groupId, String nodeIdOfMembers, String nodeIdOfObservers) {
        this.groupId = groupId;
        this.nodeIdOfMembers = nodeIdOfMembers;
        this.nodeIdOfObservers = nodeIdOfObservers;
    }

    public int getGroupId() {
        return groupId;
    }

    public String getNodeIdOfMembers() {
        return nodeIdOfMembers;
    }

    public String getNodeIdOfObservers() {
        return nodeIdOfObservers;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public String getStatusFile() {
        return statusFile;
    }

    public void setStatusFile(String statusFile) {
        this.statusFile = statusFile;
    }

    public long[] getIoRetryInterval() {
        return ioRetryInterval;
    }

    public void setIoRetryInterval(long[] ioRetryInterval) {
        this.ioRetryInterval = ioRetryInterval;
    }

    public boolean isSyncForce() {
        return syncForce;
    }

    public void setSyncForce(boolean syncForce) {
        this.syncForce = syncForce;
    }

    public int getMaxReplicateItems() {
        return maxReplicateItems;
    }

    public void setMaxReplicateItems(int maxReplicateItems) {
        this.maxReplicateItems = maxReplicateItems;
    }

    public long getMaxReplicateBytes() {
        return maxReplicateBytes;
    }

    public void setMaxReplicateBytes(long maxReplicateBytes) {
        this.maxReplicateBytes = maxReplicateBytes;
    }

    public int getSingleReplicateLimit() {
        return singleReplicateLimit;
    }

    public void setSingleReplicateLimit(int singleReplicateLimit) {
        this.singleReplicateLimit = singleReplicateLimit;
    }

    public int getMaxPendingWrites() {
        return maxPendingWrites;
    }

    public void setMaxPendingWrites(int maxPendingWrites) {
        this.maxPendingWrites = maxPendingWrites;
    }

    public long getMaxPendingWriteBytes() {
        return maxPendingWriteBytes;
    }

    public void setMaxPendingWriteBytes(long maxPendingWriteBytes) {
        this.maxPendingWriteBytes = maxPendingWriteBytes;
    }

    public boolean isStaticConfig() {
        return staticConfig;
    }

    public void setStaticConfig(boolean staticConfig) {
        this.staticConfig = staticConfig;
    }

    public int getIdxCacheSize() {
        return idxCacheSize;
    }

    public void setIdxCacheSize(int idxCacheSize) {
        this.idxCacheSize = idxCacheSize;
    }

    public int getIdxFlushThreshold() {
        return idxFlushThreshold;
    }

    public void setIdxFlushThreshold(int idxFlushThreshold) {
        this.idxFlushThreshold = idxFlushThreshold;
    }

    public boolean isIoCallbackUseGroupExecutor() {
        return ioCallbackUseGroupExecutor;
    }

    public void setIoCallbackUseGroupExecutor(boolean ioCallbackUseGroupExecutor) {
        this.ioCallbackUseGroupExecutor = ioCallbackUseGroupExecutor;
    }
}
