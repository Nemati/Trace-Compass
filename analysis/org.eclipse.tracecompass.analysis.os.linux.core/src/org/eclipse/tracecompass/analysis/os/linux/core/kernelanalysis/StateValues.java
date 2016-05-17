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

package org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis;

import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;

/**
 * State values that are used in the kernel event handler. It's much better to
 * use integer values whenever possible, since those take much less space in the
 * history file.
 *
 * @author Alexandre Montplaisir
 */
@SuppressWarnings("javadoc")
public interface StateValues {

    /* CPU Status */
    int CPU_STATUS_IDLE = 0;
    int CPU_STATUS_RUN_USERMODE = 1;
    int CPU_STATUS_RUN_SYSCALL = 2;
    int CPU_STATUS_IRQ = 3;
    int CPU_STATUS_SOFTIRQ = 4;
    /**
     * @since 2.0
     */
    int CPU_STATUS_VMX_NON_ROOT = 5;
    int CPU_STATUS_VMX_ROOT = 6;
    /**
     * @since 2.0
     */
    int CPU_STATUS_VMX_ROOT_DISK = 9;
    /**
     * @since 2.0
     */
    int CPU_STATUS_VMX_ROOT_NET = 10;
    int PREEMPTED = 7;
    /**
     * @since 2.0
     */
    int VCPU_WAIT_FOR_PCPU = 8;
    int CPU_STATUS_VMX_NESTED_ROOT = 11;
    int CPU_STATUS_VMX_NESTED_NON_ROOT = 12;
    /**
     * @since 2.0
     */

    ITmfStateValue CPU_STATUS_VMX_NON_ROOT_VALUE = TmfStateValue.newValueInt(CPU_STATUS_VMX_NON_ROOT);
    ITmfStateValue CPU_STATUS_VMX_ROOT_VALUE = TmfStateValue.newValueInt(CPU_STATUS_VMX_ROOT);

    ITmfStateValue CPU_STATUS_VMX_ROOT_DISK_VALUE = TmfStateValue.newValueInt(CPU_STATUS_VMX_ROOT_DISK);
    ITmfStateValue CPU_STATUS_VMX_ROOT_NET_VALUE = TmfStateValue.newValueInt(CPU_STATUS_VMX_ROOT_NET);
    ITmfStateValue CPU_STATUS_VMX_NESTED_ROOT_VALUE = TmfStateValue.newValueInt(CPU_STATUS_VMX_NESTED_ROOT);
    ITmfStateValue CPU_STATUS_VMX_NESTED_NON_ROOT_VALUE = TmfStateValue.newValueInt(CPU_STATUS_VMX_NESTED_NON_ROOT);
    /**
     * @since 2.0
     */
    ITmfStateValue VCPU_WAIT_FOR_PCPU_VALUE = TmfStateValue.newValueInt(VCPU_WAIT_FOR_PCPU);

    /**
     * @since 2.0
     */
    ITmfStateValue  PREEMPTED_VALUE = TmfStateValue.newValueInt( PREEMPTED);
    ITmfStateValue CPU_STATUS_IDLE_VALUE = TmfStateValue.newValueInt(CPU_STATUS_IDLE);
    ITmfStateValue CPU_STATUS_RUN_USERMODE_VALUE = TmfStateValue.newValueInt(CPU_STATUS_RUN_USERMODE);
    ITmfStateValue CPU_STATUS_RUN_SYSCALL_VALUE = TmfStateValue.newValueInt(CPU_STATUS_RUN_SYSCALL);
    ITmfStateValue CPU_STATUS_IRQ_VALUE = TmfStateValue.newValueInt(CPU_STATUS_IRQ);
    ITmfStateValue CPU_STATUS_SOFTIRQ_VALUE = TmfStateValue.newValueInt(CPU_STATUS_SOFTIRQ);
    /* NET status Qemu */
    int NET_QEMU_RUN = 1;
    int NET_QEMU_IDLE = 0;
    /**
     * @since 2.0
     */
    ITmfStateValue NET_QEMU_BUSY_VALUE = TmfStateValue.newValueInt(NET_QEMU_RUN);
    ITmfStateValue NET_QEMU_IDLE_VALUE = TmfStateValue.newValueInt(NET_QEMU_IDLE);

/*CPU Status Qemu */

