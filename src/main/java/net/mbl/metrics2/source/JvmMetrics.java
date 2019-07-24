/*
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

package net.mbl.metrics2.source;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import net.mbl.log.metrics.EventCounter;
import net.mbl.metrics2.MetricsCollector;
import net.mbl.metrics2.MetricsInfo;
import net.mbl.metrics2.MetricsRecordBuilder;
import net.mbl.metrics2.MetricsSource;
import net.mbl.metrics2.MetricsSystem;
import net.mbl.metrics2.lib.DefaultMetricsSystem;
import net.mbl.metrics2.lib.Interns;
import net.mbl.util.GcTimeMonitor;
import net.mbl.util.JvmPauseMonitor;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static net.mbl.metrics2.impl.MsInfo.ProcessName;
import static net.mbl.metrics2.impl.MsInfo.SessionId;
import static net.mbl.metrics2.source.JvmMetricsInfo.GcCount;
import static net.mbl.metrics2.source.JvmMetricsInfo.GcNumInfoThresholdExceeded;
import static net.mbl.metrics2.source.JvmMetricsInfo.GcNumWarnThresholdExceeded;
import static net.mbl.metrics2.source.JvmMetricsInfo.GcTimeMillis;
import static net.mbl.metrics2.source.JvmMetricsInfo.GcTimePercentage;
import static net.mbl.metrics2.source.JvmMetricsInfo.GcTotalExtraSleepTime;
import static net.mbl.metrics2.source.JvmMetricsInfo.JvmMetrics;
import static net.mbl.metrics2.source.JvmMetricsInfo.LogError;
import static net.mbl.metrics2.source.JvmMetricsInfo.LogFatal;
import static net.mbl.metrics2.source.JvmMetricsInfo.LogInfo;
import static net.mbl.metrics2.source.JvmMetricsInfo.LogWarn;
import static net.mbl.metrics2.source.JvmMetricsInfo.MemHeapCommittedM;
import static net.mbl.metrics2.source.JvmMetricsInfo.MemHeapMaxM;
import static net.mbl.metrics2.source.JvmMetricsInfo.MemHeapUsedM;
import static net.mbl.metrics2.source.JvmMetricsInfo.MemMaxM;
import static net.mbl.metrics2.source.JvmMetricsInfo.MemNonHeapCommittedM;
import static net.mbl.metrics2.source.JvmMetricsInfo.MemNonHeapMaxM;
import static net.mbl.metrics2.source.JvmMetricsInfo.MemNonHeapUsedM;
import static net.mbl.metrics2.source.JvmMetricsInfo.ThreadsBlocked;
import static net.mbl.metrics2.source.JvmMetricsInfo.ThreadsNew;
import static net.mbl.metrics2.source.JvmMetricsInfo.ThreadsRunnable;
import static net.mbl.metrics2.source.JvmMetricsInfo.ThreadsTerminated;
import static net.mbl.metrics2.source.JvmMetricsInfo.ThreadsTimedWaiting;
import static net.mbl.metrics2.source.JvmMetricsInfo.ThreadsWaiting;

/**
 * JVM and logging related metrics.
 * Mostly used by various servers as a part of the metrics they export.
 */
public class JvmMetrics implements MetricsSource {
    static public final float MEMORY_MAX_UNLIMITED_MB = -1;
    static final float M = 1024 * 1024;
    final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    final List<GarbageCollectorMXBean> gcBeans =
            ManagementFactory.getGarbageCollectorMXBeans();
    final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    final String processName, sessionId;
    final ConcurrentHashMap<String, MetricsInfo[]> gcInfoCache =
            new ConcurrentHashMap<String, MetricsInfo[]>();
    private JvmPauseMonitor pauseMonitor = null;
    private GcTimeMonitor gcTimeMonitor = null;

    @VisibleForTesting
    JvmMetrics(String processName, String sessionId) {
        this.processName = processName;
        this.sessionId = sessionId;
    }

    public static JvmMetrics create(String processName, String sessionId,
            MetricsSystem ms) {
        return ms.register(JvmMetrics.name(), JvmMetrics.description(),
                new JvmMetrics(processName, sessionId));
    }

    public static void reattach(MetricsSystem ms, JvmMetrics jvmMetrics) {
        ms.register(JvmMetrics.name(), JvmMetrics.description(), jvmMetrics);
    }

    public static JvmMetrics initSingleton(String processName, String sessionId) {
        return Singleton.INSTANCE.init(processName, sessionId);
    }

    /**
     * Shutdown the JvmMetrics singleton. This is not necessary if the JVM itself
     * is shutdown, but may be necessary for scenarios where JvmMetrics instance
     * needs to be re-created while the JVM is still around. One such scenario
     * is unit-testing.
     */
    public static void shutdownSingleton() {
        Singleton.INSTANCE.shutdown();
    }

    @VisibleForTesting
    public synchronized void registerIfNeeded() {
        // during tests impl might exist, but is not registered
        MetricsSystem ms = DefaultMetricsSystem.instance();
        if (ms.getSource("JvmMetrics") == null) {
            ms.register(JvmMetrics.name(), JvmMetrics.description(), this);
        }
    }

    public void setPauseMonitor(final JvmPauseMonitor pauseMonitor) {
        this.pauseMonitor = pauseMonitor;
    }

    public void setGcTimeMonitor(GcTimeMonitor gcTimeMonitor) {
        Preconditions.checkNotNull(gcTimeMonitor);
        this.gcTimeMonitor = gcTimeMonitor;
    }

