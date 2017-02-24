/*******************************************************************************
 * Copyright (c) 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Alexandre Montplaisir - Initial API and implementation
 ******************************************************************************/

package org.eclipse.tracecompass.analysis.os.linux.core.trace;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;

import com.google.common.collect.ImmutableList;

/**
 * A kernel event layout to be used by default. This can be useful for
 * data-driven traces for example, where they can provide whatever event names
 * they want.
 *
 * Due to historical reasons, the definitions are the same as LTTng event names.
 *
 * @author Alexandre Montplaisir
 * @since 1.0
 */
public class DefaultEventLayout implements IKernelAnalysisEventLayout{

    /* Event names */
    private static final String IRQ_HANDLER_ENTRY = "irq_handler_entry"; //$NON-NLS-1$
    private static final String IRQ_HANDLER_EXIT = "irq_handler_exit"; //$NON-NLS-1$
    private static final String SOFTIRQ_ENTRY = "softirq_entry"; //$NON-NLS-1$
    private static final String SOFTIRQ_EXIT = "softirq_exit"; //$NON-NLS-1$
    private static final String SOFTIRQ_RAISE = "softirq_raise"; //$NON-NLS-1$
    private static final String SCHED_SWITCH = "sched_switch"; //$NON-NLS-1$
    private static final String SCHED_PI_SETPRIO = "sched_pi_setprio"; //$NON-NLS-1$
    private static final String INFO_IO = "qemu:bdrv_co_io_em"; //$NON-NLS-1$
    private static final String NET_IF = "net_if_rx"; //$NON-NLS-1$
    private static final String NET_DEV =  "net_dev_xmit"; //$NON-NLS-1$
    private static final String SUBMIT_IO = "qemu:thread_pool_submit"; //$NON-NLS-1$
    //private static final String MODIFY_IO = "qemu:bdrv_co_io_em"; //$NON-NLS-1$
    private static final String COMPLETE_IO =  "qemu:thread_pool_complete"; //$NON-NLS-1$
    private static final String UST_MYSQL_COMMAND_DONE = "ust_mysql:command_done" ; //$NON-NLS-1$
    private static final String UST_MYSQL_COMMAND_START = "ust_mysql:command_start" ; //$NON-NLS-1$
    private static final Collection<String> KVM_ENTRY = checkNotNull(ImmutableList.of("kvm_entry", "kvm_x86_entry")); //$NON-NLS-1$ //$NON-NLS-2$
    //private static final String KVM_ENTRY =  "kvm_entry"; //$NON-NLS-1$
    //private static final String KVM_EXIT =  "kvm_exit"; //$NON-NLS-1$
    private static final Collection<String> KVM_EXIT = checkNotNull(ImmutableList.of("kvm_exit", "kvm_x86_exit")); //$NON-NLS-1$ //$NON-NLS-2$
    private static final String VCPU_ENTER_GUEST =  "addons_vcpu_enter_guest"; //$NON-NLS-1$
    private static final String KVM_NESTED_VMEXIT = "kvm_nested_vmexit"; //$NON-NLS-1$
    private static final String KVM_APIC_ACCEPT_IRQ = "kvm_apic_accept_irq"; //$NON-NLS-1$
    private static final Collection<String> SCHED_WAKEUP_EVENTS =
            checkNotNull(ImmutableList.of("sched_wakeup", "sched_wakeup_new")); //$NON-NLS-1$ //$NON-NLS-2$

    private static final String SCHED_PROCESS_FORK = "sched_process_fork"; //$NON-NLS-1$
    private static final String SCHED_PROCESS_EXIT = "sched_process_exit"; //$NON-NLS-1$
    private static final String SCHED_PROCESS_FREE = "sched_process_free"; //$NON-NLS-1$
    private static final String STATEDUMP_PROCESS_STATE = "lttng_statedump_process_state"; //$NON-NLS-1$
    private static final String STATEDUMP_FILE_DESCRIPTOR = "lttng_statedump_file_descriptor"; //$NON-NLS-1$
    private static final String SYSCALL_ENTRY_PREFIX = "syscall_entry"; //$NON-NLS-1$
    private static final String COMPAT_SYSCALL_ENTRY_PREFIX = "compat_sys_"; //$NON-NLS-1$
    private static final String SYSCALL_EXIT_PREFIX = "syscall_exit"; //$NON-NLS-1$

    /* Field names */
    private static final String IRQ = "irq"; //$NON-NLS-1$
    private static final String TID = "tid"; //$NON-NLS-1$
    private static final String VEC = "vec"; //$NON-NLS-1$
    private static final String PREV_TID = "prev_tid"; //$NON-NLS-1$
    private static final String PREV_STATE = "prev_state"; //$NON-NLS-1$
    private static final String NEXT_COMM = "next_comm"; //$NON-NLS-1$
    private static final String NEXT_TID = "next_tid"; //$NON-NLS-1$
    private static final String PARENT_TID = "parent_tid"; //$NON-NLS-1$
    private static final String CHILD_COMM = "child_comm"; //$NON-NLS-1$
    private static final String CHILD_TID = "child_tid"; //$NON-NLS-1$
    private static final String PRIO = "prio"; //$NON-NLS-1$
    private static final String NEW_PRIO = "newprio"; //$NON-NLS-1$
    private static final String NEXT_PRIO = "next_prio"; //$NON-NLS-1$

    /** All instances are the same. Only provide a static instance getter */
    private DefaultEventLayout() {
    }

    /**
     * The instance of this event layout
     *
     * This object is completely immutable, so no need to create additional
     * instances via the constructor.
     */
    static final IKernelAnalysisEventLayout INSTANCE = new DefaultEventLayout();

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

    /**
     * @since 1.0
     */
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
    /**
     * @since 2.0
     */
    @Override
    public String eventUSTMysqlCommandStart(){
        return UST_MYSQL_COMMAND_START;
    }
    /**
     * @since 2.0
     */
    @Override
    public String eventUSTMysqlCommandDone(){
        return UST_MYSQL_COMMAND_DONE;
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
    public String eventKVMAPICAccept_IRQ() {
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

    /**
     * @since 1.0
     */
    @Override
    public String fieldPrio() {
        return PRIO;
    }

    /**
     * @since 1.0
     */
    @Override
    public String fieldNewPrio() {
        return NEW_PRIO;
    }

    /**
     * @since 1.0
     */
    @Override
    public String fieldNextPrio() {
        return NEXT_PRIO;
    }

}
