/*******************************************************************************
 * Copyright (c) 2015 Keba AG
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Christian Mansky - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.analysis.os.linux.ui.views.qemuView;

import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.Attributes;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.KernelAnalysisModule;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.StateValues;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceContext;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.dialogs.ITimeGraphEntryActiveProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;

/**
 * Provides Functionality for check Active / uncheck inactive
 *
 * @noinstantiate This class is not intended to be instantiated by clients.
 * @noextend This class is not intended to be subclassed by clients.
 * @since 1.0
 */
public class ControlFlowCheckActiveProviderQemu implements ITimeGraphEntryActiveProvider {

    String fLabel;
    String fTooltip;

    /**
     * @param label
     *            Button label
     * @param tooltip
     *            Button tooltip
     */
    public ControlFlowCheckActiveProviderQemu(String label, String tooltip) {
        fLabel = label;
        fTooltip = tooltip;
    }

    @Override
    public String getLabel() {
        return fLabel;
    }

    @Override
    public String getTooltip() {
        return fTooltip;
    }

    @Override
    public boolean isActive(ITimeGraphEntry element) {
        if (element instanceof ControlFlowEntryQemu) {
            ControlFlowEntryQemu cfe = (ControlFlowEntryQemu) element;

            TmfTraceManager traceManager = TmfTraceManager.getInstance();
            TmfTraceContext traceContext = traceManager.getCurrentTraceContext();
            TmfTimeRange winRange = traceContext.getWindowRange();
            TmfTimeRange selRange = traceContext.getSelectionRange();

            /* Take precedence of selection over window range. */
            long beginTS = selRange.getStartTime().getValue();
            long endTS = selRange.getEndTime().getValue();

            /* No selection, take window range */
            if (beginTS == endTS) {
                beginTS = winRange.getStartTime().getValue();
                endTS = winRange.getEndTime().getValue();
            }

            ITmfTrace trace = cfe.getTrace();
            ITmfStateSystem ssq = TmfStateSystemAnalysisModule.getStateSystem(trace, KernelAnalysisModule.ID);
            if (ssq != null) {
                beginTS = Math.max(beginTS, ssq.getStartTime());
                endTS = Math.min(endTS, ssq.getCurrentEndTime());
                if (beginTS > endTS) {
                    return false;
                }
                try {
                    int statusQuark = ssq.getQuarkRelative(cfe.getThreadQuark(), Attributes.STATUS);

                    /* Get the initial state at beginTS */
                    ITmfStateInterval currentInterval = ssq.querySingleState(beginTS, statusQuark);
                    if (isIntervalInStateActive(currentInterval)) {
                        return true;
                    }

                    /* Get the following state changes */
                    long ts = currentInterval.getEndTime();
                    while (ts != -1 && ts < endTS) {
                        ts++; /* To "jump over" to the next state in the history */
                        currentInterval = ssq.querySingleState(ts, statusQuark);
                        if (isIntervalInStateActive(currentInterval)) {
                            return true;
                        }
                        ts = currentInterval.getEndTime();
                    }
                } catch (AttributeNotFoundException | StateSystemDisposedException e) {
                    /* Ignore ... */
                }
            }
        }

        return false;
    }

    private static boolean isIntervalInStateActive (ITmfStateInterval ival) {
        int value = ival.getStateValue().unboxInt();
        /* An entry is only active when running */
        if (value == StateValues.PROCESS_STATUS_RUN_USERMODE || value == StateValues.PROCESS_STATUS_RUN_SYSCALL ||
                value == StateValues.PROCESS_STATUS_INTERRUPTED) {
            return true;
        }

        return false;
    }

}
