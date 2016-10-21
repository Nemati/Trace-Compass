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
 *   Geneviève Bastien - Move code to provide base classes for time graph view
 *******************************************************************************/

package org.eclipse.tracecompass.analysis.os.linux.ui.views.resources;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.Attributes;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.KernelAnalysisModule;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.StateValues;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.analysis.os.linux.ui.views.resources.ResourcesEntry.Type;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.Activator;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.Messages;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.StateItem;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.ITmfTimeGraphDrawingHelper;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils.Resolution;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils.TimeFormat;

/**
 * Presentation provider for the Resource view, based on the generic TMF
 * presentation provider.
 *
 * @author Patrick Tasse
 */
public class ResourcesPresentationProvider extends TimeGraphPresentationProvider {

    private long fLastThreadId = -1;
    private Color fColorWhite;
    private Color fColorGray;
    private Integer fAverageCharWidth;

    private enum State {
        IDLE             (new RGB(200, 200, 200)),
        USERMODE         (new RGB(  0, 200,   0)),
        SYSCALL          (new RGB(  0,   0, 200)),
        IRQ              (new RGB(200,   0, 100)),
        SOFT_IRQ         (new RGB(200, 150, 100)),
        IRQ_ACTIVE       (new RGB(200,   0, 100)),
        SOFT_IRQ_RAISED  (new RGB(200, 200,   0)),
        SOFT_IRQ_ACTIVE  (new RGB(200, 150, 100)),
        SUBMITED_IO      (new RGB (51,0,0)),
        READ_IO_QEMU     (new RGB (0,0,204)),
        WRITE_IO_QEMU    (new RGB (204,0,0)),
        OTHER_IO_QEMU    (new RGB (0,51,25)),
        CPU_Qemu_Busy_ONE (new RGB (255,64,64)),
        CPU_Qemu_Busy_TWO (new RGB (200,51,51)),
        CPU_Qemu_Busy_THREE (new RGB (150,34,34)),
        CPU_Qemu_Busy_FOUR (new RGB (100,0,0)),
        CPU_Qemu_Busy_MANY (new RGB (50,0,0)),
        Net_Qemu_Busy (new RGB (255,20,147)),
        VMX_Non_Root (new RGB (166,86,40)),
        VMX_Root (new RGB (255,127,0)),
        VMX_Root_Disk (new RGB (128,205,193)),
        VMX_Root_Net (new RGB (1,133,113)),
        VMX_NESTED_ROOT (new RGB(255,127,0)),
        VMX_NESTED_L1_PREEMPTED (new RGB(55,126,184)),
        VMX_NESTED_L0_PREEMPTED (new RGB(152,78,163)),
        VMX_NESTED_NON_ROOT (new RGB(228,26,28)),
        PROCESS_BUSY_ONE (new RGB(50,136,189)),
        PROCESS_BUSY_TWO (new RGB(153,213,148)),
        PROCESS_BUSY_THREE (new RGB(230,245,152)),
        PROCESS_BUSY_MANY (new RGB(252,141,89)),
        vCPU_WAIT_PCPU (new RGB(99,99,99));

        public final RGB rgb;

        private State(RGB rgb) {
            this.rgb = rgb;
        }
    }

    /**
     * Default constructor
     */
    public ResourcesPresentationProvider() {
        super();
    }

    private static State[] getStateValues() {
        return State.values();
    }

