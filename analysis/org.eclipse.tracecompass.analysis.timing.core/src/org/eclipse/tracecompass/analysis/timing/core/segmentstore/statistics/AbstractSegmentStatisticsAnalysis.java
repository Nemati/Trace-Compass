/*******************************************************************************
 * Copyright (c) 2016 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.analysis.timing.core.segmentstore.statistics;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.timing.core.segmentstore.ISegmentStoreProvider;
import org.eclipse.tracecompass.analysis.timing.core.statistics.IStatistics;
import org.eclipse.tracecompass.analysis.timing.core.statistics.Statistics;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.ISegmentStore;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAbstractAnalysisModule;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

import com.google.common.collect.ImmutableList;

/**
 * Abstract analysis to build statistics data for a segment store
 *
 * @author Jean-Christian Kouame
 * @since 3.0
 */
public abstract class AbstractSegmentStatisticsAnalysis extends TmfAbstractAnalysisModule {

    private static Function<ISegment, Long> FCT_LENGTH = s -> s.getLength();

    private @Nullable ISegmentStoreProvider fSegmentStoreProviderModule;

    private @Nullable IStatistics<ISegment> fTotalStats;

    private Map<String, IStatistics<ISegment>> fPerSegmentTypeStats = new HashMap<>();

    @Override
    protected Iterable<IAnalysisModule> getDependentAnalyses() {
        ITmfTrace trace = getTrace();
        if (trace != null) {
            ISegmentStoreProvider provider = getSegmentProviderAnalysis(trace);
            fSegmentStoreProviderModule = provider;
            if (provider instanceof IAnalysisModule) {
                return ImmutableList.of((IAnalysisModule) provider);
            }
        }
        return super.getDependentAnalyses();
    }

    @Override
    protected boolean executeAnalysis(IProgressMonitor monitor) throws TmfAnalysisException {
        if (monitor.isCanceled()) {
            return false;
        }

        IStatistics<ISegment> totalStats = getTotalStats(TmfTimeRange.ETERNITY.getStartTime().toNanos(), TmfTimeRange.ETERNITY.getEndTime().toNanos(), monitor);
        if (totalStats == null) {
            return false;
        }

        Map<String, IStatistics<ISegment>> perTypeStats = getPerTypeStats(TmfTimeRange.ETERNITY.getStartTime().toNanos(), TmfTimeRange.ETERNITY.getEndTime().toNanos(), monitor);
        fTotalStats = totalStats;
        fPerSegmentTypeStats = perTypeStats;

        return true;
    }

    private @Nullable IStatistics<ISegment> getTotalStats(long start, long end, IProgressMonitor monitor) {
        Iterable<@NonNull ISegment> store = getSegmentStore(start, end);
        if (store == null) {
            return null;
        }
        if (monitor.isCanceled()) {
            return null;
        }
        return calculateTotalManual(store, monitor);
    }

    /**
     * Get the total statistics for a specific range. If the range start is
     * TmfTimeRange.ETERNITY.getStartTime().toNanos() and the range end is
     * TmfTimeRange.ETERNITY.getEndTime().toNanos(), it will return the
     * statistics for the whole trace.
     *
     * @param start
     *            The start time of the range
     * @param end
     *            The end time of the range
     * @param monitor
     *            The progress monitor
     * @return The total statistics, or null if segment store is not valid or if
     *         the request is canceled
     * @since 1.3
     */
    public @Nullable IStatistics<ISegment> getStatsForRange(long start, long end, IProgressMonitor monitor) {
        ITmfTrace trace = getTrace();
        if (trace != null && (start == TmfTimeRange.ETERNITY.getStartTime().toNanos() && end == TmfTimeRange.ETERNITY.getEndTime().toNanos())) {
            waitForCompletion();
            return getStatsTotal();
        }
        return getTotalStats(start, end, monitor);
    }

    private Map<@NonNull String, org.eclipse.tracecompass.analysis.timing.core.statistics.IStatistics<ISegment>> getPerTypeStats(long start, long end, IProgressMonitor monitor) {
        Iterable<@NonNull ISegment> store = getSegmentStore(start, end);
        if (monitor.isCanceled() || store == null) {
            return Collections.EMPTY_MAP;
        }
        return calculateTotalPerType(store, monitor);
    }

