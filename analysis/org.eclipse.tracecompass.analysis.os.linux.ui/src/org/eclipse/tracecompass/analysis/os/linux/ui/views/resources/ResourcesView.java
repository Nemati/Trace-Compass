/*******************************************************************************
 * Copyright (c) 2012, 2015 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Patrick Tasse - Initial API and implementation
 *   Geneviève Bastien - Move code to provide base classes for time graph views
 *******************************************************************************/

package org.eclipse.tracecompass.analysis.os.linux.ui.views.resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;


import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.Attributes;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.KernelAnalysisModule;
import org.eclipse.tracecompass.analysis.os.linux.ui.views.resources.ResourcesEntry.Type;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.Messages;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.views.timegraph.AbstractStateSystemTimeGraphView;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;
import java.lang.Math;
/**
 * Main implementation for the LTTng 2.0 kernel Resource view
 *
 * @author Patrick Tasse
 */
public class ResourcesView extends AbstractStateSystemTimeGraphView {
    private static Map<String,String> namePIDmap = new HashMap<>() ;

    /** View ID. */
    public static final String ID = "org.eclipse.tracecompass.analysis.os.linux.views.resources"; //$NON-NLS-1$

    private static final String[] FILTER_COLUMN_NAMES = new String[] {
            Messages.ResourcesView_stateTypeName


    };

    // Timeout between updates in the build thread in ms
    private static final long BUILD_UPDATE_TIMEOUT = 500;

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Default constructor
     */
    public ResourcesView() {
        super(ID, new ResourcesPresentationProvider());
        setFilterColumns(FILTER_COLUMN_NAMES);
    }

    // ------------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------------

    @Override
    protected String getNextText() {
        return Messages.ResourcesView_nextResourceActionNameText;
    }

    @Override
    protected String getNextTooltip() {
        return Messages.ResourcesView_nextResourceActionToolTipText;
    }

    @Override
    protected String getPrevText() {
        return Messages.ResourcesView_previousResourceActionNameText;
    }

    @Override
    protected String getPrevTooltip() {
        return Messages.ResourcesView_previousResourceActionToolTipText;
    }
    @Override
    protected void rebuild() {
        setStartTime(Long.MAX_VALUE);
        setEndTime(Long.MIN_VALUE);
        refresh();
        ITmfTrace viewTrace = getTrace();
        if (viewTrace == null) {
            return;
        }
        synchronized (fBuildThreadMap) {
            BuildThread buildThread = new BuildThread(viewTrace, viewTrace, getName());
            fBuildThreadMap.put(viewTrace, buildThread);
            buildThread.start();
        }
    }


