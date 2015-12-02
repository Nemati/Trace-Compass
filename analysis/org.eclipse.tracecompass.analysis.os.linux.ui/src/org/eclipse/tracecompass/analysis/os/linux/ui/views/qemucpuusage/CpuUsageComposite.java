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

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.analysis.os.linux.core.cpuusage.KernelCpuUsageAnalysis;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.Attributes;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.KernelAnalysisModule;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.AbstractTmfTreeViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeColumnDataProvider;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeViewerEntry;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeColumnData;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeColumnData.ITmfColumnPercentageProvider;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeViewerEntry;

/**
 * Tree viewer to display CPU usage information in a specified time range. It
 * shows the process's TID, its name, the time spent on the CPU during that
 * range, in % and absolute value.
 *
 * @author Geneviève Bastien
 */
public class CpuUsageComposite extends AbstractTmfTreeViewer {

    // Timeout between to wait for in the updateElements method
    private static final long BUILD_UPDATE_TIMEOUT = 500;

    private KernelCpuUsageAnalysis fModule = null;
    private String fSelectedThread = null;

    private static final String[] COLUMN_NAMES = new String[] {
            Messages.CpuUsageComposite_ColumnTID,
            Messages.CpuUsageComposite_ColumnProcess,
            Messages.CpuUsageComposite_ColumnPercent,
            Messages.CpuUsageComposite_ColumnTime
    };

    /* A map that saves the mapping of a thread ID to its executable name */
    private final Map<String, String> fProcessNameMap = new HashMap<>();
    /* A map that saves the mapping of a thread ID to its PTID */
    private final Map<String, String> fProcessPTIDMap = new HashMap<>();

    /** Provides label for the CPU usage tree viewer cells */
    protected static class CpuLabelProvider extends TreeLabelProvider {

        @Override
        public String getColumnText(Object element, int columnIndex) {
            CpuUsageEntry obj = (CpuUsageEntry) element;
            if (columnIndex == 0) {
                return obj.getTid();
            } else if (columnIndex == 1) {
                return obj.getProcessName();
            } else if (columnIndex == 2) {
                return String.format(Messages.CpuUsageComposite_TextPercent, obj.getPercent());
            } else if (columnIndex == 3) {
                return NLS.bind(Messages.CpuUsageComposite_TextTime, obj.getTime());
            }

            return element.toString();
        }

    }

    /**
     * Constructor
     *
     * @param parent
     *            The parent composite that holds this viewer
     */
    public CpuUsageComposite(Composite parent) {
        super(parent, false);
        setLabelProvider(new CpuLabelProvider());
    }