    int CPU_QEMU_RUN_ONE = 1;
    int CPU_QEMU_RUN_TWO = 2;
    int CPU_QEMU_RUN_THREE = 3;
    int CPU_QEMU_RUN_FOUR = 4;
    /**
     * @since 2.0
     */
    int CPU_QEMU_RUN_MANY = 5;
    int CPU_QEMU_IDLE = 0;
    /**
     * @since 2.0
     */
    ITmfStateValue CPU_QEMU_BUSY_VALUE_ONE = TmfStateValue.newValueInt(CPU_QEMU_RUN_ONE);
    ITmfStateValue CPU_QEMU_BUSY_VALUE_TWO = TmfStateValue.newValueInt(CPU_QEMU_RUN_TWO);
    /**
     * @since 2.0
     */
    ITmfStateValue CPU_QEMU_BUSY_VALUE_THREE = TmfStateValue.newValueInt(CPU_QEMU_RUN_THREE);
    ITmfStateValue CPU_QEMU_BUSY_VALUE_FOUR = TmfStateValue.newValueInt(CPU_QEMU_RUN_FOUR);
    ITmfStateValue CPU_QEMU_BUSY_VALUE_MANY = TmfStateValue.newValueInt(CPU_QEMU_RUN_MANY);
    ITmfStateValue CPU_QEMU_IDLE_VALUE = TmfStateValue.newValueInt(CPU_QEMU_IDLE);

    /* IO Status Qemu*/

    /**
     * @since 2.0
     */
    int IO_STATUS_SUBMITED = 1;
    int IO_STATUS_IDLE = 0;
    /**
     * @since 2.0
     */
    int IO_WRITE_QEMU = 2;
    int IO_READ_QEMU = 3;
    int IO_OTHER = 4;

    ITmfStateValue IO_STATUS_IDLE_VALUE = TmfStateValue.newValueInt(IO_STATUS_IDLE);
    ITmfStateValue IO_STATUS_SUBMITED_VALUE = TmfStateValue.newValueInt(IO_STATUS_SUBMITED);
    ITmfStateValue IO_WRITE_QEMU_VALUE = TmfStateValue.newValueInt(IO_WRITE_QEMU);
    ITmfStateValue IO_OTHER_VALUE = TmfStateValue.newValueInt(IO_OTHER);
    ITmfStateValue IO_READ_QEMU_VALUE = TmfStateValue.newValueInt(IO_READ_QEMU);

    /* Process status */
    int PROCESS_STATUS_UNKNOWN = 0;
    int PROCESS_STATUS_WAIT_BLOCKED = 1;
    int PROCESS_STATUS_RUN_USERMODE = 2;
    int PROCESS_STATUS_RUN_SYSCALL = 3;
    int PROCESS_STATUS_INTERRUPTED = 4;
    int PROCESS_STATUS_WAIT_FOR_CPU = 5;
    int PROCESS_STATUS_NON_ROOT = 6;
    /**
     * @since 2.0
     */
    int PROCESS_STATUS_ROOT = 7;
    int PROCESS_STATUS_PREEMPTED = 8;
    /**
     * @since 1.0
     */
    int PROCESS_STATUS_WAIT_UNKNOWN = 6;

    ITmfStateValue PROCESS_STATUS_UNKNOWN_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_UNKNOWN);
    /**
     * @since 1.0
     */
    ITmfStateValue PROCESS_STATUS_WAIT_UNKNOWN_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_WAIT_UNKNOWN);
    ITmfStateValue PROCESS_STATUS_WAIT_BLOCKED_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_WAIT_BLOCKED);
    ITmfStateValue PROCESS_STATUS_RUN_USERMODE_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_RUN_USERMODE);
    ITmfStateValue PROCESS_STATUS_RUN_SYSCALL_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_RUN_SYSCALL);
    ITmfStateValue PROCESS_STATUS_INTERRUPTED_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_INTERRUPTED);
    ITmfStateValue PROCESS_STATUS_WAIT_FOR_CPU_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_WAIT_FOR_CPU);
    /**
     * @since 2.0
     */
    ITmfStateValue PROCESS_STATUS_NON_ROOT_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_NON_ROOT);
    /**
     * @since 2.0
     */
    ITmfStateValue PROCESS_STATUS_ROOT_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_ROOT);
    ITmfStateValue PROCESS_STATUS_PREEMPTED_VALUE = TmfStateValue.newValueInt(PROCESS_STATUS_PREEMPTED);

    /* SoftIRQ-specific stuff. -1: null/disabled, >= 0: running on that CPU */
    int SOFT_IRQ_RAISED = -2;

    ITmfStateValue SOFT_IRQ_RAISED_VALUE = TmfStateValue.newValueInt(SOFT_IRQ_RAISED);
}
