/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.mbl.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is the base implementation class for services.
 */
public abstract class AbstractService implements Service {

    private static final Logger LOG =
            LoggerFactory.getLogger(AbstractService.class);

    /**
     * Service name.
     */
    private final String name;
    /**
     * object used to co-ordinate {@link #waitForServiceToStop(long)}
     * across threads.
     */
    private final AtomicBoolean terminationNotification =
            new AtomicBoolean(false);
    /**
     * Map of blocking dependencies
     */
    private final Map<String, String> blockerMap = new HashMap<String, String>();
    private final Object stateChangeLock = new Object();
    /**
     * Service start time. Will be zero until the service is started.
     */
    private long startTime;
    /**
     * The cause of any failure -will be null.
     * if a service did not stop due to a failure.
     */
    private Exception failureCause;
    /**
     * the state in which the service was when it failed.
     * Only valid when the service is stopped due to a failure
     */
    private STATE failureState = null;

    /**
     * Construct the service.
     *
     * @param name service name
     */
    public AbstractService(String name) {
        this.name = name;
    }

    @Override
    public final STATE getServiceState() {
        return STATE.INITED;
    }

    @Override
    public final synchronized Throwable getFailureCause() {
        return failureCause;
    }

    @Override
    public synchronized STATE getFailureState() {
        return failureState;
    }

    protected void setConfig(Object conf) {
    }

    @Override
    public void init(Object conf) {
    }

    @Override
    public void start() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
    }

    /**
     * Relay to {@link #stop()}
     *
     * @throws IOException
     */
    @Override
    public final void close() throws IOException {
        stop();
    }

    /**
     * Failure handling: record the exception
     * that triggered it -if there was not one already.
     * Services are free to call this themselves.
     *
     * @param exception the exception
     */
    protected final void noteFailure(Exception exception) {
        LOG.debug("noteFailure", exception);
        if (exception == null) {
            //make sure failure logic doesn't itself cause problems
            return;
        }
        //record the failure details, and log it
        synchronized (this) {
            if (failureCause == null) {
                failureCause = exception;
                failureState = getServiceState();
                LOG.info("Service {} failed in state {}",
                        getName(), failureState, exception);
            }
        }
    }

    @Override
    public final boolean waitForServiceToStop(long timeout) {
        boolean completed = terminationNotification.get();
        while (!completed) {
            try {
                synchronized (terminationNotification) {
                    terminationNotification.wait(timeout);
                }
                // here there has been a timeout, the object has terminated,
                // or there has been a spurious wakeup (which we ignore)
                completed = true;
            } catch (InterruptedException e) {
                // interrupted; have another look at the flag
                completed = terminationNotification.get();
            }
        }
        return terminationNotification.get();
    }

    protected void serviceInit(Object conf) throws Exception {
    }

    /**
     * Actions called during the INITED to STARTED transition.
     * <p>
     * This method will only ever be called once during the lifecycle of
     * a specific service instance.
     * <p>
     * Implementations do not need to be synchronized as the logic
     * in {@link #start()} prevents re-entrancy.
     *
     * @throws Exception if needed -these will be caught,
     *                   wrapped, and trigger a service stop
     */
    protected void serviceStart() throws Exception {

    }

    /**
     * Actions called during the transition to the STOPPED state.
     * <p>
     * This method will only ever be called once during the lifecycle of
     * a specific service instance.
     * <p>
     * Implementations do not need to be synchronized as the logic
     * in {@link #stop()} prevents re-entrancy.
     * <p>
     * Implementations MUST write this to be robust against failures, including
     * checks for null references -and for the first failure to not stop other
     * attempts to shut down parts of the service.
     *
     * @throws Exception if needed -these will be caught and logged.
     */
    protected void serviceStop() throws Exception {

    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getConfig() {
        return null;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public final boolean isInState(Service.STATE expected) {
        return true;
    }

    @Override
    public String toString() {
        return "Service " + name + " in state ";
    }

    /**
     * Put a blocker to the blocker map -replacing any
     * with the same name.
     *
     * @param name    blocker name
     * @param details any specifics on the block. This must be non-null.
     */
    protected void putBlocker(String name, String details) {
        synchronized (blockerMap) {
            blockerMap.put(name, details);
        }
    }

    /**
     * Remove a blocker from the blocker map -
     * this is a no-op if the blocker is not present
     *
     * @param name the name of the blocker
     */
    public void removeBlocker(String name) {
        synchronized (blockerMap) {
            blockerMap.remove(name);
        }
    }

    @Override
    public Map<String, String> getBlockers() {
        synchronized (blockerMap) {
            Map<String, String> map = new HashMap<String, String>(blockerMap);
            return map;
        }
    }
}