    @Override
    protected ITmfTreeColumnDataProvider getColumnDataProvider() {
        return new ITmfTreeColumnDataProvider() {

            @Override
            public List<TmfTreeColumnData> getColumnData() {
                /* All columns are sortable */
                List<TmfTreeColumnData> columns = new ArrayList<>();
                TmfTreeColumnData column = new TmfTreeColumnData(COLUMN_NAMES[0]);
                column.setComparator(new ViewerComparator() {
                    @Override
                    public int compare(Viewer viewer, Object e1, Object e2) {
                        CpuUsageEntry n1 = (CpuUsageEntry) e1;
                        CpuUsageEntry n2 = (CpuUsageEntry) e2;

                        return n1.getTid().compareTo(n2.getTid());

                    }
                });
                columns.add(column);
                column = new TmfTreeColumnData(COLUMN_NAMES[1]);
                column.setComparator(new ViewerComparator() {
                    @Override
                    public int compare(Viewer viewer, Object e1, Object e2) {
                        CpuUsageEntry n1 = (CpuUsageEntry) e1;
                        CpuUsageEntry n2 = (CpuUsageEntry) e2;

                        return n1.getProcessName().compareTo(n2.getProcessName());

                    }
                });
                columns.add(column);
                column = new TmfTreeColumnData(COLUMN_NAMES[2]);
                column.setComparator(new ViewerComparator() {
                    @Override
                    public int compare(Viewer viewer, Object e1, Object e2) {
                        CpuUsageEntry n1 = (CpuUsageEntry) e1;
                        CpuUsageEntry n2 = (CpuUsageEntry) e2;

                        return n1.getPercent().compareTo(n2.getPercent());

                    }
                });
                column.setPercentageProvider(new ITmfColumnPercentageProvider() {

                    @Override
                    public double getPercentage(Object data) {
                        CpuUsageEntry parent = (CpuUsageEntry) data;
                        return parent.getPercent() / 100;
                    }
                });
                columns.add(column);
                column = new TmfTreeColumnData(COLUMN_NAMES[3]);
                column.setComparator(new ViewerComparator() {
                    @Override
                    public int compare(Viewer viewer, Object e1, Object e2) {
                        CpuUsageEntry n1 = (CpuUsageEntry) e1;
                        CpuUsageEntry n2 = (CpuUsageEntry) e2;

                        return n1.getTime().compareTo(n2.getTime());

                    }
                });
                columns.add(column);

                return columns;
            }

        };
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    @Override
    protected void contentChanged(ITmfTreeViewerEntry rootEntry) {
        String selectedThread = fSelectedThread;
        if (selectedThread != null) {
            /* Find the selected thread among the inputs */
            for (ITmfTreeViewerEntry entry : rootEntry.getChildren()) {
                if (entry instanceof CpuUsageEntry) {
                    if (selectedThread.equals(((CpuUsageEntry) entry).getTid())) {
                        List<ITmfTreeViewerEntry> list = checkNotNull(Collections.singletonList(entry));
                        super.setSelection(list);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void initializeDataSource() {
        /* Should not be called while trace is still null */
        ITmfTrace trace = checkNotNull(getTrace());

        fModule = TmfTraceUtils.getAnalysisModuleOfClass(trace, KernelCpuUsageAnalysis.class, KernelCpuUsageAnalysis.ID);
        if (fModule == null) {
            return;
        }
        fModule.schedule();
        fModule.waitForInitialization();
        fProcessNameMap.clear();
        fProcessPTIDMap.clear();
    }

    @Override
    protected ITmfTreeViewerEntry updateElements(long start, long end, boolean isSelection) {
        if (isSelection || (start == end)) {
            return null;
        }
        if (getTrace() == null || fModule == null) {
            return null;
        }
        fModule.waitForInitialization();
        ITmfStateSystem ss = fModule.getStateSystem();
        if (ss == null) {
            return null;
        }

        boolean complete = false;
        long currentEnd = start;

        while (!complete && currentEnd < end) {
            complete = ss.waitUntilBuilt(BUILD_UPDATE_TIMEOUT);
            currentEnd = ss.getCurrentEndTime();
        }

        /* Initialize the data */
        Map<String, Long> cpuUsageMap = fModule.getCpuUsageInRange(Math.max(start, getStartTime()), Math.min(end, getEndTime()));

        TmfTreeViewerEntry root = new TmfTreeViewerEntry(""); //$NON-NLS-1$
        List<ITmfTreeViewerEntry> entryList = root.getChildren();

        Map<String, Long> fQemuPTIDMaps = new HashMap<>();
        for (Entry<String, Long> entry : cpuUsageMap.entrySet()) {
            /*
             * Process only entries representing the total of all CPUs and that
             * have time on CPU
             */
            if (entry.getValue() == 0) {
                continue;
            }
            if (!entry.getKey().startsWith(KernelCpuUsageAnalysis.TOTAL)) {
                continue;
            }
            String[] strings = entry.getKey().split(KernelCpuUsageAnalysis.SPLIT_STRING, 2);

            if ((strings.length > 1) && !(strings[1].equals(KernelCpuUsageAnalysis.TID_ZERO))) {
                if (getProcessName(strings[1]).equals("qemu-system-x86")){ //$NON-NLS-1$
                    String PTID = getProcessPTID(strings[1]);
                    String PTIDTmp = new String();
                    if (PTID.equals("1")){ //$NON-NLS-1$

                        if (fQemuPTIDMaps.get(strings[1]) != null){
                        fQemuPTIDMaps.put(strings[1], fQemuPTIDMaps.get(strings[1])+ entry.getValue());
                        } else {fQemuPTIDMaps.put(strings[1], entry.getValue());}

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

                        fQemuPTIDMaps.put(PTIDTmp,  tmpSum + entry.getValue());


                    }

                }
            }
        }
        for (String key : fQemuPTIDMaps.keySet()) {
            CpuUsageEntry obj = new CpuUsageEntry(key, getProcessName(key), (double) fQemuPTIDMaps.get(key) / (double) (end - start) * 100, fQemuPTIDMaps.get(key));
            entryList.add(obj);
        }


        return root;
    }

    /*
     * Get the process name from its TID by using the LTTng kernel analysis
     * module
     */
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


    /**
     * Set the currently selected thread ID
     *
     * @param tid
     *            The selected thread ID
     */
    public void setSelectedThread(String tid) {
        fSelectedThread = tid;
    }

}
