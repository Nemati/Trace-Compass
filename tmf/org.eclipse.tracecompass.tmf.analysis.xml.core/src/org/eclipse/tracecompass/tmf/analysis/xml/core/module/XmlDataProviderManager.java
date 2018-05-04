/**********************************************************************
 * Copyright (c) 2017, 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/

package org.eclipse.tracecompass.tmf.analysis.xml.core.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.ITimeGraphDataProvider;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.TmfTimeGraphCompositeDataProvider;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.tree.TmfTreeDataModel;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.xy.ITmfTreeXYDataProvider;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.xy.TmfTreeXYCompositeDataProvider;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalManager;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceClosedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.w3c.dom.Element;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;

/**
 * Class to manage instances of XML data providers which cannot be handled by
 * extension points as there are possibly several instances of XML providers per
 * trace.
 *
 * @since 2.4
 * @author Loic Prieur-Drevon
 */
@SuppressWarnings("restriction")
public class XmlDataProviderManager {

    private static @Nullable XmlDataProviderManager INSTANCE;

    private static final String ID_ATTRIBUTE = "id"; //$NON-NLS-1$
    private final Table<ITmfTrace, String, ITmfTreeXYDataProvider<@NonNull TmfTreeDataModel>> fXyProviders = HashBasedTable.create();
    private final Table<ITmfTrace, String, ITimeGraphDataProvider<@NonNull XmlTimeGraphEntryModel>> fTimeGraphProviders = HashBasedTable.create();

