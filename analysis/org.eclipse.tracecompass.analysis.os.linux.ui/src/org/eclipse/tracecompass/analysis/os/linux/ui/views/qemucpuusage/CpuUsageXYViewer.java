/*******************************************************************************
 * Copyright (c) 2014, 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Geneviève Bastien - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.analysis.os.linux.ui.views.qemucpuusage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.analysis.os.linux.core.cpuusage.KernelCpuUsageAnalysis;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.Attributes;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.KernelAnalysisModule;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.Activator;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.viewers.xycharts.linecharts.TmfCommonXLineChartViewer;

/**
 * CPU usage viewer with XY line chart. It displays the total CPU usage and that
 * of the threads selected in the CPU usage tree viewer.
 *
 * @author Geneviève Bastien
 */
public class CpuUsageXYViewer extends TmfCommonXLineChartViewer {

    /* A map that saves the mapping of a thread ID to its executable name */
    private final Map<String, String> fProcessNameMap = new HashMap<>();
    /* A map that saves the mapping of a thread ID to its PTID */
    private final Map<String, String> fProcessPTIDMap = new HashMap<>();


    private KernelCpuUsageAnalysis fModule = null;

    /* Maps a thread ID to a list of y values */
    private final Map<String, double[]> fYValues = new LinkedHashMap<>();
    /*
     * To avoid up and downs CPU usage when process is in and out of CPU
     * frequently, use a smaller resolution to get better averages.
     */
    private static final double RESOLUTION = 0.4;

    // Timeout between updates in the updateData thread
    private static final long BUILD_UPDATE_TIMEOUT = 500;

    private long fSelectedThread = -1;

    /**
     * Constructor
     *
     * @param parent
     *            parent composite
     */
    public CpuUsageXYViewer(Composite parent) {
        super(parent, Messages.CpuUsageXYViewer_Title, Messages.CpuUsageXYViewer_TimeXAxis, Messages.CpuUsageXYViewer_CpuYAxis);
        setResolution(RESOLUTION);
    }

    @Override
    protected void initializeDataSource() {
        ITmfTrace trace = getTrace();
        if (trace != null) {
            fModule = TmfTraceUtils.getAnalysisModuleOfClass(trace, KernelCpuUsageAnalysis.class, KernelCpuUsageAnalysis.ID);
            if (fModule == null) {
                return;
            }
            fModule.schedule();
        }
    }

    private static double[] zeroFill(int nb) {
        double[] arr = new double[nb];
        Arrays.fill(arr, 0.0);
        return arr;
    }

