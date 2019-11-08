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

package net.mbl.metrics2.lib;

import net.mbl.metrics2.MetricsInfo;
import net.mbl.metrics2.MetricsRecordBuilder;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A mutable long counter
 */
public class MutableCounterLong extends MutableCounter {
    private AtomicLong value = new AtomicLong();

    public MutableCounterLong(MetricsInfo info, long initValue) {
        super(info);
        this.value.set(initValue);
    }

    @Override
    public void incr() {
        incr(1);
    }

    /**
     * Increment the value by a delta
     *
     * @param delta of the increment
     */
    public void incr(long delta) {
        value.addAndGet(delta);
        setChanged();
    }

    public long value() {
        return value.get();
    }

    @Override
    public void snapshot(MetricsRecordBuilder builder, boolean all) {
        if (all || changed()) {
            builder.addCounter(info(), value());
            clearChanged();
        }
    }
}
