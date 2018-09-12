/*******************************************************************************
 * Copyright (c) 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Francis Giraldeau - Initial implementation and API
 *   Geneviève Bastien - Initial implementation and API
 *******************************************************************************/

package org.eclipse.tracecompass.internal.analysis.graph.core.base;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.graph.core.base.IGraphWorker;
import org.eclipse.tracecompass.analysis.graph.core.base.ITmfGraphVisitor;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge.EdgeType;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfGraph;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex;

/**
 * Class that computes statistics on time spent in the elements (objects) of a
 * graph
 *
 * @author Francis Giraldeau
 * @author Geneviève Bastien
 * @author Matthew Khouzam
 */
public class TmfGraphStatistics implements ITmfGraphVisitor {

    private final Map<IGraphWorker, Long> fWorkerStats;
    private final Map<IGraphWorker, Map<EdgeType, Long>> waitStat;
    private Long fTotal;
    private @Nullable TmfGraph fGraph;

    /**
     * Constructor
     */
    public TmfGraphStatistics() {
        fWorkerStats = new HashMap<>();
        waitStat = new HashMap<>();
        fTotal = 0L;
    }

    /**
     * Compute the statistics for a graph
     *
     * @param graph
     *            The graph on which to calculate statistics
     * @param current
     *            The element from which to start calculations
     */
    public void computeGraphStatistics(TmfGraph graph, @Nullable IGraphWorker current) {
        if (current == null) {
            return;
        }
        clear();
        fGraph = graph;
        fGraph.scanLineTraverse(fGraph.getHead(current), this);
    }

    @Override
    public void visitHead(TmfVertex node) {

    }

    @Override
    public void visit(TmfVertex node) {

    }

    @Override
    public void visit(TmfEdge edge, boolean horizontal) {
        // Add the duration of the link only if it is horizontal
        TmfGraph graph = fGraph;

        Long start = 1533654154188941556L;
        Long end = 1533654155621964921L;

        synchronized (fWorkerStats) {
            if (edge.getVertexFrom().getTs() > start && edge.getVertexFrom().getTs() < end) {


                if (horizontal && graph != null) {
                    IGraphWorker worker = graph.getParentOf(edge.getVertexFrom());
                    if (worker == null) {
                        return;
                    }

                    System.out.println(edge.getVertexFrom().getTs());

                    @NonNull
                    Map<EdgeType, Long>  waitStatWorker ;

                    if ( waitStat.containsKey(worker)) {
                        Map<EdgeType, Long> map = waitStat.get(worker);
                        waitStatWorker = map;
                    } else {
                        waitStatWorker = new HashMap<>();
                    }
                    Long duration = edge.getDuration();
                    if (waitStatWorker.containsKey(edge.getType())) {

                        Long edgeDuration = waitStatWorker.get(edge.getType());

                        if (edgeDuration != null) {
                            waitStatWorker.put(edge.getType(), edgeDuration+duration);
                        }

                    } else {

                        waitStatWorker.put(edge.getType(), duration);
                    }
                    Long currentTotal = fWorkerStats.get(worker);
                    if (currentTotal != null) {
                        duration += currentTotal;
                    }
                    fWorkerStats.put(worker, duration);
                    waitStat.put(worker, waitStatWorker);
                    fTotal += edge.getDuration();
                }
            } else {

            }
        }
    }

    /**
     * Get the total duration spent by one element of the graph
     *
     * @param worker
     *            The object to get the time spent for
     * @return The sum of all durations
     */
    public Long getSum(@Nullable IGraphWorker worker) {
        Long sum = 0L;
        synchronized (fWorkerStats) {
            Long elapsed = fWorkerStats.get(worker);
            if (elapsed != null) {
                sum += elapsed;
            }
        }
        return sum;
    }

    public Long getSum(@Nullable IGraphWorker worker, EdgeType et) {
        Long sum = 0L;
        synchronized (fWorkerStats) {
            Map<EdgeType, Long> stat = waitStat.get(worker);

            if (stat!=null && stat.containsKey(et)) {
                Long elapsed = stat.get(et);
                if (elapsed != null) {
                    sum += elapsed;
                }
            }
        }
        return sum;
    }


    /**
     * Get the total duration of the graph vertices
     *
     * @return The sum of all durations
     */
    public Long getSum() {
        synchronized (fWorkerStats) {
            return fTotal;
        }
    }

    /**
     * Get the percentage of time by one element of the graph
     *
     * @param worker
     *            The object to get the percentage for
     * @return The percentage time spent in this element
     */
    public Double getPercent(@Nullable IGraphWorker worker) {
        synchronized (fWorkerStats) {
            if (getSum() == 0) {
                return 0D;
            }
            return (double) getSum(worker) / (double) getSum();
        }
    }

    /**
     * Clear statistics
     */

    public void clear() {
        synchronized (fWorkerStats) {
            fTotal = 0L;
            fWorkerStats.clear();
        }
    }

}
