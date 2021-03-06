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

package net.mbl.metrics2.impl;

import net.mbl.metrics2.MetricsInfo;

import java.util.StringJoiner;

/**
 * Metrics system related metrics info instances
 */
public enum MsInfo implements MetricsInfo {
    NumActiveSources("Number of active metrics sources"),
    NumAllSources("Number of all registered metrics sources"),
    NumActiveSinks("Number of active metrics sinks"),
    NumAllSinks("Number of all registered metrics sinks"),
    Context("Metrics context"),
    Hostname("Local hostname"),
    SessionId("Session ID"),
    ProcessName("Process name");
    private final String desc;

    MsInfo(String desc) {
        this.desc = desc;
    }

    @Override
    public String description() {
        return desc;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", this.getClass().getSimpleName() + "{", "}")
                .add("name=" + name())
                .add("description=" + desc)
                .toString();
    }
}
