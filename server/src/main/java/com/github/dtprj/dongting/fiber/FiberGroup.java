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
package com.github.dtprj.dongting.fiber;

import com.github.dtprj.dongting.common.DtException;
import com.github.dtprj.dongting.common.DtUtil;
import com.github.dtprj.dongting.common.IndexedQueue;
import com.github.dtprj.dongting.log.BugLog;
import com.github.dtprj.dongting.log.DtLog;
import com.github.dtprj.dongting.log.DtLogs;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.HashSet;

/**
 * @author huangli
 */
public class FiberGroup {
    private static final DtLog log = DtLogs.getLogger(FiberGroup.class);
    private final String name;
    final Dispatcher dispatcher;
    final IndexedQueue<Fiber> readyFibers = new IndexedQueue<>(64);
    private final HashSet<Fiber> normalFibers = new HashSet<>();
    private final HashSet<Fiber> daemonFibers = new HashSet<>();

    @SuppressWarnings("FieldMayBeFinal")
    private volatile boolean shouldStop = false;
    private final static VarHandle SHOULD_STOP;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            SHOULD_STOP = l.findVarHandle(FiberGroup.class, "shouldStop", boolean.class);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    boolean finished;
    boolean ready;

    Fiber currentFiber;

    final FiberCondition shouldStopCondition = new FiberCondition(this);

    public FiberGroup(String name, Dispatcher dispatcher) {
        this.name = name;
        this.dispatcher = dispatcher;
    }

    /**
     * can call in any thread
     */
    public void fireFiber(Fiber fiber) {
        if (fiber.fiberGroup != this) {
            throw new DtException("fiber not in group");
        }
        // if the dispatcher stopped, no ops
        dispatcher.doInDispatcherThread(new FiberQueueTask() {
            @Override
            protected void run() {
                start(fiber);
            }
        });
    }

    /**
     * can call in any thread
     */
    public void fireTask(Runnable task) {
        // if the dispatcher stopped, no ops
        dispatcher.doInDispatcherThread(new FiberQueueTask() {
            @Override
            protected void run() {
                if (finished) {
                    log.warn("group already finished, ignore task", task);
                }
                task.run();
            }
        });
    }

    /**
     * can call in any thread
     */
    public void requestShutdown() {
        // if the dispatcher stopped, no ops
        dispatcher.doInDispatcherThread(new FiberQueueTask() {
            @Override
            protected void run() {
                if ((boolean) SHOULD_STOP.get(this)) {
                    return;
                }
                SHOULD_STOP.setVolatile(this, true);
                shouldStopCondition.signalAll();
                updateFinishStatus();
            }
        });
    }

    public static FiberGroup currentGroup() {
        return DispatcherThead.currentGroup();
    }

    public String getName() {
        return name;
    }

    public FiberCondition newCondition() {
        return new FiberCondition(this);
    }

    public <T> FiberFuture<T> newFuture() {
        return new FiberFuture<>(this);
    }

    public <T> FiberChannel<T> newChannel() {
        FiberChannel<T> channel = new FiberChannel<>(this);
        return channel;
    }

    public FiberLock newLock() {
        return new FiberLock(this);
    }

    void checkThread() {
        if (!DtUtil.DEBUG) {
            return;
        }
        if (Thread.currentThread() != dispatcher.thread) {
            throw new FiberException("not in dispatcher thread");
        }
    }

    void start(Fiber f) {
        if (f.started) {
            BugLog.getLog().error("fiber already started: {}", f.getFiberName());
            return;
        }
        if (finished) {
            log.error("group already finished");
            return;
        }
        if (f.daemon) {
            daemonFibers.add(f);
        } else {
            normalFibers.add(f);
        }
        if (f.source == null) {
            tryMakeFiberReady(f, false);
        } else {
            // the fiber is future callback fiber
            FiberFuture<?> future = (FiberFuture<?>) f.source;
            if (future.isDone()) {
                tryMakeFiberReady(f, true);
            } else {
                future.addWaiter(f);
            }
        }
    }

    void removeFiber(Fiber f) {
        boolean removed;
        if (f.daemon) {
            removed = daemonFibers.remove(f);
        } else {
            removed = normalFibers.remove(f);
            updateFinishStatus();
        }
        if (!removed) {
            BugLog.getLog().error("fiber is not in set: {}", f.getFiberName());
        }
    }

    void tryMakeFiberReady(Fiber f, boolean addFirst) {
        if (finished) {
            log.warn("group finished, ignore makeReady: {}", f.getFiberName());
            return;
        }
        if (f.finished) {
            log.warn("fiber already finished, ignore makeReady: {}", f.getFiberName());
            return;
        }
        if (!f.ready) {
            f.ready = true;
            if (addFirst) {
                readyFibers.addFirst(f);
            } else {
                readyFibers.addLast(f);
            }
            makeGroupReady();
        }
    }

    private void makeGroupReady() {
        if (ready) {
            return;
        }
        ready = true;
        dispatcher.readyGroups.addLast(this);
    }

    private void updateFinishStatus() {
        if (!finished) {
            boolean ss = (boolean) SHOULD_STOP.get(this);
            finished = ss && normalFibers.isEmpty();
        }
    }

    public boolean isShouldStop() {
        return (boolean) SHOULD_STOP.getOpaque(this);
    }

    boolean isShouldStopPlain() {
        return (boolean) SHOULD_STOP.get(this);
    }
}