    @Override
    public void getMetrics(MetricsCollector collector, boolean all) {
        MetricsRecordBuilder rb = collector.addRecord(JvmMetrics)
                .setContext("jvm").tag(ProcessName, processName)
                .tag(SessionId, sessionId);
        getMemoryUsage(rb);
        getGcUsage(rb);
        getThreadUsage(rb);
        getEventCounters(rb);
    }

    private void getMemoryUsage(MetricsRecordBuilder rb) {
        MemoryUsage memNonHeap = memoryMXBean.getNonHeapMemoryUsage();
        MemoryUsage memHeap = memoryMXBean.getHeapMemoryUsage();
        Runtime runtime = Runtime.getRuntime();
        rb.addGauge(MemNonHeapUsedM, memNonHeap.getUsed() / M)
                .addGauge(MemNonHeapCommittedM, memNonHeap.getCommitted() / M)
                .addGauge(MemNonHeapMaxM, calculateMaxMemoryUsage(memNonHeap))
                .addGauge(MemHeapUsedM, memHeap.getUsed() / M)
                .addGauge(MemHeapCommittedM, memHeap.getCommitted() / M)
                .addGauge(MemHeapMaxM, calculateMaxMemoryUsage(memHeap))
                .addGauge(MemMaxM, runtime.maxMemory() / M);
    }

    private float calculateMaxMemoryUsage(MemoryUsage memHeap) {
        long max = memHeap.getMax();

        if (max == -1) {
            return MEMORY_MAX_UNLIMITED_MB;
        }

        return max / M;
    }

    private void getGcUsage(MetricsRecordBuilder rb) {
        long count = 0;
        long timeMillis = 0;
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            long c = gcBean.getCollectionCount();
            long t = gcBean.getCollectionTime();
            MetricsInfo[] gcInfo = getGcInfo(gcBean.getName());
            rb.addCounter(gcInfo[0], c).addCounter(gcInfo[1], t);
            count += c;
            timeMillis += t;
        }
        rb.addCounter(GcCount, count)
                .addCounter(GcTimeMillis, timeMillis);

        if (pauseMonitor != null) {
            rb.addCounter(GcNumWarnThresholdExceeded,
                    pauseMonitor.getNumGcWarnThresholdExceeded());
            rb.addCounter(GcNumInfoThresholdExceeded,
                    pauseMonitor.getNumGcInfoThresholdExceeded());
            rb.addCounter(GcTotalExtraSleepTime,
                    pauseMonitor.getTotalGcExtraSleepTime());
        }

        if (gcTimeMonitor != null) {
            rb.addGauge(GcTimePercentage,
                    gcTimeMonitor.getLatestGcData().getGcTimePercentage());
        }
    }

    private MetricsInfo[] getGcInfo(String gcName) {
        MetricsInfo[] gcInfo = gcInfoCache.get(gcName);
        if (gcInfo == null) {
            gcInfo = new MetricsInfo[2];
            gcInfo[0] = Interns.info("GcCount" + gcName, "GC Count for " + gcName);
            gcInfo[1] = Interns
                    .info("GcTimeMillis" + gcName, "GC Time for " + gcName);
            MetricsInfo[] previousGcInfo = gcInfoCache.putIfAbsent(gcName, gcInfo);
            if (previousGcInfo != null) {
                return previousGcInfo;
            }
        }
        return gcInfo;
    }

    private void getThreadUsage(MetricsRecordBuilder rb) {
        int threadsNew = 0;
        int threadsRunnable = 0;
        int threadsBlocked = 0;
        int threadsWaiting = 0;
        int threadsTimedWaiting = 0;
        int threadsTerminated = 0;
        long threadIds[] = threadMXBean.getAllThreadIds();
        for (ThreadInfo threadInfo : threadMXBean.getThreadInfo(threadIds, 0)) {
            if (threadInfo == null) {
                continue; // race protection
            }
            switch (threadInfo.getThreadState()) {
                case NEW:
                    threadsNew++;
                    break;
                case RUNNABLE:
                    threadsRunnable++;
                    break;
                case BLOCKED:
                    threadsBlocked++;
                    break;
                case WAITING:
                    threadsWaiting++;
                    break;
                case TIMED_WAITING:
                    threadsTimedWaiting++;
                    break;
                case TERMINATED:
                    threadsTerminated++;
                    break;
            }
        }
        rb.addGauge(ThreadsNew, threadsNew)
                .addGauge(ThreadsRunnable, threadsRunnable)
                .addGauge(ThreadsBlocked, threadsBlocked)
                .addGauge(ThreadsWaiting, threadsWaiting)
                .addGauge(ThreadsTimedWaiting, threadsTimedWaiting)
                .addGauge(ThreadsTerminated, threadsTerminated);
    }

    private void getEventCounters(MetricsRecordBuilder rb) {
        rb.addCounter(LogFatal, EventCounter.getFatal())
                .addCounter(LogError, EventCounter.getError())
                .addCounter(LogWarn, EventCounter.getWarn())
                .addCounter(LogInfo, EventCounter.getInfo());
    }

    enum Singleton {
        INSTANCE;

        JvmMetrics impl;

        synchronized JvmMetrics init(String processName, String sessionId) {
            if (impl == null) {
                impl = create(processName, sessionId, DefaultMetricsSystem.instance());
            }
            return impl;
        }

        synchronized void shutdown() {
            DefaultMetricsSystem.instance().unregisterSource(JvmMetrics.name());
            impl = null;
        }
    }
}
