/**
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package net.mbl.statedemo;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.mbl.event.Dispatcher;
import net.mbl.event.EventHandler;
import net.mbl.state.InvalidStateTransitionException;
import net.mbl.state.SingleArcTransition;
import net.mbl.state.StateMachine;
import net.mbl.state.StateMachineFactory;
import net.mbl.statedemo.event.ResourceEvent;
import net.mbl.statedemo.event.ResourceEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Datum representing a localized resource. Holds the statemachine of a
 * resource. State of the resource is one of {@link ResourceState}.
 * 
 */
public class LocalizedResource implements EventHandler<ResourceEvent> {

  private static final Logger LOG =
       LoggerFactory.getLogger(LocalizedResource.class);

  final Dispatcher dispatcher;
  final StateMachine<ResourceState, ResourceEventType,ResourceEvent>
    stateMachine;
  final Semaphore sem = new Semaphore(1);
                                // resource
  private final Lock readLock;
  private final Lock writeLock;
  private final LocalResourceRequest rsrc;

  final AtomicLong timestamp = new AtomicLong(currentTime());

  private static final StateMachineFactory<LocalizedResource,ResourceState,
        ResourceEventType,ResourceEvent> stateMachineFactory =
        new StateMachineFactory<LocalizedResource,ResourceState,
          ResourceEventType,ResourceEvent>(ResourceState.INIT)

    // From INIT (ref == 0, awaiting req)
    .addTransition(ResourceState.INIT, ResourceState.DOWNLOADING,
        ResourceEventType.REQUEST, new FetchResourceTransition())
    .addTransition(ResourceState.INIT, ResourceState.LOCALIZED,
        ResourceEventType.RECOVERED, new RecoveredTransition())

    // From DOWNLOADING (ref > 0, may be localizing)
    .addTransition(ResourceState.DOWNLOADING, ResourceState.DOWNLOADING,
        ResourceEventType.REQUEST, new FetchResourceTransition()) // TODO: Duplicate addition!!
    .addTransition(ResourceState.DOWNLOADING, ResourceState.LOCALIZED,
        ResourceEventType.LOCALIZED, new FetchSuccessTransition())
    .addTransition(ResourceState.DOWNLOADING,ResourceState.DOWNLOADING,
        ResourceEventType.RELEASE, new ReleaseTransition())
    .addTransition(ResourceState.DOWNLOADING, ResourceState.FAILED,
        ResourceEventType.LOCALIZATION_FAILED, new FetchFailedTransition())

    // From LOCALIZED (ref >= 0, on disk)
    .addTransition(ResourceState.LOCALIZED, ResourceState.LOCALIZED,
        ResourceEventType.REQUEST, new LocalizedResourceTransition())
    .addTransition(ResourceState.LOCALIZED, ResourceState.LOCALIZED,
        ResourceEventType.RELEASE, new ReleaseTransition())
    .installTopology();

  public LocalizedResource(LocalResourceRequest rsrc, Dispatcher dispatcher) {
    this.rsrc = rsrc;
    this.dispatcher = dispatcher;

    ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    this.readLock = readWriteLock.readLock();
    this.writeLock = readWriteLock.writeLock();

    this.stateMachine = stateMachineFactory.make(this);
  }

  private long currentTime() {
    return System.nanoTime();
  }

  public ResourceState getState() {
    this.readLock.lock();
    try {
      return stateMachine.getCurrentState();
    } finally {
      this.readLock.unlock();
    }
  }

  @Override
  public void handle(ResourceEvent event) {
    this.writeLock.lock();
    try {
      LOG.debug("Processing of type {}", event.getType());
      ResourceState oldState = this.stateMachine.getCurrentState();
      ResourceState newState = null;
      try {
        newState = this.stateMachine.doTransition(event.getType(), event);
      } catch (InvalidStateTransitionException e) {
        LOG.error("Can't handle this event at current state", e);
      }
      if (newState != null && oldState != newState) {
        LOG.debug("Resource transitioned from {} to {}", oldState, newState);
      }
    } finally {
      this.writeLock.unlock();
    }
  }

  static abstract class ResourceTransition implements
          SingleArcTransition<LocalizedResource,ResourceEvent> {
    // typedef
  }

  /**
   * Transition from INIT to DOWNLOADING.
   */
  @SuppressWarnings("unchecked") // dispatcher not typed
  private static class FetchResourceTransition extends ResourceTransition {
    @Override
    public void transition(LocalizedResource rsrc, ResourceEvent event) {
      System.out.println("f");
    }
  }

  /**
   * Resource localized, notify waiting containers.
   */
  @SuppressWarnings("unchecked") // dispatcher not typed
  private static class FetchSuccessTransition extends ResourceTransition {
    @Override
    public void transition(LocalizedResource rsrc, ResourceEvent event) {
      System.out.println("e");
    }
  }

  /**
   * Resource localization failed, notify waiting containers.
   */
  @SuppressWarnings("unchecked")
  private static class FetchFailedTransition extends ResourceTransition {
    @Override
    public void transition(LocalizedResource rsrc, ResourceEvent event) {
      System.out.println("d");
    }
  }

  /**
   * Resource already localized, notify immediately.
   */
  @SuppressWarnings("unchecked") // dispatcher not typed
  private static class LocalizedResourceTransition
      extends ResourceTransition {
    @Override
    public void transition(LocalizedResource rsrc, ResourceEvent event) {
      System.out.println("c");
    }
  }

  /**
   * Decrement resource count, update timestamp.
   */
  private static class ReleaseTransition extends ResourceTransition {
    @Override
    public void transition(LocalizedResource rsrc, ResourceEvent event) {
      System.out.println("b");
    }
  }

  private static class RecoveredTransition extends ResourceTransition {
    @Override
    public void transition(LocalizedResource rsrc, ResourceEvent event) {
      System.out.println("a");
    }
  }
}
