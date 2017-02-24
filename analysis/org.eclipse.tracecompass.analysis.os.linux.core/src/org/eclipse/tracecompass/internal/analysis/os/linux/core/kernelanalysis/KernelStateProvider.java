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
import java.util.List;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


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
    private class ioClass {
        //       int status;
        int isWrite;
        int numberOfSector ;
        long ts;
        ioClass(int isWrite, int numberOfSector, Long ts){
            //           this.status = status;
            this.isWrite = isWrite;
            this.numberOfSector = numberOfSector;
            this.ts = ts;
        }

        public ioClass() {
        }
    }
    private class exitVMClass{
        Long ts;
        int reason;
        public exitVMClass(Long ts, int reason) {
            this.ts=ts;
            this.reason = reason;
        }

    }
    private class NVM {
        exitVMClass lastExit;
        int nextState;

        Long process;
        Long VM;
        public NVM(exitVMClass lastExit, int nextState,  Long process, Long VM){
            this.lastExit=lastExit;

            this.nextState=nextState;
            this.process=process;
            this.VM = VM;
        }



    }
    // ------------------------------------------------------------------------
    // Static fields
    // ------------------------------------------------------------------------

    /**
     * Version number of this state provider. Please bump this if you modify the
     * contents of the generated state history in some way.
     */
    private static long timeDump = 0;
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
    private static final int KVM_NESTED_VMEXIT = 22;
    private static final int KVM_APIC_ACCEPT_IRQ = 23;
    private static final int UST_MYSQL_COMMAND_START = 24;
    private static final int UST_MYSQL_COMMAND_DONE = 25;
    private static Map<Integer,Integer> netIf = new HashMap<>() ;
    private static Map<Integer,Integer> netDev = new HashMap<>() ;
    private static Map<Integer,Long> netTs = new HashMap<>() ;
    public static Map<String,Map<Integer,stackData>> stackRange = new HashMap<>();
    public static Map<Integer, String> TIDtoName = new HashMap<>();
    public static Map<Integer,Integer> tidToPtid = new HashMap<>();
    public static Map<Integer,Map<Integer,Long>> VMToUsage = new HashMap<>();
    // showing on which CPU it is being preempted
    public static Map<Integer,Integer> threadPreempted = new HashMap<>();
    // It has all the threads which are preempted on this cpu
    // <cpu,<tid,ts>>
    public static Map<Integer,Map<Integer,Long>> cpuTopreemption = new HashMap<>();

    // It has the thread which are running on this preempted cpu
    // <cpu,<tid,ts>>
    public static Map<Integer,Map<Integer,Long>> preemptingThread = new HashMap<>();


    public static Map<Integer, Map<Integer,Long>> waitingCPU = new HashMap<>();

    public static Map<Integer,Map<String,ioClass>> threadPool = new HashMap<>();
    public static Map<Integer,Map<Integer,Integer>> diskCon = new HashMap<>();
    public static Map<Integer,Integer> diskUsage = new HashMap<>();
    public static Map<Integer,Integer> diskReadTotal = new HashMap<>();
    public static Map<Integer,Integer> diskWriteTotal = new HashMap<>();
    public static Map<Integer,Map<Integer,Integer>> diskConTotal = new HashMap<>();
    public static Map<Integer,Map<Integer,Long>> statistics_reason = new HashMap<>();
    public static Map<Integer,Map<Integer,Long>> time_reason = new HashMap<>();
    public static int counter_time_vote = 0;
    public static Map<Integer,Long> timeForIO = new HashMap<>();
    public static Map<Integer,Long> timeForexit = new HashMap<>();
    public static Map<Integer,Long> nestedExit = new HashMap<>();
    public static Map<Integer,Integer> prevExit = new HashMap<>();

    public static Map<Integer,NVM> nestedVM = new HashMap<>();
    public static Map<Integer,Integer> isNestedVM = new HashMap<>();
    public static Map<Integer,Long> prevCR3 = new HashMap<>();
    public static Map<Long,Integer> nestedVMCR3 = new HashMap<>();
    public static Map<Integer,Map<String,Long>> allsqlThread = new HashMap<>();
    public static Map<Integer,Integer> vcpu2vtid = new HashMap<>();
    public static Map<Integer,Integer> sql_vcpu2pcpu = new HashMap<>();
    public static Map<Integer,Integer> sql_pcpu2vcpu = new HashMap<>();
    @Nullable public FileWriter out1;
    public static Map<Integer,Map<Integer,Integer>> allsqlExit = new HashMap<>();

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
    private class stackData {
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
        builder.put(layout.eventUSTMysqlCommandStart(), UST_MYSQL_COMMAND_START);
        builder.put(layout.eventUSTMysqlCommandDone(), UST_MYSQL_COMMAND_DONE);
        //builder.put(layout.eventKVMEntry(), KVM_ENTRY);
        //builder.put(layout.eventKVMExit(), KVM_EXIT);
        builder.put(layout.eventVCPUEnterGuest(), VCPU_ENTER_GUEST);
        builder.put(layout.eventKVMNestedVMExit(), KVM_NESTED_VMEXIT);
        builder.put(layout.eventKVMAPICAccept_IRQ(), KVM_APIC_ACCEPT_IRQ);
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
        for (String eventKVMEntry : layout.eventKVMEntry()) {
            builder.put(eventKVMEntry, KVM_ENTRY);
        }
        for (String eventKVMExit : layout.eventKVMExit()) {
            builder.put(eventKVMExit, KVM_EXIT);
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
    private static int getNodeDelayQemu(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd("DelayCPU");
    }
    private static int getNodeNetQemu(ITmfStateSystemBuilder ssb) {
        return ssb.getQuarkAbsoluteAndAdd(Attributes.NetQemu);
    }
    private int getVMPTID(int thread1){
        int threadPTID = thread1;
        try {
            if (!tidToPtid.containsKey(thread1)) {
                final ITmfStateSystemBuilder ss = checkNotNull(getStateSystemBuilder());
                int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(thread1));
                int quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, Attributes.PPID);
                ITmfStateValue value = ss.queryOngoingState(quark);
                Integer PTID = value.isNull() ? 0 : value.unboxInt();
                if (PTID == 0){
                    return 0;
                }
                int newCurrentThreadNodeTmp = quark;
                String execName ;
                int quarkName = ss.getQuarkRelativeAndAdd(currentThreadCPU, Attributes.EXEC_NAME);
                ITmfStateValue valueName = ss.queryOngoingState(quarkName);
                execName = valueName.isNull() ? "0" : valueName.unboxStr(); //$NON-NLS-1$

                while (PTID != 1 && PTID !=0 && !execName.equals("libvirtd")) { //$NON-NLS-1$
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);

                    newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                    quarkName = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.EXEC_NAME);
                    value = ss.queryOngoingState(quark);
                    valueName = ss.queryOngoingState(quarkName);
                    execName = valueName.isNull() ? "0" : valueName.unboxStr();
                    threadPTID = PTID;
                    PTID = value.isNull() ? 0 : value.unboxInt();
                }
                if (threadPTID == 0 ) {
                    return 0;
                }
                if (tidToPtid.containsKey(threadPTID)){
                    while (tidToPtid.containsKey(threadPTID)&&threadPTID!=tidToPtid.get(threadPTID)){
                        threadPTID=tidToPtid.get(threadPTID);
                    }
                }
                tidToPtid.put(thread1, threadPTID);
            } else {
                threadPTID = tidToPtid.get(thread1);
            }
        } catch (Exception e){
            System.out.println(thread1+ " error" );
        }
        return threadPTID;
    }

    private static void showPercPreemption(Map<String,Map<String,Long>> preemptedVMsValue, Map<String,Long> usageVMsValue, Map<String,Long> preemptingVMsValue, Map<Integer,Integer> diskRead){
        counter_time_vote++;
        Map<String, Long> votes = new HashMap<>();
        Map<String, Integer> votesToRemove = new HashMap<>();
        String gatherData = new String();
        System.out.println("Counter = "+counter_time_vote);
        for (Entry<String, Map<String, Long>> threads:preemptedVMsValue.entrySet()){
            Long sumPreemption = 0L;
            for (Entry<String,Long> vm:threads.getValue().entrySet()){
                sumPreemption += vm.getValue();
            }
            gatherData = gatherData + threads.getKey()+","+ String.valueOf(sumPreemption) +"," + preemptingVMsValue.get(threads.getKey())+","+ usageVMsValue.get(threads.getKey())+",";
            System.out.println("Preempted VM= " + threads.getKey());
            System.out.println("Preempting = "+ preemptingVMsValue.get(threads.getKey()));
            System.out.println("Preempted = " + String.valueOf(sumPreemption)+" Usage = "+ usageVMsValue.get(threads.getKey()));
            votes.put(threads.getKey(),Math.round((double)sumPreemption/(sumPreemption+usageVMsValue.get(threads.getKey()))*100));
            System.out.println("Votes = " + votes.get(threads.getKey()));

            for (Entry<String,Long> vm:threads.getValue().entrySet()){
                if (votesToRemove.containsKey(vm.getKey())){
                    Integer voteTmp = votesToRemove.get(vm.getKey()) + Math.round(((float)votes.get(threads.getKey())*vm.getValue()/sumPreemption));
                    votesToRemove.put(vm.getKey(), voteTmp);
                } else {
                    votesToRemove.put(vm.getKey(), Math.round(((float)votes.get(threads.getKey())*vm.getValue()/sumPreemption)));
                }
                System.out.println(vm.getKey() + "->"+Math.round(((float)votes.get(threads.getKey())*vm.getValue()/sumPreemption)));
            }
        }
        System.out.println(gatherData);

        Set<Entry<Integer, Integer>> set = diskRead.entrySet();
        List<Entry<Integer, Integer>> list = new ArrayList<>(set);
        Map<Integer,Integer> votesSum = new HashMap<>();
        Collections.sort( list, new Comparator<Map.Entry<Integer, Integer>>()
        {
            @Override
            public int compare(  @Nullable Entry<Integer, Integer> o1,   @Nullable Entry<Integer, Integer> o2 )
            {
                if (o2!=null && o1!=null){
                    return Integer.valueOf((o2.getValue()).compareTo( o1.getValue() ));
                }
                return -1;
            }
        } );

        Set<Entry<String, Integer>>set1 = votesToRemove.entrySet();
        List<Entry<String, Integer>> list1 = new ArrayList<>(set1);
        Collections.sort( list1, new Comparator<Map.Entry<String, Integer>>()
        {
            @Override
            public int compare(  @Nullable Entry<String, Integer> o1,   @Nullable Entry<String, Integer> o2 )
            {
                if (o2!=null && o1!=null){
                    return Integer.valueOf((o2.getValue()).compareTo( o1.getValue() ));
                }
                return -1;
            }
        } );

        int j = -1;
        Integer tmpListValue = 0;
        for(Entry<Integer, Integer> entry:list){
            if (tmpListValue != entry.getValue()/1000){
                tmpListValue = entry.getValue()/1000;
                j++;
            }
            votesSum.put(entry.getKey(), j);
            System.out.println("VM Disk " +entry.getKey()+ " = " + entry.getValue() + " votes = "+ votesSum.get(entry.getKey()));
        }
        j = 1;
        // votesSum1 has all information about vote goes for CPU
        tmpListValue = 0;
        Map<String,Integer> votesSum1 = new HashMap<>();
        for (Entry<String,Integer> entry:list1){
            if (tmpListValue != entry.getValue()){
                tmpListValue = entry.getValue();
                j++;
            }
            votesSum1.put(entry.getKey(), list1.size()-j);
            System.out.println("VM CPU " +entry.getKey()+ " = " + entry.getValue()+ " votes = "+ votesSum1.get(entry.getKey()));

        }
        for (Entry<Integer, Integer> entry:votesSum.entrySet()){
            System.out.println("Votes VM " + entry.getKey() + " = " + entry.getValue()+"--"+votesSum1.get(entry.getKey().toString()));
        }
    }
    @Override
    public KernelStateProvider getNewInstance() {
        return new KernelStateProvider(this.getTrace(), fLayout);
    }
    @Override
    protected void eventHandle(@Nullable ITmfEvent event) {
        boolean alg_voting = false;
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


            // Voting algorithm 22 April 2016
            if (alg_voting == true){
                if ((ts - timeDump) >= 1000000000){
                    Map<String,Map<String,Long>> preemptedVMsValue = new HashMap<>();
                    Map<String,Long> usageVMsValue = new HashMap<>();
                    Map<String,Long> preemptingVMsValue = new HashMap<>();
                    if (timeDump == 0){
                        timeDump = ts ;
                    } else {
                        System.out.println("****************************************************");
                        timeDump = ts ;
                        // Preemption Matrix for each VM
                        Integer currentDelayThread = ss.getQuarkRelativeAndAdd(getNodeDelayQemu(ss), "preempting"); //$NON-NLS-1$
                        List<Integer> quarksList = ss.getSubAttributes(currentDelayThread, false);
                        for (Integer vm:quarksList){
                            String vmThread = ss.getAttributeName(vm);
                            Integer quark = ss.getQuarkRelativeAndAdd(vm, "VM"); //$NON-NLS-1$
                            List<Integer> preemptingVMsList = ss.getSubAttributes(quark, false);
                            Map<String,Long> tmpValue = new HashMap<>();
                            for (Integer preemptingVMList:preemptingVMsList){
                                ITmfStateValue value = ss.queryOngoingState(preemptingVMList);
                                Long preemtingValue = value.isNull() ? 0 : value.unboxLong();
                                tmpValue.put(ss.getAttributeName(preemptingVMList), preemtingValue);
                                Long lastPreemptingValue = 0L;
                                if (preemptingVMsValue.containsKey(ss.getAttributeName(preemptingVMList))){
                                    lastPreemptingValue = preemptingVMsValue.get(ss.getAttributeName(preemptingVMList));
                                }
                                preemptingVMsValue.put(ss.getAttributeName(preemptingVMList), preemtingValue+lastPreemptingValue);

                            }
                            preemptedVMsValue.put(vmThread,tmpValue);
                        }
                        // Usage Matrix for each VM
                        Integer currentUsageThread = ss.getQuarkRelativeAndAdd(getNodeDelayQemu(ss), "usage"); //$NON-NLS-1$
                        List<Integer> quarksListUsage = ss.getSubAttributes(currentUsageThread, false);
                        for (Integer vm:quarksListUsage){
                            String vmThread = ss.getAttributeName(vm);
                            ITmfStateValue value = ss.queryOngoingState(vm);
                            Long usageValue = value.isNull() ? 0 : value.unboxLong();
                            usageVMsValue.put(vmThread, usageValue);
                        }
                        //System.out.println(usageVMsValue);
                        showPercPreemption(preemptedVMsValue,usageVMsValue,preemptingVMsValue,diskReadTotal);
                    }
                }
            }
            // End of Voting Algorithm


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

            case UST_MYSQL_COMMAND_START:{
                // states :    out = 0, start=1, vmx_root=3, vmx_non-root=4, wait= 5
                ITmfEventField content = event.getContent();
                //System.out.println(ts+"\t"+event.getName() + "\t" + event.getContent());
                Integer sqlTID = Integer.parseInt(content.getField("thread_id").getValue().toString()); //$NON-NLS-1$
                Integer vtid = Integer.parseInt(content.getField("context._vtid").getValue().toString());
                Integer command = Integer.parseInt(content.getField("command").getValue().toString());
                Map<String,Long> sqlThread = new HashMap<>();
                Map<Integer,Integer> exitThread = new HashMap<>();
                allsqlExit.put(vtid, exitThread);
                sqlThread.put("non-root_start", ts);
                sqlThread.put("on-cpu_start",ts);
                sqlThread.put("vmx_nonroot", 0L);
                sqlThread.put("start",ts);
                sqlThread.put("status",4L);
                sqlThread.put("vtid", vtid.longValue());
                sqlThread.put("sqlTID",sqlTID.longValue());
                sqlThread.put("wait",0L);
                sqlThread.put("vmx_root",0L);
                sqlThread.put("on-cpu",0L);
                sqlThread.put("root_start",0L);
                sqlThread.put("command", (long) command);
                allsqlThread.put(vtid, sqlThread);
                vcpu2vtid.put(cpu, vtid);


            } break;
            case UST_MYSQL_COMMAND_DONE:{
                ITmfEventField content = event.getContent();
                //System.out.println(ts+"\t"+event.getName() + "\t" + event.getContent());
                //int sqlTID = Integer.parseInt(content.getField("thread_id").getValue().toString());
                int vtid = Integer.parseInt(content.getField("context._vtid").getValue().toString());
                Map <String,Long> sqlThread = allsqlThread.get(vtid);

                if (vcpu2vtid.containsKey(cpu)){
                    vcpu2vtid.remove(cpu);
                }
                int e30 = 0;
                int e32 = 0;
                int e01 = 0;

                sqlThread.put("on-cpu",sqlThread.get("on-cpu")+ts - sqlThread.get("on-cpu_start"));  //$NON-NLS-1$//$NON-NLS-2$
                sqlThread.put("vmx_nonroot", sqlThread.get("vmx_nonroot")+ts-sqlThread.get("non-root_start"));
                                System.out.println("----------------------------------------------------------");
                System.out.println("Total:"+(ts - sqlThread.get("start")));
                Map <Integer,Integer> exitThread = allsqlExit.get(vtid);
                System.out.println("Exit Reasons");
                for (Integer key:exitThread.keySet()){
                    if (key == 30){
                       e30 = exitThread.get(key);
                    } else if (key==32){
                        e32 = exitThread.get(key);
                    }  else if (key==01){
                        e01 = exitThread.get(key);
                    }
                    System.out.print(key+":"+exitThread.get(key)+" ");
                }
                System.out.println("\nCommand:"+sqlThread.get("command")+"\twait:" + sqlThread.get("wait") + "\tvmxroot:" + sqlThread.get("vmx_root")+"\tvxm-nonRoot:"+sqlThread.get("vmx_nonroot")+"\ton-cpu:"+sqlThread.get("on-cpu"));

                try {

                    out1 = new FileWriter("hani.csv",true);


                    final FileWriter out12 = out1;

                    out12.append(sqlThread.get("command").toString()+","+sqlThread.get("wait").toString()+","+sqlThread.get("vmx_root").toString()+","+sqlThread.get("vmx_nonroot").toString()+","+sqlThread.get("on-cpu").toString()+","+e30+","+e32+","+e01+"\n");


                    out12.close();

                }
                catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }



                allsqlThread.remove(vtid);



            }break;
            case KVM_NESTED_VMEXIT:{

                ITmfEventField content = event.getContent();
                final int reason = Integer.parseInt(content.getField("exit_code").getValue().toString()); //$NON-NLS-1$
                isNestedVM.put(thread,1);
                if (timeForexit.containsKey(thread)){
                    long tmpTS = timeForexit.get(thread);
                    exitVMClass exitVM = new exitVMClass(tmpTS,reason);
                    if (nestedVM.containsKey(thread)){
                        NVM nvm = nestedVM.get(thread);
                        nvm.lastExit = exitVM;
                        nvm.nextState = 3;
                        nestedVM.put(thread, nvm);
                    } else {
                        NVM nvm = new NVM(exitVM,3,-1L,-1L);
                        nestedVM.put(thread, nvm);
                    }

                    int threadPTID = getVMPTID(thread);
                    if (threadPTID==0){
                        break;
                    }
                    int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                    int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                    quark = ss.getQuarkRelativeAndAdd(currentThreadNode, "vcpu"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    String vcpu_id = value.toString();
                    int vcpuIDquark = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);
                    int statusQuark = ss.getQuarkRelativeAndAdd(vcpuIDquark, Attributes.STATUS); //$NON-NLS-1$
                    value = StateValues.CPU_STATUS_VMX_ROOT_VALUE;
                    ss.modifyAttribute(tmpTS, value, statusQuark);
                    value = StateValues.CPU_STATUS_VMX_NESTED_ROOT_VALUE;
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                    ss.modifyAttribute(tmpTS, value, quark);

                    //int vmNameQuark = ss.getQuarkAbsoluteAndAdd("vmName"); //$NON-NLS-1$
                    int vmNameQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU,"nestedVM");//$NON-NLS-1$
                    if (nestedVM.containsKey(thread) && nestedVM.get(thread).process>0) {
                        int nestedVMQuark = ss.getQuarkRelativeAndAdd(vmNameQuark,nestedVM.get(thread).VM.toString() ); //$NON-NLS-1$
                        int nestedVMCPUQuark = ss.getQuarkRelativeAndAdd(nestedVMQuark,vcpu_id);
                        int nestedVMAppQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,"process");//$NON-NLS-1$
                        value = TmfStateValue.newValueLong(nestedVM.get(thread).process);
                        ss.modifyAttribute(nestedVM.get(thread).lastExit.ts, value, nestedVMAppQuark);
                        int nestedVMStatusQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,Attributes.STATUS);//$NON-NLS-1$
                        value = StateValues.CPU_STATUS_VMX_NESTED_ROOT_VALUE;
                        ss.modifyAttribute(nestedVM.get(thread).lastExit.ts, value, nestedVMStatusQuark);
                    }
                }
            } break;
            case VCPU_ENTER_GUEST:{
                boolean vmthreadName = false;
                boolean checkAddons = true;
                boolean processState = true;
                if (checkAddons == false) {
                    break;
                }
                try {
                    ITmfEventField content = event.getContent();
                    Long  sptmp =  Long.valueOf((content.getField("sptmp").getValue()).toString()); //$NON-NLS-1$
                    Long cr3tmp =  Long.valueOf((content.getField("cr3tmp").getValue()).toString()); //$NON-NLS-1$
                    Integer FvCPUID = Integer.valueOf((content.getField("vcpuID").getValue().toString())); //$NON-NLS-1$
                    int threadPTID = getVMPTID(thread);
                    if (threadPTID==0){
                        break;
                    }
                    int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                    if (prevCR3.containsKey(thread)&&isNestedVM.containsKey(thread)&& isNestedVM.get(thread)==1&&(nestedVM.containsKey(thread))){
                        NVM nvm = nestedVM.get(thread);
                        exitVMClass exitObj = nvm.lastExit;
                        // 7 means it is preempted
                        if (nestedVM.get(thread).nextState==7 && !nestedVM.get(thread).process.equals(cr3tmp)){
                            int vmNameQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU,"nestedVM");//$NON-NLS-1$

                            if (nestedVM.containsKey(thread)&&nestedVM.get(thread).process>0) {
                                int nestedVMQuark = ss.getQuarkRelativeAndAdd(vmNameQuark,nestedVM.get(thread).VM.toString() ); //$NON-NLS-1$
                                quark = ss.getQuarkRelativeAndAdd(currentThreadNode, "vcpu"); //$NON-NLS-1$
                                value = ss.queryOngoingState(quark);
                                String vcpu_id = value.toString();
                                int nestedVMCPUQuark = ss.getQuarkRelativeAndAdd(nestedVMQuark,vcpu_id);//$NON-NLS-1$
                                int nestedVMAppQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,"process");
                                value = TmfStateValue.newValueLong(nestedVM.get(thread).process);
                                ss.modifyAttribute(ts, value, nestedVMAppQuark);
                                int nestedVMStatusQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,Attributes.STATUS);//$NON-NLS-1$
                                value = StateValues.L1_PREEMPTED_VALUE;
                                ss.modifyAttribute(nestedVM.get(thread).lastExit.ts, value, nestedVMStatusQuark);
                            }
                        }
                        if (exitObj.reason==24 || exitObj.reason==20 ){
                            //System.out.println(nvm.process +"    "+cr3tmp + "   "+nestedVM.get(thread).nextState);

                            nestedVMCR3.put(prevCR3.get(thread),1);
                            nvm.nextState = 3;
                            nvm.process = cr3tmp;
                            nvm.VM = prevCR3.get(thread);
                            nestedVM.put(thread, nvm);
                            int vmNameQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU,"nestedVM");//$NON-NLS-1$


                            int nestedVMQuark = ss.getQuarkRelativeAndAdd(vmNameQuark,nestedVM.get(thread).VM.toString() ); //$NON-NLS-1$
                            quark = ss.getQuarkRelativeAndAdd(currentThreadNode, "vcpu"); //$NON-NLS-1$
                            value = ss.queryOngoingState(quark);
                            String vcpu_id = value.toString();
                            int nestedVMCPUQuark = ss.getQuarkRelativeAndAdd(nestedVMQuark,vcpu_id);//$NON-NLS-1$
                            int nestedVMAppQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,"process");
                            value = TmfStateValue.newValueLong(cr3tmp);
                            ss.modifyAttribute(nestedVM.get(thread).lastExit.ts, value, nestedVMAppQuark);
                            int nestedVMStatusQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,Attributes.STATUS);//$NON-NLS-1$
                            value = StateValues.CPU_STATUS_VMX_ROOT_VALUE;
                            ss.modifyAttribute(nestedVM.get(thread).lastExit.ts, value, nestedVMStatusQuark);

                        }
                    } else {
                        prevCR3.put(thread,cr3tmp);
                    }
                    if (nestedVMCR3.containsKey(cr3tmp) ){
                        NVM nvm = nestedVM.get(thread);
                        nvm.nextState = 1;
                        isNestedVM.put(thread, 1);
                        nestedVM.put(thread, nvm);
                    }
                    if (nestedVM.containsKey(thread)&& cr3tmp == nestedVM.get(thread).process){
                        NVM nvm = nestedVM.get(thread);
                        nvm.nextState = 3;
                        isNestedVM.put(thread, 1);
                        nestedVM.put(thread, nvm);
                    }
                    // Thread inside the VM


                    if (vmthreadName){
                        if (sptmp > 0){


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
                                    int statusQuark = ss.getQuarkRelativeAndAdd(vCPUID, Attributes.STATUS); //$NON-NLS-1$
                                    value = TmfStateValue.newValueInt(StateValues.PROCESS_STATUS_RUN_USERMODE);
                                    ss.modifyAttribute(ts, value, statusQuark);
                                    int threadQuark = ss.getQuarkRelativeAndAdd(vCPUID, "thread_in"); //$NON-NLS-1$
                                    value = TmfStateValue.newValueInt(entry.getValue().tid);
                                    ss.modifyAttribute(ts, value, threadQuark);
                                    threadQuark = ss.getQuarkRelativeAndAdd(vCPUID, "threadName"); //$NON-NLS-1$
                                    value = TmfStateValue.newValueString(entry.getValue().name);
                                    ss.modifyAttribute(ts, value, threadQuark);
                                    int vmNameQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU,"nestedVM");//$NON-NLS-1$

                                    int vm_thread_quark = ss.getQuarkRelativeAndAdd(vmNameQuark, Integer.toString(entry.getValue().tid));
                                    vm_thread_quark = ss.getQuarkRelativeAndAdd(vm_thread_quark,Attributes.STATUS);//$NON-NLS-1$
                                    value = TmfStateValue.newValueInt(StateValues.PROCESS_STATUS_RUN_USERMODE);
                                    ss.modifyAttribute(ts, value, vm_thread_quark);
                                }
                            }
                        } else {

                            currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                            int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                            String vcpu_id = content.getField("vcpuID").getValue().toString(); //$NON-NLS-1$
                            int vCPUID = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);
                            int statusQuark = ss.getQuarkRelativeAndAdd(vCPUID, Attributes.STATUS); //$NON-NLS-1$
                            value = TmfStateValue.newValueInt(StateValues.PROCESS_STATUS_RUN_SYSCALL);
                            ss.modifyAttribute(ts, value, statusQuark);
                        }
                    }

                    if (processState==true){
                        threadPTID = getVMPTID(thread);
                        if (threadPTID==0){
                            break;
                        }
                        currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                        int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                        int vProcesses = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vProcesses"); //$NON-NLS-1$
                        quark = ss.getQuarkRelativeAndAdd(vProcesses, cr3tmp.toString());
                        value = ss.queryOngoingState(quark);
                        int numberOfThreads = value.isNull() ? 1 : value.unboxInt()+1;
                        value = TmfStateValue.newValueInt(numberOfThreads);
                        ss.modifyAttribute(ts, value,quark);
                        quark = ss.getQuarkRelativeAndAdd(vCPUQuark, FvCPUID.toString());//$NON-NLS-1$
                        quark = ss.getQuarkRelativeAndAdd(quark, "process_in");//$NON-NLS-1$
                        value = ss.queryOngoingState(quark);
                        Long prevCR3_Process = value.isNull() ? 0 : value.unboxLong();
                        value = TmfStateValue.newValueLong(cr3tmp);
                        ss.modifyAttribute(ts, value,quark);
                        if (prevCR3_Process!=cr3tmp && prevCR3_Process !=0){
                            quark = ss.getQuarkRelativeAndAdd(vProcesses, prevCR3_Process.toString());
                            value = ss.queryOngoingState(quark);
                            numberOfThreads = value.isNull() ? 0 : value.unboxInt()-1;
                            if (numberOfThreads < 0){
                                numberOfThreads = 0;
                            }
                            value = TmfStateValue.newValueInt(numberOfThreads);
                            ss.modifyAttribute(ts, value,quark);
                        }


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
                String vcpu_id = content.getField("vcpu_id").getValue().toString(); //$NON-NLS-1$


                ITmfEventField sql_vcpu_id = content.getField("vcpu_id");

                sql_pcpu2vcpu.put(cpu, Integer.parseInt( sql_vcpu_id.getValue().toString())) ;
                sql_vcpu2pcpu.put(Integer.parseInt( sql_vcpu_id.getValue().toString()), cpu) ;
                if (vcpu2vtid.containsKey(Integer.parseInt( sql_vcpu_id.getValue().toString())) ) {
                    Integer vtid = vcpu2vtid.get(Integer.parseInt(sql_vcpu_id.getValue().toString()));
                    if (allsqlThread.containsKey(vtid)){

                        Map <String,Long> sqlThread = allsqlThread.get(vtid);
                        if (sqlThread.containsKey("vmx_root") && sqlThread.get("status")== 3L){
                            //System.out.println(ts+"\t"+event.getName() + "\t" + event.getContent());

                            Long sum_root = sqlThread.get("vmx_root");
                            sqlThread.put("vmx_root", sum_root+ts-sqlThread.get("root_start"));
                            sqlThread.put("non-root_start", ts);
                            sqlThread.put("status",4L);
                        }
                        allsqlThread.put(vcpu2vtid.get(Integer.parseInt(sql_vcpu_id.getValue().toString())),sqlThread);

                    }
                }


                int threadPTID = getVMPTID(thread);
                if (threadPTID==0){
                    break;
                }
                int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$


                int vCPUID = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);
                value = TmfStateValue.newValueInt(1);
                ss.modifyAttribute(ts, value, vCPUID);
                quark = ss.getQuarkRelativeAndAdd(vCPUID, "thread_in");//$NON-NLS-1$
                value = ss.queryOngoingState(quark);
                int thread_in = value.isNull() ? 0 : value.unboxInt();

                int quarkVM = ss.getQuarkAbsoluteAndAdd("vmName"); //$NON-NLS-1$

                int vm_thread_quark = ss.getQuarkRelativeAndAdd(quarkVM, "testU1"); //$NON-NLS-1$
                vm_thread_quark = ss.getQuarkRelativeAndAdd(vm_thread_quark, Integer.toString(thread_in));
                vm_thread_quark = ss.getQuarkRelativeAndAdd(vm_thread_quark,Attributes.STATUS);//$NON-NLS-1$
                value = TmfStateValue.newValueInt(StateValues.PROCESS_STATUS_NON_ROOT);
                ss.modifyAttribute(ts, value, vm_thread_quark);

                quark = ss.getQuarkRelativeAndAdd(vCPUID, Attributes.STATUS); //$NON-NLS-1$

                if (isNestedVM.containsKey(thread) && isNestedVM.get(thread)==1 && nestedVM.containsKey(thread)&& nestedVM.get(thread).nextState == 3 &&nestedVM.get(thread).lastExit.reason!=12){
                    value = StateValues.CPU_STATUS_VMX_NON_ROOT_VALUE;
                    ss.modifyAttribute(ts, value, quark);
                    /* save the thread is simulating which vcpu */
                    quark = ss.getQuarkRelativeAndAdd(currentThreadNode, "vcpu"); //$NON-NLS-1$
                    value = TmfStateValue.newValueString(vcpu_id);
                    ss.modifyAttribute(ts, value, quark);
                    value = StateValues.CPU_STATUS_VMX_NESTED_NON_ROOT_VALUE;
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                    ss.modifyAttribute(ts, value, quark);
                    int vmNameQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU,"nestedVM");//$NON-NLS-1$
                    if (nestedVM.get(thread).process>0) {
                        int nestedVMQuark = ss.getQuarkRelativeAndAdd(vmNameQuark,nestedVM.get(thread).VM.toString() ); //$NON-NLS-1$

                        int nestedVMCPUQuark = ss.getQuarkRelativeAndAdd(nestedVMQuark,vcpu_id);//$NON-NLS-1$
                        int nestedVMAppQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,"process");//$NON-NLS-1$
                        value = TmfStateValue.newValueLong(nestedVM.get(thread).process);
                        ss.modifyAttribute(ts, value, nestedVMAppQuark);
                        int nestedVMStatusQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,Attributes.STATUS);//$NON-NLS-1$
                        value = StateValues.CPU_STATUS_VMX_NESTED_NON_ROOT_VALUE;
                        ss.modifyAttribute(ts, value, nestedVMStatusQuark);
                    }


                } else if (prevCR3.containsKey(thread)&&nestedVMCR3.containsKey(prevCR3.get(thread))&&nestedVM.containsKey(thread)&&nestedVM.get(thread).lastExit.reason!=12){
                    value = StateValues.CPU_STATUS_VMX_NON_ROOT_VALUE;
                    ss.modifyAttribute(ts, value, quark);
                    /* save the thread is simulating which vcpu */
                    quark = ss.getQuarkRelativeAndAdd(currentThreadNode, "vcpu"); //$NON-NLS-1$
                    value = TmfStateValue.newValueString(vcpu_id);
                    ss.modifyAttribute(ts, value, quark);
                    value = StateValues.CPU_STATUS_VMX_NON_ROOT_VALUE;
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                    ss.modifyAttribute(ts, value, quark);


                    int vmNameQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU,"nestedVM");//$NON-NLS-1$

                    if (nestedVM.containsKey(thread)&&nestedVM.get(thread).process>0) {
                        int nestedVMQuark = ss.getQuarkRelativeAndAdd(vmNameQuark,nestedVM.get(thread).VM.toString() ); //$NON-NLS-1$

                        int nestedVMCPUQuark = ss.getQuarkRelativeAndAdd(nestedVMQuark,vcpu_id);//$NON-NLS-1$
                        int nestedVMAppQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,"process");//$NON-NLS-1$
                        value = TmfStateValue.newValueLong(nestedVM.get(thread).process);
                        ss.modifyAttribute(ts, value, nestedVMAppQuark);
                        int nestedVMStatusQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,Attributes.STATUS);//$NON-NLS-1$
                        value = StateValues.CPU_STATUS_VMX_NON_ROOT_VALUE;
                        ss.modifyAttribute(ts, value, nestedVMStatusQuark);
                    }
                }else if (prevCR3.containsKey(thread)&&nestedVMCR3.containsKey(prevCR3.get(thread))&&nestedVM.containsKey(thread)&&nestedVM.get(thread).lastExit.reason==12){
                    value = StateValues.CPU_STATUS_VMX_NON_ROOT_VALUE;
                    ss.modifyAttribute(ts, value, quark);
                    /* save the thread is simulating which vcpu */
                    quark = ss.getQuarkRelativeAndAdd(currentThreadNode, "vcpu"); //$NON-NLS-1$
                    value = TmfStateValue.newValueString(vcpu_id);
                    ss.modifyAttribute(ts, value, quark);
                    value = StateValues.CPU_STATUS_VMX_NON_ROOT_VALUE;
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                    ss.modifyAttribute(ts, value, quark);




                    int vmNameQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU,"nestedVM");//$NON-NLS-1$
                    if (nestedVM.containsKey(thread)&&nestedVM.get(thread).process>0) {
                        int nestedVMQuark = ss.getQuarkRelativeAndAdd(vmNameQuark,nestedVM.get(thread).VM.toString() ); //$NON-NLS-1$

                        int nestedVMCPUQuark = ss.getQuarkRelativeAndAdd(nestedVMQuark,vcpu_id);//$NON-NLS-1$
                        int nestedVMAppQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,"process"); //$NON-NLS-1$
                        value = TmfStateValue.newValueLong(nestedVM.get(thread).process);
                        ss.modifyAttribute(ts, value, nestedVMAppQuark);
                        int nestedVMStatusQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,Attributes.STATUS);//$NON-NLS-1$
                        value = StateValues.CPU_STATUS_IDLE_VALUE;
                        ss.modifyAttribute(ts, value, nestedVMStatusQuark);
                    }
                }
                else {
                    value = StateValues.CPU_STATUS_VMX_NON_ROOT_VALUE;
                    ss.modifyAttribute(ts, value, quark);
                    /* save the thread is simulating which vcpu */
                    quark = ss.getQuarkRelativeAndAdd(currentThreadNode, "vcpu"); //$NON-NLS-1$
                    value = TmfStateValue.newValueString(vcpu_id);
                    ss.modifyAttribute(ts, value, quark);
                    value = StateValues.CPU_STATUS_VMX_NON_ROOT_VALUE;
                    quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                    ss.modifyAttribute(ts, value, quark);
                }
                quark = ss.getQuarkRelativeAndAdd(vCPUID, "exit_reason"); //$NON-NLS-1$
                value = ss.queryOngoingState(quark);
                Integer reason = value.isNull() ? 0 : value.unboxInt();

                // for time spend for each transition
                if (time_reason.containsKey(thread)){
                    Map<Integer,Long> tmp_time = time_reason.get(thread);
                    if (tmp_time.containsKey(reason)){
                        Long ts_tmp = tmp_time.get(reason);
                        ts_tmp = ts - ts_tmp;
                        quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "statistics"); //$NON-NLS-1$
                        quark = ss.getQuarkRelativeAndAdd(quark, reason.toString()); //$NON-NLS-1$
                        quark = ss.getQuarkRelativeAndAdd(quark, "latency"); //$NON-NLS-1$
                        value = ss.queryOngoingState(quark);
                        Long time_spend = value.isNull() ? ts_tmp : value.unboxLong()+ts_tmp;
                        value = TmfStateValue.newValueLong(time_spend);
                        ss.modifyAttribute(ts, value, quark);
                        time_reason.remove(thread);
                    }
                }

            }
            break;
            case KVM_APIC_ACCEPT_IRQ:{
                ITmfEventField content = event.getContent();
                Integer  apicid =  Integer.valueOf((content.getField("apicid").getValue()).toString()); //$NON-NLS-1$
                Integer  vec =  Integer.valueOf((content.getField("vec").getValue()).toString()); //$NON-NLS-1$

                final int currentExecNameQuark = ss.getQuarkRelativeAndAdd(currentThreadNode,"Exec_name"); //$NON-NLS-1$
                value = ss.queryOngoingState(currentExecNameQuark);
                String exec_name = value.isNull() ? "0" : value.toString(); //$NON-NLS-1$


                if (vec == 239){
                    // It is a Timer
                    int threadPTID = getVMPTID(thread);
                    if (threadPTID==0){
                        break;
                    }
                    int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                    int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                    int vCPUIDquark = ss.getQuarkRelativeAndAdd(vCPUQuark, apicid.toString());//$NON-NLS-1$
                    quark = ss.getQuarkRelativeAndAdd(vCPUIDquark, "wakeup");//$NON-NLS-1$
                    value = TmfStateValue.newValueString("timer"); //$NON-NLS-1$
                    ss.modifyAttribute(ts, value, quark);
                    break;
                }


                if (exec_name.contains("vhost")) { //$NON-NLS-1$
                    // It is Network

                    String fff = exec_name.substring(6, exec_name.length());

                    int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(Integer.valueOf(fff)));
                    int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                    int vCPUIDquark = ss.getQuarkRelativeAndAdd(vCPUQuark, apicid.toString());//$NON-NLS-1$
                    quark = ss.getQuarkRelativeAndAdd(vCPUIDquark, "wakeup");//$NON-NLS-1$
                    value = TmfStateValue.newValueString("net"); //$NON-NLS-1$
                    ss.modifyAttribute(ts, value, quark);
                    break;
                }

                int threadPTID = getVMPTID(thread);
                if (threadPTID==0){
                    break;
                }
                if (threadPTID == thread){
                    // It is main thread
                    int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                    int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                    int vCPUIDquark = ss.getQuarkRelativeAndAdd(vCPUQuark, apicid.toString());//$NON-NLS-1$
                    quark = ss.getQuarkRelativeAndAdd(vCPUIDquark, "wakeup");//$NON-NLS-1$
                    value = TmfStateValue.newValueString("disk"); //$NON-NLS-1$
                    ss.modifyAttribute(ts, value, quark);


                } else {
                    // It is vCPU thread
                    Integer currVCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadNode,"vcpu");//$NON-NLS-1$
                    value = ss.queryOngoingState(currVCPUQuark);
                    String currVCPU = value.isNull() ? "-1" : value.unboxStr(); //$NON-NLS-1$
                    int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                    int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                    int vCPUIDquark = ss.getQuarkRelativeAndAdd(vCPUQuark, apicid.toString());//$NON-NLS-1$
                    int currVCPUQ = ss.getQuarkRelativeAndAdd(vCPUQuark, currVCPU);
                    int currCR3 = ss.getQuarkRelativeAndAdd(currVCPUQ,"process_in"); //$NON-NLS-1$
                    value = ss.queryOngoingState(currCR3);
                    Long wakingCR3 = value.isNull() ? 0 : value.unboxLong();
                    quark = ss.getQuarkRelativeAndAdd(vCPUIDquark, "process_out");//$NON-NLS-1$
                    value = TmfStateValue.newValueLong(wakingCR3);
                    ss.modifyAttribute(ts, value, quark);
                    quark = ss.getQuarkRelativeAndAdd(vCPUIDquark, "wakeup");//$NON-NLS-1$
                    value = TmfStateValue.newValueString("thread"); //$NON-NLS-1$
                    ss.modifyAttribute(ts, value, quark);

                }
                break;
            }
            case KVM_EXIT:{
                ITmfEventField content = event.getContent();
                final int reason = Integer.parseInt(content.getField("exit_reason").getValue().toString()); //$NON-NLS-1$

                if (sql_pcpu2vcpu.containsKey(cpu)){
                    Integer vcpu = sql_pcpu2vcpu.get(cpu);
                    if (vcpu2vtid.containsKey(vcpu) && allsqlThread.containsKey(vcpu2vtid.get(vcpu))){
                        Map<String,Long> sqlThread = allsqlThread.get(vcpu2vtid.get(vcpu));

                        if (sqlThread.containsKey("non-root_start") && sqlThread.containsKey("vmx_nonroot") && sqlThread.get("status") == 4L){
                            //System.out.println(ts+"\t"+event.getName() + "\t" + event.getContent());
                            Map <Integer,Integer> exitThread = allsqlExit.get(vcpu2vtid.get(vcpu));
                            if (exitThread.containsKey(reason)){
                                exitThread.put(reason,exitThread.get(reason)+1);
                            } else{
                                exitThread.put(reason, 1);
                            }
                            allsqlExit.put(vcpu2vtid.get(vcpu), exitThread);
                            sqlThread.put("root_start", ts);
                            Long sum_nonroot = sqlThread.get("vmx_nonroot");
                            sqlThread.put("vmx_nonroot", sum_nonroot + ts-sqlThread.get("non-root_start"));
                            sqlThread.put("status", 3L);


                        }
                        allsqlThread.put(vcpu2vtid.get(vcpu), sqlThread);

                    }
                }

                if (reason == 24 || reason == 20){
                    exitVMClass exitVM = new exitVMClass(ts,reason);
                    NVM nvm = new NVM(exitVM,3,-1L,-1L);
                    if (nestedVM.containsKey(thread)){
                        nvm = nestedVM.get(thread);
                        if (nvm.lastExit.reason!=12){
                            nvm.lastExit = exitVM;
                            // 7 is preempted
                            nvm.nextState = 7;
                        } else
                        {
                            nvm.lastExit = exitVM;
                            nvm.nextState = 3;
                        }
                    }

                    isNestedVM.put(thread,1);
                    nestedVM.put(thread, nvm);
                } else {
                    isNestedVM.put(thread,0);
                }


                timeForexit.put(thread,ts);
                prevExit.put(thread,reason);
                if (reason == 30){
                    timeForIO.put(thread,ts);
                }

                int threadPTID = getVMPTID(thread);
                if (threadPTID==0){
                    break;
                }
                int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                int statistics_exit_reason = ss.getQuarkRelativeAndAdd(currentThreadCPU, "statistics");//$NON-NLS-1$
                statistics_exit_reason = ss.getQuarkRelativeAndAdd(statistics_exit_reason, Integer.toString(reason));

                if (statistics_reason.containsKey(threadPTID)){
                    Map<Integer,Long> tmp_static = statistics_reason.get(threadPTID);
                    if (tmp_static.containsKey(reason)){
                        tmp_static.put(reason, tmp_static.get(reason)+1);
                    }
                    else {
                        tmp_static.put(reason, (long) 1);
                    }
                    statistics_reason.put(threadPTID, tmp_static);
                } else {
                    Map<Integer,Long> tmp_static = new HashMap<>();
                    tmp_static.put(reason, (long) 1);
                    statistics_reason.put(threadPTID, tmp_static);
                }
                value = TmfStateValue.newValueLong(statistics_reason.get(threadPTID).get(reason));
                ss.modifyAttribute(ts, value, statistics_exit_reason);

                quark = ss.getQuarkRelativeAndAdd(currentThreadNode, "vcpu"); //$NON-NLS-1$
                int quarkExit = ss.getQuarkRelativeAndAdd(currentThreadNode, "exit_reason"); //$NON-NLS-1$
                value = ss.queryOngoingState(quark);
                String vcpu_id = value.toString();
                // For saving time spend for each exit
                Map<Integer,Long> tmp_time = new HashMap<>();
                tmp_time.put(Integer.valueOf(reason), ts);
                time_reason.put(thread,tmp_time);

                int vcpuIDquark = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);

                quark = ss.getQuarkRelativeAndAdd(vcpuIDquark, "thread_in");//$NON-NLS-1$
                value = ss.queryOngoingState(quark);
                int thread_in = value.isNull() ? 0 : value.unboxInt();

                int quarkVM = ss.getQuarkAbsoluteAndAdd("vmName"); //$NON-NLS-1$
                int vm_thread_quark = ss.getQuarkRelativeAndAdd(quarkVM, "testU1"); //$NON-NLS-1$
                vm_thread_quark = ss.getQuarkRelativeAndAdd(vm_thread_quark, Integer.toString(thread_in)); //$NON-NLS-1$
                vm_thread_quark = ss.getQuarkRelativeAndAdd(vm_thread_quark,Attributes.STATUS);
                value = TmfStateValue.newValueInt(StateValues.PROCESS_STATUS_ROOT);
                ss.modifyAttribute(ts, value, vm_thread_quark);

                int statusQuark = ss.getQuarkRelativeAndAdd(vcpuIDquark, Attributes.STATUS); //$NON-NLS-1$
                int qeuarkExitQemuvCPU = ss.getQuarkRelativeAndAdd(vcpuIDquark, "exit_reason"); //$NON-NLS-1$
                value = StateValues.CPU_STATUS_VMX_ROOT_VALUE;
                ss.modifyAttribute(ts, value, statusQuark);

                if (reason == 12){
                    if (nestedVM.containsKey(thread)){
                        int vmNameQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU,"nestedVM");//$NON-NLS-1$
                        int nestedVMQuark = ss.getQuarkRelativeAndAdd(vmNameQuark,nestedVM.get(thread).VM.toString() ); //$NON-NLS-1$

                        int nestedVMCPUQuark = ss.getQuarkRelativeAndAdd(nestedVMQuark,vcpu_id);//$NON-NLS-1$
                        int nestedVMStatusQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,Attributes.STATUS);//$NON-NLS-1$
                        value = StateValues.CPU_STATUS_IDLE_VALUE;
                        ss.modifyAttribute(ts, value, nestedVMStatusQuark);
                    }
                    //int vcpuIDquark = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);
                    int threadQuark = ss.getQuarkRelativeAndAdd(vcpuIDquark, "thread"); //$NON-NLS-1$
                    value = TmfStateValue.newValueInt(0);
                    ss.modifyAttribute(ts, value, threadQuark);

                    // Reduced number of threads running for a Process
                    threadPTID = getVMPTID(thread);
                    if (threadPTID==0){
                        break;
                    }
                    currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                    vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                    int vCPUIDquark = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);//$NON-NLS-1$
                    quark = ss.getQuarkRelativeAndAdd(vCPUIDquark, "process_in");//$NON-NLS-1$
                    value = ss.queryOngoingState(quark);

                    Long prevCR3_Process = value.isNull() ? 0 : value.unboxLong();
                    value = TmfStateValue.newValueLong(0);
                    ss.modifyAttribute(ts, value, quark);
                    if (prevCR3_Process>0){
                        int vProcesses = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vProcesses"); //$NON-NLS-1$
                        quark = ss.getQuarkRelativeAndAdd(vProcesses, prevCR3_Process.toString());
                        value = ss.queryOngoingState(quark);
                        int numberOfThreads = value.isNull() ? 0 : value.unboxInt()-1;
                        value = TmfStateValue.newValueInt(numberOfThreads);
                        ss.modifyAttribute(ts, value,quark);
                    }
                }
                value = TmfStateValue.newValueInt(reason);
                ss.modifyAttribute(ts, value, quarkExit);
                ss.modifyAttribute(ts, value, qeuarkExitQemuvCPU);

                value = StateValues.CPU_STATUS_VMX_ROOT_VALUE;
                quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                ss.modifyAttribute(ts, value, quark);
                if (prevCR3.containsKey(thread)&&nestedVMCR3.containsKey(prevCR3.get(thread))&&nestedVM.containsKey(thread)&&nestedVM.get(thread).lastExit.reason!=12&&nestedVM.get(thread).lastExit.reason!=24){


                    int vmNameQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU,"nestedVM");//$NON-NLS-1$

                    if (nestedVM.containsKey(thread)&&nestedVM.get(thread).process>0) {
                        int nestedVMQuark = ss.getQuarkRelativeAndAdd(vmNameQuark,nestedVM.get(thread).VM.toString() ); //$NON-NLS-1$

                        int nestedVMCPUQuark = ss.getQuarkRelativeAndAdd(nestedVMQuark,vcpu_id);//$NON-NLS-1$
                        int nestedVMAppQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,"process");
                        value = TmfStateValue.newValueLong(nestedVM.get(thread).process);
                        ss.modifyAttribute(ts, value, nestedVMAppQuark);
                        int nestedVMStatusQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,Attributes.STATUS);//$NON-NLS-1$
                        value = StateValues.CPU_STATUS_VMX_ROOT_VALUE;
                        ss.modifyAttribute(ts, value, nestedVMStatusQuark);
                    }
                }else if (prevCR3.containsKey(thread)&&nestedVMCR3.containsKey(prevCR3.get(thread))&&nestedVM.containsKey(thread)&&nestedVM.get(thread).lastExit.reason!=12&&reason==24){

                }
                else if (prevCR3.containsKey(thread)&&nestedVMCR3.containsKey(prevCR3.get(thread))&&nestedVM.containsKey(thread)&&nestedVM.get(thread).lastExit.reason==12){


                    int vmNameQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU,"nestedVM");//$NON-NLS-1$

                    if (nestedVM.containsKey(thread)&&nestedVM.get(thread).process>0) {
                        int nestedVMQuark = ss.getQuarkRelativeAndAdd(vmNameQuark,nestedVM.get(thread).VM.toString() ); //$NON-NLS-1$

                        int nestedVMCPUQuark = ss.getQuarkRelativeAndAdd(nestedVMQuark,vcpu_id);//$NON-NLS-1$
                        int nestedVMAppQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,"process"); //$NON-NLS-1$
                        value = TmfStateValue.newValueLong(nestedVM.get(thread).process);
                        ss.modifyAttribute(ts, value, nestedVMAppQuark);
                        int nestedVMStatusQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,Attributes.STATUS);//$NON-NLS-1$
                        value = StateValues.CPU_STATUS_IDLE_VALUE;
                        ss.modifyAttribute(ts, value, nestedVMStatusQuark);
                    }
                }
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
                ioClass tmpioClass = new ioClass(isWrite,numberOfSector,ts);
                Map<String,ioClass> tmpIO = new HashMap<>();
                if (threadPool.containsKey(thread)){
                    tmpIO = threadPool.get(thread);
                } else {
                    return;
                }
                long timeStampIO;
                if (tmpIO.containsKey(reqNumber)){
                    ioClass tmp= tmpIO.get(reqNumber);
                    timeStampIO = tmp.ts;
                } else {
                    return;
                }
                tmpioClass.ts = timeStampIO;
                tmpIO.put(reqNumber, tmpioClass);
                threadPool.put(thread, tmpIO);



                if ( isWrite == 0) {
                    int rQuark = ss.getQuarkRelativeAndAdd(currentThreadIO, "read"); //$NON-NLS-1$

                    quark = ss.getQuarkRelativeAndAdd(rQuark, "numberOfSubmited"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    int numberOfSubmited = value.isNull() ? 0 : value.unboxInt();
                    numberOfSubmited++;
                    value = TmfStateValue.newValueInt(numberOfSubmited);
                    ss.modifyAttribute(timeStampIO, value, quark);


                    quark = ss.getQuarkRelativeAndAdd(rQuark,"transfer");//$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    int valueWrite = value.isNull() ? 0 : value.unboxInt();
                    valueWrite += numberOfSector;
                    value = TmfStateValue.newValueInt(valueWrite);
                    ss.modifyAttribute(timeStampIO, value, quark);
                    if (diskUsage.containsKey(thread)){
                        Integer usage = diskUsage.get(thread);
                        usage += numberOfSector;
                        diskUsage.put(thread, usage);
                    } else {
                        diskUsage.put(thread, numberOfSector);
                    }

                    quark = ss.getQuarkRelativeAndAdd(rQuark, Attributes.STATUS );//$NON-NLS-1$
                    value = StateValues.IO_READ_QEMU_VALUE;
                    ss.modifyAttribute(timeStampIO, value, quark);

                } else if (isWrite == 1){

                    int wQuark = ss.getQuarkRelativeAndAdd(currentThreadIO, "write"); //$NON-NLS-1$

                    quark = ss.getQuarkRelativeAndAdd(wQuark, "numberOfSubmited"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    int numberOfSubmited = value.isNull() ? 0 : value.unboxInt();
                    numberOfSubmited++;
                    value = TmfStateValue.newValueInt(numberOfSubmited);
                    ss.modifyAttribute(timeStampIO, value, quark);

                    quark = ss.getQuarkRelativeAndAdd(wQuark,"transfer");//$NON-NLS-1$

                    value = ss.queryOngoingState(quark);

                    int valueWrite = value.isNull() ? 0 : value.unboxInt();
                    valueWrite += numberOfSector;
                    value = TmfStateValue.newValueInt(valueWrite);
                    ss.modifyAttribute(timeStampIO, value, quark);
                    if (diskUsage.containsKey(thread)){
                        Integer usage = diskUsage.get(thread);
                        usage += numberOfSector;
                        diskUsage.put(thread, usage);
                    } else {
                        diskUsage.put(thread, numberOfSector);
                    }


                    quark = ss.getQuarkRelativeAndAdd(wQuark, Attributes.STATUS );//$NON-NLS-1$
                    value = StateValues.IO_WRITE_QEMU_VALUE;
                    ss.modifyAttribute(timeStampIO, value, quark);
                } else {
                    quark = ss.getQuarkRelativeAndAdd(currentThreadIO, "other" );//$NON-NLS-1$
                    quark = ss.getQuarkRelativeAndAdd(quark, Attributes.STATUS );//$NON-NLS-1$
                    value = StateValues.IO_OTHER_VALUE;
                    ss.modifyAttribute(timeStampIO, value, quark);
                }

                for ( Entry<Integer, Map<Integer, Integer>> threadDisk:diskCon.entrySet()){
                    Map<Integer,Integer> tmp = diskCon.get(threadDisk.getKey());
                    if (tmp.containsKey(thread)){
                        Integer usageValue = tmp.get(thread);
                        usageValue += numberOfSector;
                        tmp.put(thread, usageValue);

                    } else {
                        tmp.put(thread, numberOfSector);
                    }
                    /*
                    for (Entry<Integer,Integer> otherThreadDisk:threadDisk.getValue().entrySet()){
                        if (thread == otherThreadDisk.getKey()){
                            Integer usageValue = tmp.get(otherThreadDisk.getKey());
                            usageValue += numberOfSector;
                            tmp.put(otherThreadDisk.getKey(), usageValue);
                        }
                    } // end for 1
                     */
                    diskCon.put(threadDisk.getKey(), tmp);
                } //end for 2

            } // end IO_info switch

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
                int threadPTID = getVMPTID(thread);
                if (threadPTID==0){
                    break;
                }
                /*                int currentThreadIO = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(thread));

                quark = ss.getQuarkRelativeAndAdd(currentThreadIO, Attributes.PPID);
                value = ss.queryOngoingState(quark);
                Integer PTID = value.isNull() ? 0 : value.unboxInt();
                int newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                int threadPTID = thread;
                if (!tidToPtid.containsKey(thread)) {

                    while (PTID != 1 && PTID !=0) {
                        quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);
                        newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                        value = ss.queryOngoingState(quark);
                        threadPTID = PTID;
                        PTID = value.isNull() ? 0 : value.unboxInt();

                    }
                    if (PTID == 0) {
                        break;
                    }
                    tidToPtid.put(thread, threadPTID);
                } else {
                    threadPTID = tidToPtid.get(thread);
                }*/
                //                int currentThreadIO = ss.getQuarkRelativeAndAdd(getNodeIO(ss), String.valueOf(threadPTID));
                //

                ITmfEventField content = event.getContent();
                String reqNumber = content.getField("req").getValue().toString(); //$NON-NLS-1$

                ioClass newReq = new ioClass(2,0,ts);

                Map<String,ioClass> tmpIO = new HashMap<>();
                if (threadPool.containsKey(threadPTID)){
                    tmpIO = threadPool.get(threadPTID);
                }
                tmpIO.put(reqNumber, newReq);
                threadPool.put(threadPTID, tmpIO);
                Map<Integer,Integer> tmpCon = new HashMap<>();
                if (diskUsage.size()>0){
                    for ( Entry<Integer,Integer> entry:diskUsage.entrySet()){
                        tmpCon.put(entry.getKey(), entry.getValue());
                    }
                }
                if (tmpCon.size()>0){
                    diskCon.put(threadPTID,tmpCon);
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
                int threadPTID = getVMPTID(thread);
                if (threadPTID==0){
                    break;
                }
                int currentThreadIO = ss.getQuarkRelativeAndAdd(getNodeIO(ss), String.valueOf(threadPTID));




                ITmfEventField content = event.getContent();
                String reqNumber = content.getField("req").getValue().toString(); //$NON-NLS-1$

                Map<String,ioClass> tmpIO = new HashMap<>();
                if (threadPool.containsKey(threadPTID)){
                    tmpIO = threadPool.get(threadPTID);
                }
                ioClass tmpioClass = new ioClass();
                if (tmpIO.containsKey(reqNumber)){
                    tmpioClass = tmpIO.get(reqNumber);
                } else {
                    return;
                }
                int valueWrite = -1;
                int valueRead = -1;
                int isWrite = tmpioClass.isWrite;
                int valueReqWrite = tmpioClass.numberOfSector;
                long timeStampStart = tmpioClass.ts;

                if ( isWrite == 1) {
                    int wQuark = ss.getQuarkRelativeAndAdd(currentThreadIO, "write"); //$NON-NLS-1$
                    quark = ss.getQuarkRelativeAndAdd(wQuark, "latency"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    Long latency = value.isNull() ? 0 : value.unboxLong();
                    latency += ts - timeStampStart;
                    value = TmfStateValue.newValueLong(latency);
                    ss.modifyAttribute(ts, value, quark);

                    quark = ss.getQuarkRelativeAndAdd(wQuark, "numberOfSubmited"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    int numberOfSubmited = value.isNull() ? 0 : value.unboxInt();
                    numberOfSubmited--;
                    value = TmfStateValue.newValueInt(numberOfSubmited);
                    ss.modifyAttribute(ts, value, quark);
                    quark = ss.getQuarkRelativeAndAdd(wQuark,"totalTransfer"); //$NON-NLS-1$
                    //value = ss.queryOngoingState(quark);

                    if (diskWriteTotal.containsKey(threadPTID)){
                        diskWriteTotal.put(threadPTID, diskWriteTotal.get(threadPTID)+ valueReqWrite);
                    } else {
                        diskWriteTotal.put(threadPTID, valueReqWrite);
                    }
                    //valueWrite = value.isNull() ? 0 : value.unboxInt();
                    //valueWrite += valueReqWrite;
                    valueWrite = diskWriteTotal.get(threadPTID);
                    value = TmfStateValue.newValueInt(valueWrite);
                    ss.modifyAttribute(ts, value, quark);

                    quark = ss.getQuarkRelativeAndAdd(wQuark,"transfer");//$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    valueWrite = value.isNull() ? 0 : value.unboxInt();
                    valueWrite -= valueReqWrite;
                    value = TmfStateValue.newValueInt(valueWrite);
                    ss.modifyAttribute(ts, value, quark);
                    if (diskUsage.containsKey(threadPTID)) {
                        Integer usage = diskUsage.get(threadPTID);
                        usage -= valueReqWrite;
                        diskUsage.put(threadPTID, usage);
                    } else {
                        diskUsage.put(threadPTID,0);
                    }

                    quark = ss.getQuarkRelativeAndAdd(wQuark, Attributes.STATUS );//$NON-NLS-1$
                    if (valueWrite == 0){
                        value = StateValues.IO_STATUS_IDLE_VALUE;
                        ss.modifyAttribute(ts, value, quark);
                    }
                } else if (isWrite == 0 ){

                    int rQuark = ss.getQuarkRelativeAndAdd(currentThreadIO, "read"); //$NON-NLS-1$

                    quark = ss.getQuarkRelativeAndAdd(rQuark, "latency"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    Long latency = value.isNull() ? 0 : value.unboxLong();
                    latency += ts - timeStampStart;
                    value = TmfStateValue.newValueLong(latency);
                    ss.modifyAttribute(ts, value, quark);



                    quark = ss.getQuarkRelativeAndAdd(rQuark, "numberOfSubmited"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    int numberOfSubmited = value.isNull() ? 0 : value.unboxInt();
                    numberOfSubmited--;
                    value = TmfStateValue.newValueInt(numberOfSubmited);
                    ss.modifyAttribute(ts, value, quark);
                    quark = ss.getQuarkRelativeAndAdd(rQuark,"totalTransfer"); //$NON-NLS-1$
                    //value = ss.queryOngoingState(quark);
                    //valueRead = value.isNull() ? 0 : value.unboxInt();
                    if (diskReadTotal.containsKey(threadPTID)){
                        diskReadTotal.put(threadPTID, diskReadTotal.get(threadPTID)+ valueReqWrite);
                    } else {
                        diskReadTotal.put(threadPTID, valueReqWrite);
                    }
                    //valueRead += valueReqWrite;
                    valueRead = diskReadTotal.get(threadPTID);
                    value = TmfStateValue.newValueInt(valueRead);
                    ss.modifyAttribute(ts, value, quark);

                    quark = ss.getQuarkRelativeAndAdd(rQuark,"transfer");//$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    valueRead = value.isNull() ? 0 : value.unboxInt();
                    valueRead -= valueReqWrite;
                    value = TmfStateValue.newValueInt(valueRead);
                    ss.modifyAttribute(ts, value, quark);
                    if (diskUsage.containsKey(threadPTID)) {
                        Integer usage = diskUsage.get(threadPTID);
                        usage -= valueReqWrite;
                        diskUsage.put(threadPTID, usage);
                    } else {
                        diskUsage.put(threadPTID,0);
                    }

                    quark = ss.getQuarkRelativeAndAdd(rQuark, Attributes.STATUS );//$NON-NLS-1$
                    if (valueRead == 0){
                        value = StateValues.IO_STATUS_IDLE_VALUE;
                        ss.modifyAttribute(ts, value, quark);
                    }
                }
                if ((valueRead == 0 || valueWrite == 0)){
                    if (diskConTotal.containsKey(threadPTID) && diskCon.containsKey(threadPTID)){
                        Map<Integer,Integer> tmpDiskTotal =  diskConTotal.get(threadPTID);
                        Map<Integer,Integer> tmpDisk = diskCon.get(threadPTID);
                        for (Entry<Integer,Integer> tmp:tmpDisk.entrySet()){
                            if (tmpDiskTotal.containsKey(tmp.getKey())){
                                Integer totalUsage = tmpDisk.get(tmp.getKey())+tmpDiskTotal.get(tmp.getKey());
                                tmpDiskTotal.put(tmp.getKey(), totalUsage);
                            } else {
                                tmpDiskTotal.put(tmp.getKey(),tmpDisk.get(tmp.getKey()));
                            }
                        }
                    } else if (diskCon.containsKey(threadPTID)) {
                        diskConTotal.put(threadPTID, diskCon.get(threadPTID));
                    } else {

                        break;}


                    if (diskCon.containsKey(threadPTID)){
                        diskCon.remove(threadPTID);
                    }

                    if (diskUsage.containsKey(threadPTID)){
                        diskUsage.remove(threadPTID);
                    }
                    Map<Integer,Integer> tmpDiskTotal =  diskConTotal.get(threadPTID);
                    for (Entry<Integer,Integer> tmp:tmpDiskTotal.entrySet()){
                        if (threadPTID != tmp.getKey()){
                            Integer Usage = tmpDiskTotal.get(tmp.getKey());
                            quark = ss.getQuarkRelativeAndAdd(currentThreadIO, "VM"); //$NON-NLS-1$
                            quark = ss.getQuarkRelativeAndAdd(quark, tmp.getKey().toString()); //$NON-NLS-1$
                            value = TmfStateValue.newValueInt(Usage);
                            ss.modifyAttribute(ts, value, quark);
                        }
                    }
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
                boolean vcpu_find = false ;
                ITmfEventField content = event.getContent();
                Integer prevTid = ((Long) content.getField(fLayout.fieldPrevTid()).getValue()).intValue();
                Long prevState = (Long) content.getField(fLayout.fieldPrevState()).getValue();
                String nextProcessName = (String) content.getField(fLayout.fieldNextComm()).getValue();
                String prevProcessName = (String) content.getField("prev_comm").getValue(); //$NON-NLS-1$
                Integer nextTid = ((Long) content.getField(fLayout.fieldNextTid()).getValue()).intValue();
                Integer nextPrio = ((Long) content.getField(fLayout.fieldNextPrio()).getValue()).intValue();
                Integer formerThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), prevTid.toString());
                Integer newCurrentThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), nextTid.toString());

                if (nextProcessName.equals("mysqld") && allsqlThread.containsKey(nextTid)){
                    Map <String,Long> sqlThread = allsqlThread.get(nextTid);
                    vcpu2vtid.put(cpu, nextTid);
                    sql_pcpu2vcpu.put(sql_vcpu2pcpu.get(cpu),cpu);
                    sqlThread.put("on-cpu_start", ts);
                    if (sqlThread.get("status") == 5L ){
                        sqlThread.put("non-root_start",ts);
                    }
                    //System.out.println(ts+"\t"+event.getName() + "\t" + event.getContent());

                    Long sum_wait = sqlThread.get("wait");
                    sqlThread.put("wait", sum_wait+ts-sqlThread.get("wait_start"));
                    sqlThread.put("status", 4L);
                    allsqlThread.put(nextTid, sqlThread);
                }
                if (prevProcessName.equals("mysqld") && allsqlThread.containsKey(prevTid)) {
                    Map <String,Long> sqlThread = allsqlThread.get(prevTid);
                    sqlThread.put("wait_start",ts) ;
                    //System.out.println(ts+"\t"+event.getName() + "\t" + event.getContent());

                    Long sum_nonroot = sqlThread.get("vmx_nonroot");
                    sqlThread.put("vmx_nonroot", sum_nonroot + ts-sqlThread.get("non-root_start"));
                    sqlThread.put("status", 5L);

                    if (sqlThread.get("on-cpu_start")> 0){
                        Long sum_on_cpu = sqlThread.get("on-cpu");
                        sqlThread.put("on-cpu",sum_on_cpu+ts-sqlThread.get("on-cpu_start")) ;
                    }

                    allsqlThread.put(prevTid, sqlThread);
                }

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
                if (nextTid > 0) {
                    // add all the threads that preempt vcpu

                    /* Check if the entering process is in kernel or user mode */
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.SYSTEM_CALL);
                    if (vcpu_find) {
                        value = StateValues.CPU_STATUS_VMX_ROOT_VALUE;

                    } else {
                        value = StateValues.CPU_STATUS_RUN_USERMODE_VALUE;
                    }
                    if (ss.queryOngoingState(quark).isNull()) {

                    } else {
                        value = StateValues.CPU_STATUS_RUN_SYSCALL_VALUE;
                    }
                } else {
                    value = StateValues.CPU_STATUS_IDLE_VALUE;
                }
                quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
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
                if (prevProcessName.equals("qemu-system-x86")||prevProcessName.equals("qemu-kvm")||prevProcessName.equals("qemu:ubuntu")){ //$NON-NLS-1$
                    int threadPTID = 0;
                    if (!tidToPtid.containsKey(prevTid) ) {
                        threadPTID = getVMPTID(prevTid);
                        if (threadPTID==0){
                            break;
                        }
                        tidToPtid.put(prevTid, threadPTID);
                    }
                    else {
                        threadPTID = tidToPtid.get(prevTid);
                    }

                    /*                    quark = ss.getQuarkRelativeAndAdd(formerThreadNode, Attributes.PPID);
                    value = ss.queryOngoingState(quark);
                    Integer PTID = value.isNull() ? 0 : value.unboxInt();
                    int currentThreadCPU = 0;
                    int newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                    Integer threadPTID = prevTid;
                    if (!tidToPtid.containsKey(prevTid) ) {
                        while (PTID != 1 && PTID !=0) {
                            quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);
                            newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                            value = ss.queryOngoingState(quark);
                            threadPTID = PTID;
                            PTID = value.isNull() ? 0 : value.unboxInt();
                        }
                        if (PTID == 0) {
                            break;
                        }
                        tidToPtid.put(prevTid, threadPTID);
                    } else {
                        threadPTID = tidToPtid.get(prevTid);
                    }*/
                    quark = ss.getQuarkRelativeAndAdd(formerThreadNode, "vcpu"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                    String vcpu_id = value.toString();
                    if (!value.isNull()){
                        // calculating Usage for VMs
                        Integer currentUsageThread = ss.getQuarkRelativeAndAdd(getNodeDelayQemu(ss), "usage"); //$NON-NLS-1$
                        if (VMToUsage.containsKey(threadPTID)) {
                            Map<Integer,Long> usageTmp = VMToUsage.get(threadPTID);
                            if (usageTmp.containsKey(prevTid)){
                                quark = ss.getQuarkRelativeAndAdd(currentUsageThread, String.valueOf(threadPTID));
                                value = ss.queryOngoingState(quark);
                                Long prevUsage = value.isNull() ? 0 : value.unboxLong();
                                prevUsage += ts- usageTmp.get(prevTid);
                                value = TmfStateValue.newValueLong(prevUsage);
                                ss.modifyAttribute(ts, value, quark);
                                usageTmp.remove(prevTid);
                                VMToUsage.put(threadPTID, usageTmp);
                            }
                        }
                        // *********************************************************
                        int quarktmp = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                        int quarkvCPU= ss.getQuarkRelativeAndAdd(quarktmp, vcpu_id); //$NON-NLS-1$

                        quark = ss.getQuarkRelativeAndAdd(quarkvCPU, "thread"); //$NON-NLS-1$
                        value = TmfStateValue.newValueInt(prevTid);
                        ss.modifyAttribute(ts, value , quark);
                        quark = ss.getQuarkRelativeAndAdd(quarkvCPU, "pCPU"); //$NON-NLS-1$
                        value = TmfStateValue.newValueInt(-1);
                        ss.modifyAttribute(ts, value , quark);
                        quark = ss.getQuarkRelativeAndAdd(quarkvCPU, "thread_in"); //$NON-NLS-1$
                        value = ss.queryOngoingState(quark);
                        int thread_in = value.isNull() ? 0 : value.unboxInt();
                        int quarkVM = ss.getQuarkAbsoluteAndAdd("vmName"); //$NON-NLS-1$
                        int vm_thread_quark = ss.getQuarkRelativeAndAdd(quarkVM, "testU1"); //$NON-NLS-1$
                        vm_thread_quark = ss.getQuarkRelativeAndAdd(vm_thread_quark, Integer.toString(thread_in)); //$NON-NLS-1$
                        vm_thread_quark = ss.getQuarkRelativeAndAdd(vm_thread_quark,Attributes.STATUS);
                        value = TmfStateValue.newValueInt(StateValues.PROCESS_STATUS_WAIT_BLOCKED);
                        ss.modifyAttribute(ts, value, vm_thread_quark);

                        quark = ss.getQuarkRelativeAndAdd(formerThreadNode, "exit_reason"); //$NON-NLS-1$
                        value = ss.queryOngoingState(quark);
                        if (value.unboxInt() != 12 && !value.isNull()) {
                            if (cpuTopreemption.containsKey(cpu)){
                                Map<Integer,Long> tmpCPU = cpuTopreemption.get(cpu);

                                tmpCPU.put(prevTid, ts);

                                cpuTopreemption.put(cpu, tmpCPU);
                            } else {
                                Map<Integer,Long> tmpCPU = new HashMap<>();
                                tmpCPU.put(prevTid, ts);

                                cpuTopreemption.put(cpu, tmpCPU);
                            }
                            threadPreempted.put(prevTid, cpu);
                            quark = ss.getQuarkRelativeAndAdd(quarkvCPU, Attributes.STATUS ); //$NON-NLS-1$
                            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                            value = StateValues.L0_PREEMPTED_VALUE;
                            ss.modifyAttribute(ts, value, quark);
                            quark = ss.getQuarkRelativeAndAdd(formerThreadNode, Attributes.STATUS);
                            ss.modifyAttribute(ts, value, quark);
                            value = TmfStateValue.newValueInt(StateValues.PROCESS_STATUS_PREEMPTED);
                            ss.modifyAttribute(ts, value, vm_thread_quark);
                            if (prevCR3.containsKey(thread)&&isNestedVM.containsKey(thread)&& isNestedVM.get(thread)==1&&(nestedVM.containsKey(thread))){



                                int vmNameQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU,"nestedVM");//$NON-NLS-1$

                                if (nestedVM.containsKey(thread)&&nestedVM.get(thread).process>0) {
                                    int nestedVMQuark = ss.getQuarkRelativeAndAdd(vmNameQuark,nestedVM.get(thread).VM.toString() ); //$NON-NLS-1$

                                    int nestedVMCPUQuark = ss.getQuarkRelativeAndAdd(nestedVMQuark,vcpu_id);//$NON-NLS-1$
                                    int nestedVMAppQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,"process");
                                    value = TmfStateValue.newValueLong(nestedVM.get(thread).process);
                                    ss.modifyAttribute(ts, value, nestedVMAppQuark);
                                    int nestedVMStatusQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,Attributes.STATUS);//$NON-NLS-1$
                                    value = StateValues.L0_PREEMPTED_VALUE;
                                    //ss.modifyAttribute(nestedVM.get(thread).lastExit.ts, value, nestedVMStatusQuark);
                                    ss.modifyAttribute(ts, value, nestedVMStatusQuark);
                                }
                            }

                        }
                        else {
                            quark = ss.getQuarkRelativeAndAdd(quarkvCPU, Attributes.STATUS ); //$NON-NLS-1$
                            value = TmfStateValue.newValueInt(0);
                            ss.modifyAttribute(ts, value, quark);
                            ss.modifyAttribute(ts, value, quarkvCPU);
                            if (nestedVM.containsKey(thread)&&nestedVM.get(thread).process>0) {
                                int vmNameQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU,"nestedVM");//$NON-NLS-1$
                                int nestedVMQuark = ss.getQuarkRelativeAndAdd(vmNameQuark,nestedVM.get(thread).VM.toString() ); //$NON-NLS-1$

                                int nestedVMCPUQuark = ss.getQuarkRelativeAndAdd(nestedVMQuark,vcpu_id);//$NON-NLS-1$
                                int nestedVMStatusQuark = ss.getQuarkRelativeAndAdd(nestedVMCPUQuark,Attributes.STATUS);//$NON-NLS-1$
                                value = StateValues.CPU_STATUS_IDLE_VALUE;
                                ss.modifyAttribute(ts, value, nestedVMStatusQuark);
                            }
                        }
                        Integer currentDelayThread = ss.getQuarkRelativeAndAdd(getNodeDelayQemu(ss), "waiting"); //$NON-NLS-1$

                        if (waitingCPU.containsKey(cpu)){
                            Map<Integer, Long> tmp = waitingCPU.get(cpu);
                            for ( Entry<Integer,Long> entry:waitingCPU.get(cpu).entrySet())
                            {
                                Long timeStamp = tmp.get(entry.getKey());
                                if (tidToPtid.containsKey(entry.getKey()) && tidToPtid.containsKey(prevTid)){
                                    currentDelayThread = ss.getQuarkRelativeAndAdd(currentDelayThread, tidToPtid.get(entry.getKey()).toString());
                                    quark = ss.getQuarkRelativeAndAdd(currentDelayThread, "VM"); //$NON-NLS-1$
                                    quark = ss.getQuarkRelativeAndAdd(quark, tidToPtid.get(prevTid).toString()); //$NON-NLS-1$
                                    Long delay = ts - timeStamp;
                                    value = ss.queryOngoingState(quark);
                                    Long accWait = value.isNull() ? 0 : value.unboxLong();
                                    value = TmfStateValue.newValueLong(delay+accWait);
                                    ss.modifyAttribute(ts, value, quark);

                                    quark = ss.getQuarkRelativeAndAdd(quark, prevTid.toString() );
                                    value = ss.queryOngoingState(quark);
                                    accWait = value.isNull() ? 0 : value.unboxLong();
                                    value = TmfStateValue.newValueLong(delay+accWait);
                                    ss.modifyAttribute(ts, value, quark);
                                }

                                tmp.put(entry.getKey(), ts);
                            }
                            waitingCPU.put(cpu, tmp);
                        }
                    }
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "ValueCPU"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);


                    int valueCPU = value.isNull() ? 0 : value.unboxInt();
                    valueCPU--;

                    if (valueCPU > 0) {
                        value = TmfStateValue.newValueInt(valueCPU);
                        ss.modifyAttribute(ts, value, quark);
                        quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, Attributes.STATUS ); //$NON-NLS-1$
                        switch (valueCPU) {
                        case 1:{
                            value = StateValues.CPU_QEMU_BUSY_VALUE_ONE;
                            break;
                        }
                        case 2:{
                            value = StateValues.CPU_QEMU_BUSY_VALUE_TWO;
                            break;
                        }
                        case 3:{
                            value = StateValues.CPU_QEMU_BUSY_VALUE_THREE;
                            break;
                        }
                        case 4:{
                            value = StateValues.CPU_QEMU_BUSY_VALUE_FOUR;
                            break;
                        }
                        default:
                            value = StateValues.CPU_QEMU_BUSY_VALUE_MANY;
                            break;
                        }
                        ss.modifyAttribute(ts, value, quark);
                    } else {
                        value = TmfStateValue.newValueInt(0);
                        ss.modifyAttribute(ts, value, quark);
                        quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, Attributes.STATUS ); //$NON-NLS-1$
                        value = StateValues.CPU_QEMU_IDLE_VALUE;
                        ss.modifyAttribute(ts, value, quark);
                    }
                } else if (prevProcessName.contains("vhost")) { //$NON-NLS-1$
                    final int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeNetQemu(ss), String.valueOf(prevTid));
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, Attributes.STATUS ); //$NON-NLS-1$
                    value = StateValues.NET_QEMU_IDLE_VALUE;
                    ss.modifyAttribute(ts, value, quark);
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "Netif" );//$NON-NLS-1$
                    if (netIf.containsKey(prevTid)) {
                        value = TmfStateValue.newValueInt(netIf.get(prevTid));
                    } else {
                        break;
                    }

                    ss.modifyAttribute(netTs.get(prevTid), value, quark);
                    value = TmfStateValue.newValueInt(0);
                    ss.modifyAttribute(ts, value, quark);
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "Netdev" );//$NON-NLS-1$
                    value = TmfStateValue.newValueInt(netDev.get(prevTid));
                    ss.modifyAttribute(netTs.get(prevTid), value, quark);
                    value = TmfStateValue.newValueInt(0);
                    ss.modifyAttribute(ts, value, quark);

                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "tNetdev" );//$NON-NLS-1$
                    value = ss.queryOngoingState(quark);

                    Long tnetDev = value.isNull() ? netDev.get(prevTid).longValue() : value.unboxLong()+netDev.get(prevTid).longValue();
                    value = TmfStateValue.newValueLong(tnetDev);
                    ss.modifyAttribute(ts, value, quark);


                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "tNetif" );//$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    Long tNetif = value.isNull() ? netIf.get(prevTid).longValue() : value.unboxLong()+netIf.get(prevTid).longValue();
                    value = TmfStateValue.newValueLong(tNetif);
                    ss.modifyAttribute(ts, value, quark);


                }
                if (nextProcessName.equals("qemu-system-x86")||nextProcessName.equals("qemu-kvm")||nextProcessName.equals("qemu:ubuntu")){ //$NON-NLS-1$
                    Integer currentDelayThread = ss.getQuarkRelativeAndAdd(getNodeDelayQemu(ss), "preempting"); //$NON-NLS-1$
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, "exit_reason"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    if (value.unboxInt() != 12 && !value.isNull()) {
                        if (threadPreempted.containsKey(nextTid)){
                            if (cpu != threadPreempted.get(nextTid)){
                                Integer formerCPU = threadPreempted.get(nextTid);
                                if (cpuTopreemption.containsKey(formerCPU)){
                                    Map<Integer,Long> tmpCPU = cpuTopreemption.get(formerCPU);
                                    if (tmpCPU.containsKey(nextTid)){
                                        quark = ss.getQuarkRelativeAndAdd(currentDelayThread, tidToPtid.get(nextTid).toString());
                                        value = ss.queryOngoingState(quark);
                                        Long accPreempt = value.isNull() ? 0 : value.unboxLong();
                                        value = TmfStateValue.newValueLong(ts-tmpCPU.get(nextTid)+accPreempt);
                                        ss.modifyAttribute(ts, value, quark);

                                        int currentCPUNodeTmp = ss.getQuarkRelativeAndAdd(getNodeCPUs(ss), formerCPU.toString());
                                        /*
                                         * Shortcut for the "current thread" attribute node. It requires
                                         * querying the current CPU's current thread.
                                         */
                                        quark = ss.getQuarkRelativeAndAdd(currentCPUNodeTmp, Attributes.CURRENT_THREAD);
                                        value = ss.queryOngoingState(quark);
                                        Integer prevRunningThread = value.isNull() ? -1 : value.unboxInt();

                                        if (tidToPtid.containsKey(prevRunningThread)){
                                            quark  = ss.getQuarkRelativeAndAdd(quark,"VM"); //$NON-NLS-1$
                                            quark = ss.getQuarkRelativeAndAdd(quark,tidToPtid.get(prevRunningThread).toString());
                                        }else {
                                            quark  = ss.getQuarkRelativeAndAdd(quark,"Other"); //$NON-NLS-1$
                                            quark = ss.getQuarkRelativeAndAdd(quark,prevRunningThread.toString());
                                        }
                                        value = ss.queryOngoingState(quark);
                                        Long accPreemptVM = value.isNull() ? 0 : value.unboxLong();
                                        value = TmfStateValue.newValueLong(ts-tmpCPU.get(nextTid)+accPreemptVM);
                                        ss.modifyAttribute(ts, value, quark);

                                        quark = ss.getQuarkRelativeAndAdd(quark, prevRunningThread.toString());
                                        value = ss.queryOngoingState(quark);
                                        Long accPreemptT = value.isNull() ? 0 : value.unboxLong();
                                        value = TmfStateValue.newValueLong(ts-tmpCPU.get(nextTid)+accPreemptT);
                                        ss.modifyAttribute(ts, value, quark);
                                        tmpCPU.remove(nextTid);
                                        cpuTopreemption.put(formerCPU, tmpCPU);
                                    }

                                }
                            }
                        }
                        if (cpuTopreemption.containsKey(cpu)){
                            Map<Integer,Long> tmpCPU = cpuTopreemption.get(cpu);
                            Map<Integer,Long> tmp = new HashMap<>();
                            //preemptingThread.remove(cpu);
                            if ( tmpCPU.containsKey(nextTid)){
                                Map<Integer,Long> tmpCPUt = new HashMap<>();
                                tmpCPUt.putAll(tmpCPU);
                                for (Entry<Integer, Long> entry:tmpCPU.entrySet()){
                                    if (entry.getKey().equals(nextTid)){
                                        quark = ss.getQuarkRelativeAndAdd(currentDelayThread, tidToPtid.get(nextTid).toString());
                                        value = ss.queryOngoingState(quark);
                                        Long accPreempt = value.isNull() ? 0 : value.unboxLong();
                                        value = TmfStateValue.newValueLong(ts-entry.getValue()+accPreempt);
                                        ss.modifyAttribute(ts, value, quark);
                                        if (tidToPtid.containsKey(prevTid)){
                                            quark  = ss.getQuarkRelativeAndAdd(quark,"VM"); //$NON-NLS-1$
                                            quark = ss.getQuarkRelativeAndAdd(quark,tidToPtid.get(prevTid).toString());
                                        }else {
                                            quark  = ss.getQuarkRelativeAndAdd(quark,"Other"); //$NON-NLS-1$
                                            quark = ss.getQuarkRelativeAndAdd(quark,prevTid.toString());
                                        }
                                        value = ss.queryOngoingState(quark);
                                        Long accPreemptVM = value.isNull() ? 0 : value.unboxLong();
                                        value = TmfStateValue.newValueLong(ts-entry.getValue()+accPreemptVM);
                                        ss.modifyAttribute(ts, value, quark);

                                        quark = ss.getQuarkRelativeAndAdd(quark, prevTid.toString());
                                        value = ss.queryOngoingState(quark);
                                        Long accPreemptT = value.isNull() ? 0 : value.unboxLong();
                                        value = TmfStateValue.newValueLong(ts-entry.getValue()+accPreemptT);
                                        ss.modifyAttribute(ts, value, quark);
                                        tmpCPUt.put(entry.getKey(), ts);
                                    }
                                }
                                tmp.put(nextTid, ts);
                                preemptingThread.put(cpu, tmp);
                                tmpCPUt.remove(nextTid);
                                if (tmpCPU.size()>0){
                                    cpuTopreemption.put(cpu, tmpCPUt);
                                } else {
                                    cpuTopreemption.remove(cpu);
                                }
                            }
                        }
                    }
                    int threadPTID = 0;
                    if (!tidToPtid.containsKey(nextTid) ) {
                        threadPTID = getVMPTID(nextTid);
                        if (threadPTID==0){
                            break;
                        }
                        tidToPtid.put(nextTid, threadPTID);
                    }
                    else {
                        threadPTID = tidToPtid.get(nextTid);
                    }

                    /*                   quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.PPID);
                    value = ss.queryOngoingState(quark);
                    Integer PTID = value.isNull() ? 0 : value.unboxInt();
                    int currentThreadCPU = 0;
                    int newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                    int threadPTID = nextTid;
                    if (!tidToPtid.containsKey(nextTid) ) {
                        while (PTID != 1 && PTID !=0) {
                            quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);
                            newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                            value = ss.queryOngoingState(quark);
                            threadPTID = PTID;
                            PTID = value.isNull() ? 0 : value.unboxInt();
                        }
                        if (PTID == 0) {
                            break;
                        }
                        tidToPtid.put(nextTid, threadPTID);
                    } else {
                        threadPTID = tidToPtid.get(nextTid);
                    }*/

                    int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));

                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, "vcpu"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    String vcpu_id = value.toString();


                    if (!value.isNull()){
                        // calculating Usage for VMs
                        if (VMToUsage.containsKey(threadPTID)) {

                            Map<Integer,Long> usageTmp = VMToUsage.get(threadPTID);
                            usageTmp.put(nextTid, ts);
                            VMToUsage.put(threadPTID, usageTmp);
                        } else {
                            Map<Integer,Long> usageTmp = new HashMap<>();
                            usageTmp.put(nextTid, ts);
                            VMToUsage.put(threadPTID, usageTmp);
                        }
                        // *********************************************************


                        int quarktmp = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                        int quarkvCPU= ss.getQuarkRelativeAndAdd(quarktmp, vcpu_id); //$NON-NLS-1$
                        int statusQuark = ss.getQuarkRelativeAndAdd(quarkvCPU,Attributes.STATUS);
                        value = StateValues.CPU_STATUS_VMX_ROOT_VALUE;
                        ss.modifyAttribute(ts, value, statusQuark);
                        quark = ss.getQuarkRelativeAndAdd(quarkvCPU, "thread_in"); //$NON-NLS-1$
                        value = ss.queryOngoingState(quark);
                        int thread_in = value.isNull() ? 0 : value.unboxInt();
                        int quarkVM = ss.getQuarkAbsoluteAndAdd("vmName"); //$NON-NLS-1$
                        int vm_thread_quark = ss.getQuarkRelativeAndAdd(quarkVM, "testU1"); //$NON-NLS-1$
                        vm_thread_quark = ss.getQuarkRelativeAndAdd(vm_thread_quark, Integer.toString(thread_in)); //$NON-NLS-1$
                        vm_thread_quark = ss.getQuarkRelativeAndAdd(vm_thread_quark,Attributes.STATUS);
                        value = TmfStateValue.newValueInt(StateValues.PROCESS_STATUS_ROOT);
                        ss.modifyAttribute(ts, value, vm_thread_quark);



                        value = TmfStateValue.newValueInt(1);
                        ss.modifyAttribute(ts, value, quarkvCPU);
                        quark = ss.getQuarkRelativeAndAdd(quarkvCPU, Attributes.STATUS ); //$NON-NLS-1$
                        value = StateValues.CPU_STATUS_VMX_ROOT_VALUE;
                        ss.modifyAttribute(ts, value, quark);
                        vcpu_find = true;
                        quark = ss.getQuarkRelativeAndAdd(quarkvCPU, "pCPU"); //$NON-NLS-1$
                        value = TmfStateValue.newValueInt(cpu);
                        ss.modifyAttribute(ts, value , quark);
                        if (waitingCPU.containsKey(cpu)){
                            Map<Integer, Long> tmp = waitingCPU.get(cpu);
                            for ( Entry<Integer,Long> entry:waitingCPU.get(cpu).entrySet())
                            {
                                tmp.put(entry.getKey(), ts);
                            }
                            tmp.remove(nextTid);
                            waitingCPU.put(cpu, tmp);
                        }
                    }
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "ValueCPU"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    int valueCPU = value.isNull() ? 0 : value.unboxInt();
                    valueCPU++;
                    value = TmfStateValue.newValueInt(valueCPU);
                    ss.modifyAttribute(ts, value, quark);
                    if (valueCPU > 0) {
                        quark = ss.getQuarkRelativeAndAdd(currentThreadCPU,Attributes.STATUS ); //$NON-NLS-1$
                        switch (valueCPU) {
                        case 1:{
                            value = StateValues.CPU_QEMU_BUSY_VALUE_ONE;
                            break;
                        }
                        case 2:{
                            value = StateValues.CPU_QEMU_BUSY_VALUE_TWO;
                            break;
                        }
                        case 3:{
                            value = StateValues.CPU_QEMU_BUSY_VALUE_THREE;
                            break;
                        }
                        case 4:{
                            value = StateValues.CPU_QEMU_BUSY_VALUE_FOUR;
                            break;
                        }
                        default:
                            value = StateValues.CPU_QEMU_BUSY_VALUE_MANY;
                            break;
                        }
                        ss.modifyAttribute(ts, value, quark);
                    }

                    //final int currentThreadIO = ss.getQuarkRelativeAndAdd(getNodeIO(ss), String.valueOf(thread));
                } else if (nextProcessName.contains("vhost")){ //$NON-NLS-1$
                    final int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeNetQemu(ss), String.valueOf(nextTid));
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, Attributes.STATUS ); //$NON-NLS-1$
                    value = StateValues.NET_QEMU_BUSY_VALUE;
                    ss.modifyAttribute(ts, value, quark);
                    netTs.put(nextTid, ts)  ;
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "Netif" );//$NON-NLS-1$
                    netIf.put(nextTid, 0);
                    quark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "Netdev" );//$NON-NLS-1$
                    netDev.put(nextTid, 0) ;
                } else {
                    if (waitingCPU.containsKey(cpu)) {
                        Map<Integer, Long> tmp = waitingCPU.get(cpu);
                        for ( Entry<Integer,Long> entry:waitingCPU.get(cpu).entrySet())
                        {
                            tmp.put(entry.getKey(), ts);
                        }
                        tmp.remove(nextTid);
                        waitingCPU.put(cpu, tmp);
                    }
                }

                if (cpuTopreemption.containsKey(cpu)){
                    Map<Integer,Long> tmpCPU = cpuTopreemption.get(cpu);
                    if (preemptingThread.containsKey(cpu)){
                        Map<Integer,Long> tmp = preemptingThread.get(cpu);
                        if (prevTid>0){
                            if (tmp.containsKey(prevTid)){
                                Integer currentDelayThread = ss.getQuarkRelativeAndAdd(getNodeDelayQemu(ss), "preempting"); //$NON-NLS-1$
                                for (Entry<Integer, Long> entry:tmpCPU.entrySet()){
                                    // if (tidToPtid.containsKey(entry.getKey())){
                                    quark = ss.getQuarkRelativeAndAdd(currentDelayThread,tidToPtid.get(entry.getKey()).toString());
                                    value = ss.queryOngoingState(quark);
                                    Long accPreemptVM = value.isNull() ? 0 : value.unboxLong();
                                    value = TmfStateValue.newValueLong(ts-tmpCPU.get(entry.getKey())+accPreemptVM);
                                    ss.modifyAttribute(ts, value, quark);
                                    if (tidToPtid.containsKey(prevTid)){
                                        quark = ss.getQuarkRelativeAndAdd(quark,"VM");
                                        quark = ss.getQuarkRelativeAndAdd(quark,tidToPtid.get(prevTid).toString());
                                    } else {
                                        quark = ss.getQuarkRelativeAndAdd(quark,"Other");
                                        quark = ss.getQuarkRelativeAndAdd(quark,prevTid.toString());
                                    }
                                    value = ss.queryOngoingState(quark);
                                    accPreemptVM = value.isNull() ? 0 : value.unboxLong();
                                    value = TmfStateValue.newValueLong(ts-tmpCPU.get(entry.getKey())+accPreemptVM);
                                    ss.modifyAttribute(ts, value, quark);

                                    quark = ss.getQuarkRelativeAndAdd(quark, prevTid.toString());
                                    value = ss.queryOngoingState(quark);
                                    Long accPreemptT = value.isNull() ? 0 : value.unboxLong();
                                    value = TmfStateValue.newValueLong(ts-tmpCPU.get(entry.getKey())+accPreemptT);
                                    ss.modifyAttribute(ts, value, quark);
                                    //}
                                    tmpCPU.put(entry.getKey(), ts);
                                }
                                cpuTopreemption.put(cpu, tmpCPU);
                                tmp.remove(prevTid);
                            }
                        }
                        if (nextTid>0){
                            tmp.put(nextTid, ts);
                        }
                        if (tmp.size()>0){
                            preemptingThread.put(cpu, tmp);
                        } else if(preemptingThread.containsKey(cpu)) {
                            preemptingThread.remove(cpu);
                        }
                    }
                }


                /* Set the status of the CPU itself */
                if (nextTid > 0) {
                    // add all the threads that preempt vcpu

                    /* Check if the entering process is in kernel or user mode */
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.SYSTEM_CALL);
                    if (vcpu_find) {
                        value = StateValues.CPU_STATUS_VMX_ROOT_VALUE;

                    } else {
                        value = StateValues.CPU_STATUS_RUN_USERMODE_VALUE;
                    }
                    if (ss.queryOngoingState(quark).isNull()) {

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
                    int lenStart = "/home/hani/".length(); //$NON-NLS-1$
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
                    //String hostname = (String) content.getField("hostname").getValue(); //$NON-NLS-1$
                    String hostname = "testU1";
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
                        value = TmfStateValue.newValueInt(ppid);
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
                final Integer target_cpu = ((Long) event.getContent().getField("target_cpu").getValue()).intValue(); //$NON-NLS-1$
                int exitReasonQuark = ss.getQuarkRelativeAndAdd(currentThreadNode, "exit_reason");
                value = ss.queryOngoingState(exitReasonQuark);
                int exit_reason = value.isNull() ? -1 : value.unboxInt();
                String processName = (String) event.getContent().getField("comm").getValue(); //$NON-NLS-1$
                if (processName.contains("vhost")){

                    /*
                    Integer newCurrentThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(tid));
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.PPID);
                    value = ss.queryOngoingState(quark);
                    Integer PTID = value.isNull() ? 0 : value.unboxInt();
                    int newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                    int threadPTID = thread;
                    if (!tidToPtid.containsKey(thread)) {
                        String execName ;
                        while (PTID != 1 && PTID !=0) {
                            quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);
                            int quarkName = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.EXEC_NAME);
                            newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                            value = ss.queryOngoingState(quark);
                            value = ss.queryOngoingState(quarkName);
                            execName = value.isNull() ? "0" : value.unboxStr();
                            if (execName.equals("libvirtd")){ //$NON-NLS-1$
                                break;
                            }
                            threadPTID = PTID;
                            PTID = value.isNull() ? 0 : value.unboxInt();

                        }
                        if (PTID == 0) {
                            break;
                        }
                        tidToPtid.put(thread, threadPTID);
                    } else {
                        threadPTID = tidToPtid.get(thread);
                    }
                     */                  int threadPTID = getVMPTID(thread);
                     if (threadPTID==0){
                         break;
                     }
                     if (exit_reason == 30){
                         int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                         int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU");
                         quark = ss.getQuarkRelativeAndAdd(currentThreadNode, "vcpu"); //$NON-NLS-1$
                         value = ss.queryOngoingState(quark);
                         String vcpu_id = value.toString();
                         int vcpuIDquark = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);
                         int statusQuark = ss.getQuarkRelativeAndAdd(vcpuIDquark, Attributes.STATUS); //$NON-NLS-1$
                         value = StateValues.CPU_STATUS_VMX_ROOT_NET_VALUE;
                         Long timeIO = ts;
                         if (timeForIO.containsKey(thread)){
                             timeIO = timeForIO.get(thread);
                         }
                         ss.modifyAttribute(timeIO, value, statusQuark);

                         quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                         value = StateValues.CPU_STATUS_VMX_ROOT_NET_VALUE;
                         ss.modifyAttribute(timeIO, value, quark);


                     }
                }
                if (processName.equals("qemu-system-x86")||processName.equals("qemu-kvm")||processName.equals("qemu:ubuntu")){ //$NON-NLS-1$

                    /*Integer newCurrentThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(tid));
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, Attributes.PPID);
                    value = ss.queryOngoingState(quark);
                    Integer PTID = value.isNull() ? 0 : value.unboxInt();
                    int newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                    int threadPTID = tid;
                    if (!tidToPtid.containsKey(tid)) {
                        String execName ;
                        while (PTID != 1 && PTID !=0) {
                            quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.PPID);
                            int quarkName = ss.getQuarkRelativeAndAdd(newCurrentThreadNodeTmp, Attributes.EXEC_NAME);
                            newCurrentThreadNodeTmp = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), PTID.toString());
                            value = ss.queryOngoingState(quark);
                            value = ss.queryOngoingState(quarkName);
                            execName = value.isNull() ? "0" : value.unboxStr();
                            if (execName.equals("libvirtd")){ //$NON-NLS-1$
                                break;
                            }
                            threadPTID = PTID;
                            PTID = value.isNull() ? 0 : value.unboxInt();

                        }
                        if (PTID == 0) {
                            break;
                        }
                        tidToPtid.put(tid, threadPTID);
                    } else {
                        threadPTID = tidToPtid.get(tid);
                    }*/
                    int threadPTID = getVMPTID(tid);
                    if (threadPTID == 0){
                        break;
                    }
                    if (tid == threadPTID && exit_reason == 30){
                        int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                        int vCPUQuark = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU");
                        quark = ss.getQuarkRelativeAndAdd(currentThreadNode, "vcpu"); //$NON-NLS-1$
                        value = ss.queryOngoingState(quark);
                        String vcpu_id = value.toString();
                        int vcpuIDquark = ss.getQuarkRelativeAndAdd(vCPUQuark, vcpu_id);
                        int statusQuark = ss.getQuarkRelativeAndAdd(vcpuIDquark, Attributes.STATUS); //$NON-NLS-1$
                        value = StateValues.CPU_STATUS_VMX_ROOT_DISK_VALUE;
                        Long timeIO = ts;
                        if (timeForIO.containsKey(thread)){
                            timeIO = timeForIO.get(thread);
                        }
                        ss.modifyAttribute(timeIO, value, statusQuark);

                        quark = ss.getQuarkRelativeAndAdd(currentCPUNode, Attributes.STATUS);
                        value = StateValues.CPU_STATUS_VMX_ROOT_DISK_VALUE;
                        ss.modifyAttribute(timeIO, value, quark);
                    }
                    int currentThreadCPU = ss.getQuarkRelativeAndAdd(getNodeCPUQemu(ss), String.valueOf(threadPTID));
                    int newCurrentThreadNode = ss.getQuarkRelativeAndAdd(getNodeThreads(ss), String.valueOf(tid));
                    quark = ss.getQuarkRelativeAndAdd(newCurrentThreadNode, "vcpu"); //$NON-NLS-1$
                    value = ss.queryOngoingState(quark);
                    String vcpu_id = value.toString();
                    if (!value.isNull()){
                        int quarktmp = ss.getQuarkRelativeAndAdd(currentThreadCPU, "vCPU"); //$NON-NLS-1$
                        int quarkvCPU= ss.getQuarkRelativeAndAdd(quarktmp, vcpu_id); //$NON-NLS-1$
                        value = TmfStateValue.newValueInt(2);
                        ss.modifyAttribute(ts, value, quarkvCPU);
                        quark = ss.getQuarkRelativeAndAdd(quarkvCPU, Attributes.STATUS ); //$NON-NLS-1$
                        value = StateValues.VCPU_WAIT_FOR_PCPU_VALUE;
                        ss.modifyAttribute(ts, value, quark);
                        //$NON-NLS-1$
                        int currentCPUNodetmp = ss.getQuarkRelativeAndAdd(getNodeCPUs(ss), target_cpu.toString());
                        quark = ss.getQuarkRelativeAndAdd(currentCPUNodetmp, Attributes.CURRENT_THREAD);
                        value = ss.queryOngoingState(quark);
                        thread = value.isNull() ? -1 : value.unboxInt();

                        //quark = ss.getQuarkRelativeAndAdd(quarkvCPU, "waiting" ); //$NON-NLS-1$
                        //value = TmfStateValue.newValueLong(thread);
                        //ss.modifyAttribute(ts, value, quark);


                        if (waitingCPU.containsKey(target_cpu)){
                            Map<Integer,Long> tmp = waitingCPU.get(target_cpu);
                            tmp.put(tid, ts);
                            waitingCPU.put(target_cpu, tmp);
                        } else {
                            Map<Integer,Long> tmp = new HashMap<>();
                            tmp.put(tid, ts);
                            waitingCPU.put(target_cpu, tmp);
                        }
                        quark = ss.getQuarkRelativeAndAdd(quarkvCPU, "pCPU"); //$NON-NLS-1$
                        value = TmfStateValue.newValueInt(target_cpu);
                        ss.modifyAttribute(ts, value , quark);
                    }

                }
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
