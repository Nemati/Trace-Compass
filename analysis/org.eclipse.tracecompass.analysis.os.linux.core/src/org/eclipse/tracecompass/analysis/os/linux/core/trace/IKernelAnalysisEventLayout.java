/*******************************************************************************
 * Copyright (c) 2014, 2015 Ericsson
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

import java.util.Collection;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Interface to define "concepts" present in the Linux kernel (represented by
 * its tracepoints), that can then be exposed by different tracers under
 * different names.
 *
 * @author Alexandre Montplaisir
 */
// The methods are named after the TRACE_EVENT's, should be straightforward
@SuppressWarnings("javadoc")
public interface IKernelAnalysisEventLayout {

    // ------------------------------------------------------------------------
    // Common definitions
    // ------------------------------------------------------------------------

    IKernelAnalysisEventLayout DEFAULT_LAYOUT = DefaultEventLayout.INSTANCE;

    /**
     * Whenever a process appears for the first time in a trace, we assume it
     * starts inside this system call. (The syscall prefix is defined by the
     * implementer of this interface.)
     *
     * TODO Change to a default method with Java 8?
     */
    String INITIAL_SYSCALL_NAME = "clone"; //$NON-NLS-1$

    // ------------------------------------------------------------------------
    // Event names
    // ------------------------------------------------------------------------

    String eventIrqHandlerEntry();
    String eventIrqHandlerExit();
    String eventSoftIrqEntry();
    String eventSoftIrqExit();
    String eventSoftIrqRaise();
    String eventSchedSwitch();

    /** @since 1.0 */
    String eventSchedPiSetprio();

    Collection<String> eventsSchedWakeup();
    String eventSchedProcessFork();
    String eventSchedProcessExit();
    String eventSchedProcessFree();
    @Nullable String eventStatedumpProcessState();
    /**
     * @since 2.0
     */
    @Nullable String eventStatedumpFileDescriptor();
    String eventSyscallEntryPrefix();
    String eventCompatSyscallEntryPrefix();
    String eventSyscallExitPrefix();
    /**
     * @since 1.1
     */
    String eventInfoIO();
    String eventCompleteIO();
    String eventSubmitIO();
    String eventNetIf();
    String eventNetDev();
    /**
     * @since 2.0
     */
    Collection<String> eventKVMEntry();
    Collection<String> eventKVMExit();
    String eventVCPUEnterGuest();
    /**
     * @since 2.0
     */
    String eventKVMNestedVMExit();
    /**
     * @since 2.0
     */
    String eventKVMAPICAccept_IRQ();
    // ------------------------------------------------------------------------
    // Event field names
    // ------------------------------------------------------------------------

    String fieldIrq();
    String fieldVec();
    String fieldTid();
    String fieldPrevTid();
    String fieldPrevState();
    String fieldNextComm();
    String fieldNextTid();
    String fieldChildComm();
    String fieldParentTid();
    String fieldChildTid();

    /** @since 1.0 */
    String fieldPrio();

    /** @since 1.0 */
    String fieldNewPrio();

    /** @since 1.0 */
    String fieldNextPrio();
}