    /**
     * Get the per segment type statistics for a specific range. If the range
     * start is TmfTimeRange.ETERNITY.getStartTime().toNanos() and the range end
     * is TmfTimeRange.ETERNITY.getEndTime().toNanos(), it will return the
     * statistics for the whole trace.
     *
     * @param start
     *            The start time of the range
     * @param end
     *            The end time of the range
     * @param monitor
     *            The progress monitor
     * @return The per segment type statistics, or null if segment store is not
     *         valid or if the request is canceled
     * @since 1.3
     */
    public Map<@NonNull String, org.eclipse.tracecompass.analysis.timing.core.statistics.IStatistics<ISegment>> getStatsPerTypeForRange(long start, long end, IProgressMonitor monitor) {
        ITmfTrace trace = getTrace();
        if (trace != null && (start == TmfTimeRange.ETERNITY.getStartTime().toNanos() && end == TmfTimeRange.ETERNITY.getEndTime().toNanos())) {
            waitForCompletion();
            return getStatsPerType();
        }
        return getPerTypeStats(start, end, monitor);
    }

    /**
     * Get the segment store from which we want the statistics
     *
     * @return The segment store
     */
    private @Nullable Iterable<@NonNull ISegment> getSegmentStore(long start, long end) {
        ISegmentStoreProvider segmentStoreProviderModule = fSegmentStoreProviderModule;
        if (segmentStoreProviderModule == null) {
            return null;
        }
        if (segmentStoreProviderModule instanceof IAnalysisModule) {
            ((IAnalysisModule) segmentStoreProviderModule).waitForCompletion();
        }

        ISegmentStore<@NonNull ISegment> segmentStore = segmentStoreProviderModule.getSegmentStore();
        return segmentStore != null ? start != TmfTimeRange.ETERNITY.getStartTime().toNanos() || end != TmfTimeRange.ETERNITY.getEndTime().toNanos() ? (Iterable<@NonNull ISegment>) segmentStore.getIntersectingElements(start, end) : segmentStore
                : Collections.EMPTY_LIST;
    }

    private static @Nullable IStatistics<ISegment> calculateTotalManual(Iterable<@NonNull ISegment> segments, IProgressMonitor monitor) {
        IStatistics<ISegment> total = new Statistics<>(FCT_LENGTH);
        Iterator<@NonNull ISegment> iter = segments.iterator();
        while (iter.hasNext()) {
            if (monitor.isCanceled()) {
                return null;
            }
            ISegment segment = iter.next();
            total.update(segment);
        }
        return total;
    }

    private Map<@NonNull String, org.eclipse.tracecompass.analysis.timing.core.statistics.IStatistics<ISegment>> calculateTotalPerType(Iterable<ISegment> segments, IProgressMonitor monitor) {
        Map<String, IStatistics<ISegment>> perSegmentTypeStats = new HashMap<>();

        Iterator<ISegment> iter = segments.iterator();
        while (iter.hasNext()) {
            if (monitor.isCanceled()) {
                return Collections.EMPTY_MAP;
            }
            ISegment segment = iter.next();
            String segmentType = getSegmentType(segment);
            if (segmentType != null) {
                IStatistics<ISegment> values = perSegmentTypeStats.get(segmentType);
                if (values == null) {
                    values = new Statistics<>(FCT_LENGTH);
                }
                values.update(segment);
                perSegmentTypeStats.put(segmentType, values);
            }
        }
        return perSegmentTypeStats;
    }

    /**
     * Get the type of a segment. Statistics per type will use this type as a
     * key
     *
     * @param segment
     *            the segment for which to get the type
     * @return The type of the segment
     */
    protected abstract @Nullable String getSegmentType(ISegment segment);

    /**
     * Find the segment store provider used for this analysis
     *
     * @param trace
     *            The active trace
     *
     * @return The segment store provider
     */
    protected abstract @Nullable ISegmentStoreProvider getSegmentProviderAnalysis(ITmfTrace trace);

    @Override
    protected void canceling() {
    }

    /**
     * Get the statistics for the full segment store
     *
     * @return The complete statistics
     * @since 1.3
     */
    public @Nullable IStatistics<ISegment> getStatsTotal() {
        return fTotalStats;
    }

    /**
     * Get the statistics for each type of segment in this segment store
     *
     * @return the map of statistics per type
     * @since 1.3
     */
    public Map<String, IStatistics<ISegment>> getStatsPerType() {
        return fPerSegmentTypeStats;
    }

}