    /**
     * Get the instance of the manager
     *
     * @return the singleton instance
     */
    public static synchronized XmlDataProviderManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new XmlDataProviderManager();
        }
        return INSTANCE;
    }

    /**
     * Dispose the singleton instance if it exists
     *
     * @since 2.5
     */
    public static synchronized void dispose() {
        XmlDataProviderManager manager = INSTANCE;
        if (manager != null) {
            TmfSignalManager.deregister(manager);
            manager.fXyProviders.clear();
            manager.fTimeGraphProviders.clear();
        }
        INSTANCE = null;
    }

    /**
     * Private constructor.
     */
    private XmlDataProviderManager() {
        TmfSignalManager.register(this);
    }

    /**
     * Create (if necessary) and get the {@link XmlXYDataProvider} for the specified
     * trace and viewElement.
     *
     * @param trace
     *            trace for which we are querying a provider
     * @param viewElement
     *            the XML XY view for which we are querying a provider
     * @return the unique instance of an XY provider for the queried parameters
     */
    public synchronized ITmfTreeXYDataProvider<@NonNull TmfTreeDataModel> getXyProvider(@NonNull ITmfTrace trace, @NonNull Element viewElement) {
        if (!viewElement.hasAttribute(ID_ATTRIBUTE)) {
            return null;
        }
        String viewId = viewElement.getAttribute(ID_ATTRIBUTE);
        ITmfTreeXYDataProvider<@NonNull TmfTreeDataModel> provider = fXyProviders.get(trace, viewId);
        if (provider != null) {
            return provider;
        }
        if (Iterables.any(TmfTraceManager.getInstance().getOpenedTraces(),
                opened -> TmfTraceManager.getTraceSetWithExperiment(opened).contains(trace))) {
            /* if this trace or an experiment containing this trace is opened */
            Collection<ITmfTrace> traces = TmfTraceManager.getTraceSet(trace);
            if (traces.size() == 1) {
                Set<@NonNull String> analysisIds = TmfXmlUtils.getViewAnalysisIds(viewElement);
                Element entry = TmfXmlUtils.getChildElements(viewElement, TmfXmlStrings.ENTRY_ELEMENT).get(0);

                provider = XmlXYDataProvider.create(trace, analysisIds, entry);
            } else {
                provider = generateExperimentProviderXy(traces, viewElement);
            }
            if (provider != null) {
                fXyProviders.put(trace, viewId, provider);
            }
            return provider;
        }
        return null;
    }

    private ITmfTreeXYDataProvider<@NonNull TmfTreeDataModel> generateExperimentProviderXy(Collection<@NonNull ITmfTrace> traces, @NonNull Element viewElement) {
        List<@NonNull ITmfTreeXYDataProvider<@NonNull TmfTreeDataModel>> providers = new ArrayList<>();
        for (ITmfTrace child : traces) {
            ITmfTreeXYDataProvider<@NonNull TmfTreeDataModel> childProvider = getXyProvider(child, viewElement);
            if (childProvider != null) {
                providers.add(childProvider);
            }
        }
        if (providers.isEmpty()) {
            return null;
        } else if (providers.size() == 1) {
            return providers.get(0);
        }
        return new TmfTreeXYCompositeDataProvider<>(providers, XmlXYDataProvider.ID, XmlXYDataProvider.ID);
    }

    /**
     * Create (if necessary) and get the {@link XmlXYDataProvider} for the specified
     * trace and viewElement.
     *
     * @param trace
     *            trace for which we are querying a provider
     * @param viewElement
     *            the XML XY view for which we are querying a provider
     * @return the unique instance of an XY provider for the queried parameters
     * @since 2.6
     */
    public synchronized ITimeGraphDataProvider<@NonNull XmlTimeGraphEntryModel> getTimeGraphProvider(@NonNull ITmfTrace trace, @NonNull Element viewElement) {
        if (!viewElement.hasAttribute(ID_ATTRIBUTE)) {
            return null;
        }
        String viewId = viewElement.getAttribute(ID_ATTRIBUTE);
        ITimeGraphDataProvider<@NonNull XmlTimeGraphEntryModel> provider = fTimeGraphProviders.get(trace, viewId);
        if (provider != null) {
            return provider;
        }

        if (Iterables.any(TmfTraceManager.getInstance().getOpenedTraces(),
                opened -> TmfTraceManager.getTraceSetWithExperiment(opened).contains(trace))) {

            // Create with the trace or experiment first
            provider = XmlTimeGraphDataProvider.create(trace, viewElement);
            if (provider == null) {
                // Otherwise, see if it's an experiment and create a composite if that's the
                // case
                Collection<ITmfTrace> traces = TmfTraceManager.getTraceSet(trace);
                if (traces.size() > 1) {
                    // Try creating a composite only if there are many traces, otherwise, the
                    // previous call to create should have returned the data provider
                    provider = generateExperimentProviderTimeGraph(traces, viewElement);
                }
            }
            if (provider != null) {
                fTimeGraphProviders.put(trace, viewId, provider);
            }
            return provider;
        }
        return null;
    }

    private ITimeGraphDataProvider<@NonNull XmlTimeGraphEntryModel> generateExperimentProviderTimeGraph(Collection<@NonNull ITmfTrace> traces, @NonNull Element viewElement) {
        List<@NonNull ITimeGraphDataProvider<@NonNull XmlTimeGraphEntryModel>> providers = new ArrayList<>();
        for (ITmfTrace child : traces) {
            ITimeGraphDataProvider<@NonNull XmlTimeGraphEntryModel> childProvider = getTimeGraphProvider(child, viewElement);
            if (childProvider != null) {
                providers.add(childProvider);
            }
        }
        if (providers.isEmpty()) {
            return null;
        } else if (providers.size() == 1) {
            return providers.get(0);
        }
        return new TmfTimeGraphCompositeDataProvider<>(providers, XmlTimeGraphDataProvider.ID);
    }

    /**
     * Signal handler for the traceClosed signal.
     *
     * @param signal
     *            The incoming signal
     * @since 2.5
     */
    @TmfSignalHandler
    public synchronized void traceClosed(final TmfTraceClosedSignal signal) {
        for (ITmfTrace trace : TmfTraceManager.getTraceSetWithExperiment(signal.getTrace())) {
            fXyProviders.row(trace).clear();
            fTimeGraphProviders.row(trace).clear();
        }
    }
}