    @Override
    protected void updateData(long start, long end, int nb, IProgressMonitor monitor) {
        try {
            if (getTrace() == null || fModule == null) {
                return;
            }
            fModule.waitForInitialization();
            ITmfStateSystem ss = fModule.getStateSystem();

            if (ss == null) {
                return;
            }
            double[] xvalues = getXAxis(start, end, nb);
            if (xvalues.length == 0) {
                return;
            }
            setXAxis(xvalues);

            boolean complete = false;
            long currentEnd = start;

            while (!complete && currentEnd < end) {

                if (monitor.isCanceled()) {
                    return;
                }

                long traceStart = getStartTime();
                long traceEnd = getEndTime();
                long offset = getTimeOffset();
                long selectedThread = fSelectedThread;

                complete = ss.waitUntilBuilt(BUILD_UPDATE_TIMEOUT);
                currentEnd = ss.getCurrentEndTime();

                /* Initialize the data */
                Map<String, Long> cpuUsageMap = fModule.getCpuUsageInRange(Math.max(start, traceStart), Math.min(end, traceEnd));
                Map<String, String> totalEntries = new HashMap<>();
                fYValues.clear();
                fYValues.put(Messages.CpuUsageXYViewer_Total, zeroFill(xvalues.length));
                String stringSelectedThread = Long.toString(selectedThread);

                if (selectedThread != -1) {
                    fYValues.put(stringSelectedThread, zeroFill(xvalues.length));
                }

                for (Entry<String, Long> entry : cpuUsageMap.entrySet()) {
                    /*
                     * Process only entries representing the total of all CPUs
                     * and that have time on CPU
                     */
                    if (entry.getValue() == 0) {
                        continue;
                    }
                    if (!entry.getKey().startsWith(KernelCpuUsageAnalysis.TOTAL)) {
                        continue;
                    }
                    String[] strings = entry.getKey().split(KernelCpuUsageAnalysis.SPLIT_STRING, 2);

                    if ((strings.length > 1) && !(strings[1].equals(KernelCpuUsageAnalysis.TID_ZERO))) {
                        /* This is the total cpu usage for a thread */
                        if (getProcessName(strings[1]).equals("qemu-system-x86")) { //$NON-NLS-1$
                            totalEntries.put(strings[1], entry.getKey());
                        }
                    }
                }

                double prevX = xvalues[0] - 1;
                long prevTime = (long) prevX + offset;
                /*
                 * make sure that time is in the trace range after double to
                 * long conversion
                 */
                prevTime = Math.max(traceStart, prevTime);
                prevTime = Math.min(traceEnd, prevTime);
                /* Get CPU usage statistics for each x value */
                for (int i = 0; i < xvalues.length; i++) {
                    if (monitor.isCanceled()) {
                        return;
                    }
                    long totalCpu = 0;
                    double x = xvalues[i];
                    long time = (long) x + offset;
                    time = Math.max(traceStart, time);
                    time = Math.min(traceEnd, time);
                    if (time == prevTime) {
                        /*
                         * we need at least 1 time unit to be able to get cpu
                         * usage when zoomed in
                         */
                        prevTime = time - 1;
                    }

                    cpuUsageMap = fModule.getCpuUsageInRange(prevTime, time);

                    /*
                     * Calculate the sum of all total entries, and add a data
                     * point to the selected one
                     */
                    Map<String, Long> fQemuPTIDMaps = new HashMap<>();
                    for (Entry<String, String> entry : totalEntries.entrySet()) {
                        Long cpuEntry = cpuUsageMap.get(entry.getValue());
                        cpuEntry = cpuEntry != null ? cpuEntry : 0L;

                        String tid = entry.getKey();
                        String PTID = getProcessPTID(tid);
                        String PTIDTmp = new String();
                        if (PTID.equals("1")){ //$NON-NLS-1$
                            if (fQemuPTIDMaps.get(tid)!=null) {
                            fQemuPTIDMaps.put(tid, fQemuPTIDMaps.get(tid)+ cpuEntry);
                            } else {fQemuPTIDMaps.put(tid,  cpuEntry);}
                        } else {
                            while (!PTID.equals("1")){ //$NON-NLS-1$
                                PTIDTmp = PTID;
                                PTID = getProcessPTID(PTID);
                            }
                            Long tmpSum= 0L ;
                            Long execSum = fQemuPTIDMaps.get(PTIDTmp);
                            if (execSum!= null){
                                tmpSum = fQemuPTIDMaps.get(PTIDTmp);

                            }else {
                                tmpSum = 0L;
                                }
                            fQemuPTIDMaps.put(PTIDTmp,  tmpSum + cpuEntry);
                        }
                        totalCpu += cpuEntry;
                        for (String key : fQemuPTIDMaps.keySet()) {
                            if (fYValues.get(key) != null){
                                fYValues.get(key)[i] = (double) fQemuPTIDMaps.get(key) / (double) (time - prevTime) * 100;
                            } else {
                                for (String key1 : fQemuPTIDMaps.keySet()) {
                                    fYValues.put(key1, zeroFill(xvalues.length));
                                }
                            }

                        }

                    }

                    fYValues.get(Messages.CpuUsageXYViewer_Total)[i] = (double) totalCpu / (double) (time - prevTime) * 100;
                    prevTime = time;
                }
                for (Entry<String, double[]> entry : fYValues.entrySet()) {
                    setSeries(entry.getKey(), entry.getValue());
                }
                if (monitor.isCanceled()) {
                    return;
                }
                updateDisplay();
            }
        } catch (StateValueTypeException e) {
            Activator.getDefault().logError("Error updating the data of the CPU usage view", e); //$NON-NLS-1$
        }

    }
    /**
     * Set the selected thread ID, which will be graphed in this viewer
     *
     * @param tid
     *            The selected thread ID
     */
    public void setSelectedThread(long tid) {
        cancelUpdate();
        deleteSeries(Long.toString(fSelectedThread));
        fSelectedThread = tid;
        updateContent();
    }
    private String getProcessName(String tid) {
        String execName = fProcessNameMap.get(tid);
        if (execName != null) {
            return execName;
        }
        ITmfTrace trace = getTrace();
        if (trace == null) {
            return tid;
        }
        ITmfStateSystem kernelSs = TmfStateSystemAnalysisModule.getStateSystem(trace, KernelAnalysisModule.ID);
        if (kernelSs == null) {
            return tid;
        }
        try {
            int cpusNode = kernelSs.getQuarkAbsolute(Attributes.THREADS);

            /* Get the quarks for each cpu */
            List<Integer> cpuNodes = kernelSs.getSubAttributes(cpusNode, false);

            for (Integer tidQuark : cpuNodes) {
                if (kernelSs.getAttributeName(tidQuark).equals(tid)) {
                    int execNameQuark;
                    List<ITmfStateInterval> execNameIntervals;
                    try {
                        execNameQuark = kernelSs.getQuarkRelative(tidQuark, Attributes.EXEC_NAME);
                        execNameIntervals = StateSystemUtils.queryHistoryRange(kernelSs, execNameQuark, getStartTime(), getEndTime());
                    } catch (AttributeNotFoundException e) {
                        /* No information on this thread (yet?), skip it for now */
                        continue;
                    } catch (StateSystemDisposedException e) {
                        /* State system is closing down, no point continuing */
                        break;
                    }
                    for (ITmfStateInterval execNameInterval : execNameIntervals) {
                        if (!execNameInterval.getStateValue().isNull() &&
                                execNameInterval.getStateValue().getType() == ITmfStateValue.Type.STRING) {
                            execName = execNameInterval.getStateValue().unboxStr();
                            fProcessNameMap.put(tid, execName);
                            return execName;
                        }
                    }
                }
            }

        } catch (AttributeNotFoundException e) {
            /* can't find the process name, just return the tid instead */
        }
        return tid;
    }

