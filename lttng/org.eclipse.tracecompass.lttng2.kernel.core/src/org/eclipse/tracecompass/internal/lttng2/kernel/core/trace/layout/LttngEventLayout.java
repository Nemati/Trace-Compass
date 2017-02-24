/*******************************************************************************
 * Copyright (c) 2012, 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Alexandre Montplaisir - Initial API and implementation
 ******************************************************************************/

package org.eclipse.tracecompass.internal.lttng2.kernel.core.trace.layout;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;

import com.google.common.collect.ImmutableList;

/**
 * This file defines all the known event and field names for LTTng kernel
 * traces, for versions of lttng-modules up to 2.5.
 *
 * These should not be externalized, since they need to match exactly what the
 * tracer outputs. If you want to localize them in a view, you should do a
 * mapping in the view itself.
 *
 * @author Alexandre Montplaisir
 */
@SuppressWarnings("nls")
public class LttngEventLayout implements IKernelAnalysisEventLayout {

    /* Event names */
    private static final String IRQ_HANDLER_ENTRY = "irq_handler_entry";
    private static final String IRQ_HANDLER_EXIT = "irq_handler_exit";
    private static final String SOFTIRQ_ENTRY = "softirq_entry";
    private static final String SOFTIRQ_EXIT = "softirq_exit";
    private static final String SOFTIRQ_RAISE = "softirq_raise";
    private static final String SCHED_SWITCH = "sched_switch";
    private static final String SCHED_PI_SETPRIO = "sched_pi_setprio";
    private static final String SUBMIT_IO = "qemu:thread_pool_submit"; //$NON-NLS-1$
    private static final String COMPLETE_IO =  "qemu:thread_pool_complete"; //$NON-NLS-1$
    private static final Collection<String> KVM_ENTRY =  checkNotNull(ImmutableList.of("kvm_entry", "kvm_x86_entry"));
    private static final Collection<String> KVM_EXIT =  checkNotNull(ImmutableList.of("kvm_exit", "kvm_x86_exit"));
    private static final String INFO_IO = "qemu:bdrv_co_io_em"; //$NON-NLS-1$
    private static final String NET_IF = "netif_rx"; //$NON-NLS-1$
    private static final String NET_DEV = "net_dev_xmit"; //$NON-NLS-1$
    private static final String VCPU_ENTER_GUEST =  "addons_vcpu_enter_guest"; //$NON-NLS-1$
    private static final String KVM_NESTED_VMEXIT = "kvm_nested_vmexit"; //$NON-NLS-1$
    private static final String KVM_APIC_ACCEPT_IRQ = "kvm_apic_accept_irq";
    private static final String UST_MYSQL_COMMAND_DONE = "ust_mysql:command_done";
    private static final String UST_MYSQL_COMMAND_START = "ust_mysql:command_start";

    private static final Collection<String> SCHED_WAKEUP_EVENTS =
            checkNotNull(ImmutableList.of("sched_wakeup", "sched_wakeup_new"));

    private static final String SCHED_PROCESS_FORK = "sched_process_fork";
    private static final String SCHED_PROCESS_EXIT = "sched_process_exit";
    private static final String SCHED_PROCESS_FREE = "sched_process_free";
    private static final String STATEDUMP_PROCESS_STATE = "lttng_statedump_process_state";
    private static final String STATEDUMP_FILE_DESCRIPTOR = "lttng_statedump_file_descriptor";
    private static final String SYSCALL_ENTRY_PREFIX = "sys_";
    private static final String COMPAT_SYSCALL_ENTRY_PREFIX = "compat_sys_";
    private static final String SYSCALL_EXIT_PREFIX = "exit_syscall";

    /* Field names */
    private static final String IRQ = "irq";
    private static final String TID = "tid";
    private static final String VEC = "vec";
    private static final String PREV_TID = "prev_tid";
    private static final String PREV_STATE = "prev_state";
    private static final String NEXT_COMM = "next_comm";
    private static final String NEXT_TID = "next_tid";
    private static final String PARENT_TID = "parent_tid";
    private static final String CHILD_COMM = "child_comm";
    private static final String CHILD_TID = "child_tid";
    private static final String PRIO = "prio";
    private static final String NEXT_PRIO = "next_prio";
    private static final String NEW_PRIO = "newprio";

    /** All instances are the same. Only provide a static instance getter */
    protected LttngEventLayout() {
    }

    private static final IKernelAnalysisEventLayout INSTANCE = new LttngEventLayout();

