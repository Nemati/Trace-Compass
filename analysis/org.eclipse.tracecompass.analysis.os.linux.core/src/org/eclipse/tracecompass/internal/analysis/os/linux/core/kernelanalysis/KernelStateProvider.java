/*******************************************************************************
 * Copyright (c) 2012, 2015 Ericsson
 * Copyright (c) 2010, 2011 École Polytechnique de Montréal
 * Copyright (c) 2010, 2011 Alexandre Montplaisir <alexandre.montplaisir@gmail.com>
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/

package org.eclipse.tracecompass.internal.analysis.os.linux.core.kernelanalysis;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.Attributes;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.StateValues;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernelanalysis.LinuxValues;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

import com.google.common.collect.ImmutableMap;

/**
 * This is the state change input plugin for TMF's state system which handles
 * the LTTng 2.0 kernel traces in CTF format.
 *
 * It uses the reference handler defined in CTFKernelHandler.java.
 *
 * @author alexmont
 *
 */
public class KernelStateProvider extends AbstractTmfStateProvider {

    // ------------------------------------------------------------------------
    // Static fields
    // ------------------------------------------------------------------------

    /**
     * Version number of this state provider. Please bump this if you modify the
     * contents of the generated state history in some way.
     */
    private static final int VERSION = 8;

    private static final int IRQ_HANDLER_ENTRY_INDEX = 1;
    private static final int IRQ_HANDLER_EXIT_INDEX = 2;
    private static final int SOFT_IRQ_ENTRY_INDEX = 3;
    private static final int SOFT_IRQ_EXIT_INDEX = 4;
    private static final int SOFT_IRQ_RAISE_INDEX = 5;
    private static final int SCHED_SWITCH_INDEX = 6;
    private static final int SCHED_PROCESS_FORK_INDEX = 7;
    private static final int SCHED_PROCESS_EXIT_INDEX = 8;
    private static final int SCHED_PROCESS_FREE_INDEX = 9;
    private static final int STATEDUMP_PROCESS_STATE_INDEX = 10;
    private static final int SCHED_WAKEUP_INDEX = 11;
    private static final int SCHED_PI_SETPRIO_INDEX = 12;
    private static final int SUBMIT_IO = 13;
    private static final int COMPLETE_IO = 14;
    private static final int INFO_IO = 15;
    private static final int NET_IF = 16;
    private static final int NET_DEV = 17;
    private static final int KVM_ENTRY = 18;
    private static final int KVM_EXIT = 19;
    private static final int STATEDUMP_FILE_DESCRIPTOR = 20;
    private static final int VCPU_ENTER_GUEST = 21;
    private static Map<Integer,Integer> netIf = new HashMap<>() ;
    private static Map<Integer,Integer> netDev = new HashMap<>() ;
    private static Map<Integer,Long> netTs = new HashMap<>() ;
    public static Map<String,Map<Integer,stackData>> stackRange = new HashMap<>();
    public static Map<Integer, String> TIDtoName = new HashMap<>();
    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    private final Map<String, Integer> fEventNames;
    private final IKernelAnalysisEventLayout fLayout;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Instantiate a new state provider plugin.
     *
     * @param trace
     *            The LTTng 2.0 kernel trace directory
     * @param layout
     *            The event layout to use for this state provider. Usually
     *            depending on the tracer implementation.
     */
    public KernelStateProvider(ITmfTrace trace, IKernelAnalysisEventLayout layout) {
        super(trace, "Kernel"); //$NON-NLS-1$
        fLayout = layout;
        fEventNames = buildEventNames(layout);
    }
    class stackData {
        public Long start = (long) 0;
        public Long end = (long)0;
        public int tid = 0;
        public String name = "null";
    }

    // ------------------------------------------------------------------------
    // Event names management
    // ------------------------------------------------------------------------

    private static Map<String, Integer> buildEventNames(IKernelAnalysisEventLayout layout) {
        ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();

        builder.put(layout.eventIrqHandlerEntry(), IRQ_HANDLER_ENTRY_INDEX);
        builder.put(layout.eventIrqHandlerExit(), IRQ_HANDLER_EXIT_INDEX);
        builder.put(layout.eventSoftIrqEntry(), SOFT_IRQ_ENTRY_INDEX);
        builder.put(layout.eventSoftIrqExit(), SOFT_IRQ_EXIT_INDEX);
        builder.put(layout.eventSoftIrqRaise(), SOFT_IRQ_RAISE_INDEX);
        builder.put(layout.eventSchedSwitch(), SCHED_SWITCH_INDEX);
        builder.put(layout.eventSchedPiSetprio(), SCHED_PI_SETPRIO_INDEX);
        builder.put(layout.eventSchedProcessFork(), SCHED_PROCESS_FORK_INDEX);
        builder.put(layout.eventSchedProcessExit(), SCHED_PROCESS_EXIT_INDEX);
        builder.put(layout.eventSchedProcessFree(), SCHED_PROCESS_FREE_INDEX);
        builder.put(layout.eventSubmitIO(), SUBMIT_IO);
        builder.put(layout.eventCompleteIO(), COMPLETE_IO);
        builder.put(layout.eventInfoIO(), INFO_IO);
        builder.put(layout.eventNetIf(), NET_IF);
        builder.put(layout.eventNetDev(), NET_DEV);
        builder.put(layout.eventKVMEntry(), KVM_ENTRY);
        builder.put(layout.eventKVMExit(), KVM_EXIT);
        builder.put(layout.eventVCPUEnterGuest(), VCPU_ENTER_GUEST);
        final String eventStatedumpProcessState = layout.eventStatedumpProcessState();
        if (eventStatedumpProcessState != null) {
            builder.put(eventStatedumpProcessState, STATEDUMP_PROCESS_STATE_INDEX);
        }

        final String eventStatedumpFileDescriptor = layout.eventStatedumpFileDescriptor();
        if (eventStatedumpFileDescriptor != null) {
            builder.put(eventStatedumpFileDescriptor, STATEDUMP_FILE_DESCRIPTOR);
        }
        for (String eventSchedWakeup : layout.eventsSchedWakeup()) {
            builder.put(eventSchedWakeup, SCHED_WAKEUP_INDEX);
        }

        return checkNotNull(builder.build());
    }