    private static State getEventState(TimeEvent event) {
        if (event.hasValue()) {
            ResourcesEntry entry = (ResourcesEntry) event.getEntry();
            int value = event.getValue();

            if (entry.getType() == Type.CPU) {
                if (value == StateValues.CPU_STATUS_IDLE) {
                    return State.IDLE;
                } else if (value == StateValues.CPU_STATUS_RUN_USERMODE) {
                    return State.USERMODE;
                } else if (value == StateValues.CPU_STATUS_RUN_SYSCALL) {
                    return State.SYSCALL;
                } else if (value == StateValues.CPU_STATUS_IRQ) {
                    return State.IRQ;
                } else if (value == StateValues.CPU_STATUS_SOFTIRQ) {
                    return State.SOFT_IRQ;
                }
                else if (value == StateValues.CPU_STATUS_VMX_NON_ROOT) {
                    return State.VMX_Non_Root;
                } else if (value == StateValues.CPU_STATUS_VMX_ROOT) {
                    return State.VMX_Root;
                }else if (value == StateValues.CPU_STATUS_VMX_ROOT_DISK) {
                    return State.VMX_Root_Disk;
                }else if (value == StateValues.CPU_STATUS_VMX_ROOT_NET) {
                    return State.VMX_Root_Net;
                }else if (value == StateValues.CPU_STATUS_VMX_NESTED_ROOT) {
                    return State.VMX_NESTED_ROOT;
                }else if (value == StateValues.CPU_STATUS_VMX_NESTED_NON_ROOT) {
                    return State.VMX_NESTED_NON_ROOT;
                }else if (value == StateValues.L1_PREEMPTED) {
                    return State.VMX_NESTED_L1_PREEMPTED;
                }else if (value == StateValues.L0_PREEMPTED) {
                    return State.VMX_NESTED_L0_PREEMPTED;
                } else if (value == StateValues.VCPU_WAIT_FOR_PCPU){
                    return State.vCPU_WAIT_PCPU;
                }

            } else if (entry.getType() == Type.IRQ) {
                return State.IRQ_ACTIVE;
            } else if (entry.getType() == Type.SOFT_IRQ) {
                if (value == StateValues.SOFT_IRQ_RAISED) {
                    return State.SOFT_IRQ_RAISED;
                }
                return State.SOFT_IRQ_ACTIVE;
            }
            else if (entry.getType() == Type.IOQemuRead) {
                if (value == StateValues.IO_STATUS_IDLE) {
                    return State.IDLE;
                } else if (value == StateValues.IO_WRITE_QEMU) {
                    return State.WRITE_IO_QEMU;
                } else if (value == StateValues.IO_READ_QEMU) {
                    return State.READ_IO_QEMU;
                }
                else if (value == StateValues.IO_OTHER) {
                    return State.OTHER_IO_QEMU;
                }
            }
            else if (entry.getType() == Type.IOQemuWrite) {
                if (value == StateValues.IO_STATUS_IDLE) {
                    return State.IDLE;
                } else if (value == StateValues.IO_WRITE_QEMU) {
                    return State.WRITE_IO_QEMU;
                } else if (value == StateValues.IO_READ_QEMU) {
                    return State.READ_IO_QEMU;
                }
                else if (value == StateValues.IO_OTHER) {
                    return State.OTHER_IO_QEMU;
                }
            }
            else if (entry.getType() == Type.CPUQemu) {
                if (value == StateValues.CPU_QEMU_IDLE) {
                    return State.IDLE;
                } else if (value == StateValues.CPU_QEMU_RUN_ONE) {
                    return State.CPU_Qemu_Busy_ONE;
                }   else if (value == StateValues.CPU_QEMU_RUN_TWO) {
                    return State.CPU_Qemu_Busy_TWO;
                }   else if (value == StateValues.CPU_QEMU_RUN_THREE) {
                    return State.CPU_Qemu_Busy_THREE;
                }   else if (value == StateValues.CPU_QEMU_RUN_FOUR) {
                    return State.CPU_Qemu_Busy_FOUR;
                }  else if (value == StateValues.CPU_QEMU_RUN_MANY) {
                    return State.CPU_Qemu_Busy_MANY;
                }
            }
            else if (entry.getType() == Type.Process){
                if (value==1){
                    return State.PROCESS_BUSY_ONE;
                } else if (value==2){
                    return State.PROCESS_BUSY_TWO;
                } else if (value==3){
                    return State.PROCESS_BUSY_THREE;
                } else if (value>3){
                    return State.PROCESS_BUSY_MANY;
                }

                return State.IDLE;
            }
            else if (entry.getType() == Type.NetQemu) {
                if (value == StateValues.NET_QEMU_IDLE) {
                    return State.IDLE;
                } else if (value == StateValues.NET_QEMU_RUN) {
                    return State.Net_Qemu_Busy;
                }
            }
        }
        return null;
    }

    @Override
    public int getStateTableIndex(ITimeEvent event) {
        State state = getEventState((TimeEvent) event);
        if (state != null) {
            return state.ordinal();
        }
        if (event instanceof NullTimeEvent) {
            return INVISIBLE;
        }
        return TRANSPARENT;
    }

    @Override
    public StateItem[] getStateTable() {
        State[] states = getStateValues();
        StateItem[] stateTable = new StateItem[states.length];
        for (int i = 0; i < stateTable.length; i++) {
            State state = states[i];
            stateTable[i] = new StateItem(state.rgb, state.toString());
        }
        return stateTable;
    }

    @Override
    public String getEventName(ITimeEvent event) {
        State state = getEventState((TimeEvent) event);
        if (state != null) {
            return state.toString();
        }
        if (event instanceof NullTimeEvent) {
            return null;
        }
        return Messages.ResourcesView_multipleStates;
    }

