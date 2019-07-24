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
package net.mbl.state;

/**
 * A State Transition Listener.
 * It exposes a pre and post transition hook called before and
 * after the transition.
 */
public interface StateTransitionListener
    <OPERAND, EVENT, STATE extends Enum<STATE>> {

  /**
   * Pre Transition Hook. This will be called before transition.
   * @param op Operand.
   * @param beforeState State before transition.
   * @param eventToBeProcessed Incoming Event.
   */
  void preTransition(OPERAND op, STATE beforeState, EVENT eventToBeProcessed);

  /**
   * Post Transition Hook. This will be called after the transition.
   * @param op Operand.
   * @param beforeState State before transition.
   * @param afterState State after transition.
   * @param processedEvent Processed Event.
   */
  void postTransition(OPERAND op, STATE beforeState, STATE afterState,
          EVENT processedEvent);
}
