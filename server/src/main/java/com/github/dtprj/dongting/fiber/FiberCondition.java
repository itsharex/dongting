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

/**
 * @author huangli
 */
public class FiberCondition extends WaitSource {

    public FiberCondition(FiberGroup group) {
        super(group);
    }

    @Override
    protected boolean shouldWait(Fiber currentFiber) {
        return true;
    }

    private Fiber check() {
        Fiber fiber = Dispatcher.checkAndGetCurrentFiber();
        if (fiber.fiberGroup != fiberGroup) {
            throw new FiberException("condition not belong to the current fiber group");
        }
        return fiber;
    }

    public void signal() {
        check();
        signal0();
    }

    public void signalAll() {
        check();
        signalAll0();
    }

    public FrameCallResult await(FrameCall<Void> resumePoint) {
        return Dispatcher.awaitOn(this, 0, resumePoint);
    }

    public FrameCallResult await(long millis, FrameCall<Void> resumePoint) {
        return Dispatcher.awaitOn(this, millis, resumePoint);
    }
}