    /**
     * Get an instance of this event layout
     *
     * This object is completely immutable, so no need to create additional
     * instances via the constructor.
     *
     * @return The instance
     */
    public static IKernelAnalysisEventLayout getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------------
    // Event names
    // ------------------------------------------------------------------------

    @Override
    public String eventIrqHandlerEntry() {
        return IRQ_HANDLER_ENTRY;
    }

    @Override
    public String eventIrqHandlerExit() {
        return IRQ_HANDLER_EXIT;
    }

    @Override
    public String eventSoftIrqEntry() {
        return SOFTIRQ_ENTRY;
    }

    @Override
    public String eventSoftIrqExit() {
        return SOFTIRQ_EXIT;
    }

    @Override
    public String eventSoftIrqRaise() {
        return SOFTIRQ_RAISE;
    }

    @Override
    public String eventSchedSwitch() {
        return SCHED_SWITCH;
    }

    @Override
    public String eventSchedPiSetprio() {
        return SCHED_PI_SETPRIO;
    }

    @Override
    public Collection<String> eventsSchedWakeup() {
        return SCHED_WAKEUP_EVENTS;
    }

    @Override
    public String eventSchedProcessFork() {
        return SCHED_PROCESS_FORK;
    }

    @Override
    public String eventSchedProcessExit() {
        return SCHED_PROCESS_EXIT;
    }

    @Override
    public String eventSchedProcessFree() {
        return SCHED_PROCESS_FREE;
    }

    @Override
    public @NonNull String eventStatedumpProcessState() {
        return STATEDUMP_PROCESS_STATE;
    }
    @Override
    public @NonNull String eventStatedumpFileDescriptor() {
        return STATEDUMP_FILE_DESCRIPTOR;
    }
    @Override
    public String eventSyscallEntryPrefix() {
        return SYSCALL_ENTRY_PREFIX;
    }

    @Override
    public String eventCompatSyscallEntryPrefix() {
        return COMPAT_SYSCALL_ENTRY_PREFIX;
    }

    @Override
    public String eventSyscallExitPrefix() {
        return SYSCALL_EXIT_PREFIX;
    }
  //Submit IO
    /**
     * @return submit_io
     * @since 2.0
     */
    @Override
    public String eventSubmitIO() {
        return SUBMIT_IO;
    }
    /**
     * s
     */
    @Override
    public String eventUSTMysqlCommandStart() {
        return UST_MYSQL_COMMAND_START;
    }

    /**
     * s
     */
    @Override
    public String eventUSTMysqlCommandDone() {
        return UST_MYSQL_COMMAND_DONE;
    }
    @Override
    public Collection<String> eventKVMEntry() {
        return KVM_ENTRY;
    }
    @Override
    public Collection<String> eventKVMExit() {
        return KVM_EXIT;
    }
    @Override
    public String eventVCPUEnterGuest() {
        return VCPU_ENTER_GUEST;
    }
    @Override
    public String eventKVMNestedVMExit() {
        return     KVM_NESTED_VMEXIT;
    }
    @Override
    public String eventKVMAPICAccept_IRQ(){
        return KVM_APIC_ACCEPT_IRQ;
    }
    // completed IO
    /**
     * @return complete_io
     * @since 2.0
     */
    @Override
    public String eventCompleteIO() {
        return COMPLETE_IO;
    }

    @Override
    public String eventInfoIO() {
        return INFO_IO;
    }
    @Override
    public String eventNetIf() {
        return NET_IF;
    }
    @Override
    public String eventNetDev() {
        return NET_DEV;
    }
    // ------------------------------------------------------------------------
    // Event field names
    // ------------------------------------------------------------------------

    @Override
    public String fieldIrq() {
        return IRQ;
    }

    @Override
    public String fieldVec() {
        return VEC;
    }

    @Override
    public String fieldTid() {
        return TID;
    }

    @Override
    public String fieldPrevTid() {
        return PREV_TID;
    }

    @Override
    public String fieldPrevState() {
        return PREV_STATE;
    }

    @Override
    public String fieldNextComm() {
        return NEXT_COMM;
    }

    @Override
    public String fieldNextTid() {
        return NEXT_TID;
    }

    @Override
    public String fieldChildComm() {
        return CHILD_COMM;
    }

    @Override
    public String fieldParentTid() {
        return PARENT_TID;
    }

    @Override
    public String fieldChildTid() {
        return CHILD_TID;
    }

    @Override
    public String fieldPrio() {
        return PRIO;
    }

    @Override
    public String fieldNewPrio() {
        return NEW_PRIO;
    }

    @Override
    public String fieldNextPrio() {
        return NEXT_PRIO;
    }

}