    @Override
    protected void buildEventList(ITmfTrace trace, ITmfTrace parentTrace, final IProgressMonitor monitor)  {
        final ITmfStateSystem ssq = TmfStateSystemAnalysisModule.getStateSystem(parentTrace, KernelAnalysisModule.ID);

        if (ssq == null) {
            return;
        }

        Comparator<ITimeGraphEntry> comparator = new Comparator<ITimeGraphEntry>() {
            @Override
            public int compare(ITimeGraphEntry o1, ITimeGraphEntry o2) {
                return ((ResourcesEntry) o1).compareTo(o2);
            }
        };

        Map<Integer, ResourcesEntry> entryMap = new HashMap<>();
        TimeGraphEntry traceEntry = null;

        long startTime = ssq.getStartTime();
        long start = startTime;
        setStartTime(Math.min(getStartTime(), startTime));
        boolean complete = false;
        while (!complete) {
            if (monitor.isCanceled()) {
                return;
            }
            complete = ssq.waitUntilBuilt(BUILD_UPDATE_TIMEOUT);
            if (ssq.isCancelled()) {
                return;
            }
            long end = ssq.getCurrentEndTime();

            try {
                //int quark = ssq.getQuarkAbsolute("vmName"); //$NON-NLS-1$
                List<Integer> vmQuarks = ssq.getQuarks("vmName", "*"); //$NON-NLS-1$ //$NON-NLS-2$
                for (Integer vmQuark : vmQuarks) {
                    List<ITmfStateInterval> intervals = StateSystemUtils.queryHistoryRange(ssq, vmQuark, start, end);
                    Iterator<ITmfStateInterval> iterator = intervals.iterator();
                    while (iterator.hasNext()) {
                        String vmName = (ssq.getAttributeName(vmQuark));
                        String tidVM = iterator.next().getStateValue().toString();
                        if (!tidVM.equals("nullValue")){ //$NON-NLS-1$
                            namePIDmap.put(tidVM, vmName);
                        }
                    }

                }

            }

            catch (StateSystemDisposedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            catch (AttributeNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }


            if (start == end && !complete) { // when complete execute one last time regardless of end time
                continue;
            }
            long endTime = end + 1;
            setEndTime(Math.max(getEndTime(), endTime));
            if (traceEntry == null) {
                traceEntry = new ResourcesEntry(trace,"Qemu Resource View" , startTime, endTime, 0); //$NON-NLS-1$
                traceEntry.sortChildren(comparator);
                List<TimeGraphEntry> entryList = Collections.singletonList(traceEntry);
                addToEntryList(parentTrace, ssq, entryList);
            } else {
                traceEntry.updateEndTime(endTime);
            }

            List<Integer> cpuQuarks = ssq.getQuarks(Attributes.CPUS, "*"); //$NON-NLS-1$
            for (Integer cpuQuark : cpuQuarks) {
                int cpu = Integer.parseInt(ssq.getAttributeName(cpuQuark));
                ResourcesEntry entry = entryMap.get(cpuQuark);
                if (entry == null) {
                    entry = new ResourcesEntry(cpuQuark, parentTrace, startTime, endTime, Type.CPU, cpu);
                    entryMap.put(cpuQuark, entry);
                    traceEntry.addChild(entry);
                } else {
                    entry.updateEndTime(endTime);
                }
            }
            List<Integer> IOQuarks = ssq.getQuarks(Attributes.IO, "*"); //$NON-NLS-1$
            for (Integer IOQuark : IOQuarks) {
                //Integer quark =  ssq.getq
                int IOQ = Integer.parseInt(ssq.getAttributeName(IOQuark));
                ResourcesEntry entry = entryMap.get(IOQuark);
                if (entry == null) {
                    if (namePIDmap.get(String.valueOf(IOQ))!=null){

                        entry = new ResourcesEntry(IOQuark, parentTrace, startTime, endTime, Type.IOQemuWrite, IOQ, "Disk "+ namePIDmap.get(String.valueOf(IOQ))); //$NON-NLS-1$
                        entryMap.put(IOQuark, entry);
                        traceEntry.addChild(entry);

                        entry = new ResourcesEntry(IOQuark, parentTrace, startTime, endTime, Type.IOQemuRead, IOQ, "Disk "+ namePIDmap.get(String.valueOf(IOQ))); //$NON-NLS-1$
                        entryMap.put(IOQuark, entry);
                        traceEntry.addChild(entry);


                    }
                } else {
                    entry.updateEndTime(endTime);
                }
            }



            List<Integer> CPUQemuQuarks = ssq.getQuarks(Attributes.CPUQemu, "*"); //$NON-NLS-1$
            for (Integer CPUQemuQuark : CPUQemuQuarks) {
                int CPUQ = Integer.parseInt(ssq.getAttributeName(CPUQemuQuark));

                ResourcesEntry entry = entryMap.get(CPUQemuQuark);

                if (entry == null) {
                    entry = new ResourcesEntry(CPUQemuQuark, parentTrace, startTime, endTime, Type.CPUQemu, CPUQ, "CPU "+ namePIDmap.get(String.valueOf(CPUQ))); //$NON-NLS-1$
                    entryMap.put(CPUQemuQuark, entry);
                    traceEntry.addChild(entry);
                } else {
                    entry.updateEndTime(endTime);
                }
            }
            List<Integer> NetQemuQuarks = ssq.getQuarks(Attributes.NetQemu, "*"); //$NON-NLS-1$
            //int counter = 1;
            for (Integer NetQemuQuark : NetQemuQuarks) {
                int NetQ = Integer.parseInt(ssq.getAttributeName(NetQemuQuark));
                ResourcesEntry entry = entryMap.get(NetQemuQuark);
                if (entry == null) {
                    String Name = "Unknown"; //$NON-NLS-1$
                    Set<String> test =  namePIDmap.keySet();
                    Iterator<String> iterator = test.iterator();
                    while (iterator.hasNext()){
                        int tidOfVM = Integer.valueOf(iterator.next());
                        if (Math.abs(Integer.valueOf(NetQ)-tidOfVM)<25){
                            Name = String.valueOf(tidOfVM);
                        }
                    }
                    entry = new ResourcesEntry(NetQemuQuark, parentTrace, startTime, endTime, Type.NetQemu, NetQ, "Net "+ namePIDmap.get(Name)); //$NON-NLS-1$
                    entryMap.put(NetQemuQuark, entry);
                    traceEntry.addChild(entry);
                } else {
                    entry.updateEndTime(endTime);
                }
            }
            List<Integer> irqQuarks = ssq.getQuarks(Attributes.RESOURCES, Attributes.IRQS, "*"); //$NON-NLS-1$
            for (Integer irqQuark : irqQuarks) {
                int irq = Integer.parseInt(ssq.getAttributeName(irqQuark));
                ResourcesEntry entry = entryMap.get(irqQuark);
                if (entry == null) {
                    entry = new ResourcesEntry(irqQuark, parentTrace, startTime, endTime, Type.IRQ, irq);
                    entryMap.put(irqQuark, entry);
                    traceEntry.addChild(entry);
                } else {
                    entry.updateEndTime(endTime);
                }
            }
            List<Integer> softIrqQuarks = ssq.getQuarks(Attributes.RESOURCES, Attributes.SOFT_IRQS, "*"); //$NON-NLS-1$
            for (Integer softIrqQuark : softIrqQuarks) {
                int softIrq = Integer.parseInt(ssq.getAttributeName(softIrqQuark));
                ResourcesEntry entry = entryMap.get(softIrqQuark);
                if (entry == null) {
                    entry = new ResourcesEntry(softIrqQuark, parentTrace, startTime, endTime, Type.SOFT_IRQ, softIrq);
                    entryMap.put(softIrqQuark, entry);
                    traceEntry.addChild(entry);
                } else {
                    entry.updateEndTime(endTime);
                }
            }

            if (parentTrace.equals(getTrace())) {
                refresh();
            }
            final List<? extends ITimeGraphEntry> traceEntryChildren = traceEntry.getChildren();
            final long resolution = Math.max(1, (endTime - ssq.getStartTime()) / getDisplayWidth());
            final long qStart = start;
            final long qEnd = end;
            queryFullStates(ssq, qStart, qEnd, resolution, monitor, new IQueryHandler() {
                @Override
                public void handle(List<List<ITmfStateInterval>> fullStates, List<ITmfStateInterval> prevFullState) {
                    for (ITimeGraphEntry child : traceEntryChildren) {
                        if (monitor.isCanceled()) {
                            return;
                        }
                        if (child instanceof TimeGraphEntry) {
                            TimeGraphEntry entry = (TimeGraphEntry) child;
                            List<ITimeEvent> eventList = getEventList(entry, ssq, fullStates, prevFullState, monitor);
                            if (eventList != null) {
                                for (ITimeEvent event : eventList) {
                                    entry.addEvent(event);
                                }
                            }
                        }
                    }
                }
            });

            start = end;
        }
    }


    @Override
    protected @Nullable List<ITimeEvent> getEventList(@NonNull TimeGraphEntry entry, ITmfStateSystem ssq,
            @NonNull List<List<ITmfStateInterval>> fullStates, @Nullable List<ITmfStateInterval> prevFullState, @NonNull IProgressMonitor monitor) {
        ResourcesEntry resourcesEntry = (ResourcesEntry) entry;
        List<ITimeEvent> eventList = null;
        int quark = resourcesEntry.getQuark();

        if (resourcesEntry.getType().equals(Type.CPU)) {
            int statusQuark;
            try {
                statusQuark = ssq.getQuarkRelative(quark, Attributes.STATUS);
            } catch (AttributeNotFoundException e) {
                /*
                 * The sub-attribute "status" is not available. May happen
                 * if the trace does not have sched_switch events enabled.
                 */
                return null;
            }
            eventList = new ArrayList<>(fullStates.size());
            ITmfStateInterval lastInterval = prevFullState == null || statusQuark >= prevFullState.size() ? null : prevFullState.get(statusQuark);
            long lastStartTime = lastInterval == null ? -1 : lastInterval.getStartTime();
            long lastEndTime = lastInterval == null ? -1 : lastInterval.getEndTime() + 1;
            for (List<ITmfStateInterval> fullState : fullStates) {
                if (monitor.isCanceled()) {
                    return null;
                }
                if (statusQuark >= fullState.size()) {
                    /* No information on this cpu (yet?), skip it for now */
                    continue;
                }
                ITmfStateInterval statusInterval = fullState.get(statusQuark);
                int status = statusInterval.getStateValue().unboxInt();
                long time = statusInterval.getStartTime();
                long duration = statusInterval.getEndTime() - time + 1;

                if (time == lastStartTime) {
                    continue;
                }
                if (!statusInterval.getStateValue().isNull()) {
                    if (lastEndTime != time && lastEndTime != -1) {
                        eventList.add(new TimeEvent(entry, lastEndTime, time - lastEndTime));
                    }
                    eventList.add(new TimeEvent(entry, time, duration, status));
                } else {
                    eventList.add(new NullTimeEvent(entry, time, duration));
                }
                lastStartTime = time;
                lastEndTime = time + duration;
            }
        } else if (resourcesEntry.getType().equals(Type.IOQemuRead)) {
            int statusQuark;
            try {
                statusQuark = ssq.getQuarkRelative(quark,"read"); //$NON-NLS-1$dd
                statusQuark = ssq.getQuarkRelative(statusQuark,"STATUS"); //$NON-NLS-1$dd
            } catch (AttributeNotFoundException e) {
                /*
                 * The sub-attribute "status" is not available. May happen
                 * if the trace does not have sched_switch events enabled.
                 */
                return null;
            }
            eventList = new ArrayList<>(fullStates.size());
            ITmfStateInterval lastInterval = prevFullState == null || statusQuark >= prevFullState.size() ? null : prevFullState.get(statusQuark);
            long lastStartTime = lastInterval == null ? -1 : lastInterval.getStartTime();
            long lastEndTime = lastInterval == null ? -1 : lastInterval.getEndTime() + 1;
            for (List<ITmfStateInterval> fullState : fullStates) {
                if (monitor.isCanceled()) {
                    return null;
                }
                if (statusQuark >= fullState.size()) {
                    /* No information on this cpu (yet?), skip it for now */
                    continue;
                }
                ITmfStateInterval statusInterval = fullState.get(statusQuark);
                int status = statusInterval.getStateValue().unboxInt();
                long time = statusInterval.getStartTime();
                long duration = statusInterval.getEndTime() - time + 1;
                duration += 0;
                if (time == lastStartTime) {
                    continue;
                }
                if (!statusInterval.getStateValue().isNull()) {
                    if (lastEndTime != time && lastEndTime != -1) {
                        eventList.add(new TimeEvent(entry, lastEndTime, time - lastEndTime));
                    }
                    eventList.add(new TimeEvent(entry, time, duration, status));
                } else {
                    eventList.add(new NullTimeEvent(entry, time, duration));
                }
                lastStartTime = time;
                lastEndTime = time + duration;
            }
        }
        else if (resourcesEntry.getType().equals(Type.IOQemuWrite)) {
            int statusQuark;
            try {
                statusQuark = ssq.getQuarkRelative(quark,"write"); //$NON-NLS-1$dd
                statusQuark = ssq.getQuarkRelative(statusQuark,"STATUS"); //$NON-NLS-1$dd
            } catch (AttributeNotFoundException e) {
                /*
                 * The sub-attribute "status" is not available. May happen
                 * if the trace does not have sched_switch events enabled.
                 */
                return null;
            }
            eventList = new ArrayList<>(fullStates.size());
            ITmfStateInterval lastInterval = prevFullState == null || statusQuark >= prevFullState.size() ? null : prevFullState.get(statusQuark);
            long lastStartTime = lastInterval == null ? -1 : lastInterval.getStartTime();
            long lastEndTime = lastInterval == null ? -1 : lastInterval.getEndTime() + 1;
            for (List<ITmfStateInterval> fullState : fullStates) {
                if (monitor.isCanceled()) {
                    return null;
                }
                if (statusQuark >= fullState.size()) {
                    /* No information on this cpu (yet?), skip it for now */
                    continue;
                }
                ITmfStateInterval statusInterval = fullState.get(statusQuark);
                int status = statusInterval.getStateValue().unboxInt();
                long time = statusInterval.getStartTime();
                long duration = statusInterval.getEndTime() - time + 1;
                duration += 0;
                if (time == lastStartTime) {
                    continue;
                }
                if (!statusInterval.getStateValue().isNull()) {
                    if (lastEndTime != time && lastEndTime != -1) {
                        eventList.add(new TimeEvent(entry, lastEndTime, time - lastEndTime));
                    }
                    eventList.add(new TimeEvent(entry, time, duration, status));
                } else {
                    eventList.add(new NullTimeEvent(entry, time, duration));
                }
                lastStartTime = time;
                lastEndTime = time + duration;
            }
        }
        else if (resourcesEntry.getType().equals(Type.CPUQemu)) {
            int statusQuark;
            try {
                statusQuark = ssq.getQuarkRelative(quark,"STATUS"); //$NON-NLS-1$
            } catch (AttributeNotFoundException e) {
                /*
                 * The sub-attribute "status" is not available. May happen
                 * if the trace does not have sched_switch events enabled.
                 */
                return null;
            }
            eventList = new ArrayList<>(fullStates.size());
            ITmfStateInterval lastInterval = prevFullState == null || statusQuark >= prevFullState.size() ? null : prevFullState.get(statusQuark);
            long lastStartTime = lastInterval == null ? -1 : lastInterval.getStartTime();
            long lastEndTime = lastInterval == null ? -1 : lastInterval.getEndTime() + 1;
            for (List<ITmfStateInterval> fullState : fullStates) {
                if (monitor.isCanceled()) {
                    return null;
                }
                if (statusQuark >= fullState.size()) {
                    /* No information on this cpu (yet?), skip it for now */
                    continue;
                }
                ITmfStateInterval statusInterval = fullState.get(statusQuark);
                int status = statusInterval.getStateValue().unboxInt();
                long time = statusInterval.getStartTime();
                long duration = statusInterval.getEndTime() - time + 1;
                if (time == lastStartTime) {
                    continue;
                }
                if (!statusInterval.getStateValue().isNull()) {
                    if (lastEndTime != time && lastEndTime != -1) {
                        eventList.add(new TimeEvent(entry, lastEndTime, time - lastEndTime));
                    }
                    eventList.add(new TimeEvent(entry, time, duration, status));
                } else {
                    eventList.add(new NullTimeEvent(entry, time, duration));
                }
                lastStartTime = time;
                lastEndTime = time + duration;
            }
        }
        else if (resourcesEntry.getType().equals(Type.NetQemu)) {
            int statusQuark;
            try {
                statusQuark = ssq.getQuarkRelative(quark,"STATUS"); //$NON-NLS-1$
            } catch (AttributeNotFoundException e) {
                /*
                 * The sub-attribute "status" is not available. May happen
                 * if the trace does not have sched_switch events enabled.
                 */
                return null;
            }
            eventList = new ArrayList<>(fullStates.size());
            ITmfStateInterval lastInterval = prevFullState == null || statusQuark >= prevFullState.size() ? null : prevFullState.get(statusQuark);
            long lastStartTime = lastInterval == null ? -1 : lastInterval.getStartTime();
            long lastEndTime = lastInterval == null ? -1 : lastInterval.getEndTime() + 1;
            for (List<ITmfStateInterval> fullState : fullStates) {
                if (monitor.isCanceled()) {
                    return null;
                }
                if (statusQuark >= fullState.size()) {
                    /* No information on this cpu (yet?), skip it for now */
                    continue;
                }
                ITmfStateInterval statusInterval = fullState.get(statusQuark);
                int status = statusInterval.getStateValue().unboxInt();
                long time = statusInterval.getStartTime();
                long duration = statusInterval.getEndTime() - time + 1;
                if (time == lastStartTime) {
                    continue;
                }
                if (!statusInterval.getStateValue().isNull()) {
                    if (lastEndTime != time && lastEndTime != -1) {
                        eventList.add(new TimeEvent(entry, lastEndTime, time - lastEndTime));
                    }
                    eventList.add(new TimeEvent(entry, time, duration, status));
                } else {
                    eventList.add(new NullTimeEvent(entry, time, duration));
                }
                lastStartTime = time;
                lastEndTime = time + duration;
            }
        }
        else if (resourcesEntry.getType().equals(Type.IRQ) || resourcesEntry.getType().equals(Type.SOFT_IRQ)) {
            eventList = new ArrayList<>(fullStates.size());
            ITmfStateInterval lastInterval = prevFullState == null ? null : prevFullState.get(quark);
            long lastStartTime = lastInterval == null ? -1 : lastInterval.getStartTime();
            long lastEndTime = lastInterval == null ? -1 : lastInterval.getEndTime() + 1;
            boolean lastIsNull = lastInterval == null ? false : lastInterval.getStateValue().isNull();
            for (List<ITmfStateInterval> fullState : fullStates) {
                if (monitor.isCanceled()) {
                    return null;
                }
                ITmfStateInterval irqInterval = fullState.get(quark);
                long time = irqInterval.getStartTime();
                long duration = irqInterval.getEndTime() - time + 1;
                if (time == lastStartTime) {
                    continue;
                }
                if (!irqInterval.getStateValue().isNull()) {
                    int cpu = irqInterval.getStateValue().unboxInt();
                    eventList.add(new TimeEvent(entry, time, duration, cpu));
                    lastIsNull = false;
                } else {
                    if (lastEndTime != time && lastIsNull) {
                        /* This is a special case where we want to show IRQ_ACTIVE state but we don't know the CPU (it is between two null samples) */
                        eventList.add(new TimeEvent(entry, lastEndTime, time - lastEndTime, -1));
                    }
                    eventList.add(new NullTimeEvent(entry, time, duration));
                    lastIsNull = true;
                }
                lastStartTime = time;
                lastEndTime = time + duration;
            }
        }

        return eventList;
    }

}