    @Override
    public Map<String, String> getEventHoverToolTipInfo(ITimeEvent event, long hoverTime) {

        Map<String, String> retMap = new LinkedHashMap<>();
        if (event instanceof TimeEvent && ((TimeEvent) event).hasValue()) {

            TimeEvent tcEvent = (TimeEvent) event;
            ResourcesEntry entry = (ResourcesEntry) event.getEntry();

            if (tcEvent.hasValue()) {
                ITmfStateSystem ss = TmfStateSystemAnalysisModule.getStateSystem(entry.getTrace(), KernelAnalysisModule.ID);
                if (ss == null) {
                    return retMap;
                }
                try{

                    int inform = 1;
                    int nestedVMbool = 0;
                    if (inform == 1){
                        System.out.println("Printing...");
                        Integer cpuqemuQuark = ss.getQuarkAbsolute("CPUQemu");
                        List<Integer> VMsRoots = ss.getSubAttributes(cpuqemuQuark, false);
                        for (Integer VMRoot:VMsRoots){
                            int vCPUQuark = ss.getQuarkRelative(VMRoot,"vCPU");

                            List<Integer> VMvCPUs = ss.getSubAttributes(vCPUQuark, false);
                            ITmfStateValue value ;

                            Long sumVMXRoot = 0L;
                            Long sumVMXNonRoot = 0L;
                            for (Integer VMvCPU:VMvCPUs){
                                Integer VMvCPUStatus = ss.getQuarkRelative(VMvCPU,"Status");
                                List<ITmfStateInterval> intervalList = StateSystemUtils.queryHistoryRange(ss,VMvCPUStatus,ss.getStartTime(),hoverTime);

                                for (ITmfStateInterval intervals:intervalList){
                                    if (!intervals.getStateValue().isNull()) {
                                        value = intervals.getStateValue();
                                        if (value.unboxInt()==StateValues.CPU_STATUS_VMX_ROOT){
                                            sumVMXRoot+= intervals.getEndTime()-intervals.getStartTime();
                                        } else if (value.unboxInt()==StateValues.CPU_STATUS_VMX_NON_ROOT){
                                            sumVMXNonRoot+= intervals.getEndTime()-intervals.getStartTime();
                                        }
                                    }
                                }
                            }
                            System.out.println("Time:"+ hoverTime + "   VMXRoot:"+ sumVMXRoot + "   VMXNonRoot:"+ sumVMXNonRoot );
                            if (nestedVMbool == 1){
                                int nestedVMQuark = ss.getQuarkRelative(VMRoot,"nestedVM");
                                List<Integer> nestedVMsRoot = ss.getSubAttributes(nestedVMQuark, false);

                                for (Integer nestedVMRoot:nestedVMsRoot){
                                    sumVMXRoot = 0L;
                                    sumVMXNonRoot = 0L;
                                    Long sumVMXNestedRoot = 0L;
                                    Long sumVMXNestedNonRoot = 0L;
                                    VMvCPUs = ss.getSubAttributes(nestedVMRoot, false);
                                    for (Integer VMvCPU:VMvCPUs){
                                        int VMvCPUStatus = ss.getQuarkRelative(VMvCPU,"Status");
                                        List<ITmfStateInterval> intervalList = StateSystemUtils.queryHistoryRange(ss,VMvCPUStatus,ss.getStartTime(),hoverTime);
                                        for (ITmfStateInterval intervals:intervalList){
                                            if (!intervals.getStateValue().isNull()) {
                                                value = intervals.getStateValue();
                                                if (value.unboxInt()==StateValues.CPU_STATUS_VMX_ROOT){
                                                    sumVMXRoot+= intervals.getEndTime()-intervals.getStartTime();
                                                } else if (value.unboxInt()==StateValues.CPU_STATUS_VMX_NON_ROOT){
                                                    sumVMXNonRoot+= intervals.getEndTime()-intervals.getStartTime();
                                                } else if (value.unboxInt()==StateValues.CPU_STATUS_VMX_NESTED_NON_ROOT){
                                                    sumVMXNestedNonRoot+= intervals.getEndTime()-intervals.getStartTime();
                                                } else if (value.unboxInt()==StateValues.CPU_STATUS_VMX_NESTED_ROOT){
                                                    sumVMXNestedRoot+= intervals.getEndTime()-intervals.getStartTime();
                                                }
                                            }
                                        }
                                    }
                                    System.out.println("Time:"+ hoverTime + "   VMXRoot:"+ sumVMXRoot + "   VMXNonRoot:"+ sumVMXNonRoot + "   VMXNestedRoot:"+ sumVMXNestedRoot + "   VMXNestedNonRoot:"+ sumVMXNestedNonRoot );
                                }
                            }
                        }
                    }
                } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                    Activator.getDefault().logError("Error in ResourcesPresentationProvider", e); //$NON-NLS-1$
                } catch (StateSystemDisposedException e) {

                }

                // Check for IO
                if (entry.getType().equals(Type.IOQemuRead)) {


                    int status = tcEvent.getValue();

                    try {
                        int IOQuark = entry.getQuark();

                        if (status == StateValues.IO_READ_QEMU){
                            int currentThreadQuark = ss.getQuarkRelative(IOQuark, "read"); //$NON-NLS-1$
                            currentThreadQuark = ss.getQuarkRelative(currentThreadQuark, "transfer"); //$NON-NLS-1$
                            ITmfStateInterval interval = ss.querySingleState(hoverTime, currentThreadQuark);
                            if (!interval.getStateValue().isNull()) {
                                ITmfStateValue value = interval.getStateValue();
                                retMap.put("Read",value.toString()); //$NON-NLS-1$
                            }
                        }
                        int currentThreadQuark = ss.getQuarkRelative(IOQuark, "read"); //$NON-NLS-1$
                        currentThreadQuark = ss.getQuarkRelative(currentThreadQuark, "numberOfSubmited"); //$NON-NLS-1$
                        ITmfStateInterval interval = ss.querySingleState(hoverTime, currentThreadQuark);
                        if (!interval.getStateValue().isNull()) {
                            ITmfStateValue value = interval.getStateValue();
                            retMap.put("# Submited",value.toString());  //$NON-NLS-1$
                        }
                        //$NON-NLS-2$

                    } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                        Activator.getDefault().logError("Error in ResourcesPresentationProvider", e); //$NON-NLS-1$
                    } catch (StateSystemDisposedException e) {
                        /* Ignored */
                    }
                }

                if (entry.getType().equals(Type.IOQemuWrite)) {


                    int status = tcEvent.getValue();

                    try {
                        int IOQuark = entry.getQuark();
                        if (status == StateValues.IO_WRITE_QEMU){
                            int currentThreadQuark = ss.getQuarkRelative(IOQuark, "write"); //$NON-NLS-1$
                            currentThreadQuark = ss.getQuarkRelative(currentThreadQuark, "transfer"); //$NON-NLS-1$
                            ITmfStateInterval interval = ss.querySingleState(hoverTime, currentThreadQuark);
                            if (!interval.getStateValue().isNull()) {
                                ITmfStateValue value = interval.getStateValue();
                                retMap.put("Write",value.toString()); //$NON-NLS-1$
                            }
                        }


                        int currentThreadQuark = ss.getQuarkRelative(IOQuark, "write"); //$NON-NLS-1$
                        currentThreadQuark = ss.getQuarkRelative(currentThreadQuark, "numberOfSubmited"); //$NON-NLS-1$
                        ITmfStateInterval interval = ss.querySingleState(hoverTime, currentThreadQuark);
                        if (!interval.getStateValue().isNull()) {
                            ITmfStateValue value = interval.getStateValue();
                            retMap.put("# Submited",value.toString());  //$NON-NLS-1$
                        }
                        //$NON-NLS-2$

                    } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                        Activator.getDefault().logError("Error in ResourcesPresentationProvider", e); //$NON-NLS-1$
                    } catch (StateSystemDisposedException e) {
                        /* Ignored */
                    }
                }


