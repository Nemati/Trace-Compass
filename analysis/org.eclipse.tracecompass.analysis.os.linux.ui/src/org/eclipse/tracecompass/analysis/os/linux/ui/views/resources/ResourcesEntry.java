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

import org.eclipse.jdt.annotation.NonNull;
//import org.eclipse.tracecompass.analysis.os.linux.ui.views.resources.ResourcesEntry.Type;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;
/**
 * An entry, or row, in the resource view
 *
 * @author Patrick Tasse
 */
public class ResourcesEntry extends TimeGraphEntry implements Comparable<ITimeGraphEntry> {

    /** Type of resource */
    public static enum Type {
        /** Null resources (filler rows, etc.) */
        NULL,
        /**
         * @since 1.1
         *
         */
        IOQemuRead,
        /**
         * @since 1.1
         */
        IOQemuWrite,

        /**
         * for Cpu of Qemu
         * @since 1.1
         */
        CPUQemu,

        /**
         * for Network of Qemu
         * @since 1.1
         */
        NetQemu,

        /** Entries for CPUs */
        CPU,
        /** Entries for IRQs */
        IRQ,
        /** Entries for Soft IRQ */
        SOFT_IRQ,
        /**
         *  For nested VM
         * @since 1.1
         */
        NestedVM,
        VM,
        /**
         *
         */
        Process

    }

    private final int fId;
    private final @NonNull ITmfTrace fTrace;
    private final Type fType;
    private final int fQuark;

    /**
     * Constructor
     *
     * @param quark
     *            The attribute quark matching the entry
     * @param trace
     *            The trace on which we are working
     * @param name
     *            The exec_name of this entry
     * @param startTime
     *            The start time of this entry lifetime
     * @param endTime
     *            The end time of this entry
     * @param type
     *            The type of this entry
     * @param id
     *            The id of this entry
     */
    public ResourcesEntry(int quark, @NonNull ITmfTrace trace, String name,
            long startTime, long endTime, Type type, int id) {
        super(name, startTime, endTime);
        fId = id;
        fTrace = trace;
        fType = type;
        fQuark = quark;
    }

    /**
     * Constructor
     *
     * @param trace
     *            The trace on which we are working
     * @param name
     *            The exec_name of this entry
     * @param startTime
     *            The start time of this entry lifetime
     * @param endTime
     *            The end time of this entry
     * @param id
     *            The id of this entry
     */
    public ResourcesEntry(@NonNull ITmfTrace trace, String name,
            long startTime, long endTime, int id) {
        this(-1, trace, name, startTime, endTime, Type.NULL, id);
    }

    /**
     * Constructor
     *
     * @param quark
     *            The attribute quark matching the entry
     * @param trace
     *            The trace on which we are working
     * @param startTime
     *            The start time of this entry lifetime
     * @param endTime
     *            The end time of this entry
     * @param type
     *            The type of this entry
     * @param id
     *            The id of this entry
     */
    public ResourcesEntry(int quark, @NonNull ITmfTrace trace,
            long startTime, long endTime, Type type, int id) {
        //this(quark, trace, type.toString() + " " + id, startTime, endTime, type, id); //$NON-NLS-1$
        //this(quark, trace, type.toString() + " " + id, startTime, endTime, type, id); //$NON-NLS-1$
         //  this(quark, trace, KernelStateProvider.vmNameMap.get(id) , startTime, endTime, type, id); //$NON-NLS-1$

      //  } else {
            this(quark, trace, type.toString() + " " + id, startTime, endTime, type, id); //$NON-NLS-1$
        //}
    }

    /**
     * @param quark
     * @param trace
     * @param startTime
     * @param endTime
     * @param type
     * @param id
     * @param Name
     * @since 1.1
     */
    public ResourcesEntry(int quark, @NonNull ITmfTrace trace,
            long startTime, long endTime, Type type, int id, String Name) {
            this(quark, trace, Name , startTime, endTime, type, id); //$NON-NLS-1$

    }



    /**
     * @since 1.1
     */
    public ResourcesEntry(Integer processQuark, ITmfTrace parentTrace, long startTime, long endTime, Type process, String processName) {
        // TODO Auto-generated constructor stub
        this(processQuark, parentTrace, processName , startTime, endTime, process, 1000); //$NON-NLS-1$
    }

    /**
     * Get the entry's id
     *
     * @return the entry's id
     */
    public int getId() {
        return fId;
    }

    /**
     * Get the entry's trace
     *
     * @return the entry's trace
     */
    public @NonNull ITmfTrace getTrace() {
        return fTrace;
    }

    /**
     * Get the entry Type of this entry. Uses the inner Type enum.
     *
     * @return The entry type
     */
    public Type getType() {
        return fType;
    }

    /**
     * Retrieve the attribute quark that's represented by this entry.
     *
     * @return The integer quark The attribute quark matching the entry
     */
    public int getQuark() {
        return fQuark;
    }

    @Override
    public boolean hasTimeEvents() {
        if (fType == Type.NULL) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(ITimeGraphEntry other) {
        if (!(other instanceof ResourcesEntry)) {
            /* Should not happen, but if it does, put those entries at the end */
            return -1;
        }
        ResourcesEntry o = (ResourcesEntry) other;

        /*
         * Resources entry names should all be of type "ABC 123"
         *
         * We want to filter on the Type first (the "ABC" part), then on the ID
         * ("123") in numerical order (so we get 1,2,10 and not 1,10,2).
         */
        int ret = this.getType().compareTo(o.getType());
        if (ret != 0) {
            return ret;
        }
        return Integer.compare(this.getId(), o.getId());
    }

}