    private String getProcessPTID(String tid) {

        String execPTID = fProcessPTIDMap.get(tid);
        if (execPTID != null) {
            return execPTID;
        }
        ITmfTrace trace = getTrace();
        if (trace == null) {
            return tid;
        }
        ITmfStateSystem kernelSs = TmfStateSystemAnalysisModule.getStateSystem(trace, KernelAnalysisModule.ID);
        if (kernelSs == null) {
            return tid;
        }
        try {
            int cpusNode = kernelSs.getQuarkAbsolute(Attributes.THREADS);

            /* Get the quarks for each cpu */
            List<Integer> cpuNodes = kernelSs.getSubAttributes(cpusNode, false);

            for (Integer tidQuark : cpuNodes) {
                if (kernelSs.getAttributeName(tidQuark).equals(tid)) {

                    int execPTIDQuark;

                    List<ITmfStateInterval> execPTIDIntervals;
                    try {

                        execPTIDQuark = kernelSs.getQuarkRelative(tidQuark, Attributes.PPID);

                        execPTIDIntervals = StateSystemUtils.queryHistoryRange(kernelSs, execPTIDQuark, getStartTime(), getEndTime());
                    } catch (AttributeNotFoundException e) {
                        /* No information on this thread (yet?), skip it for now */
                        continue;
                    } catch (StateSystemDisposedException e) {
                        /* State system is closing down, no point continuing */
                        break;
                    }
                    for (ITmfStateInterval execPTIDInterval : execPTIDIntervals) {
                        if (!execPTIDInterval.getStateValue().isNull() &&
                                execPTIDInterval.getStateValue().getType() == ITmfStateValue.Type.INTEGER) {
                           Integer execPTIDs = execPTIDInterval.getStateValue().unboxInt();
                           execPTID = execPTIDs.toString();
                            fProcessPTIDMap.put(tid, execPTID);
                            return execPTID;
                        }
                    }



                }
            }

        } catch (AttributeNotFoundException e) {
            /* can't find the process name, just return the tid instead */
        }
        return tid;
    }



}