    // ------------------------------------------------------------------------
    // IStateChangeInput
    // ------------------------------------------------------------------------

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public void assignTargetStateSystem(ITmfStateSystemBuilder ssb) {
        /* We can only set up the locations once the state system is assigned */
        super.assignTargetStateSystem(ssb);
    }
    private static int getNodeIO(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.IO);
    }
    private static int getNodeCPUQemu(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.CPUQemu);
    }
    private static int getNodeNetQemu(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.NetQemu);
    }

    @Override
    public KernelStateProvider getNewInstance() {
        return new KernelStateProvider(this.getTrace(), fLayout);
    }

    @Override
    protected void eventHandle(@Nullable ITmfEvent event) {
        if (event == null) {
            return;
        }

        Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpuObj == null) {
            /* We couldn't find any CPU information, ignore this event */
            return;
        }
        Integer cpu = (Integer) cpuObj;

        final String eventName = event.getName();

        final long ts = event.getTimestamp().getValue();

        try {
            final ITmfStateSystemBuilder ss = checkNotNull(getStateSystemBuilder());

            /* Shortcut for the "current CPU" attribute node */
            final int currentCPUNode = ss.getQuarkRelativeAndAdd(getNodeCPUs(ss), cpu.toString());

            /*
             * Shortcut for the "current thread" attribute node. It requires
             * querying the current CPU's current thread.
             */
            int quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CURRENT_THREAD);
            ITmfStateValue value = ss.queryOngoingState(quark);
            int thread = value.isNull() ? -1 : value.unboxInt();
            final int currentThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(thread));

            /*
             * Feed event to the history system if it's known to cause a state
             * transition.
             */
            Integer idx = fEventNames.get(eventName);
            int intval = (idx == null ? -1 : idx.intValue());

            switch (intval) {
            case VCPU_ENTER_GUEST:{
                try {
                ITmfEventField content = event.getContent();
                Long  sptmp =  Long.valueOf((content.getField("sptmp").getValue()).toString()); //$NON-NLS-1$
                int currentThreadCPU = 0;
                if (sptmp > 0){
                    currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(thread));
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, Attributes.PPID);
                    value = ss.queryOngoingState(quark);
                    Integer PTID = value.isNull() ? 0 : value.unboxInt();
                    int newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                    int threadPTID = thread;

                    while (PTID != 1) {
                        quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);
                        newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                        value = ss.queryOngoingState(quark);
                        threadPTID = PTID;
                        PTID = value.isNull() ? 0 : value.unboxInt();
                    }
                    String vmName = TIDtoName.get(threadPTID);
                    Map<Integer, stackData> stackTIDtmps = new HashMap<>();
                    stackTIDtmps = stackRange.get(vmName);


                    for (Entry<Integer, stackData> entry : stackTIDtmps.entrySet())
                    {
                        if (sptmp > entry.getValue().start  && sptmp < entry.getValue().end) {
                            currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                            int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                            String vcpu_id = content.getField("vcpuID").getValue().toString(); //$NON-NLS-1$
                            int vCPUID = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);
                            int statusQuark = ss.getQuarkRelativeAndAdd(vCPUID, "STATUS"); //$NON-NLS-1$
                            value = TmfStateValue.newValueInt(StateValues.PROCESS_STATUS_RUN_USERMODE);
                            ss.modifyAttribute(ts, value, statusQuark);
                            int threadQuark = ss.getQuarkRelativeAndAdd(vCPUID, "thread"); //$NON-NLS-1$
                            value = TmfStateValue.newValueInt(entry.getValue().tid);
                            ss.modifyAttribute(ts, value, threadQuark);
                            threadQuark = ss.getQuarkRelativeAndAdd(vCPUID, "threadName"); //$NON-NLS-1$
                            value = TmfStateValue.newValueString(entry.getValue().name);
                            ss.modifyAttribute(ts, value, threadQuark);

                        }
                    }
                } else {
                    currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(thread));
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, Attributes.PPID);
                    value = ss.queryOngoingState(quark);
                    Integer PTID = value.isNull() ? 0 : value.unboxInt();
                    int newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                    int threadPTID = thread;

                    while (PTID != 1) {
                        quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);
                        newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                        value = ss.queryOngoingState(quark);
                        threadPTID = PTID;
                        PTID = value.isNull() ? 0 : value.unboxInt();
                    }
                     currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                    int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                    String vcpu_id = content.getField("vcpuID").getValue().toString(); //$NON-NLS-1$
                    int vCPUID = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);
                    int statusQuark = ss.getQuarkRelativeAndAdd(vCPUID, "STATUS"); //$NON-NLS-1$
                    value = TmfStateValue.newValueInt(StateValues.PROCESS_STATUS_RUN_SYSCALL);
                    ss.modifyAttribute(ts, value, statusQuark);
                }
                break;
                } catch (TimeRangeException tre) {
                    /*
                     * This would happen if the events in the trace aren't ordered
                     * chronologically, which should never be the case ...
                     */
                    System.err.println("TimeRangeExcpetion caught in the state system's event manager."); //$NON-NLS-1$
                    System.err.println("Are the events in the trace correctly ordered?"); //$NON-NLS-1$
                    tre.printStackTrace();

                } catch (StateValueTypeException sve) {
                    /*
                     * This would happen if we were trying to push/pop attributes not of
                     * type integer. Which, once again, should never happen.
                     */
                    sve.printStackTrace();
                } catch (Exception e){

                }
                break;
            }
            case KVM_ENTRY:{
                ITmfEventField content = event.getContent();

                int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(thread));

                quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, Attributes.PPID);
                value = ss.queryOngoingState(quark);
                Integer PTID = value.isNull() ? 0 : value.unboxInt();
                int newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                int threadPTID = thread;

                while (PTID != 1) {
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);
                    newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                    value = ss.queryOngoingState(quark);
                    threadPTID = PTID;
                    PTID = value.isNull() ? 0 : value.unboxInt();

                }
                currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                String vcpu_id = content.getField("vcpu_id").getValue().toString(); //$NON-NLS-1$
                int vCPUID = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);
                value = TmfStateValue.newValueInt(1);
                ss.modifyAttribute(ts, value, vCPUID);
                /* save the thread is simulating which vcpu */
                quark = ss.getQuarkRelativeAndAdd(currentThreadNode, "vcpu"); //$NON-NLS-1$
                value = TmfStateValue.newValueString(vcpu_id);
                ss.modifyAttribute(ts, value, quark);

                value = StateValues.CPU_STATUS_VM_RUNNING_VALUE;
                quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                ss.modifyAttribute(ts, value, quark);


                }
            break;
            case KVM_EXIT:{
                ITmfEventField content = event.getContent();
                final int reason = Integer.parseInt(content.getField("exit_reason").getValue().toString()); //$NON-NLS-1$
                int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(thread));

                quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, Attributes.PPID);
                value = ss.queryOngoingState(quark);
                Integer PTID = value.isNull() ? 0 : value.unboxInt();
                int newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                int threadPTID = thread;

                while (PTID != 1) {
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);
                    newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                    value = ss.queryOngoingState(quark);
                    threadPTID = PTID;
                    PTID = value.isNull() ? 0 : value.unboxInt();

                }
                currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$

                quark = ss.getQuarkRelativeAndAdd(currentThreadNode, "vcpu"); //$NON-NLS-1$
                int quarkExit = ss.getQuarkRelativeAndAdd(currentThreadNode, "exit_reason"); //$NON-NLS-1$
                value = ss.queryOngoingState(quark);
                String vcpu_id = value.toString();
                int vcpuIDquark = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);
                int statusQuark = ss.getQuarkRelativeAndAdd(vcpuIDquark, "STATUS"); //$NON-NLS-1$
                value = TmfStateValue.newValueInt(0);
                ss.modifyAttribute(ts, value, statusQuark);
                if (reason == 12){
                    //int vcpuIDquark = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);
                    int threadQuark = ss.getQuarkRelativeAndAdd(vcpuIDquark, "thread"); //$NON-NLS-1$
                    value = TmfStateValue.newValueInt(0);
                    ss.modifyAttribute(ts, value, threadQuark);
                }
                value = TmfStateValue.newValueInt(reason);
                ss.modifyAttribute(ts, value, quarkExit);
                int vCPUID = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);
                value = TmfStateValue.newValueInt(0);
                ss.modifyAttribute(ts, value, vCPUID);
                value = StateValues.CPU_STATUS_VMX_RUNNING_VALUE;
                quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                ss.modifyAttribute(ts, value, quark);
                }
            break;
            case NET_DEV:{
                ITmfEventField content = event.getContent();
                final int len = Integer.parseInt(content.getField("len").getValue().toString()); //$NON-NLS-1$
                final int currentExecNameQuark = ss.getQuarkRelativeAndAdd(currentThreadNode,"Exec_name"); //$NON-NLS-1$
                value = ss.queryOngoingState(currentExecNameQuark);
                String exec_name = value.isNull() ? "0" : value.toString(); //$NON-NLS-1$
                if (exec_name.contains("vhost")) { //$NON-NLS-1$
                    netDev.put(thread, len) ;

                }
            }
            break;
            case NET_IF:{
                ITmfEventField content = event.getContent();
                final int len = Integer.parseInt(content.getField("len").getValue().toString()); //$NON-NLS-1$
                final int currentExecNameQuark = ss.getQuarkRelativeAndAdd(currentThreadNode,"Exec_name"); //$NON-NLS-1$
                value = ss.queryOngoingState(currentExecNameQuark);
                String exec_name = value.isNull() ? "0" : value.toString(); //$NON-NLS-1$
                if (exec_name.contains("vhost")) { //$NON-NLS-1$
                    int tmp = netIf.get(thread);
                    if (tmp == 0){
                        netIf.put(thread, len) ;
                    } else {netIf.put(thread, len+tmp) ;}
                }

            }
            break;
            case INFO_IO:{
                if (thread == 0){
                    return ;
                }
                final int currentExecNameQuark = ss.getQuarkRelativeAndAdd(currentThreadNode,"Exec_name"); //$NON-NLS-1$
                value = ss.queryOngoingState(currentExecNameQuark);
                String exec_name = value.isNull() ? "0" : value.toString(); //$NON-NLS-1$
                if (!exec_name.contains("qemu")) { //$NON-NLS-1$
                    return;
                }


                ITmfEventField content = event.getContent();
                final String reqNumber = content.getField("acb").getValue().toString(); //$NON-NLS-1$
                final int isWrite = Integer.parseInt(content.getField("is_write").getValue().toString()); //$NON-NLS-1$
                final int numberOfSector = Integer.parseInt(content.getField("nb_sectors").getValue().toString()); //$NON-NLS-1$
                final int currentThreadIO = ss.getQuarkRelativeAndAdd(getNodeIO(ss), String.valueOf(thread));


                int req = ss.getQuarkRelativeAndAdd(currentThreadIO, "req"); //$NON-NLS-1$
                int reqQuark = ss.getQuarkRelativeAndAdd(req, reqNumber);


                quark =  ss.getQuarkRelativeAndAdd(reqQuark, "isWrite"); //$NON-NLS-1$
                value = TmfStateValue.newValueInt(isWrite);
                ss.updateOngoingState(value, quark);
                quark =  ss.getQuarkRelativeAndAdd(reqQuark, "NSector"); //$NON-NLS-1$
                value = TmfStateValue.newValueInt(numberOfSector);
                ss.updateOngoingState(value, quark);


                if ( isWrite == 0) {
                    int rQuark = ss.getQuarkRelativeAndAdd(currentThreadIO, "read"); //$NON-NLS-1$
                    ITmfStateValue valueR = ss.queryOngoingState(rQuark);
                    int valueRead = valueR.isNull() ? 0 : valueR.unboxInt();
                    valueRead += value.unboxInt();
                    valueR = TmfStateValue.newValueInt(valueRead);
                    ss.updateOngoingState(valueR, rQuark);
                    quark = ss.getQuarkRelativeAndAdd(currentThreadIO, "STATUS" );//$NON-NLS-1$
                    value = StateValues.IO_READ_QEMU_VALUE;
                    ss.updateOngoingState( value, quark);
                } else if (isWrite == 1){

                    int wQuark = ss.getQuarkRelativeAndAdd(currentThreadIO, "write"); //$NON-NLS-1$
                    ITmfStateValue valueW = ss.queryOngoingState(wQuark);
                    int valueWrite = valueW.isNull() ? 0 : valueW.unboxInt();
                    valueWrite += value.unboxInt();

                    valueW = TmfStateValue.newValueInt(valueWrite);
                    ss.updateOngoingState( valueW, wQuark);
                    quark = ss.getQuarkRelativeAndAdd(currentThreadIO, "STATUS" );//$NON-NLS-1$
                    value = StateValues.IO_WRITE_QEMU_VALUE;
                    ss.updateOngoingState( value, quark);
                } else {
                    quark = ss.getQuarkRelativeAndAdd(currentThreadIO, "STATUS" );//$NON-NLS-1$
                    value = StateValues.IO_OTHER_VALUE;
                    ss.updateOngoingState( value, quark);
                }
            }

            break;

            case SUBMIT_IO:{
                if (thread == 0){
                    return ;
                }
                final int currentExecNameQuark = ss.getQuarkRelativeAndAdd(currentThreadNode,"Exec_name"); //$NON-NLS-1$
                value = ss.queryOngoingState(currentExecNameQuark);
                String exec_name = value.isNull() ? "0" : value.toString(); //$NON-NLS-1$
                if (!exec_name.contains("qemu")) { //$NON-NLS-1$
                    return;
                }
                int currentThreadIO = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(thread));

                quark = ss.getQuarkRelativeAndAdd(currentThreadIO, Attributes.PPID);
                value = ss.queryOngoingState(quark);
                Integer PTID = value.isNull() ? 0 : value.unboxInt();
                int newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                int threadPTID = thread;

                while (PTID != 1) {
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);
                    newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                    value = ss.queryOngoingState(quark);
                    threadPTID = PTID;
                    PTID = value.isNull() ? 0 : value.unboxInt();

                }
                currentThreadIO = ss.getQuarkRelativeAndAdd(getNodeIO(ss), String.valueOf(threadPTID));





                ITmfEventField content = event.getContent();
                String reqNumber = content.getField("req").getValue().toString(); //$NON-NLS-1$





                int req = ss.getQuarkRelativeAndAdd(currentThreadIO, "req"); //$NON-NLS-1$
                int reqQuark = ss.getQuarkRelativeAndAdd(req, reqNumber);
                quark =  ss.getQuarkRelativeAndAdd(reqQuark, "Status"); //$NON-NLS-1$
                value = TmfStateValue.newValueInt(1);
                ss.modifyAttribute(ts, value, quark);
                quark =  ss.getQuarkRelativeAndAdd(reqQuark, "isWrite"); //$NON-NLS-1$
                value = TmfStateValue.newValueInt(2);
                ss.modifyAttribute(ts, value, quark);
                quark =  ss.getQuarkRelativeAndAdd(reqQuark, "NSector"); //$NON-NLS-1$
                value = TmfStateValue.newValueInt(0);
                ss.modifyAttribute(ts, value, quark);

                quark = ss.getQuarkRelativeAndAdd(currentThreadIO, "ValueIO"); //$NON-NLS-1$
                value = ss.queryOngoingState(quark);
                int valueIO = value.isNull() ? 0 : value.unboxInt();
                valueIO++;
                value = TmfStateValue.newValueInt(valueIO);
                ss.modifyAttribute(ts, value, quark);


                int wQuark = ss.getQuarkRelativeAndAdd(currentThreadIO, "write"); //$NON-NLS-1$
                value = ss.queryOngoingState(wQuark);
                int valueWrite = value.isNull() ? 0 : value.unboxInt();
                value = TmfStateValue.newValueInt(0);
                ss.modifyAttribute(ts, value, wQuark);
                value = TmfStateValue.newValueInt(valueWrite);
                ss.modifyAttribute(ts, value, wQuark);




                int rQuark = ss.getQuarkRelativeAndAdd(currentThreadIO, "read"); //$NON-NLS-1$
                value = ss.queryOngoingState(rQuark);
                int valueRead = value.isNull() ? 0 : value.unboxInt();
                value = TmfStateValue.newValueInt(0);
                ss.modifyAttribute(ts, value, rQuark);
                value = TmfStateValue.newValueInt(valueRead);
                ss.modifyAttribute(ts, value, rQuark);

                if (valueIO > 0) {
                    quark = ss.getQuarkRelativeAndAdd(currentThreadIO, "STATUS" ); //$NON-NLS-1$
                    value = StateValues.IO_OTHER_VALUE;
                    ss.modifyAttribute(ts, value, quark);
                }
            }
            break;
            case COMPLETE_IO:{
                if (thread == 0){
                    return ;
                }

                final int currentExecNameQuark = ss.getQuarkRelativeAndAdd(currentThreadNode,"Exec_name"); //$NON-NLS-1$
                value = ss.queryOngoingState(currentExecNameQuark);
                String exec_name = value.isNull() ? "0" : value.toString(); //$NON-NLS-1$
                if (!exec_name.contains("qemu")) { //$NON-NLS-1$
                    return;
                }
                int currentThreadIO = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(thread));



                quark = ss.getQuarkRelativeAndAdd(currentThreadIO, Attributes.PPID);
                value = ss.queryOngoingState(quark);
                Integer PTID = value.isNull() ? 0 : value.unboxInt();
                int newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                int threadPTID = thread;
                if (thread == 0){
                    return ;
                }
                while (PTID != 1) {
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);
                    newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                    value = ss.queryOngoingState(quark);
                    threadPTID = PTID;
                    PTID = value.isNull() ? 0 : value.unboxInt();

                }
                currentThreadIO = ss.getQuarkRelativeAndAdd(getNodeIO(ss), String.valueOf(threadPTID));




                ITmfEventField content = event.getContent();
                String reqNumber = content.getField("req").getValue().toString(); //$NON-NLS-1$
                int req = ss.getQuarkRelativeAndAdd(currentThreadIO, "req"); //$NON-NLS-1$

                int reqQuark = ss.getQuarkRelativeAndAdd(req, reqNumber);
                quark =  ss.getQuarkRelativeAndAdd(reqQuark, "Status"); //$NON-NLS-1$
                value = TmfStateValue.newValueInt(0);
                ss.modifyAttribute(ts, value, quark);
                quark =  ss.getQuarkRelativeAndAdd(reqQuark, "isWrite"); //$NON-NLS-1$
                int isWrite =  ss.queryOngoingState(quark).unboxInt();
                value = TmfStateValue.newValueInt(0);
                ss.modifyAttribute(ts, value, quark);
                quark =  ss.getQuarkRelativeAndAdd(reqQuark, "NSector"); //$NON-NLS-1$
                int valueReqWrite = ss.queryOngoingState(quark).unboxInt();
                value = TmfStateValue.newValueInt(0);
                ss.modifyAttribute(ts, value, quark);
                if ( isWrite == 1) {
                    int wQuark = ss.getQuarkRelativeAndAdd(currentThreadIO, "write"); //$NON-NLS-1$
                    value = ss.queryOngoingState(wQuark);
                    int valueWrite = value.isNull() ? 0 : value.unboxInt();
                    valueWrite -= valueReqWrite;
                    value = TmfStateValue.newValueInt(valueWrite);
                    ss.modifyAttribute(ts, value, wQuark);

                } else if (isWrite == 0 ){

                    int rQuark = ss.getQuarkRelativeAndAdd(currentThreadIO, "read"); //$NON-NLS-1$
                    value = ss.queryOngoingState(rQuark);
                    int valueRead = value.isNull() ? 0 : value.unboxInt();
                    valueRead -= valueReqWrite;
                    value = TmfStateValue.newValueInt(valueRead);
                    ss.modifyAttribute(ts, value, rQuark);


                }
                quark = ss.getQuarkRelativeAndAdd(currentThreadIO, "ValueIO" ); //$NON-NLS-1$
                value = ss.queryOngoingState(quark);
                int valueIO = value.isNull() ? 0 : value.unboxInt();
                valueIO--;
                value = TmfStateValue.newValueInt(valueIO);
                ss.modifyAttribute(ts, value, quark);
                if (valueIO == 0){
                    quark = ss.getQuarkRelativeAndAdd(currentThreadIO, "STATUS" ); //$NON-NLS-1$
                    value = StateValues.IO_STATUS_IDLE_VALUE;
                    //TmfStateValue.newValueInt(0);
                    //
                    ss.modifyAttribute(ts, value, quark);
                }
            }

            break;
            case IRQ_HANDLER_ENTRY_INDEX:
            {
                Integer irqId = ((Long) event.getContent().getField(fLayout.fieldIrq()).getValue()).intValue();

                /* Mark this IRQ as active in the resource tree.
                 * The state value = the CPU on which this IRQ is sitting */
                quark = ss.getQuarkRelativeAndAdd(getNodeIRQs(ss), irqId.toString());
                value = TmfStateValue.newValueInt(cpu.intValue());
                ss.modifyAttribute(ts, value, quark);

                /* Change the status of the running process to interrupted */
                quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.STATUS);
                value = StateValues.PROCESS_STATUS_INTERRUPTED_VALUE;
                ss.modifyAttribute(ts, value, quark);

                /* Change the status of the CPU to interrupted */
                quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                value = StateValues.CPU_STATUS_IRQ_VALUE;
                ss.modifyAttribute(ts, value, quark);
            }
            break;

            case IRQ_HANDLER_EXIT_INDEX:
            {
                Integer irqId = ((Long) event.getContent().getField(fLayout.fieldIrq()).getValue()).intValue();

                /* Put this IRQ back to inactive in the resource tree */
                quark = ss.getQuarkRelativeAndAdd(getNodeIRQs(ss), irqId.toString());
                value = TmfStateValue.nullValue();
                ss.modifyAttribute(ts, value, quark);

                /* Set the previous process back to running */
                setProcessToRunning(ss, ts, currentThreadNode);

                /* Set the CPU status back to running or "idle" */
                cpuExitInterrupt(ss, ts, currentCPUNode, currentThreadNode);
            }
            break;

            case SOFT_IRQ_ENTRY_INDEX:
            {
                Integer softIrqId = ((Long) event.getContent().getField(fLayout.fieldVec()).getValue()).intValue();

                /* Mark this SoftIRQ as active in the resource tree.
                 * The state value = the CPU on which this SoftIRQ is processed */
                quark = ss.getQuarkRelativeAndAdd(getNodeSoftIRQs(ss), softIrqId.toString());
                value = TmfStateValue.newValueInt(cpu.intValue());
                ss.modifyAttribute(ts, value, quark);

                /* Change the status of the running process to interrupted */
                quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.STATUS);
                value = StateValues.PROCESS_STATUS_INTERRUPTED_VALUE;
                ss.modifyAttribute(ts, value, quark);

                /* Change the status of the CPU to interrupted */
                quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                value = StateValues.CPU_STATUS_SOFTIRQ_VALUE;
                ss.modifyAttribute(ts, value, quark);
            }
            break;

            case SOFT_IRQ_EXIT_INDEX:
            {
                Integer softIrqId = ((Long) event.getContent().getField(fLayout.fieldVec()).getValue()).intValue();

                /* Put this SoftIRQ back to inactive (= -1) in the resource tree */
                quark = ss.getQuarkRelativeAndAdd(getNodeSoftIRQs(ss), softIrqId.toString());
                value = TmfStateValue.nullValue();
                ss.modifyAttribute(ts, value, quark);

                /* Set the previous process back to running */
                setProcessToRunning(ss, ts, currentThreadNode);

                /* Set the CPU status back to "busy" or "idle" */
                cpuExitInterrupt(ss, ts, currentCPUNode, currentThreadNode);
            }
            break;

            case SOFT_IRQ_RAISE_INDEX:
                /* Fields: int32 vec */
            {
                Integer softIrqId = ((Long) event.getContent().getField(fLayout.fieldVec()).getValue()).intValue();

                /* Mark this SoftIRQ as *raised* in the resource tree.
                 * State value = -2 */
                quark = ss.getQuarkRelativeAndAdd(getNodeSoftIRQs(ss), softIrqId.toString());
                value = StateValues.SOFT_IRQ_RAISED_VALUE;
                ss.modifyAttribute(ts, value, quark);
            }
            break;

            case SCHED_SWITCH_INDEX:
            {
                ITmfEventField content = event.getContent();
                Integer prevTid = ((Long) content.getField(fLayout.fieldPrevTid()).getValue()).intValue();
                Long prevState = (Long) content.getField(fLayout.fieldPrevState()).getValue();
                String nextProcessName = (String) content.getField(fLayout.fieldNextComm()).getValue();
                String prevProcessName = (String) content.getField("prev_comm").getValue(); //$NON-NLS-1$
                Integer nextTid = ((Long) content.getField(fLayout.fieldNextTid()).getValue()).intValue();
                Integer nextPrio = ((Long) content.getField(fLayout.fieldNextPrio()).getValue()).intValue();

                Integer formerThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), prevTid.toString());
                Integer newCurrentThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), nextTid.toString());

                /*
                 * Empirical observations and look into the linux code have
                 * shown that the TASK_STATE_MAX flag is used internally and
                 * |'ed with other states, most often the running state, so it
                 * is ignored from the prevState value.
                 */
                prevState = prevState & ~(LinuxValues.TASK_STATE_MAX);

                /* Set the status of the process that got scheduled out. */
                quark = ss.getQuarkRelativeAndAdd(formerThreadNode, Attributes.STATUS);
                if (prevState != LinuxValues.TASK_STATE_RUNNING) {
                    if (prevState == LinuxValues.TASK_STATE_DEAD) {
                        value = TmfStateValue.nullValue();
                    } else {
                        value = StateValues.PROCESS_STATUS_WAIT_BLOCKED_VALUE;
                    }
                } else {
                    value = StateValues.PROCESS_STATUS_WAIT_FOR_CPU_VALUE;
                }
                ss.modifyAttribute(ts, value, quark);

                /* Set the status of the new scheduled process */
                setProcessToRunning(ss, ts, newCurrentThreadNode);

                /* Set the exec name of the new process */
                quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.EXEC_NAME);
                value = TmfStateValue.newValueString(nextProcessName);
                ss.modifyAttribute(ts, value, quark);

                /* Set the current prio for the new process */
                quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.PRIO);
                value = TmfStateValue.newValueInt(nextPrio);
                ss.modifyAttribute(ts, value, quark);

                /* Make sure the PPID and system_call sub-attributes exist */
                ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.SYSTEM_CALL);
                ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.PPID);

                /* Set the current scheduled process on the relevant CPU */
                quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.CURRENT_THREAD);
                value = TmfStateValue.newValueInt(nextTid);
                ss.modifyAttribute(ts, value, quark);

                if (prevProcessName.equals("qemu-system-x86")){ //$NON-NLS-1$

                    quark = ss.getQuarkRelativeAndAdd(formerThreadNode, Attributes.PPID);
                    value = ss.queryOngoingState(quark);
                    Integer PTID = value.isNull() ? 0 : value.unboxInt();
                    quark = ss.getQuarkRelativeAndAdd(formerThreadNode, "exit_reason"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);

                    if (value.unboxInt() != 12 && !value.isNull()) {
                        quark = ss.getQuarkRelativeAndAdd(formerThreadNode, Attributes.STATUS);
                        value = StateValues.PREEMPTED_VALUE;
                        ss.modifyAttribute(ts, value, quark);
                    }

                    int currentThreadCPU = 0;
                    int newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                    int threadPTID = prevTid;
                    while (PTID != 1) {
                        quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);
                        newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                        value = ss.queryOngoingState(quark);
                        threadPTID = PTID;
                        PTID = value.isNull() ? 0 : value.unboxInt();

                    }
                    currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));

                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "ValueCPU"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    int valueCPU = value.isNull() ? 0 : value.unboxInt();
                    valueCPU--;

                    if (valueCPU > 0) {
                        value = TmfStateValue.newValueInt(valueCPU);
                        ss.modifyAttribute(ts, value, quark);
                        quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "STATUS" ); //$NON-NLS-1$
                        value = StateValues.CPU_QEMU_BUSY_VALUE;
                        ss.modifyAttribute(ts, value, quark);
                    } else {
                        value = TmfStateValue.newValueInt(0);
                        ss.modifyAttribute(ts, value, quark);
                        quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "STATUS" ); //$NON-NLS-1$
                        value = StateValues.CPU_QEMU_IDLE_VALUE;
                        ss.modifyAttribute(ts, value, quark);
                    }


                } else if (prevProcessName.contains("vhost")) { //$NON-NLS-1$
                    final int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeNetQemu(ss), String.valueOf(prevTid));
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "STATUS" ); //$NON-NLS-1$
                    value = StateValues.NET_QEMU_IDLE_VALUE;
                    ss.modifyAttribute(ts, value, quark);
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "Netif" );//$NON-NLS-1$
                    value = TmfStateValue.newValueInt(netIf.get(prevTid));
                    ss.modifyAttribute(netTs.get(prevTid), value, quark);
                    value = TmfStateValue.newValueInt(0);
                    ss.modifyAttribute(ts, value, quark);
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "Netdev" );//$NON-NLS-1$
                    value = TmfStateValue.newValueInt(netDev.get(prevTid));
                    ss.modifyAttribute(netTs.get(prevTid), value, quark);
                    value = TmfStateValue.newValueInt(0);
                    ss.modifyAttribute(ts, value, quark);

                }

                if (nextProcessName.equals("qemu-system-x86"))    { //$NON-NLS-1$
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.PPID);
                    value = ss.queryOngoingState(quark);
                    Integer PTID = value.isNull() ? 0 : value.unboxInt();
                    int currentThreadCPU = 0;
                    int newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                    int threadPTID = nextTid;
                    while (PTID != 1) {
                        quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);
                        newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                        value = ss.queryOngoingState(quark);
                        threadPTID = PTID;
                        PTID = value.isNull() ? 0 : value.unboxInt();

                    }
                    currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));

                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "ValueCPU"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    int valueCPU = value.isNull() ? 0 : value.unboxInt();
                    valueCPU++;
                    value = TmfStateValue.newValueInt(valueCPU);
                    ss.modifyAttribute(ts, value, quark);
                    if (valueCPU > 0) {
                        quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "STATUS" ); //$NON-NLS-1$
                        value = StateValues.CPU_QEMU_BUSY_VALUE;
                        ss.modifyAttribute(ts, value, quark);
                    }

                    //final int currentThreadIO = ss.getQuarkRelativeAndAdd(getNodeIO(ss), String.valueOf(thread));
                } else if (nextProcessName.contains("vhost")){ //$NON-NLS-1$
                    final int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeNetQemu(ss), String.valueOf(nextTid));
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "STATUS" ); //$NON-NLS-1$
                    value = StateValues.NET_QEMU_BUSY_VALUE;
                    ss.modifyAttribute(ts, value, quark);
                    netTs.put(nextTid, ts)  ;
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "Netif" );//$NON-NLS-1$
                    netIf.put(nextTid, 0);
                    //value = TmfStateValue.newValueInt(0);
                    //ss.modifyAttribute(ts, value, quark);
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "Netdev" );//$NON-NLS-1$
                    //value = TmfStateValue.newValueInt(0);
                    //ss.modifyAttribute(ts, value, quark);
                    netDev.put(nextTid, 0) ;
                }


                /* Set the status of the CPU itself */
                if (nextTid > 0) {
                    /* Check if the entering process is in kernel or user mode */
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.SYSTEM_CALL);
                    if (ss.queryOngoingState(quark).isNull()) {
                        value = StateValues.CPU_STATUS_RUN_USERMODE_VALUE;
                    } else {
                        value = StateValues.CPU_STATUS_RUN_SYSCALL_VALUE;
                    }
                } else {
                    value = StateValues.CPU_STATUS_IDLE_VALUE;
                }
                quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                ss.modifyAttribute(ts, value, quark);
            }
            break;

            case SCHED_PI_SETPRIO_INDEX:
            {
                ITmfEventField content = event.getContent();
                Integer tid = ((Long) content.getField(fLayout.fieldTid()).getValue()).intValue();
                Integer prio = ((Long) content.getField(fLayout.fieldNewPrio()).getValue()).intValue();

                Integer updateThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), tid.toString());

                /* Set the current prio for the new process */
                quark = ss.getQuarkRelativeAndAdd(updateThreadNode, Attributes.PRIO);
                value = TmfStateValue.newValueInt(prio);
                ss.modifyAttribute(ts, value, quark);
            }
            break;

            case SCHED_PROCESS_FORK_INDEX:
            {
                ITmfEventField content = event.getContent();
                // String parentProcessName = (String) event.getFieldValue("parent_comm");
                String childProcessName = (String) content.getField(fLayout.fieldChildComm()).getValue();
                // assert ( parentProcessName.equals(childProcessName) );

                Integer parentTid = ((Long) content.getField(fLayout.fieldParentTid()).getValue()).intValue();
                Integer childTid = ((Long) content.getField(fLayout.fieldChildTid()).getValue()).intValue();

                Integer parentTidNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), parentTid.toString());
                Integer childTidNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), childTid.toString());

                /* Assign the PPID to the new process */
                quark = ss.getQuarkRelativeAndAdd(childTidNode, Attributes.PPID);
                value = TmfStateValue.newValueInt(parentTid);
                ss.modifyAttribute(ts, value, quark);

                /* Set the new process' exec_name */
                quark = ss.getQuarkRelativeAndAdd(childTidNode, Attributes.EXEC_NAME);
                value = TmfStateValue.newValueString(childProcessName);
                ss.modifyAttribute(ts, value, quark);

                /* Set the new process' status */
                quark = ss.getQuarkRelativeAndAdd(childTidNode, Attributes.STATUS);
                value = StateValues.PROCESS_STATUS_WAIT_FOR_CPU_VALUE;
                ss.modifyAttribute(ts, value, quark);
                /* Set the process' syscall name, to be the same as the parent's */
                quark = ss.getQuarkRelativeAndAdd(parentTidNode, Attributes.SYSTEM_CALL);
                value = ss.queryOngoingState(quark);
                if (value.isNull()) {
                    /*
                     * Maybe we were missing info about the parent? At least we
                     * will set the child right. Let's suppose "sys_clone".
                     */
                    value = TmfStateValue.newValueString(fLayout.eventSyscallEntryPrefix() + IKernelAnalysisEventLayout.INITIAL_SYSCALL_NAME);
                }
                quark = ss.getQuarkRelativeAndAdd(childTidNode, Attributes.SYSTEM_CALL);
                ss.modifyAttribute(ts, value, quark);
            }
            break;

            case SCHED_PROCESS_EXIT_INDEX:
                break;

            case SCHED_PROCESS_FREE_INDEX:
            {
                Integer tid = ((Long) event.getContent().getField(fLayout.fieldTid()).getValue()).intValue();
                /*
                 * Remove the process and all its sub-attributes from the
                 * current state
                 */
                quark = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), tid.toString());
                ss.removeAttribute(ts, quark);
            }
            break;

            case STATEDUMP_FILE_DESCRIPTOR:
                /* LTTng-specific */
            {
                ITmfEventField content = event.getContent();
                String name = (String) content.getField("filename").getValue(); //$NON-NLS-1$
                int pid = ((Long) content.getField("pid").getValue()).intValue(); //$NON-NLS-1$
                if (name.contains("img")){ //$NON-NLS-1$
                    int lenStart = "/var/lib/libvirt/images/".length(); //$NON-NLS-1$
                    int lenEnd = name.length() - 4;
                    String vmName = name.substring(lenStart, lenEnd);
                    TIDtoName.put(pid, vmName);
                    int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(pid));
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vmName" ); //$NON-NLS-1$
                    value = TmfStateValue.newValueString(vmName);
                     ss.modifyAttribute(ts, value, quark);
                    currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeIO(ss), String.valueOf(pid));
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vmName" ); //$NON-NLS-1$
                    value = TmfStateValue.newValueString(vmName);
                    ss.modifyAttribute(ts, value, quark);
                    int quarkVM = ss.getQuarkAbsoluteAndAdd("vmName"); //$NON-NLS-1$
                    quark = ss.getQuarkRelativeAndAdd(quarkVM ,vmName);
                    value = TmfStateValue.newValueInt(pid);
                    ss.modifyAttribute(ts, value, quark);

                }
                break;
            }
            case STATEDUMP_PROCESS_STATE_INDEX:
                /* LTTng-specific */
            {
                ITmfEventField content = event.getContent();
                int tid = ((Long) content.getField("tid").getValue()).intValue(); //$NON-NLS-1$
                int pid = ((Long) content.getField("pid").getValue()).intValue(); //$NON-NLS-1$
                int ppid = ((Long) content.getField("ppid").getValue()).intValue(); //$NON-NLS-1$
                int status = ((Long) content.getField("status").getValue()).intValue(); //$NON-NLS-1$
                String name = (String) content.getField("name").getValue(); //$NON-NLS-1$

                try {
                String hostname = (String) content.getField("hostname").getValue(); //$NON-NLS-1$
                Long stack_start = (Long) content.getField("stack_start").getValue(); //$NON-NLS-1$
                Long stack_end = (Long) content.getField("stack_end").getValue(); //$NON-NLS-1$

                stackData stacktmp = new stackData();
                stacktmp.start = stack_start;
                stacktmp.end = stack_end ;
                stacktmp.tid = tid;
                stacktmp.name = name;
                Map<Integer,stackData> stacktid = new HashMap<>();
                stacktid.put(tid, stacktmp);
                if (!stackRange.isEmpty()){
                  stacktid.putAll(stackRange.get(hostname));
                }
                stackRange.put(hostname, stacktid);
                int quarkVMname = ss.getQuarkAbsoluteAndAdd("vmName"); //$NON-NLS-1$
                int quarkHostname = ss.getQuarkRelativeAndAdd(quarkVMname, hostname);
                int quarkthread = ss.getQuarkRelativeAndAdd(quarkHostname, String.valueOf(tid));
                int quark_stack_start= ss.getQuarkRelativeAndAdd(quarkthread, "stack_start"); //$NON-NLS-1$
                int quark_stack_end= ss.getQuarkRelativeAndAdd(quarkthread, "stack_end"); //$NON-NLS-1$
                int quarkThreadName = ss.getQuarkRelativeAndAdd(quarkthread , Attributes.EXEC_NAME);
                int quarkPPID = ss.getQuarkRelativeAndAdd(quarkthread, Attributes.PPID);



                if (ss.queryOngoingState(quark_stack_start).isNull()) {
                    /* If the value didn't exist previously, set it */
                    value = TmfStateValue.newValueLong(stack_start);
                    ss.modifyAttribute(ts, value, quark_stack_start);
                }
                if (ss.queryOngoingState(quark_stack_end).isNull()) {
                    /* If the value didn't exist previously, set it */
                    value = TmfStateValue.newValueLong(stack_end);
                    ss.modifyAttribute(ts, value, quark_stack_end);
                }

                if (ss.queryOngoingState(quarkThreadName).isNull()) {
                    /* If the value didn't exist previously, set it */
                    value = TmfStateValue.newValueString(name);
                    ss.modifyAttribute(ts, value, quarkThreadName);
                }
                if (ss.queryOngoingState(quarkPPID).isNull()) {
                    /* If the value didn't exist previously, set it */
                    value = TmfStateValue.newValueLong(ppid);
                    ss.modifyAttribute(ts, value, quarkPPID);
                }


                break;
                } catch (TimeRangeException tre) {
                    /*
                     * This would happen if the events in the trace aren't ordered
                     * chronologically, which should never be the case ...
                     */
                    System.err.println("TimeRangeExcpetion caught in the state system's event manager."); //$NON-NLS-1$
                    System.err.println("Are the events in the trace correctly ordered?"); //$NON-NLS-1$
                    tre.printStackTrace();

                } catch (StateValueTypeException sve) {
                    /*
                     * This would happen if we were trying to push/pop attributes not of
                     * type integer. Which, once again, should never happen.
                     */
                    sve.printStackTrace();
                } catch (Exception e){

                }


                /*
                 * "mode" could be interesting too, but it doesn't seem to be
                 * populated with anything relevant for now.
                 */

                int curThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(tid));

                /* Set the process' name */
                quark = ss.getQuarkRelativeAndAdd(curThreadNode, Attributes.EXEC_NAME);
                if (ss.queryOngoingState(quark).isNull()) {
                    /* If the value didn't exist previously, set it */
                    value = TmfStateValue.newValueString(name);
                    ss.modifyAttribute(ts, value, quark);
                }

                /* Set the process' PPID */
                quark = ss.getQuarkRelativeAndAdd(curThreadNode, Attributes.PPID);
                if (ss.queryOngoingState(quark).isNull()) {
                    if (pid == tid) {
                        /* We have a process. Use the 'PPID' field. */
                        value = TmfStateValue.newValueInt(ppid);
                    } else {
                        /* We have a thread, use the 'PID' field for the parent. */
                        value = TmfStateValue.newValueInt(pid);
                    }
                    ss.modifyAttribute(ts, value, quark);
                }

                /* Set the process' status */
                quark = ss.getQuarkRelativeAndAdd(curThreadNode, Attributes.STATUS);
                if (ss.queryOngoingState(quark).isNull()) {
                    switch (status) {
                    case LinuxValues.STATEDUMP_PROCESS_STATUS_WAIT_CPU:
                        value = StateValues.PROCESS_STATUS_WAIT_FOR_CPU_VALUE;
                        break;
                    case LinuxValues.STATEDUMP_PROCESS_STATUS_WAIT:
                        /*
                         * We have no information on what the process is waiting
                         * on (unlike a sched_switch for example), so we will
                         * use the WAIT_UNKNOWN state instead of the "normal"
                         * WAIT_BLOCKED state.
                         */
                        value = StateValues.PROCESS_STATUS_WAIT_UNKNOWN_VALUE;
                        break;
                    default:
                        value = StateValues.PROCESS_STATUS_UNKNOWN_VALUE;
                    }
                    ss.modifyAttribute(ts, value, quark);
                }
            }
            break;

            case SCHED_WAKEUP_INDEX:
            {
                final int tid = ((Long) event.getContent().getField(fLayout.fieldTid()).getValue()).intValue();
                final int prio = ((Long) event.getContent().getField(fLayout.fieldPrio()).getValue()).intValue();
                final int threadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(tid));

                /*
                 * The process indicated in the event's payload is now ready to
                 * run. Assign it to the "wait for cpu" state, but only if it
                 * was not already running.
                 */
                quark = ss.getQuarkRelativeAndAdd(threadNode, Attributes.STATUS);
                int status = ss.queryOngoingState(quark).unboxInt();

                if (status != StateValues.PROCESS_STATUS_RUN_SYSCALL &&
                        status != StateValues.PROCESS_STATUS_RUN_USERMODE) {
                    value = StateValues.PROCESS_STATUS_WAIT_FOR_CPU_VALUE;
                    ss.modifyAttribute(ts, value, quark);
                }

                /*
                 * When a user changes a threads prio (e.g. with pthread_setschedparam),
                 * it shows in ftrace with a sched_wakeup.
                 */
                quark = ss.getQuarkRelativeAndAdd(threadNode, Attributes.PRIO);
                value = TmfStateValue.newValueInt(prio);
                ss.modifyAttribute(ts, value, quark);
            }
            break;

            default:
                /* Other event types not covered by the main switch */
            {

                if (eventName.startsWith(fLayout.eventSyscallEntryPrefix())
                        || eventName.startsWith(fLayout.eventCompatSyscallEntryPrefix())) {
                    /* Assign the new system call to the process */
                    quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.SYSTEM_CALL);
                    String nameOfSyscall = (String) eventName.subSequence(14, eventName.length());
                    value = TmfStateValue.newValueString(nameOfSyscall);

                    ss.modifyAttribute(ts, value, quark);

                    /* Put the process in system call mode */
                    quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.STATUS);
                    value = StateValues.PROCESS_STATUS_RUN_SYSCALL_VALUE;
                    ss.modifyAttribute(ts, value, quark);

                    /* Put the CPU in system call (kernel) mode */
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                    value = StateValues.CPU_STATUS_RUN_SYSCALL_VALUE;
                    ss.modifyAttribute(ts, value, quark);

                } else if (eventName.startsWith(fLayout.eventSyscallExitPrefix())) {

                    /* Clear the current system call on the process */
                    quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.SYSTEM_CALL);
                    value = TmfStateValue.nullValue();
                    ss.modifyAttribute(ts, value, quark);

                    /* Put the process' status back to user mode */
                    quark = ss.getQuarkRelativeAndAdd(currentThreadNode, Attributes.STATUS);
                    value = StateValues.PROCESS_STATUS_RUN_USERMODE_VALUE;
                    ss.modifyAttribute(ts, value, quark);

                    /* Put the CPU's status back to user mode */
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                    value = StateValues.CPU_STATUS_RUN_USERMODE_VALUE;
                    ss.modifyAttribute(ts, value, quark);
                }

            }
            break;
            } // End of big switch

        } catch (AttributeNotFoundException ae) {
            /*
             * This would indicate a problem with the logic of the manager here,
             * so it shouldn't happen.
             */
            ae.printStackTrace();

        } catch (TimeRangeException tre) {
            /*
             * This would happen if the events in the trace aren't ordered
             * chronologically, which should never be the case ...
             */
            System.err.println("TimeRangeExcpetion caught in the state system's event manager."); //$NON-NLS-1$
            System.err.println("Are the events in the trace correctly ordered?"); //$NON-NLS-1$
            tre.printStackTrace();

        } catch (StateValueTypeException sve) {
            /*
             * This would happen if we were trying to push/pop attributes not of
             * type integer. Which, once again, should never happen.
             */
            sve.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    // Convenience methods for commonly-used attribute tree locations
    // ------------------------------------------------------------------------

    private static int getNodeCPUs(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.CPUS);
    }

    private static int getNodeThreads(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.THREADS);
    }

    private static int getNodeIRQs(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.RESOURCES, Attributes.IRQS);
    }

    private static int getNodeSoftIRQs(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.RESOURCES, Attributes.SOFT_IRQS);
    }

    // ------------------------------------------------------------------------
    // Advanced state-setting methods
    // ------------------------------------------------------------------------

    /**
     * When we want to set a process back to a "running" state, first check
     * its current System_call attribute. If there is a system call active, we
     * put the process back in the syscall state. If not, we put it back in
     * user mode state.
     */
    private static void setProcessToRunning(ITmfStateSystemBuilder ssb, long ts, int currentThreadNode)
            throws AttributeNotFoundException, TimeRangeException,
            StateValueTypeException {
        int quark;
        ITmfStateValue value;

        quark = ssb.getQuarkRelativeAndAdd(currentThreadNode, Attributes.SYSTEM_CALL);
        if (ssb.queryOngoingState(quark).isNull()) {
            /* We were in user mode before the interruption */
            value = StateValues.PROCESS_STATUS_RUN_USERMODE_VALUE;
        } else {
            /* We were previously in kernel mode */
            value = StateValues.PROCESS_STATUS_RUN_SYSCALL_VALUE;
        }
        quark = ssb.getQuarkRelativeAndAdd(currentThreadNode, Attributes.STATUS);
        ssb.modifyAttribute(ts, value, quark);
    }

    /**
     * Similar logic as above, but to set the CPU's status when it's coming out
     * of an interruption.
     */
    private static void cpuExitInterrupt(ITmfStateSystemBuilder ssb, long ts,
            int currentCpuNode, int currentThreadNode)
                    throws StateValueTypeException, AttributeNotFoundException,
                    TimeRangeException {
        int quark;
        ITmfStateValue value;

        quark = ssb.getQuarkRelativeAndAdd(currentCpuNode, Attributes.CURRENT_THREAD);
        if (ssb.queryOngoingState(quark).unboxInt() > 0) {
            /* There was a process on the CPU */
            quark = ssb.getQuarkRelative(currentThreadNode, Attributes.SYSTEM_CALL);
            if (ssb.queryOngoingState(quark).isNull()) {
                /* That process was in user mode */
                value = StateValues.CPU_STATUS_RUN_USERMODE_VALUE;
            } else {
                /* That process was in a system call */
                value = StateValues.CPU_STATUS_RUN_SYSCALL_VALUE;
            }
        } else {
            /* There was no real process scheduled, CPU was idle */
            value = StateValues.CPU_STATUS_IDLE_VALUE;
        }
        quark = ssb.getQuarkRelativeAndAdd(currentCpuNode, Attributes.STATUS);
        ssb.modifyAttribute(ts, value, quark);
    }
}
