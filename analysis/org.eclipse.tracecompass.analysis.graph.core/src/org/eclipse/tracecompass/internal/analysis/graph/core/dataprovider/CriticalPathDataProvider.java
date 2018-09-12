/**********************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/

package org.eclipse.tracecompass.internal.analysis.graph.core.dataprovider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.graph.core.base.IGraphWorker;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge.EdgeType;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfGraph;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex;
import org.eclipse.tracecompass.analysis.graph.core.criticalpath.CriticalPathModule;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.internal.analysis.graph.core.base.TmfGraphStatistics;
import org.eclipse.tracecompass.internal.analysis.graph.core.base.TmfGraphVisitor;
import org.eclipse.tracecompass.internal.tmf.core.model.AbstractTmfTraceDataProvider;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.TimeGraphStateQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.CommonStatusMessage;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphArrow;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphDataProvider;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphState;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphArrow;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphState;
import org.eclipse.tracecompass.tmf.core.response.ITmfResponse.Status;
import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.TreeMultimap;

/**
 * Data Provider for the Critical Path.
 *
 * @author Loic Prieur-Drevon
 */
public class CriticalPathDataProvider extends AbstractTmfTraceDataProvider implements ITimeGraphDataProvider<@NonNull CriticalPathEntry> {

    /**
     * Extension point ID for the provider
     */
    public static final @NonNull String ID = "org.eclipse.tracecompass.analysis.graph.core.dataprovider.CriticalPathDataProvider"; //$NON-NLS-1$
    /**
     * Atomic long to assign each entry the same unique ID every time the data
     * provider is queried
     */
    private static final AtomicLong ATOMIC_LONG = new AtomicLong();

    private @NonNull CriticalPathModule fCriticalPathModule;

    /**
     * Remember the unique mappings from hosts to their entry IDs
     */
    private final Map<String, Long> fHostIdToEntryId = new HashMap<>();
    /**
     * Remember the unique mappings from workers to their entry IDs
     */
    private final BiMap<IGraphWorker, Long> fWorkerToEntryId = HashBiMap.create();

    private final LoadingCache<IGraphWorker, CriticalPathVisitor> fHorizontalVisitorCache = CacheBuilder.newBuilder()
            .maximumSize(10).build(new CacheLoader<IGraphWorker, CriticalPathVisitor>() {

                @Override
                public CriticalPathVisitor load(IGraphWorker key) throws Exception {
                    TmfGraph criticalPath = fCriticalPathModule.getCriticalPath();
                    return new CriticalPathVisitor(criticalPath, key);
                }
            });

    /**
     * FIXME when switching between traces, the current worker is set to null, do
     * this to remember the last arrows used.
     */
    private List<ITimeGraphArrow> fLinks;

    /**
     * Constructor
     *
     * @param trace
     *            The trace for which we will be providing the time graph models
     * @param criticalPathProvider
     *            the critical path module for this trace
     */
    public CriticalPathDataProvider(@NonNull ITmfTrace trace, @NonNull CriticalPathModule criticalPathProvider) {
        super(trace);
        fCriticalPathModule = criticalPathProvider;
    }