                // Check for CPU Qemu
                if (entry.getType().equals(Type.CPUQemu)) {
                    int CPUQuark = entry.getQuark();
                    try {
                        int currentThreadQuark = ss.getQuarkRelative(CPUQuark, "ValueCPU"); //$NON-NLS-1$
                        ITmfStateInterval interval = ss.querySingleState(hoverTime, currentThreadQuark);
                        if (!interval.getStateValue().isNull()) {
                            ITmfStateValue value = interval.getStateValue();
                            retMap.put("# Used CPU",value.toString());  //$NON-NLS-1$
                        }
                    }
                    catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                        Activator.getDefault().logError("Error in ResourcesPresentationProvider", e); //$NON-NLS-1$
                    } catch (StateSystemDisposedException e) {
                        /* Ignored */
                    }
                }

                // Check for Net Qemu
                if (entry.getType().equals(Type.NetQemu)) {
                    int NetQuark = entry.getQuark();
                    try {
                        int currentThreadQuark = ss.getQuarkRelative(NetQuark, "Netif"); //$NON-NLS-1$
                        ITmfStateInterval interval = ss.querySingleState(hoverTime, currentThreadQuark);
                        if (!interval.getStateValue().isNull()) {
                            ITmfStateValue value = interval.getStateValue();
                            retMap.put("RX",value.toString());  //$NON-NLS-1$
                        }
                        currentThreadQuark = ss.getQuarkRelative(NetQuark, "Netdev"); //$NON-NLS-1$
                        interval = ss.querySingleState(hoverTime, currentThreadQuark);
                        if (!interval.getStateValue().isNull()) {
                            ITmfStateValue value = interval.getStateValue();
                            retMap.put("TX",value.toString());  //$NON-NLS-1$
                        }


                    }
                    catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                        Activator.getDefault().logError("Error in ResourcesPresentationProvider", e); //$NON-NLS-1$
                    } catch (StateSystemDisposedException e) {
                        /* Ignored */
                    }
                }

                // Check for IRQ or Soft_IRQ type
                if (entry.getType().equals(Type.IRQ) || entry.getType().equals(Type.SOFT_IRQ)) {

                    // Get CPU of IRQ or SoftIRQ and provide it for the tooltip display
                    int cpu = tcEvent.getValue();
                    if (cpu >= 0) {
                        retMap.put(Messages.ResourcesView_attributeCpuName, String.valueOf(cpu));
                    }
                }

                // Check for type CPU
                else if (entry.getType().equals(Type.CPU)) {
                    int status = tcEvent.getValue();

                    if (status == StateValues.CPU_STATUS_IRQ) {
                        // In IRQ state get the IRQ that caused the interruption
                        int cpu = entry.getId();

                        try {
                            List<ITmfStateInterval> fullState = ss.queryFullState(event.getTime());
                            List<Integer> irqQuarks = ss.getQuarks(Attributes.RESOURCES, Attributes.IRQS, "*"); //$NON-NLS-1$

                            for (int irqQuark : irqQuarks) {
                                if (fullState.get(irqQuark).getStateValue().unboxInt() == cpu) {
                                    ITmfStateInterval value = ss.querySingleState(event.getTime(), irqQuark);
                                    if (!value.getStateValue().isNull()) {
                                        int irq = Integer.parseInt(ss.getAttributeName(irqQuark));
                                        retMap.put(Messages.ResourcesView_attributeIrqName, String.valueOf(irq));
                                    }
                                    break;
                                }
                            }
                        } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                            Activator.getDefault().logError("Error in ResourcesPresentationProvider", e); //$NON-NLS-1$
                        } catch (StateSystemDisposedException e) {
                            /* Ignored */
                        }
                    } else if (status == StateValues.CPU_STATUS_SOFTIRQ) {
                        // In SOFT_IRQ state get the SOFT_IRQ that caused the interruption
                        int cpu = entry.getId();

                        try {
                            List<ITmfStateInterval> fullState = ss.queryFullState(event.getTime());
                            List<Integer> softIrqQuarks = ss.getQuarks(Attributes.RESOURCES, Attributes.SOFT_IRQS, "*"); //$NON-NLS-1$

                            for (int softIrqQuark : softIrqQuarks) {
                                if (fullState.get(softIrqQuark).getStateValue().unboxInt() == cpu) {
                                    ITmfStateInterval value = ss.querySingleState(event.getTime(), softIrqQuark);
                                    if (!value.getStateValue().isNull()) {
                                        int softIrq = Integer.parseInt(ss.getAttributeName(softIrqQuark));
                                        retMap.put(Messages.ResourcesView_attributeSoftIrqName, String.valueOf(softIrq));
                                    }
                                    break;
                                }
                            }
                        } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                            Activator.getDefault().logError("Error in ResourcesPresentationProvider", e); //$NON-NLS-1$
                        } catch (StateSystemDisposedException e) {
                            /* Ignored */
                        }
                    } else if (status == StateValues.CPU_STATUS_RUN_USERMODE || status == StateValues.CPU_STATUS_RUN_SYSCALL) {
                        // In running state get the current tid

                        try {
                            retMap.put(Messages.ResourcesView_attributeHoverTime, Utils.formatTime(hoverTime, TimeFormat.CALENDAR, Resolution.NANOSEC));
                            int cpuQuark = entry.getQuark();
                            int currentThreadQuark = ss.getQuarkRelative(cpuQuark, Attributes.CURRENT_THREAD);
                            ITmfStateInterval interval = ss.querySingleState(hoverTime, currentThreadQuark);
                            if (!interval.getStateValue().isNull()) {
                                ITmfStateValue value = interval.getStateValue();
                                int currentThreadId = value.unboxInt();
                                retMap.put(Messages.ResourcesView_attributeTidName, Integer.toString(currentThreadId));
                                int execNameQuark = ss.getQuarkAbsolute(Attributes.THREADS, Integer.toString(currentThreadId), Attributes.EXEC_NAME);
                                interval = ss.querySingleState(hoverTime, execNameQuark);
                                if (!interval.getStateValue().isNull()) {
                                    value = interval.getStateValue();
                                    retMap.put(Messages.ResourcesView_attributeProcessName, value.unboxStr());
                                }
                                if (status == StateValues.CPU_STATUS_RUN_SYSCALL) {
                                    int syscallQuark = ss.getQuarkAbsolute(Attributes.THREADS, Integer.toString(currentThreadId), Attributes.SYSTEM_CALL);
                                    interval = ss.querySingleState(hoverTime, syscallQuark);
                                    if (!interval.getStateValue().isNull()) {
                                        value = interval.getStateValue();
                                        retMap.put(Messages.ResourcesView_attributeSyscallName, value.unboxStr());
                                    }
                                }
                            }
                        } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                            Activator.getDefault().logError("Error in ResourcesPresentationProvider", e); //$NON-NLS-1$
                        } catch (StateSystemDisposedException e) {
                            /* Ignored */
                        }
                    }
                    else if (status == StateValues.CPU_STATUS_VMX_NON_ROOT || status == StateValues.CPU_STATUS_VMX_ROOT ||status == StateValues.CPU_STATUS_VMX_NESTED_ROOT || status == StateValues.CPU_STATUS_VMX_NESTED_NON_ROOT ) {
                        // In running state get the current tid

                        try {
                            retMap.put(Messages.ResourcesView_attributeHoverTime, Utils.formatTime(hoverTime, TimeFormat.CALENDAR, Resolution.NANOSEC));
                            int cpuQuark = entry.getQuark();
                            int currentThreadQuark = ss.getQuarkRelative(cpuQuark, Attributes.CURRENT_THREAD);
                            ITmfStateInterval interval = ss.querySingleState(hoverTime, currentThreadQuark);
                            if (!interval.getStateValue().isNull()) {
                                ITmfStateValue value = interval.getStateValue();
                                int currentThreadId = value.unboxInt();
                                retMap.put(Messages.ResourcesView_attributeTidName, Integer.toString(currentThreadId));
                                int execNameQuark = ss.getQuarkAbsolute(Attributes.THREADS, Integer.toString(currentThreadId), Attributes.EXEC_NAME);
                                interval = ss.querySingleState(hoverTime, execNameQuark);
                                if (!interval.getStateValue().isNull()) {
                                    value = interval.getStateValue();
                                    retMap.put(Messages.ResourcesView_attributeProcessName, value.unboxStr());
                                }
                                // int quarkVMname = ss.getQuarkAbsolute("vmName");
                                Integer threadPTID = 0;
                                int newCurrentThreadNodeTmp = 0;

                                int currentThreadCPU = ss.getQuarkAbsolute(Attributes.THREADS, Integer.toString(currentThreadId), Attributes.PPID);
                                interval = ss.querySingleState(hoverTime, currentThreadCPU);
                                value = interval.getStateValue();

                                Integer PTID = value.isNull() ? 0 : value.unboxInt();
                                while (PTID != 1) {
                                    newCurrentThreadNodeTmp = ss.getQuarkAbsolute(Attributes.THREADS,PTID.toString(),Attributes.PPID);
                                    interval = ss.querySingleState(hoverTime, newCurrentThreadNodeTmp);
                                    if (!interval.getStateValue().isNull()) {
                                        value = interval.getStateValue();
                                    }
                                    threadPTID = PTID;
                                    PTID = value.isNull() ? 0 : value.unboxInt();

                                }
                                int quarkNamePTID = ss.getQuarkAbsolute("CPUQemu",threadPTID.toString(),"vmName"); //$NON-NLS-1$ //$NON-NLS-2$
                                interval = ss.querySingleState(hoverTime, quarkNamePTID);
                                if (!interval.getStateValue().isNull()) {
                                    value = interval.getStateValue();
                                }
                                retMap.put("VM-Name",value.toString()); //$NON-NLS-1$


                                int vcpuQuark = ss.getQuarkAbsolute(Attributes.THREADS, Integer.toString(currentThreadId), "vcpu"); //$NON-NLS-1$
                                interval = ss.querySingleState(hoverTime, vcpuQuark);
                                value = interval.getStateValue();
                                Integer vcpu_id = Integer.valueOf(value.toString());
                                retMap.put("VCPU", value.unboxStr()); //$NON-NLS-1$
                                int quarkThreadPTID = 0;
                                try{
                                    boolean invest = false;
                                    if (invest==true ){
                                        quarkThreadPTID = ss.getQuarkAbsolute("CPUQemu",threadPTID.toString(),"vCPU",vcpu_id.toString(),"thread"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                        interval = ss.querySingleState(hoverTime, quarkThreadPTID);
                                        if (!interval.getStateValue().isNull()) {
                                            value = interval.getStateValue();
                                        }
                                        if (value.unboxInt()!=0){
                                            retMap.put("T-ID-IN",value.toString()); //$NON-NLS-1$
                                        }

                                        //quarkThreadPTID = ss.getQuarkAbsolute("vmName",threadPTID.toString(),"vCPU",vcpu_id.toString(),"thread");
                                        //quarkThreadPTID = ss.getQuarkAbsolute("CPUQemu",threadPTID.toString(),"vCPU",vcpu_id.toString(),"threadName"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                        //interval = ss.querySingleState(hoverTime, quarkThreadPTID);
                                        //if (!interval.getStateValue().isNull()) {
                                        //  value = interval.getStateValue();
                                        //}
                                        //retMap.put("T-Name-IN",value.toString()); //$NON-NLS-1$
                                    }
                                } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                                    Activator.getDefault().logError("Error in ResourcesPresentationProvider", e); //$NON-NLS-1$
                                } catch (StateSystemDisposedException e) {
                                    /* Ignored */
                                }
                                quarkThreadPTID = ss.getQuarkAbsolute("CPUQemu",threadPTID.toString(),"vCPU",vcpu_id.toString(),"Status"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                interval = ss.querySingleState(hoverTime, quarkThreadPTID);
                                if (!interval.getStateValue().isNull()) {
                                    value = interval.getStateValue();
                                }
                                if (value.unboxInt()==2){
                                    retMap.put("T-State-IN","USERSPACE"); //$NON-NLS-1$ //$NON-NLS-2$
                                } else  if (value.unboxInt()==3){
                                    retMap.put("T-State-IN","SYSCALL"); //$NON-NLS-1$ //$NON-NLS-2$
                                }
                                if (status == StateValues.CPU_STATUS_VMX_ROOT || status == StateValues.CPU_STATUS_VMX_NESTED_ROOT){
                                    int exitQuark = ss.getQuarkAbsolute(Attributes.THREADS, Integer.toString(currentThreadId), "exit_reason"); //$NON-NLS-1$
                                    interval = ss.querySingleState(hoverTime, exitQuark);
                                    List<ITmfStateInterval> intervalList = StateSystemUtils.queryHistoryRange(ss,exitQuark,ss.getStartTime(),hoverTime);
                                    Long sum = 0L;
                                    for (ITmfStateInterval intervals:intervalList){
                                        if (!interval.getStateValue().isNull()) {
                                            value = intervals.getStateValue();
                                            if (value.unboxInt()==1){
                                                sum+= intervals.getEndTime()-intervals.getStartTime();
                                            }
                                        }
                                    }

                                    if (!interval.getStateValue().isNull()) {
                                        value = interval.getStateValue();
                                        int exit_reason = value.unboxInt();
                                        switch (exit_reason){
                                        case 0: {
                                            retMap.put("Exit Reason","Exception or NMI");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 1: {
                                            retMap.put("Exit Reason","External Intrupt");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 2: {
                                            retMap.put("Exit Reason","Triple fault");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 3: {
                                            retMap.put("Exit Reason","INIT signal");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 4: {
                                            retMap.put("Exit Reason","start-up IPI");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 7: {
                                            retMap.put("Exit Reason","Intrupt Window");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 9: {
                                            retMap.put("Exit Reason","Task Switch");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 10: {
                                            retMap.put("Exit Reason","CPUID");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 12: {
                                            retMap.put("Exit Reason","HLT exiting");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 21:{
                                            retMap.put("Exit Reason","VMPTRLD");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 20: {
                                            retMap.put("Exit Reason","VM LUNCH");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 24: {
                                            retMap.put("Exit Reason","VM RESUME");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 28: {
                                            retMap.put("Exit Reason","Control-Register accesses");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 30: {
                                            retMap.put("Exit Reason","I/O instruction");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 31: {
                                            retMap.put("Exit Reason","RDMSR");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 32: {
                                            retMap.put("Exit Reason","RWMSR");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 40: {
                                            retMap.put("Exit Reason","PAUSE");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 41: {
                                            retMap.put("Exit Reason","VM-Entry failure due to machine check");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 43: {
                                            retMap.put("Exit Reason","TPR below threshold");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 44: {
                                            retMap.put("Exit Reason","APIC access");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 48: {
                                            retMap.put("Exit Reason","EPT violation");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        case 55: {
                                            retMap.put("Exit Reason","XSETBV");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        default : {
                                            retMap.put("Exit Reason","Unkown");  //$NON-NLS-1$ //$NON-NLS-2$
                                            break;
                                        }
                                        }
                                    }

                                }
                            }

                        } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                            Activator.getDefault().logError("Error in ResourcesPresentationProvider", e); //$NON-NLS-1$
                        } catch (StateSystemDisposedException e) {
                            /* Ignored */
                        }
                    }



                }
            }
        }

        return retMap;
    }

    @Override
    public void postDrawEvent(ITimeEvent event, Rectangle bounds, GC gc) {
        if (fColorGray == null) {
            fColorGray = gc.getDevice().getSystemColor(SWT.COLOR_GRAY);
        }
        if (fColorWhite == null) {
            fColorWhite = gc.getDevice().getSystemColor(SWT.COLOR_WHITE);
        }
        if (fAverageCharWidth == null) {
            fAverageCharWidth = gc.getFontMetrics().getAverageCharWidth();
        }

        ITmfTimeGraphDrawingHelper drawingHelper = getDrawingHelper();
        if (bounds.width <= fAverageCharWidth) {
            return;
        }

        if (!(event instanceof TimeEvent)) {
            return;
        }
        TimeEvent tcEvent = (TimeEvent) event;
        if (!tcEvent.hasValue()) {
            return;
        }

        ResourcesEntry entry = (ResourcesEntry) event.getEntry();
        if (!entry.getType().equals(Type.CPU)) {
            return;
        }

        int status = tcEvent.getValue();
        if (status != StateValues.CPU_STATUS_RUN_USERMODE && status != StateValues.CPU_STATUS_RUN_SYSCALL && status !=StateValues.CPU_STATUS_VMX_NESTED_ROOT &&  status != StateValues.CPU_STATUS_VMX_ROOT) {
            return;
        }

        ITmfStateSystem ss = TmfStateSystemAnalysisModule.getStateSystem(entry.getTrace(), KernelAnalysisModule.ID);
        if (ss == null) {
            return;
        }
        long time = event.getTime();
        try {
            while (time < event.getTime() + event.getDuration()) {
                int cpuQuark = entry.getQuark();
                int currentThreadQuark = ss.getQuarkRelative(cpuQuark, Attributes.CURRENT_THREAD);
                ITmfStateInterval tidInterval = ss.querySingleState(time, currentThreadQuark);
                long startTime = Math.max(tidInterval.getStartTime(), event.getTime());
                int x = Math.max(drawingHelper.getXForTime(startTime), bounds.x);
                if (x >= bounds.x + bounds.width) {
                    break;
                }
                if (!tidInterval.getStateValue().isNull()) {
                    ITmfStateValue value = tidInterval.getStateValue();
                    int currentThreadId = value.unboxInt();
                    long endTime = Math.min(tidInterval.getEndTime() + 1, event.getTime() + event.getDuration());
                    int xForEndTime = drawingHelper.getXForTime(endTime);
                    if (xForEndTime > bounds.x) {
                        int width = Math.min(xForEndTime, bounds.x + bounds.width) - x - 1;
                        if (width > 0) {
                            String attribute = null;
                            int beginIndex = 0;
                            if (status == StateValues.CPU_STATUS_RUN_USERMODE && currentThreadId != fLastThreadId) {
                                attribute = Attributes.EXEC_NAME;

                            } else if (status == StateValues.CPU_STATUS_VMX_NESTED_ROOT || status == StateValues.CPU_STATUS_VMX_ROOT){
                                attribute = "exit_reason";
                                int quark = ss.getQuarkAbsolute(Attributes.THREADS, Integer.toString(currentThreadId), attribute);
                                ITmfStateInterval interval = ss.querySingleState(time, quark);
                                if (!interval.getStateValue().isNull()) {
                                    value = interval.getStateValue();
                                    Integer exit_reason = value.unboxInt();
                                    gc.setForeground(fColorWhite);
                                    int drawn = Utils.drawText(gc, exit_reason.toString(), x + 1, bounds.y - 2, width, true, true);
                                    if (drawn > 0) {
                                        fLastThreadId = currentThreadId;
                                    }
                                }
                                break;
                            }
                            else if (status == StateValues.CPU_STATUS_RUN_SYSCALL) {
                                attribute = Attributes.SYSTEM_CALL;
                                /*
                                 * Remove the "sys_" or "syscall_entry_" or similar from what we
                                 * draw in the rectangle. This depends on the trace's event layout.
                                 */
                                ITmfTrace trace = entry.getTrace();
                                if (trace instanceof IKernelTrace) {
                                    IKernelAnalysisEventLayout layout = ((IKernelTrace) trace).getKernelEventLayout();
                                    beginIndex = layout.eventSyscallEntryPrefix().length();
                                }
                            }
                            if (attribute != null) {
                                int quark = ss.getQuarkAbsolute(Attributes.THREADS, Integer.toString(currentThreadId), attribute);
                                ITmfStateInterval interval = ss.querySingleState(time, quark);
                                if (!interval.getStateValue().isNull()) {
                                    value = interval.getStateValue();
                                    gc.setForeground(fColorWhite);
                                    int drawn = Utils.drawText(gc, value.unboxStr().substring(beginIndex), x + 1, bounds.y - 2, width, true, true);
                                    if (drawn > 0) {
                                        fLastThreadId = currentThreadId;
                                    }
                                }
                            }
                            if (xForEndTime < bounds.x + bounds.width) {
                                gc.setForeground(fColorGray);
                                gc.drawLine(xForEndTime, bounds.y + 1, xForEndTime, bounds.y + bounds.height - 2);
                            }
                        }
                    }
                }
                // make sure next time is at least at the next pixel
                time = Math.max(tidInterval.getEndTime() + 1, drawingHelper.getTimeAtX(x + 1));
            }
        } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
            Activator.getDefault().logError("Error in ResourcesPresentationProvider", e); //$NON-NLS-1$
        } catch (StateSystemDisposedException e) {
            /* Ignored */
        }
    }

    @Override
    public void postDrawEntry(ITimeGraphEntry entry, Rectangle bounds, GC gc) {
        fLastThreadId = -1;
    }
}