    @Override
    public synchronized @NonNull TmfModelResponse<@NonNull List<@NonNull CriticalPathEntry>> fetchTree(
            @NonNull TimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        TmfGraph graph = fCriticalPathModule.getCriticalPath();
        if (graph == null) {
            return new TmfModelResponse<>(null, Status.RUNNING, CommonStatusMessage.RUNNING);
        }

        IGraphWorker current = getCurrent();
        if (current == null) {
            return new TmfModelResponse<>(null, Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }
        System.out.println("Will print workers.......");
        System.out.println(current + "host:" + current.getHostId());
        graph.getWorkers().forEach(System.out::println);
        System.out.println("Done");

        CriticalPathVisitor visitor = fHorizontalVisitorCache.getUnchecked(current);
        return new TmfModelResponse<>(visitor.getEntries(), Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    /**
     * Get the current {@link IGraphWorker} from the {@link CriticalPathModule}
     *
     * @return the current graph worker if it is set, else null.
     */
    private @Nullable IGraphWorker getCurrent() {
        Object obj = fCriticalPathModule.getParameter(CriticalPathModule.PARAM_WORKER);
        if (obj == null) {
            return null;
        }
        if (!(obj instanceof IGraphWorker)) {
            throw new IllegalStateException("Wrong type for critical path module parameter " + //$NON-NLS-1$
                    CriticalPathModule.PARAM_WORKER +
                    " expected IGraphWorker got " + //$NON-NLS-1$
                    obj.getClass().getSimpleName());
        }
        return (IGraphWorker) obj;
    }

    @Override
    public @NonNull String getId() {
        return ID;
    }

    @Override
    public @NonNull TmfModelResponse<@NonNull List<@NonNull ITimeGraphRowModel>> fetchRowModel(@NonNull SelectionTimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        IGraphWorker graphWorker = getCurrent();
        if (graphWorker == null) {
            return new TmfModelResponse<>(null, Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }
        CriticalPathVisitor visitor = fHorizontalVisitorCache.getIfPresent(graphWorker);
        if (visitor == null) {
            return new TmfModelResponse<>(null, Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }

        Map<@NonNull Integer, @NonNull Predicate<@NonNull Map<@NonNull String, @NonNull String>>> predicates = new HashMap<>();
        if (filter instanceof TimeGraphStateQueryFilter) {
            TimeGraphStateQueryFilter timeEventFilter = (TimeGraphStateQueryFilter) filter;
            predicates.putAll(computeRegexPredicate(timeEventFilter));
        }

        List<@NonNull ITimeGraphRowModel> rowModels = new ArrayList<>();
        for (Long id : filter.getSelectedItems()) {
            /*
             * need to use asMap, so that we don't return a row for an ID that does not
             * belong to this provider, else fStates.get(id) might return an empty
             * collection for an id from another data provider.
             */
            Collection<ITimeGraphState> states = visitor.fStates.asMap().get(id);
            if (states != null) {
                List<ITimeGraphState> filteredStates = new ArrayList<>();
                for (ITimeGraphState state : states) {
                    if (overlaps(state.getStartTime(), state.getDuration(), filter.getTimesRequested())) {
                        // Reset the properties for this state before filtering
                        state.setActiveProperties(0);
                        addToStateList(filteredStates, state, id, predicates, monitor);
                    }
                }
                rowModels.add(new TimeGraphRowModel(id, filteredStates));
            }
        }
        return new TmfModelResponse<>(rowModels, Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    private static final boolean overlaps(long start, long duration, long[] times) {
        int pos = Arrays.binarySearch(times, start);
        if (pos >= 0) {
            // start is one of the times
            return true;
        }
        int ins = -pos - 1;
        if (ins >= times.length) {
            // start is larger than the last time
            return false;
        }
        /*
         * the first queried time which is larger than the state start time, is also
         * smaller than the state end time. I.e. at least one queried time is in the
         * state range
         */
        return times[ins] <= start + duration;
    }

    @Override
    public @NonNull TmfModelResponse<@NonNull List<@NonNull ITimeGraphArrow>> fetchArrows(@NonNull TimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        return new TmfModelResponse<>(getLinkList(filter.getStart(), filter.getEnd()), Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Override
    public @NonNull TmfModelResponse<@NonNull Map<@NonNull String, @NonNull String>> fetchTooltip(@NonNull SelectionTimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        IGraphWorker worker = fWorkerToEntryId.inverse().get(filter.getSelectedItems().iterator().next());
        if (worker == null) {
            return new TmfModelResponse<>(null, Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }
        Map<@NonNull String, @NonNull String> info = worker.getWorkerInformation(filter.getStart());
        return new TmfModelResponse<>(info, Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    private final class CriticalPathVisitor extends TmfGraphVisitor {
        private final TmfGraph fGraph;
        /**
         * The {@link IGraphWorker} for which the view (tree / states) are computed
         */
        private final Map<String, CriticalPathEntry> fHostEntries = new HashMap<>();
        private final Map<IGraphWorker, CriticalPathEntry> fWorkers = new LinkedHashMap<>();
        private final TmfGraphStatistics fStatistics = new TmfGraphStatistics();

        /**
         * Store the states in a {@link TreeMultimap} so that they are grouped by entry
         * and sorted by start time.
         */
        private final TreeMultimap<Long, ITimeGraphState> fStates = TreeMultimap.create(Comparator.naturalOrder(),
                Comparator.comparingLong(ITimeGraphState::getStartTime));
        private long fStart;
        private long fEnd;
        private List<@NonNull CriticalPathEntry> fCached;
        /**
         * Cache the links once the graph has been traversed.
         */
        private @Nullable List<ITimeGraphArrow> fGraphLinks;

        private CriticalPathVisitor(TmfGraph graph, IGraphWorker worker) {
            fGraph = graph;
            fStart = getTrace().getStartTime().toNanos();
            fEnd = getTrace().getEndTime().toNanos();

           // graph.getWorkers().forEach(System.out::println);

            TmfVertex head = graph.getHead();
            if (head != null) {
                fStart = Long.min(fStart, head.getTs());
                for (IGraphWorker w : graph.getWorkers()) {
                    TmfVertex tail = graph.getTail(w);
                    if (tail != null) {
                        fEnd = Long.max(fEnd, tail.getTs());
                    }
                }
            }
            fStatistics.computeGraphStatistics(graph, worker);
        }

        @Override
        public void visitHead(TmfVertex node) {
            IGraphWorker owner = fGraph.getParentOf(node);
            if (owner == null) {
                return;
            }
            if (fWorkers.containsKey(owner)) {
                return;
            }
            TmfVertex first = fGraph.getHead(owner);
            TmfVertex last = fGraph.getTail(owner);
            if (first == null || last == null) {
                return;
            }
            fStart = Long.min(getTrace().getStartTime().toNanos(), first.getTs());
            fEnd = Long.max(getTrace().getEndTime().toNanos(), last.getTs());
            Long sum =  (fStatistics.getSum(owner));
            double total = sum/Math.pow(10.0, 3.0);
            double running = fStatistics.getSum(owner, EdgeType.RUNNING)/Math.pow(10.0, 3.0);
            double network = fStatistics.getSum(owner, EdgeType.NETWORK)/Math.pow(10.0, 3.0);
            double block = fStatistics.getSum(owner, EdgeType.BLOCK_DEVICE)/Math.pow(10.0, 3.0);
            double preempted = fStatistics.getSum(owner, EdgeType.PREEMPTED)/Math.pow(10.0, 3.0);
            double timer = fStatistics.getSum(owner, EdgeType.TIMER)/Math.pow(10.0, 3.0);

            System.out.println(owner.toString() + total + ","+ running+","+ network+","+ block +","+ preempted+ ","+ timer);
            Double percent = fStatistics.getPercent(owner);
            // create host entry
            String host = owner.getHostId();
            long parentId = fHostIdToEntryId.computeIfAbsent(host, h -> ATOMIC_LONG.getAndIncrement());
            fHostEntries.computeIfAbsent(host, h -> new CriticalPathEntry(parentId, -1L, host, fStart, fEnd, sum, percent));

            long entryId = fWorkerToEntryId.computeIfAbsent(owner, w -> ATOMIC_LONG.getAndIncrement());
            CriticalPathEntry entry = new CriticalPathEntry(entryId, parentId, NonNullUtils.nullToEmptyString(owner), fStart, fEnd, sum, percent);

            fWorkers.put(owner, entry);
        }

        @Override
        public void visit(TmfEdge link, boolean horizontal) {
            if (horizontal) {
                IGraphWorker parent = fGraph.getParentOf(link.getVertexFrom());
                Long id = fWorkerToEntryId.get(parent);
                if (id != null) {
                    String linkQualifier = link.getLinkQualifier();
                    ITimeGraphState ev = (linkQualifier == null) ? new TimeGraphState(link.getVertexFrom().getTs(), link.getDuration(), getMatchingState(link.getType())) :
                        new TimeGraphState(link.getVertexFrom().getTs(), link.getDuration(), getMatchingState(link.getType()), linkQualifier);
                    fStates.put(id, ev);
                }
            } else {
                IGraphWorker parentFrom = fGraph.getParentOf(link.getVertexFrom());
                IGraphWorker parentTo = fGraph.getParentOf(link.getVertexTo());
                CriticalPathEntry entryFrom = fWorkers.get(parentFrom);
                CriticalPathEntry entryTo = fWorkers.get(parentTo);
                List<ITimeGraphArrow> graphLinks = fGraphLinks;
                if (graphLinks != null && entryFrom != null && entryTo != null) {
                    ITimeGraphArrow lk = new TimeGraphArrow(entryFrom.getId(), entryTo.getId(), link.getVertexFrom().getTs(),
                            link.getVertexTo().getTs() - link.getVertexFrom().getTs(), getMatchingState(link.getType()));
                    graphLinks.add(lk);
                }
            }
        }

        public @NonNull List<@NonNull CriticalPathEntry> getEntries() {
            if (fCached != null) {
                return fCached;
            }

            fGraph.scanLineTraverse(fGraph.getHead(), this);
            List<@NonNull CriticalPathEntry> list = new ArrayList<>(fHostEntries.values());
            list.addAll(fWorkers.values());
            fCached = list;
            return list;
        }

        public synchronized List<ITimeGraphArrow> getGraphLinks() {
            if (fGraphLinks == null) {
                // the graph has not been traversed yet
                fGraphLinks = new ArrayList<>();
                fGraph.scanLineTraverse(fGraph.getHead(), this);
            }
            return fGraphLinks;
        }
    }

    /**
     * Critical path typically has relatively few links, so we calculate and save
     * them all, but just return those in range
     */
    private @Nullable List<@NonNull ITimeGraphArrow> getLinkList(long startTime, long endTime) {
        IGraphWorker current = getCurrent();
        List<ITimeGraphArrow> graphLinks = fLinks;
        if (current == null) {
            if (graphLinks != null) {
                return graphLinks;
            }
            return Collections.emptyList();
        }
        CriticalPathVisitor visitor = fHorizontalVisitorCache.getIfPresent(current);
        if (visitor == null) {
            return Collections.emptyList();
        }
        graphLinks = visitor.getGraphLinks();
        fLinks = graphLinks;
        return getLinksInRange(graphLinks, startTime, endTime);
    }

    private static List<@NonNull ITimeGraphArrow> getLinksInRange(List<ITimeGraphArrow> allLinks, long startTime, long endTime) {
        List<@NonNull ITimeGraphArrow> linksInRange = new ArrayList<>();
        for (ITimeGraphArrow link : allLinks) {
            if (link.getStartTime() <= endTime &&
                    link.getStartTime() + link.getDuration() >= startTime) {
                linksInRange.add(link);
            }
        }
        return linksInRange;
    }

    private static int getMatchingState(EdgeType type) {
        switch (type) {
        case RUNNING:
            return 0;
        case INTERRUPTED:
            return 1;
        case PREEMPTED:
            return 2;
        case TIMER:
            return 3;
        case BLOCK_DEVICE:
            return 4;
        case USER_INPUT:
            return 5;
        case NETWORK:
            return 6;
        case IPI:
            return 7;
        case EPS:
        case UNKNOWN:
        case DEFAULT:
        case BLOCKED:
            break;
        default:
            break;
        }
        return 8;
    }

}
