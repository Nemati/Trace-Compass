/*******************************************************************************
 * Copyright (c) 2010, 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Francois Chouinard - Initial API and implementation, replaced Table by TmfVirtualTable
 *   Patrick Tasse - Factored out from events view,
 *                   Filter implementation (inspired by www.eclipse.org/mat)
 *   Ansgar Radermacher - Support navigation to model URIs (Bug 396956)
 *   Bernd Hufmann - Updated call site and model URI implementation
 *   Alexandre Montplaisir - Update to new column API
 *   Matthew Khouzam - Add hide columns
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.ui.viewers.events;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
//import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.Attributes;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.KernelAnalysisModule;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.internal.tmf.core.filter.TmfCollapseFilter;
import org.eclipse.tracecompass.internal.tmf.ui.Activator;
import org.eclipse.tracecompass.internal.tmf.ui.Messages;
import org.eclipse.tracecompass.internal.tmf.ui.commands.CopyToClipboardOperation;
import org.eclipse.tracecompass.internal.tmf.ui.commands.ExportToTextCommandHandler;
import org.eclipse.tracecompass.internal.tmf.ui.dialogs.MultiLineInputDialog;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.component.ITmfEventProvider;
import org.eclipse.tracecompass.tmf.core.component.TmfComponent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.ITmfEventAspect;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfContentFieldAspect;
import org.eclipse.tracecompass.tmf.core.event.collapse.ITmfCollapsibleEvent;
import org.eclipse.tracecompass.tmf.core.event.lookup.ITmfCallsite;
import org.eclipse.tracecompass.tmf.core.event.lookup.ITmfModelLookup;
import org.eclipse.tracecompass.tmf.core.event.lookup.ITmfSourceLookup;
import org.eclipse.tracecompass.tmf.core.filter.ITmfFilter;
import org.eclipse.tracecompass.tmf.core.filter.model.ITmfFilterTreeNode;
import org.eclipse.tracecompass.tmf.core.filter.model.TmfFilterAndNode;
import org.eclipse.tracecompass.tmf.core.filter.model.TmfFilterMatchesNode;
import org.eclipse.tracecompass.tmf.core.filter.model.TmfFilterNode;
import org.eclipse.tracecompass.tmf.core.request.ITmfEventRequest.ExecutionType;
import org.eclipse.tracecompass.tmf.core.request.TmfEventRequest;
import org.eclipse.tracecompass.tmf.core.signal.TmfEventFilterAppliedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfEventSearchAppliedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfEventSelectedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfSelectionRangeUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.location.ITmfLocation;
import org.eclipse.tracecompass.tmf.ui.viewers.events.TmfEventsCache.CachedEvent;
import org.eclipse.tracecompass.tmf.ui.viewers.events.columns.TmfEventTableColumn;
import org.eclipse.tracecompass.tmf.ui.views.colors.ColorSetting;
import org.eclipse.tracecompass.tmf.ui.views.colors.ColorSettingsManager;
import org.eclipse.tracecompass.tmf.ui.views.colors.IColorSettingsListener;
import org.eclipse.tracecompass.tmf.ui.views.filter.FilterManager;
import org.eclipse.tracecompass.tmf.ui.widgets.rawviewer.TmfRawEventViewer;
import org.eclipse.tracecompass.tmf.ui.widgets.virtualtable.TmfVirtualTable;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.themes.ColorUtil;
import org.eclipse.ui.themes.IThemeManager;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;

/**
 * The generic TMF Events table
 *
 * This is a view that will list events that are read from a trace.
 *
 * @author Francois Chouinard
 * @author Patrick Tasse
 */
public class TmfEventsTable extends TmfComponent implements IGotoMarker, IColorSettingsListener, ISelectionProvider, IPropertyChangeListener {

    /**
     * Empty string array, used by {@link #getItemStrings}.
     */
    protected static final @NonNull String[] EMPTY_STRING_ARRAY = new String[0];

    /**
     * Empty string
     */
    protected static final @NonNull String EMPTY_STRING = ""; //$NON-NLS-1$

    private static final boolean IS_LINUX = System.getProperty("os.name").contains("Linux") ? true : false; //$NON-NLS-1$ //$NON-NLS-2$

    private static final String FONT_DEFINITION_ID = "org.eclipse.tracecompass.tmf.ui.font.eventtable"; //$NON-NLS-1$
    private static final String HIGHLIGHT_COLOR_DEFINITION_ID = "org.eclipse.tracecompass.tmf.ui.color.eventtable.highlight"; //$NON-NLS-1$

    private static final Image BOOKMARK_IMAGE = Activator.getDefault().getImageFromPath(
            "icons/elcl16/bookmark_obj.gif"); //$NON-NLS-1$
    private static final Image SEARCH_IMAGE = Activator.getDefault().getImageFromPath("icons/elcl16/search.gif"); //$NON-NLS-1$
    private static final Image SEARCH_MATCH_IMAGE = Activator.getDefault().getImageFromPath(
            "icons/elcl16/search_match.gif"); //$NON-NLS-1$
    private static final Image SEARCH_MATCH_BOOKMARK_IMAGE = Activator.getDefault().getImageFromPath(
            "icons/elcl16/search_match_bookmark.gif"); //$NON-NLS-1$
    private static final Image FILTER_IMAGE = Activator.getDefault().getImageFromPath("icons/elcl16/filter_items.gif"); //$NON-NLS-1$
    private static final Image STOP_IMAGE = Activator.getDefault().getImageFromPath("icons/elcl16/stop.gif"); //$NON-NLS-1$
    private static final String SEARCH_HINT = Messages.TmfEventsTable_SearchHint;
    private static final String FILTER_HINT = Messages.TmfEventsTable_FilterHint;
    private static final int MAX_CACHE_SIZE = 1000;

    private static final int MARGIN_COLUMN_INDEX = 0;
    private static final int FILTER_SUMMARY_INDEX = 1;
    private static final int EVENT_COLUMNS_START_INDEX = MARGIN_COLUMN_INDEX + 1;

    /**
     * The events table search/filter/data keys
     *
     * @author Patrick Tasse
     * @noimplement This interface only contains Event Table specific
     *              static definitions.
     */
    public interface Key {

        /** Search text */
        String SEARCH_TXT = "$srch_txt"; //$NON-NLS-1$

        /** Search object */
        String SEARCH_OBJ = "$srch_obj"; //$NON-NLS-1$

        /** Filter text */
        String FILTER_TXT = "$fltr_txt"; //$NON-NLS-1$

        /** Filter object */
        String FILTER_OBJ = "$fltr_obj"; //$NON-NLS-1$

        /** Timestamp */
        String TIMESTAMP = "$time"; //$NON-NLS-1$

        /** Rank */
        String RANK = "$rank"; //$NON-NLS-1$

        /** Bookmark indicator */
        String BOOKMARK = "$bookmark"; //$NON-NLS-1$

        /** Event aspect represented by this column */
        String ASPECT = "$aspect"; //$NON-NLS-1$

        /**
         * Table item list of style ranges
         *
         * @since 1.0
         */
        String STYLE_RANGES = "$style_ranges"; //$NON-NLS-1$

        /**
         * The width of a table item
         *
         * @since 2.0
         */
        String WIDTH = "$width"; //$NON-NLS-1$
    }

    /**
     * The events table search/filter state
     *
     * @version 1.0
     * @author Patrick Tasse
     */
    public static enum HeaderState {
        /** A search is being run */
        SEARCH,

        /** A filter is applied */
        FILTER
    }

    interface Direction {
        int FORWARD = +1;
        int BACKWARD = -1;
    }

    // ------------------------------------------------------------------------
    // Table data
    // ------------------------------------------------------------------------

    /** The virtual event table */
    protected TmfVirtualTable fTable;

    private Composite fComposite;
    private SashForm fSashForm;
    private TmfRawEventViewer fRawViewer;
    private ITmfTrace fTrace;
    private volatile boolean fPackDone = false;
    private HeaderState fHeaderState = HeaderState.SEARCH;
    private long fSelectedRank = -1;
    private long fSelectedBeginRank = -1;
    private ITmfTimestamp fSelectedBeginTimestamp = null;
    private IStatusLineManager fStatusLineManager = null;

    // Filter data
    private long fFilterMatchCount;
    private long fFilterCheckCount;
    private FilterThread fFilterThread;
    private boolean fFilterThreadResume = false;
    private final Object fFilterSyncObj = new Object();
    private SearchThread fSearchThread;
    private final Object fSearchSyncObj = new Object();

    /**
     * List of selection change listeners (element type:
     * <code>ISelectionChangedListener</code>).
     *
     * @see #fireSelectionChanged
     */
    private ListenerList selectionChangedListeners = new ListenerList();

    // Bookmark map <Rank, MarkerId>
    private Multimap<Long, Long> fBookmarksMap = HashMultimap.create();
    private IFile fBookmarksFile;
    private long fPendingGotoRank = -1;

    // SWT resources
    private LocalResourceManager fResourceManager = new LocalResourceManager(JFaceResources.getResources());
    private Color fGrayColor;
    private Color fGreenColor;
    private Color fHighlightColor;
    private Font fFont;
    private Font fBoldFont;

    private final List<TmfEventTableColumn> fColumns = new LinkedList<>();

    // Event cache
    private final TmfEventsCache fCache;
    private boolean fCacheUpdateBusy = false;
    private boolean fCacheUpdatePending = false;
    private boolean fCacheUpdateCompleted = false;
    private final Object fCacheUpdateSyncObj = new Object();

    // Keep track of column order, it is needed after table is disposed
    private int[] fColumnOrder;

    private boolean fDisposeOnClose;

    private Menu fHeaderMenu;

    private Menu fTablePopup;

    private Menu fRawTablePopup;

    private Point fLastMenuCursorLocation;
    private MenuManager fRawViewerPopupMenuManager;
    private MenuManager fTablePopupMenuManager;
    private MenuManager fHeaderPopupMenuManager;

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Basic constructor, using the default set of columns
     *
     * @param parent
     *            The parent composite UI object
     * @param cacheSize
     *            The size of the event table cache
     */
    public TmfEventsTable(final Composite parent, final int cacheSize) {
        this(parent, cacheSize, TmfTrace.BASE_ASPECTS);
    }

    /**
     * Legacy constructor, using ColumnData to define columns
     *
     * @param parent
     *            The parent composite UI object
     * @param cacheSize
     *            The size of the event table cache
     * @param columnData
     *            The column data array
     * @deprecated Deprecated constructor, use
     *             {@link #TmfEventsTable(Composite, int, Collection)}
     */
    @Deprecated
    public TmfEventsTable(final Composite parent, int cacheSize,
            final org.eclipse.tracecompass.tmf.ui.widgets.virtualtable.ColumnData[] columnData) {
        /*
         * We'll do a "best-effort" to keep trace types still using this API to
         * keep working, by defining a TmfEventTableColumn for each ColumnData
         * they passed.
         */
        this(parent, cacheSize, convertFromColumnData(columnData));
    }

    @Deprecated
    private static @NonNull Iterable<ITmfEventAspect> convertFromColumnData(
            org.eclipse.tracecompass.tmf.ui.widgets.virtualtable.ColumnData[] columnData) {

        ImmutableList.Builder<ITmfEventAspect> builder = new ImmutableList.Builder<>();
        for (org.eclipse.tracecompass.tmf.ui.widgets.virtualtable.ColumnData col : columnData) {
            String fieldName = col.header;
            if (fieldName != null) {
                builder.add(new TmfContentFieldAspect(fieldName, fieldName));
            }
        }
        return checkNotNull(builder.build());
    }

    /**
     * Standard constructor, where we define which columns to use.
     *
     * @param parent
     *            The parent composite UI object
     * @param cacheSize
     *            The size of the event table cache
     * @param aspects
     *            The event aspects to display in this table. One column per
     *            aspect will be created.
     *            <p>
     *            The iteration order of this collection will correspond to the
     *            initial ordering of the columns in the table.
     *            </p>
     */
    public TmfEventsTable(final Composite parent, int cacheSize,
            @NonNull Iterable<ITmfEventAspect> aspects) {
        super("TmfEventsTable"); //$NON-NLS-1$

        fComposite = new Composite(parent, SWT.NONE);
        final GridLayout gl = new GridLayout(1, false);
        gl.marginHeight = 0;
        gl.marginWidth = 0;
        gl.verticalSpacing = 0;
        fComposite.setLayout(gl);

        fSashForm = new SashForm(fComposite, SWT.HORIZONTAL);
        fSashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Create a virtual table
        final int style = SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI | SWT.FULL_SELECTION;
        fTable = new TmfVirtualTable(fSashForm, style);

        // Set the table layout
        final GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
        fTable.setLayoutData(layoutData);

        // Some cosmetic enhancements
        fTable.setHeaderVisible(true);
        fTable.setLinesVisible(true);

        // Setup the columns
        for (ITmfEventAspect aspect : aspects) {
            if (aspect != null) {
                fColumns.add(new TmfEventTableColumn(aspect));
            }
        }

        TmfMarginColumn collapseCol = new TmfMarginColumn();
        fColumns.add(MARGIN_COLUMN_INDEX, collapseCol);

        fHeaderMenu = new Menu(fTable);
        // Create the UI columns in the table
        for (TmfEventTableColumn col : fColumns) {
            TableColumn column = fTable.newTableColumn(SWT.LEFT);
            column.setText(col.getHeaderName());
            column.setToolTipText(col.getHeaderTooltip());
            column.setData(Key.ASPECT, col.getEventAspect());
            column.pack();
            if (col instanceof TmfMarginColumn) {
                column.setResizable(false);
            } else {
                column.setMoveable(true);
                column.setData(Key.WIDTH, -1);
            }
            column.addControlListener(new ControlAdapter() {
                /*
                 * Make sure that the margin column is always first and keep the
                 * column order variable up to date.
                 */
                @Override
                public void controlMoved(ControlEvent e) {
                    int[] order = fTable.getColumnOrder();
                    if (order[0] == MARGIN_COLUMN_INDEX) {
                        fColumnOrder = order;
                        return;
                    }
                    for (int i = order.length - 1; i > 0; i--) {
                        if (order[i] == MARGIN_COLUMN_INDEX) {
                            order[i] = order[i - 1];
                            order[i - 1] = MARGIN_COLUMN_INDEX;
                        }
                    }
                    fTable.setColumnOrder(order);
                    fColumnOrder = fTable.getColumnOrder();
                }
            });
        }
        fColumnOrder = fTable.getColumnOrder();

        // Set the frozen row for header row
        fTable.setFrozenRowCount(1);

        // Create the header row cell editor
        createHeaderEditor();

        // Handle the table item selection
        fTable.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                if (e.item == null) {
                    return;
                }
                updateStatusLine(null);
                if (fTable.getSelectionIndices().length > 0) {
                    if (e.item.getData(Key.RANK) instanceof Long) {
                        fSelectedRank = (Long) e.item.getData(Key.RANK);
                        fRawViewer.selectAndReveal((Long) e.item.getData(Key.RANK));
                    } else {
                        fSelectedRank = -1;
                    }
                    if (fTable.getSelectionIndices().length == 1) {
                        fSelectedBeginRank = fSelectedRank;
                    }
                    if (e.item.getData(Key.TIMESTAMP) instanceof ITmfTimestamp) {
                        final ITmfTimestamp ts = NonNullUtils.checkNotNull((ITmfTimestamp) e.item.getData(Key.TIMESTAMP));
                        if (fTable.getSelectionIndices().length == 1) {
                            fSelectedBeginTimestamp = ts;
                        }
                        ITmfTimestamp selectedBeginTimestamp = fSelectedBeginTimestamp;
                        if (selectedBeginTimestamp != null) {
                            broadcast(new TmfSelectionRangeUpdatedSignal(TmfEventsTable.this, selectedBeginTimestamp, ts));
                            if (fTable.getSelectionIndices().length == 2) {
                                updateStatusLine(ts.getDelta(selectedBeginTimestamp));
                            }
                        }
                    } else {
                        if (fTable.getSelectionIndices().length == 1) {
                            fSelectedBeginTimestamp = null;
                        }
                    }
                }
                if (e.item.getData() instanceof ITmfEvent) {
                    broadcast(new TmfEventSelectedSignal(TmfEventsTable.this, (ITmfEvent) e.item.getData()));
                    fireSelectionChanged(new SelectionChangedEvent(TmfEventsTable.this, new StructuredSelection(e.item.getData())));
                } else {
                    fireSelectionChanged(new SelectionChangedEvent(TmfEventsTable.this, StructuredSelection.EMPTY));
                }
            }
        });

        int realCacheSize = Math.max(cacheSize, Display.getDefault().getBounds().height / fTable.getItemHeight());
        realCacheSize = Math.min(realCacheSize, MAX_CACHE_SIZE);
        fCache = new TmfEventsCache(realCacheSize, this);

        // Handle the table item requests
        fTable.addListener(SWT.SetData, new Listener() {

            @Override
            public void handleEvent(final Event event) {

                final TableItem item = (TableItem) event.item;
                int index = event.index - 1; // -1 for the header row

                if (event.index == 0) {
                    setHeaderRowItemData(item);
                    return;
                }

                if (fTable.getData(Key.FILTER_OBJ) != null) {
                    if ((event.index == 1) || (event.index == (fTable.getItemCount() - 1))) {
                        setFilterStatusRowItemData(item);
                        return;
                    }
                    /* -1 for top filter status row */
                    index = index - 1;
                }

                final CachedEvent cachedEvent = fCache.getEvent(index);
                if (cachedEvent != null) {
                    setItemData(item, cachedEvent, cachedEvent.rank);
                    return;
                }

                // Else, fill the cache asynchronously (and off the UI thread)
                event.doit = false;
            }
        });

        fTable.addListener(SWT.MenuDetect, new Listener() {
            @Override
            public void handleEvent(Event event) {
                fLastMenuCursorLocation = new Point(event.x, event.y);
                Point pt = fTable.getDisplay().map(null, fTable, fLastMenuCursorLocation);
                Rectangle clientArea = fTable.getClientArea();
                boolean header = clientArea.y <= pt.y && pt.y < (clientArea.y + fTable.getHeaderHeight());
                fTable.setMenu(header ? fHeaderMenu : fTablePopup);
            }
        });

        fTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDoubleClick(final MouseEvent event) {
                if (event.button != 1) {
                    return;
                }
                // Identify the selected row
                final Point point = new Point(event.x, event.y);
                final TableItem item = fTable.getItem(point);
                if (item != null) {
                    final Rectangle imageBounds = item.getImageBounds(0);
                    imageBounds.width = BOOKMARK_IMAGE.getBounds().width;
                    if (imageBounds.contains(point)) {
                        final Long rank = (Long) item.getData(Key.RANK);
                        if (rank != null) {
                            toggleBookmark(rank);
                        }
                    }
                }
            }
        });

        final Listener tooltipListener = new Listener() {
            Shell tooltipShell = null;

            @Override
            public void handleEvent(final Event event) {
                switch (event.type) {
                case SWT.MouseHover:
                    final TableItem item = fTable.getItem(new Point(event.x, event.y));
                    if (item == null) {
                        return;
                    }
                    final Long rank = (Long) item.getData(Key.RANK);
                    if (rank == null) {
                        return;
                    }
                    final String tooltipText = (String) item.getData(Key.BOOKMARK);
                    final Rectangle bounds = item.getImageBounds(0);
                    bounds.width = BOOKMARK_IMAGE.getBounds().width;
                    if (!bounds.contains(event.x, event.y)) {
                        return;
                    }
                    if ((tooltipShell != null) && !tooltipShell.isDisposed()) {
                        tooltipShell.dispose();
                    }
                    tooltipShell = new Shell(fTable.getShell(), SWT.ON_TOP | SWT.NO_FOCUS | SWT.TOOL);
                    tooltipShell.setBackground(PlatformUI.getWorkbench().getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
                    final FillLayout layout = new FillLayout();
                    layout.marginWidth = 2;
                    tooltipShell.setLayout(layout);
                    final Label label = new Label(tooltipShell, SWT.WRAP);
                    String text = rank.toString() + (tooltipText != null ? ": " + tooltipText : EMPTY_STRING); //$NON-NLS-1$
                    label.setForeground(PlatformUI.getWorkbench().getDisplay().getSystemColor(SWT.COLOR_INFO_FOREGROUND));
                    label.setBackground(PlatformUI.getWorkbench().getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
                    label.setText(text);
                    label.addListener(SWT.MouseExit, this);
                    label.addListener(SWT.MouseDown, this);
                    label.addListener(SWT.MouseWheel, this);
                    final Point size = tooltipShell.computeSize(SWT.DEFAULT, SWT.DEFAULT);
                    /*
                     * Bug in Linux. The coordinates of the event have an origin
                     * that excludes the table header but the method toDisplay()
                     * expects coordinates relative to an origin that includes
                     * the table header.
                     */
                    int y = event.y;
                    if (IS_LINUX) {
                        y += fTable.getHeaderHeight();
                    }
                    Point pt = fTable.toDisplay(event.x, y);
                    pt.x += BOOKMARK_IMAGE.getBounds().width;
                    pt.y += item.getBounds().height;
                    tooltipShell.setBounds(pt.x, pt.y, size.x, size.y);
                    tooltipShell.setVisible(true);
                    break;
                case SWT.Dispose:
                case SWT.KeyDown:
                case SWT.MouseMove:
                case SWT.MouseExit:
                case SWT.MouseDown:
                case SWT.MouseWheel:
                    if (tooltipShell != null) {
                        tooltipShell.dispose();
                        tooltipShell = null;
                    }
                    break;
                default:
                    break;
                }
            }
        };

        fTable.addListener(SWT.MouseHover, tooltipListener);
        fTable.addListener(SWT.Dispose, tooltipListener);
        fTable.addListener(SWT.KeyDown, tooltipListener);
        fTable.addListener(SWT.MouseMove, tooltipListener);
        fTable.addListener(SWT.MouseExit, tooltipListener);
        fTable.addListener(SWT.MouseDown, tooltipListener);
        fTable.addListener(SWT.MouseWheel, tooltipListener);

        fTable.addListener(SWT.EraseItem, new Listener() {
            @Override
            public void handleEvent(Event event) {
                TableItem item = (TableItem) event.item;
                List<?> styleRanges = (List<?>) item.getData(Key.STYLE_RANGES);

                GC gc = event.gc;
                Color background = item.getBackground(event.index);
                /*
                 * Paint the background if it is not the default system color.
                 * In Windows, if you let the widget draw the background, it
                 * will not show the item's background color if the item is
                 * selected or hot. If there are no style ranges and the item
                 * background is the default system color, we do not want to
                 * paint it or otherwise we would override the platform theme
                 * (e.g. alternating colors).
                 */
                if (styleRanges != null || !background.equals(item.getParent().getBackground())) {
                    // we will paint the table item's background
                    event.detail &= ~SWT.BACKGROUND;

                    // paint the item's default background
                    gc.setBackground(background);
                    gc.fillRectangle(event.x, event.y, event.width, event.height);
                }

                /*
                 * We will paint the table item's foreground. In Windows, if you
                 * paint the background but let the widget draw the foreground,
                 * it will override your background, unless the item is selected
                 * or hot.
                 */
                event.detail &= ~SWT.FOREGROUND;

                // paint the highlighted background for all style ranges
                if (styleRanges != null) {
                    Rectangle textBounds = item.getTextBounds(event.index);
                    String text = item.getText(event.index);
                    for (Object o : styleRanges) {
                        if (o instanceof StyleRange) {
                            StyleRange styleRange = (StyleRange) o;
                            if (styleRange.data.equals(event.index)) {
                                int startIndex = styleRange.start;
                                int endIndex = startIndex + styleRange.length;
                                int startX = gc.stringExtent(text.substring(0, startIndex)).x;
                                int endX = gc.stringExtent(text.substring(0, endIndex)).x;
                                gc.setBackground(styleRange.background);
                                gc.fillRectangle(textBounds.x + startX, textBounds.y, (endX - startX), textBounds.height);
                            }
                        }
                    }
                }
            }
        });

        fTable.addListener(SWT.PaintItem, new Listener() {
            @Override
            public void handleEvent(Event event) {
                TableItem item = (TableItem) event.item;

                // we promised to paint the table item's foreground
                GC gc = event.gc;
                Image image = item.getImage(event.index);
                if (image != null) {
                    Rectangle imageBounds = item.getImageBounds(event.index);
                    /*
                     * The image bounds don't match the default image position.
                     */
                    if (IS_LINUX) {
                        gc.drawImage(image, imageBounds.x + 1, imageBounds.y + 3);
                    } else {
                        gc.drawImage(image, imageBounds.x, imageBounds.y + 1);
                    }
                }
                gc.setForeground(item.getForeground(event.index));
                gc.setFont(item.getFont(event.index));
                String text = item.getText(event.index);
                Rectangle textBounds = item.getTextBounds(event.index);
                /*
                 * The text bounds don't match the default text position.
                 */
                if (IS_LINUX) {
                    gc.drawText(text, textBounds.x + 1, textBounds.y + 3, true);
                } else {
                    gc.drawText(text, textBounds.x - 1, textBounds.y + 2, true);
                }
            }
        });

        // Create resources
        createResources();

        initializeFonts();
        initializeColors();
        PlatformUI.getWorkbench().getThemeManager().addPropertyChangeListener(this);

        ColorSettingsManager.addColorSettingsListener(this);

        fTable.setItemCount(1); // +1 for header row

        fRawViewer = new TmfRawEventViewer(fSashForm, SWT.H_SCROLL | SWT.V_SCROLL);

        fRawViewer.addSelectionListener(new Listener() {
            @Override
            public void handleEvent(final Event e) {
                if (fTrace == null) {
                    return;
                }
                long rank;
                if (e.data instanceof Long) {
                    rank = (Long) e.data;
                } else if (e.data instanceof ITmfLocation) {
                    rank = findRank((ITmfLocation) e.data);
                } else {
                    return;
                }
                int index = (int) rank;
                if (fTable.getData(Key.FILTER_OBJ) != null) {
                    // +1 for top filter status row
                    index = fCache.getFilteredEventIndex(rank) + 1;
                }
                // +1 for header row
                fTable.setSelection(index + 1);
                fSelectedRank = rank;
                fSelectedBeginRank = fSelectedRank;
                updateStatusLine(null);
                final TableItem[] selection = fTable.getSelection();
                if ((selection != null) && (selection.length > 0)) {
                    TableItem item = fTable.getSelection()[0];
                    final TmfTimestamp ts = (TmfTimestamp) item.getData(Key.TIMESTAMP);
                    if (ts != null) {
                        broadcast(new TmfSelectionRangeUpdatedSignal(TmfEventsTable.this, ts));
                    }
                    if (item.getData() instanceof ITmfEvent) {
                        broadcast(new TmfEventSelectedSignal(TmfEventsTable.this, (ITmfEvent) item.getData()));
                        fireSelectionChanged(new SelectionChangedEvent(TmfEventsTable.this, new StructuredSelection(item.getData())));
                    } else {
                        fireSelectionChanged(new SelectionChangedEvent(TmfEventsTable.this, StructuredSelection.EMPTY));
                    }
                }
            }

            private long findRank(final ITmfLocation selectedLocation) {
                final double selectedRatio = fTrace.getLocationRatio(selectedLocation);
                long low = 0;
                long high = fTrace.getNbEvents();
                long rank = high / 2;
                double ratio = -1;
                while (ratio != selectedRatio) {
                    ITmfContext context = fTrace.seekEvent(rank);
                    ratio = fTrace.getLocationRatio(context.getLocation());
                    context.dispose();
                    if (ratio < selectedRatio) {
                        low = rank;
                        rank = (rank + high) / 2;
                    } else if (ratio > selectedRatio) {
                        high = rank;
                        rank = (rank + low) / 2;
                    }
                    if ((high - low) < 2) {
                        break;
                    }
                }
                return rank;
            }
        });

        fSashForm.setWeights(new int[] { 1, 1 });
        fRawViewer.setVisible(false);

        createPopupMenu();
    }

    /**
     * Checked menu creator to make columns visible or not.
     *
     * @param parent
     *            the parent menu
     * @param column
     *            the column
     */
    private static IAction createHeaderAction(final TableColumn column) {
        final IAction columnMenuAction = new Action(column.getText(), IAction.AS_CHECK_BOX) {
            @Override
            public void run() {
                if (isChecked()) {
                    column.setWidth((int) column.getData(Key.WIDTH));
                    column.setResizable(true);
                } else {
                    column.setData(Key.WIDTH, column.getWidth());
                    column.setWidth(0);
                    column.setResizable(false);
                }
            }
        };
        columnMenuAction.setChecked(column.getResizable());
        return columnMenuAction;
    }

    private IAction createResetHeaderAction() {
        return new Action(Messages.TmfEventsTable_ShowAll) {
            @Override
            public void run() {
                for (TableColumn column : fTable.getColumns()) {
                    final Object widthVal = column.getData(Key.WIDTH);
                    if (widthVal instanceof Integer) {
                        Integer width = (Integer) widthVal;
                        if (!column.getResizable()) {
                            column.setWidth(width);
                            column.setResizable(true);
                            /*
                             * This is because Linux always resizes the last
                             * column to fill in the void, this means that
                             * hiding a column resizes others and we can have 10
                             * columns that are 1000 pixels wide by hiding the
                             * last one progressively.
                             */
                            if (IS_LINUX) {
                                column.pack();
                            }
                        }
                    }
                }
            }
        };
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    /**
     * Create a pop-up menu.
     */
    private void createPopupMenu() {
        final IAction copyAction = new Action(Messages.TmfEventsTable_CopyToClipboardActionText) {
            @Override
            public void run() {
                ITmfTrace trace = fTrace;
                if (trace == null || (fSelectedRank == -1 && fSelectedBeginRank == -1)) {
                    return;
                }

                List<TmfEventTableColumn> columns = new ArrayList<>();
                for (int i : fTable.getColumnOrder()) {
                    TableColumn column = fTable.getColumns()[i];
                    // Omit the margin column and hidden columns
                    if (i >= EVENT_COLUMNS_START_INDEX && (column.getResizable() || column.getWidth() > 0)) {
                        columns.add(fColumns.get(i));
                    }
                }

                long start = Math.min(fSelectedBeginRank, fSelectedRank);
                long end = Math.max(fSelectedBeginRank, fSelectedRank);
                final ITmfFilter filter = (ITmfFilter) fTable.getData(Key.FILTER_OBJ);
                IRunnableWithProgress operation = new CopyToClipboardOperation(trace, filter, columns, start, end);
                try {
                    PlatformUI.getWorkbench().getProgressService().busyCursorWhile(operation);
                } catch (InvocationTargetException e) {
                    Activator.getDefault().logError("Invocation target exception copying to clipboard ", e); //$NON-NLS-1$
                } catch (InterruptedException e) {
                    /* ignored */
                }
            }
        };

        final IAction showTableAction = new Action(Messages.TmfEventsTable_ShowTableActionText) {
            @Override
            public void run() {
                fTable.setVisible(true);
                fSashForm.layout();
            }
        };

        final IAction hideTableAction = new Action(Messages.TmfEventsTable_HideTableActionText) {
            @Override
            public void run() {
                fTable.setVisible(false);
                fSashForm.layout();
            }
        };

        final IAction showRawAction = new Action(Messages.TmfEventsTable_ShowRawActionText) {
            @Override
            public void run() {
                fRawViewer.setVisible(true);
                fSashForm.layout();
                final int index = fTable.getSelectionIndex();
                if (index >= 1) {
                    fRawViewer.selectAndReveal(index - 1);
                }
            }
        };

        final IAction hideRawAction = new Action(Messages.TmfEventsTable_HideRawActionText) {
            @Override
            public void run() {
                fRawViewer.setVisible(false);
                fSashForm.layout();
            }
        };

        final IAction openCallsiteAction = new Action(Messages.TmfEventsTable_OpenSourceCodeActionText) {
            @Override
            public void run() {
                final TableItem items[] = fTable.getSelection();
                if (items.length != 1) {
                    return;
                }
                final TableItem item = items[0];

                final Object data = item.getData();
                if (data instanceof ITmfSourceLookup) {
                    ITmfSourceLookup event = (ITmfSourceLookup) data;
                    ITmfCallsite cs = event.getCallsite();
                    if (cs == null || cs.getFileName() == null) {
                        return;
                    }
                    IMarker marker = null;
                    try {
                        String fileName = cs.getFileName();
                        final String trimmedPath = fileName.replaceAll("\\.\\./", EMPTY_STRING); //$NON-NLS-1$
                        final ArrayList<IFile> files = new ArrayList<>();
                        ResourcesPlugin.getWorkspace().getRoot().accept(new IResourceVisitor() {
                            @Override
                            public boolean visit(IResource resource) throws CoreException {
                                if (resource instanceof IFile && resource.getFullPath().toString().endsWith(trimmedPath)) {
                                    files.add((IFile) resource);
                                }
                                return true;
                            }
                        });
                        IFile file = null;
                        if (files.size() > 1) {
                            ListDialog dialog = new ListDialog(getTable().getShell());
                            dialog.setContentProvider(ArrayContentProvider.getInstance());
                            dialog.setLabelProvider(new LabelProvider() {
                                @Override
                                public String getText(Object element) {
                                    return ((IFile) element).getFullPath().toString();
                                }
                            });
                            dialog.setInput(files);
                            dialog.setTitle(Messages.TmfEventsTable_OpenSourceCodeSelectFileDialogTitle);
                            dialog.setMessage(Messages.TmfEventsTable_OpenSourceCodeSelectFileDialogTitle + '\n' + cs.toString());
                            dialog.open();
                            Object[] result = dialog.getResult();
                            if (result != null && result.length > 0) {
                                file = (IFile) result[0];
                            }
                        } else if (files.size() == 1) {
                            file = files.get(0);
                        }
                        if (file != null) {
                            marker = file.createMarker(IMarker.MARKER);
                            marker.setAttribute(IMarker.LINE_NUMBER, Long.valueOf(cs.getLineNumber()).intValue());
                            IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), marker);
                            marker.delete();
                        } else if (files.size() == 0) {
                            displayException(new FileNotFoundException('\'' + cs.toString() + '\'' + '\n' + Messages.TmfEventsTable_OpenSourceCodeNotFound));
                        }
                    } catch (CoreException e) {
                        displayException(e);
                    }
                }
            }
        };

        final IAction openModelAction = new Action(Messages.TmfEventsTable_OpenModelActionText) {
            @Override
            public void run() {

                final TableItem items[] = fTable.getSelection();
                if (items.length != 1) {
                    return;
                }
                final TableItem item = items[0];

                final Object eventData = item.getData();
                if (eventData instanceof ITmfModelLookup) {
                    String modelURI = ((ITmfModelLookup) eventData).getModelUri();

                    if (modelURI != null) {
                        IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();

                        IFile file = null;
                        final URI uri = URI.createURI(modelURI);
                        if (uri.isPlatformResource()) {
                            IPath path = new Path(uri.toPlatformString(true));
                            file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
                        } else if (uri.isFile() && !uri.isRelative()) {
                            file = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(
                                    new Path(uri.toFileString()));
                        }

                        if (file != null) {
                            try {
                                /*
                                 * create a temporary validation marker on the
                                 * model file, remove it afterwards thus,
                                 * navigation works with all model editors
                                 * supporting the navigation to a marker
                                 */
                                IMarker marker = file.createMarker(EValidator.MARKER);
                                marker.setAttribute(EValidator.URI_ATTRIBUTE, modelURI);
                                marker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO);

                                IDE.openEditor(activePage, marker, OpenStrategy.activateOnOpen());
                                marker.delete();
                            } catch (CoreException e) {
                                displayException(e);
                            }
                        } else {
                            displayException(new FileNotFoundException('\'' + modelURI + '\'' + '\n' + Messages.TmfEventsTable_OpenModelUnsupportedURI));
                        }
                    }
                }
            }
        };

        final IAction exportToTextAction = new Action(Messages.TmfEventsTable_Export_to_text) {
            @Override
            public void run() {
                IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                Object handlerServiceObject = activePage.getActiveEditor().getSite().getService(IHandlerService.class);
                IHandlerService handlerService = (IHandlerService) handlerServiceObject;
                Object cmdServiceObject = activePage.getActiveEditor().getSite().getService(ICommandService.class);
                ICommandService cmdService = (ICommandService) cmdServiceObject;
                try {
                    HashMap<String, Object> parameters = new HashMap<>();
                    Command command = cmdService.getCommand(ExportToTextCommandHandler.COMMAND_ID);
                    ParameterizedCommand cmd = ParameterizedCommand.generateCommand(command, parameters);

                    IEvaluationContext context = handlerService.getCurrentState();
                    List<TmfEventTableColumn> exportColumns = new ArrayList<>();
                    for (int i : fTable.getColumnOrder()) {
                        TableColumn column = fTable.getColumns()[i];
                        // Omit the margin column and hidden columns
                        if (i >= EVENT_COLUMNS_START_INDEX && (column.getResizable() || column.getWidth() > 0)) {
                            exportColumns.add(fColumns.get(i));
                        }
                    }
                    context.addVariable(ExportToTextCommandHandler.TMF_EVENT_TABLE_COLUMNS_ID, exportColumns);

                    handlerService.executeCommandInContext(cmd, null, context);
                } catch (ExecutionException | NotDefinedException | NotEnabledException | NotHandledException e) {
                    displayException(e);
                }
            }
        };

        final IAction haniTest = new Action(Messages.TmfEventsTable_Hani_Test) {
            @Override
            public void run() {
                Map<String,Map<String,Map<String,Double>>> disk = new HashMap<>();
                Map<String,Map<String,Map<String,Double>>> vcpu = new HashMap<>();
                Map<String,Map<String,Map<String,Double>>> net = new HashMap<>();
                Map<String,Map<String,Double>> preemptedVMsValue = new HashMap<>();
                Map<String,Double> preemptedValue = new HashMap<>();
                Map<String,Double> preemptingVMsValue = new HashMap<>();
                final ITmfTrace trace = fTrace;
                if (trace == null) {
                    System.out.println("Trace is null");

                }  else {
                    System.out.println(trace.toString());
                }
                ITmfStateSystem kernelSs = TmfStateSystemAnalysisModule.getStateSystem(trace, KernelAnalysisModule.ID);
                if (kernelSs == null) {
                    System.out.println("Trace is null");
                } else {
                    try {


                        int diskNode = kernelSs.getQuarkAbsolute("DiskQemu");
                        int netNode = kernelSs.getQuarkAbsolute("NetQemu");
                        int cpuNode = kernelSs.getQuarkAbsolute("CPUQemu");
                        List<Integer> diskNodes = kernelSs.getSubAttributes(diskNode, false);
                        List<Integer> netNodes = kernelSs.getSubAttributes(netNode, false);
                        List<Integer> cpuNodes = kernelSs.getSubAttributes(cpuNode, false);

                        // disk Part *******************************************************
                        System.out.println("Disk Analyzing ...");
                        for (Integer tidQuark : diskNodes) {
                            Map<String,Map<String,Double>> vmStatistics = new HashMap<>();
                            Map<String,Double> statistics = new HashMap<>();
                            Long sum = 0L;
                            int count = 0;
                            double std = 0.0;

                            int readQuark = kernelSs.getQuarkRelative(tidQuark, "read");
                            int readSTATUS = kernelSs.getQuarkRelative(readQuark, "STATUS");
                            int totalTransfer = kernelSs.getQuarkRelative(readQuark, "totalTransfer");
                            int latency = kernelSs.getQuarkRelative(readQuark, "latency");
                            List<ITmfStateInterval> StatusIntervals;
                            StatusIntervals = StateSystemUtils.queryHistoryRange(kernelSs, readSTATUS, kernelSs.getStartTime(),kernelSs.getCurrentEndTime());
                            for (ITmfStateInterval StatusInterval : StatusIntervals) {
                                if (StatusInterval.getStateValue().unboxInt() == 3){
                                    sum += StatusInterval.getEndTime()-StatusInterval.getStartTime();
                                    count ++;
                                }
                            }
                            for (ITmfStateInterval StatusInterval : StatusIntervals) {
                                if (StatusInterval.getStateValue().unboxInt() == 3){
                                    std += Math.pow(StatusInterval.getEndTime()-StatusInterval.getStartTime() - sum/count,2) ;
                                }
                            }
                            List<ITmfStateInterval> tts = StateSystemUtils.queryHistoryRange(kernelSs, totalTransfer, kernelSs.getCurrentEndTime()-1, kernelSs.getCurrentEndTime());
                            for (ITmfStateInterval tt : tts) {
                                statistics.put("transfer", (Math.pow(10, 9))*tt.getStateValue().unboxLong()/(kernelSs.getCurrentEndTime()-kernelSs.getStartTime()));
                            }
                            List<ITmfStateInterval> tls =  StateSystemUtils.queryHistoryRange(kernelSs, latency, kernelSs.getCurrentEndTime()-1, kernelSs.getCurrentEndTime());
                            for (ITmfStateInterval tl : tls) {
                                statistics.put("latency", (double)tl.getStateValue().unboxLong()/(kernelSs.getCurrentEndTime()-kernelSs.getStartTime()));
                            }
                            statistics.put("avg", (double)sum/(count));
                            statistics.put("std", Math.sqrt(std/count));
                            statistics.put("count",(Math.pow(10, 9))*count/(kernelSs.getCurrentEndTime()-kernelSs.getStartTime()));
                            vmStatistics.put("diskRead", statistics);
                            disk.put(kernelSs.getAttributeName(tidQuark), vmStatistics);
                            System.out.println(kernelSs.getAttributeName(tidQuark) + ": Avg = " + sum/count +"  Std = "+ Math.sqrt(std/count));
                        }
                     // Net Part *******************************************************
                        System.out.println("Net Analyzing ...");
                        for (Integer tidQuark : netNodes) {
                            Map<String,Map<String,Double>> vmStatistics = new HashMap<>();
                            Map<String,Double> statistics = new HashMap<>();
                            Long sum = 0L;
                            int count = 0;
                            double std = 0.0;

                            int netQuark = kernelSs.getQuarkRelative(tidQuark, "STATUS");

                            int totalTransferDev = kernelSs.getQuarkRelative(tidQuark, "tNetdev");
                            int totalTransferIf = kernelSs.getQuarkRelative(tidQuark, "tNetif");

                            List<ITmfStateInterval> StatusIntervals;
                            StatusIntervals = StateSystemUtils.queryHistoryRange(kernelSs, netQuark, kernelSs.getStartTime(),kernelSs.getCurrentEndTime());
                            for (ITmfStateInterval StatusInterval : StatusIntervals) {
                                if (StatusInterval.getStateValue().unboxInt() == 1){
                                    sum += StatusInterval.getEndTime()-StatusInterval.getStartTime();
                                    count ++;
                                }
                            }
                            for (ITmfStateInterval StatusInterval : StatusIntervals) {
                                if (StatusInterval.getStateValue().unboxInt() == 1){
                                    std += Math.pow(StatusInterval.getEndTime()-StatusInterval.getStartTime() - sum/count,2) ;
                                }
                            }
                            List<ITmfStateInterval> tts = StateSystemUtils.queryHistoryRange(kernelSs, totalTransferDev, kernelSs.getCurrentEndTime()-1, kernelSs.getCurrentEndTime());
                            for (ITmfStateInterval tt : tts) {
                                statistics.put("tDev", (Math.pow(10, 9))*tt.getStateValue().unboxLong()/(kernelSs.getCurrentEndTime()-kernelSs.getStartTime()));
                            }
                            List<ITmfStateInterval> tls =  StateSystemUtils.queryHistoryRange(kernelSs, totalTransferIf, kernelSs.getCurrentEndTime()-1, kernelSs.getCurrentEndTime());
                            for (ITmfStateInterval tl : tls) {
                                statistics.put("tIf", (Math.pow(10, 9))*tl.getStateValue().unboxLong()/(kernelSs.getCurrentEndTime()-kernelSs.getStartTime()));
                            }
                            statistics.put("avg", (double) sum/(count));
                            statistics.put("std",  Math.sqrt(std/count));
                            statistics.put("count", (Math.pow(10, 9))*count/(kernelSs.getCurrentEndTime()-kernelSs.getStartTime()));
                            vmStatistics.put("net", statistics);
                            net.put(kernelSs.getAttributeName(tidQuark), vmStatistics);
                            System.out.println(kernelSs.getAttributeName(tidQuark) + ": Avg = " + sum/count);
                        }

                        // CPU Part *******************************************************
                        System.out.println("CPU Analyzing ...");
                        for (Integer tidQuark : cpuNodes) {
                            Map<String,Map<String,Double>> vmStatistics = new HashMap<>();
                            Map<String,Double> statistics = new HashMap<>();
                            Long sum = 0L;
                            int count = 0;
                            double std = 0.0;

                            int cpuQuark = kernelSs.getQuarkRelative(tidQuark, "vCPU");
                            List<Integer> vcpuNodes = kernelSs.getSubAttributes(cpuQuark, false);
                            for (Integer vcpuNode : vcpuNodes) {
                                int status = kernelSs.getQuarkRelative(vcpuNode, "STATUS");
                                List<ITmfStateInterval> StatusIntervals;
                                StatusIntervals = StateSystemUtils.queryHistoryRange(kernelSs, status, kernelSs.getStartTime(),kernelSs.getCurrentEndTime());
                                for (ITmfStateInterval StatusInterval : StatusIntervals) {
                                    if (StatusInterval.getStateValue().unboxInt() >= 1){
                                        sum += StatusInterval.getEndTime()-StatusInterval.getStartTime();
                                        count ++;
                                    }
                                }
                                for (ITmfStateInterval StatusInterval : StatusIntervals) {
                                    if (StatusInterval.getStateValue().unboxInt() >= 1){
                                        std += Math.pow(StatusInterval.getEndTime()-StatusInterval.getStartTime() - sum/count,2) ;
                                    }
                                }
                            }


                            int delayNode = kernelSs.getQuarkAbsolute("DelayCPU");

                            Integer currentDelayThread = kernelSs.getQuarkRelative(delayNode, "preempting"); //$NON-NLS-1$
                            List<Integer> quarksList = kernelSs.getSubAttributes(currentDelayThread, false);
                            for (Integer vm:quarksList){
                                List<ITmfStateInterval> StatusIntervals;
                                StatusIntervals = StateSystemUtils.queryHistoryRange(kernelSs, vm, kernelSs.getCurrentEndTime()-1,kernelSs.getCurrentEndTime());
                                double preemptedValuetmp = 0.0;
                                for (ITmfStateInterval StatusInterval : StatusIntervals) {
                                    preemptedValuetmp = StatusInterval.getStateValue().unboxLong();
                                    preemptedValue.put(kernelSs.getAttributeName(vm), preemptedValuetmp/(kernelSs.getCurrentEndTime()-kernelSs.getStartTime()));
                                }

                                String vmThread = kernelSs.getAttributeName(vm);
                                Integer quark = kernelSs.getQuarkRelative(vm, "VM"); //$NON-NLS-1$
                                List<Integer> preemptingVMsList = kernelSs.getSubAttributes(quark, false);
                                double preemtingValue = 0.0;
                                Map<String, Double> tmpValue = new HashMap<>();
                                for (Integer preemptingVMList:preemptingVMsList){
                                    StatusIntervals = StateSystemUtils.queryHistoryRange(kernelSs, preemptingVMList, kernelSs.getCurrentEndTime()-1,kernelSs.getCurrentEndTime());
                                    for (ITmfStateInterval StatusInterval : StatusIntervals) {
                                        preemtingValue = StatusInterval.getStateValue().unboxLong();
                                        tmpValue.put(kernelSs.getAttributeName(preemptingVMList), preemtingValue/(kernelSs.getCurrentEndTime()-kernelSs.getStartTime()));
                                    }

                                    double lastPreemptingValue = 0.0;
                                    if (preemptingVMsValue.containsKey(kernelSs.getAttributeName(preemptingVMList))){
                                        lastPreemptingValue = preemptingVMsValue.get(kernelSs.getAttributeName(preemptingVMList));
                                    }
                                    preemptingVMsValue.put(kernelSs.getAttributeName(preemptingVMList), preemtingValue/(kernelSs.getCurrentEndTime()-kernelSs.getStartTime())+lastPreemptingValue);

                                }
                                preemptedVMsValue.put(vmThread,tmpValue);
                            }

                            System.out.println(kernelSs.getAttributeName(tidQuark) + ": Avg = " + sum/count + "  Std = " + + Math.sqrt(std/count));
                            statistics.put("avg", (double)sum/(count));
                            statistics.put("std",  Math.sqrt(std/count));
                            statistics.put("count", (Math.pow(10, 9))*count/(kernelSs.getCurrentEndTime()-kernelSs.getStartTime()));

                            vmStatistics.put("cpu", statistics);

                            vcpu.put(kernelSs.getAttributeName(tidQuark), vmStatistics);
                        }

                        // Memory Part ****************************************************
                    } catch (AttributeNotFoundException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (StateSystemDisposedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    System.out.println((kernelSs.getCurrentEndTime()-kernelSs.getStartTime()));
                    System.out.println(preemptedValue);
                    System.out.println(preemptedVMsValue);
                    System.out.println(preemptingVMsValue);
                    System.out.println(disk);
                    System.out.println(vcpu);
                    System.out.println(net);


                }
            }
        };

        final IAction showSearchBarAction = new Action(Messages.TmfEventsTable_ShowSearchBarActionText) {
            @Override
            public void run() {
                fHeaderState = HeaderState.SEARCH;
                fTable.refresh();
                fTable.redraw();
            }
        };

        final IAction showFilterBarAction = new Action(Messages.TmfEventsTable_ShowFilterBarActionText) {
            @Override
            public void run() {
                fHeaderState = HeaderState.FILTER;
                fTable.refresh();
                fTable.redraw();
            }
        };

        final IAction clearFiltersAction = new Action(Messages.TmfEventsTable_ClearFiltersActionText) {
            @Override
            public void run() {
                clearFilters();
            }
        };

        final IAction collapseAction = new Action(Messages.TmfEventsTable_CollapseFilterMenuName) {
            @Override
            public void run() {
                applyFilter(new TmfCollapseFilter());
            }
        };

        class ToggleBookmarkAction extends Action {
            Long fRank;

            public ToggleBookmarkAction(final String text, final Long rank) {
                super(text);
                fRank = rank;
            }

            @Override
            public void run() {
                toggleBookmark(fRank);
            }
        }

        fHeaderPopupMenuManager = new MenuManager();
        fHeaderPopupMenuManager.setRemoveAllWhenShown(true);
        fHeaderPopupMenuManager.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                for (int index : fTable.getColumnOrder()) {
                    if (fTable.getColumns()[index].getData(Key.WIDTH) != null) {
                        fHeaderPopupMenuManager.add(createHeaderAction(fTable.getColumns()[index]));
                    }
                }
                fHeaderPopupMenuManager.add(new Separator());
                fHeaderPopupMenuManager.add(createResetHeaderAction());
            }
        });

        fTablePopupMenuManager = new MenuManager();
        fTablePopupMenuManager.setRemoveAllWhenShown(true);
        fTablePopupMenuManager.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(final IMenuManager manager) {
                if (fTable.getSelectionIndices().length == 1 && fTable.getSelectionIndices()[0] == 0) {
                    // Right-click on header row
                    if (fHeaderState == HeaderState.FILTER) {
                        fTablePopupMenuManager.add(showSearchBarAction);
                    } else {
                        fTablePopupMenuManager.add(showFilterBarAction);
                    }
                    return;
                }
                final Point point = fTable.toControl(fLastMenuCursorLocation);
                final TableItem item = fTable.getSelection().length > 0 ? fTable.getSelection()[0] : null;
                if (item != null) {
                    final Rectangle imageBounds = item.getImageBounds(0);
                    imageBounds.width = BOOKMARK_IMAGE.getBounds().width;
                    if (point.x <= (imageBounds.x + imageBounds.width)) {
                        // Right-click on left margin
                        final Long rank = (Long) item.getData(Key.RANK);
                        if ((rank != null) && (fBookmarksFile != null)) {
                            if (fBookmarksMap.containsKey(rank)) {
                                fTablePopupMenuManager.add(new ToggleBookmarkAction(
                                        Messages.TmfEventsTable_RemoveBookmarkActionText, rank));
                            } else {
                                fTablePopupMenuManager.add(new ToggleBookmarkAction(
                                        Messages.TmfEventsTable_AddBookmarkActionText, rank));
                            }
                        }
                        return;
                    }
                }

                // Right-click on table
                if (fSelectedRank != -1 && fSelectedBeginRank != -1) {
                    fTablePopupMenuManager.add(copyAction);
                    fTablePopupMenuManager.add(new Separator());
                }
                if (fTable.isVisible() && fRawViewer.isVisible()) {
                    fTablePopupMenuManager.add(hideTableAction);
                    fTablePopupMenuManager.add(hideRawAction);
                } else if (!fTable.isVisible()) {
                    fTablePopupMenuManager.add(showTableAction);
                } else if (!fRawViewer.isVisible()) {
                    fTablePopupMenuManager.add(showRawAction);
                }
                fTablePopupMenuManager.add(exportToTextAction);
                fTablePopupMenuManager.add(new Separator());
                fTablePopupMenuManager.add(haniTest);
                fTablePopupMenuManager.add(new Separator());
                if (item != null) {
                    final Object data = item.getData();
                    Separator separator = null;
                    if (data instanceof ITmfSourceLookup) {
                        ITmfSourceLookup event = (ITmfSourceLookup) data;
                        if (event.getCallsite() != null) {
                            fTablePopupMenuManager.add(openCallsiteAction);
                            separator = new Separator();
                        }
                    }

                    if (data instanceof ITmfModelLookup) {
                        ITmfModelLookup event = (ITmfModelLookup) data;
                        if (event.getModelUri() != null) {
                            fTablePopupMenuManager.add(openModelAction);
                            separator = new Separator();
                        }

                        if (separator != null) {
                            fTablePopupMenuManager.add(separator);
                        }
                    }
                }

                /*
                 * Only show collapse filter if at least one trace can be
                 * collapsed.
                 */
                boolean isCollapsible = false;
                if (fTrace != null) {
                    for (ITmfTrace trace : TmfTraceManager.getTraceSet(fTrace)) {
                        Class<? extends ITmfEvent> eventClass = trace.getEventType();
                        isCollapsible = ITmfCollapsibleEvent.class.isAssignableFrom(eventClass);
                        if (isCollapsible) {
                            break;
                        }
                    }
                }

                if (isCollapsible && !(fTable.getData(Key.FILTER_OBJ) instanceof TmfCollapseFilter)) {
                    fTablePopupMenuManager.add(collapseAction);
                    fTablePopupMenuManager.add(new Separator());
                }

                fTablePopupMenuManager.add(clearFiltersAction);
                final ITmfFilterTreeNode[] savedFilters = FilterManager.getSavedFilters();
                if (savedFilters.length > 0) {
                    final MenuManager subMenu = new MenuManager(Messages.TmfEventsTable_ApplyPresetFilterMenuName);
                    for (final ITmfFilterTreeNode node : savedFilters) {
                        if (node instanceof TmfFilterNode) {
                            final TmfFilterNode filter = (TmfFilterNode) node;
                            subMenu.add(new Action(filter.getFilterName()) {
                                @Override
                                public void run() {
                                    applyFilter(filter);
                                }
                            });
                        }
                    }
                    fTablePopupMenuManager.add(subMenu);
                }
                appendToTablePopupMenu(fTablePopupMenuManager, item);
            }
        });

        fRawViewerPopupMenuManager = new MenuManager();
        fRawViewerPopupMenuManager.setRemoveAllWhenShown(true);
        fRawViewerPopupMenuManager.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(final IMenuManager manager) {
                if (fTable.isVisible() && fRawViewer.isVisible()) {
                    fRawViewerPopupMenuManager.add(hideTableAction);
                    fRawViewerPopupMenuManager.add(hideRawAction);
                } else if (!fTable.isVisible()) {
                    fRawViewerPopupMenuManager.add(showTableAction);
                } else if (!fRawViewer.isVisible()) {
                    fRawViewerPopupMenuManager.add(showRawAction);
                }
                appendToRawPopupMenu(fRawViewerPopupMenuManager);
            }
        });

        fHeaderMenu = fHeaderPopupMenuManager.createContextMenu(fTable);

        fTablePopup = fTablePopupMenuManager.createContextMenu(fTable);
        fTable.setMenu(fTablePopup);

        fRawTablePopup = fRawViewerPopupMenuManager.createContextMenu(fRawViewer);
        fRawViewer.setMenu(fRawTablePopup);
    }

    /**
     * Append an item to the event table's pop-up menu.
     *
     * @param tablePopupMenu
     *            The menu manager
     * @param selectedItem
     *            The item to append
     */
    protected void appendToTablePopupMenu(final MenuManager tablePopupMenu, final TableItem selectedItem) {
        // override to append more actions
    }

    /**
     * Append an item to the raw viewer's pop-up menu.
     *
     * @param rawViewerPopupMenu
     *            The menu manager
     */
    protected void appendToRawPopupMenu(final MenuManager rawViewerPopupMenu) {
        // override to append more actions
    }

    @Override
    public void dispose() {
        stopSearchThread();
        stopFilterThread();
        PlatformUI.getWorkbench().getThemeManager().removePropertyChangeListener(this);
        ColorSettingsManager.removeColorSettingsListener(this);
        fComposite.dispose();
        if ((fTrace != null) && fDisposeOnClose) {
            fTrace.dispose();
        }
        fResourceManager.dispose();
        fRawViewer.dispose();
        if (fRawViewerPopupMenuManager != null) {
            fRawViewerPopupMenuManager.dispose();
        }
        if (fHeaderPopupMenuManager != null) {
            fHeaderPopupMenuManager.dispose();
        }
        if (fTablePopupMenuManager != null) {
            fTablePopupMenuManager.dispose();
        }

        super.dispose();
    }

    /**
     * Assign a layout data object to this view.
     *
     * @param layoutData
     *            The layout data to assign
     */
    public void setLayoutData(final Object layoutData) {
        fComposite.setLayoutData(layoutData);
    }

    /**
     * Get the virtual table contained in this event table.
     *
     * @return The TMF virtual table
     */
    public TmfVirtualTable getTable() {
        return fTable;
    }

    /**
     * @param columnData
     *            columnData
     * @deprecated The column headers are now set at the constructor, this
     *             shouldn't be called anymore.
     */
    @Deprecated
    protected void setColumnHeaders(final org.eclipse.tracecompass.tmf.ui.widgets.virtualtable.ColumnData[] columnData) {
        /* No-op */
    }

    /**
     * Set a table item's data.
     *
     * @param item
     *            The item to set
     * @param event
     *            Which trace event to link with this entry
     * @param rank
     *            Which rank this event has in the trace/experiment
     */
    protected void setItemData(final TableItem item, final ITmfEvent event, final long rank) {
        String[] itemStrings = getItemStrings(fColumns, event);

        // Get the actual ITmfEvent from the CachedEvent
        ITmfEvent tmfEvent = event;
        if (event instanceof CachedEvent) {
            tmfEvent = ((CachedEvent) event).event;
        }
        item.setText(itemStrings);
        item.setData(tmfEvent);
        item.setData(Key.TIMESTAMP, new TmfTimestamp(tmfEvent.getTimestamp()));
        item.setData(Key.RANK, rank);

        final Collection<Long> markerIds = fBookmarksMap.get(rank);
        if (!markerIds.isEmpty()) {
            Joiner joiner = Joiner.on("\n -").skipNulls(); //$NON-NLS-1$
            List<Object> parts = new ArrayList<>();
            if (markerIds.size() > 1) {
                parts.add(Messages.TmfEventsTable_MultipleBookmarksToolTip);
            }
            try {
                for (long markerId : markerIds) {
                    final IMarker marker = fBookmarksFile.findMarker(markerId);
                    parts.add(marker.getAttribute(IMarker.MESSAGE));
                }
            } catch (CoreException e) {
                displayException(e);
            }
            item.setData(Key.BOOKMARK, joiner.join(parts));
        } else {
            item.setData(Key.BOOKMARK, null);
        }

        boolean searchMatch = false;
        boolean searchNoMatch = false;
        final ITmfFilter searchFilter = (ITmfFilter) fTable.getData(Key.SEARCH_OBJ);
        if (searchFilter != null) {
            if (searchFilter.matches(tmfEvent)) {
                searchMatch = true;
            } else {
                searchNoMatch = true;
            }
        }

        final ColorSetting colorSetting = ColorSettingsManager.getColorSetting(tmfEvent);
        if (searchNoMatch) {
            item.setForeground(colorSetting.getDimmedForegroundColor());
            item.setBackground(colorSetting.getDimmedBackgroundColor());
        } else {
            item.setForeground(colorSetting.getForegroundColor());
            item.setBackground(colorSetting.getBackgroundColor());
        }
        item.setFont(fFont);

        if (searchMatch) {
            if (!markerIds.isEmpty()) {
                item.setImage(SEARCH_MATCH_BOOKMARK_IMAGE);
            } else {
                item.setImage(SEARCH_MATCH_IMAGE);
            }
        } else if (!markerIds.isEmpty()) {
            item.setImage(BOOKMARK_IMAGE);
        } else {
            item.setImage((Image) null);
        }

        List<StyleRange> styleRanges = new ArrayList<>();
        for (int index = 0; index < fTable.getColumns().length; index++) {
            TableColumn column = fTable.getColumns()[index];
            String regex = null;
            if (fHeaderState == HeaderState.FILTER) {
                regex = (String) column.getData(Key.FILTER_TXT);
            } else if (searchMatch) {
                regex = (String) column.getData(Key.SEARCH_TXT);
            }
            if (regex != null) {
                String text = item.getText(index);
                try {
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(text);
                    while (matcher.find()) {
                        int start = matcher.start();
                        int length = matcher.end() - start;
                        Color foreground = colorSetting.getForegroundColor();
                        Color background = fHighlightColor;
                        StyleRange styleRange = new StyleRange(start, length, foreground, background);
                        styleRange.data = index;
                        styleRanges.add(styleRange);
                    }
                } catch (PatternSyntaxException e) {
                    /* ignored */
                }
            }
        }
        if (styleRanges.isEmpty()) {
            item.setData(Key.STYLE_RANGES, null);
        } else {
            item.setData(Key.STYLE_RANGES, styleRanges);
        }
        item.getParent().redraw();

        if ((itemStrings[MARGIN_COLUMN_INDEX] != null) && !itemStrings[MARGIN_COLUMN_INDEX].isEmpty()) {
            packMarginColumn();
        }
    }

    /**
     * Set the item data of the header row.
     *
     * @param item
     *            The item to use as table header
     */
    protected void setHeaderRowItemData(final TableItem item) {
        String txtKey = null;
        if (fHeaderState == HeaderState.SEARCH) {
            item.setImage(SEARCH_IMAGE);
            txtKey = Key.SEARCH_TXT;
        } else if (fHeaderState == HeaderState.FILTER) {
            item.setImage(FILTER_IMAGE);
            txtKey = Key.FILTER_TXT;
        }
        item.setForeground(fGrayColor);
        // Ignore collapse and image column
        for (int i = EVENT_COLUMNS_START_INDEX; i < fTable.getColumns().length; i++) {
            final TableColumn column = fTable.getColumns()[i];
            final String filter = (String) column.getData(txtKey);
            if (filter == null) {
                if (fHeaderState == HeaderState.SEARCH) {
                    item.setText(i, SEARCH_HINT);
                } else if (fHeaderState == HeaderState.FILTER) {
                    item.setText(i, FILTER_HINT);
                }
                item.setForeground(i, fGrayColor);
                item.setFont(i, fFont);
            } else {
                item.setText(i, filter);
                item.setForeground(i, fGreenColor);
                item.setFont(i, fBoldFont);
            }
        }
    }

    /**
     * Set the item data of the "filter status" row.
     *
     * @param item
     *            The item to use as filter status row
     */
    protected void setFilterStatusRowItemData(final TableItem item) {
        for (int i = 0; i < fTable.getColumns().length; i++) {
            if (i == MARGIN_COLUMN_INDEX) {
                if ((fTrace == null) || (fFilterCheckCount == fTrace.getNbEvents())) {
                    item.setImage(FILTER_IMAGE);
                } else {
                    item.setImage(STOP_IMAGE);
                }
            }

            if (i == FILTER_SUMMARY_INDEX) {
                item.setText(FILTER_SUMMARY_INDEX, fFilterMatchCount + "/" + fFilterCheckCount); //$NON-NLS-1$
            } else {
                item.setText(i, EMPTY_STRING);
            }
        }
        item.setData(null);
        item.setData(Key.TIMESTAMP, null);
        item.setData(Key.RANK, null);
        item.setData(Key.STYLE_RANGES, null);
        item.setForeground(null);
        item.setBackground(null);
        item.setFont(fFont);
    }

    /**
     * Create an editor for the header.
     */
    private void createHeaderEditor() {
        final TableEditor tableEditor = fTable.createTableEditor();
        tableEditor.horizontalAlignment = SWT.LEFT;
        tableEditor.verticalAlignment = SWT.CENTER;
        tableEditor.grabHorizontal = true;
        tableEditor.minimumWidth = 50;

        // Handle the header row selection
        fTable.addMouseListener(new MouseAdapter() {
            int columnIndex;
            TableColumn column;
            TableItem item;

            @Override
            public void mouseDown(final MouseEvent event) {
                if (event.button != 1) {
                    return;
                }
                // Identify the selected row
                final Point point = new Point(event.x, event.y);
                item = fTable.getItem(point);

                // Header row selected
                if ((item != null) && (fTable.indexOf(item) == 0)) {

                    // Margin column selected
                    if (item.getBounds(0).contains(point)) {
                        if (fHeaderState == HeaderState.SEARCH) {
                            fHeaderState = HeaderState.FILTER;
                        } else if (fHeaderState == HeaderState.FILTER) {
                            fHeaderState = HeaderState.SEARCH;
                        }
                        fTable.setSelection(0);
                        fTable.refresh();
                        fTable.redraw();
                        return;
                    }

                    // Identify the selected column
                    columnIndex = -1;
                    for (int i = 0; i < fTable.getColumns().length; i++) {
                        final Rectangle rect = item.getBounds(i);
                        if (rect.contains(point)) {
                            columnIndex = i;
                            break;
                        }
                    }

                    if (columnIndex == -1) {
                        return;
                    }

                    column = fTable.getColumns()[columnIndex];

                    String txtKey = null;
                    if (fHeaderState == HeaderState.SEARCH) {
                        txtKey = Key.SEARCH_TXT;
                    } else if (fHeaderState == HeaderState.FILTER) {
                        txtKey = Key.FILTER_TXT;
                    }

                    /*
                     * The control that will be the editor must be a child of
                     * the Table
                     */
                    final Text newEditor = (Text) fTable.createTableEditorControl(Text.class);
                    final String headerString = (String) column.getData(txtKey);
                    if (headerString != null) {
                        newEditor.setText(headerString);
                    }
                    newEditor.addFocusListener(new FocusAdapter() {
                        @Override
                        public void focusLost(final FocusEvent e) {
                            final boolean changed = updateHeader(newEditor.getText());
                            if (changed) {
                                applyHeader();
                            }
                        }
                    });
                    newEditor.addKeyListener(new KeyAdapter() {
                        @Override
                        public void keyPressed(final KeyEvent e) {
                            if (e.character == SWT.CR) {
                                updateHeader(newEditor.getText());
                                applyHeader();

                                /*
                                 * Set focus on the table so that the next
                                 * carriage return goes to the next result
                                 */
                                TmfEventsTable.this.getTable().setFocus();
                            } else if (e.character == SWT.ESC) {
                                tableEditor.getEditor().dispose();
                            }
                        }
                    });
                    newEditor.selectAll();
                    newEditor.setFocus();
                    tableEditor.setEditor(newEditor, item, columnIndex);
                }
            }

            /*
             * returns true is value was changed
             */
            private boolean updateHeader(final String regex) {
                String objKey = null;
                String txtKey = null;
                if (fHeaderState == HeaderState.SEARCH) {
                    objKey = Key.SEARCH_OBJ;
                    txtKey = Key.SEARCH_TXT;
                } else if (fHeaderState == HeaderState.FILTER) {
                    objKey = Key.FILTER_OBJ;
                    txtKey = Key.FILTER_TXT;
                }
                if (regex.length() > 0) {
                    try {
                        Pattern.compile(regex);
                        if (regex.equals(column.getData(txtKey))) {
                            tableEditor.getEditor().dispose();
                            return false;
                        }
                        final TmfFilterMatchesNode filter = new TmfFilterMatchesNode(null);
                        ITmfEventAspect aspect = (ITmfEventAspect) column.getData(Key.ASPECT);
                        filter.setEventAspect(aspect);
                        filter.setRegex(regex);
                        column.setData(objKey, filter);
                        column.setData(txtKey, regex);
                    } catch (final PatternSyntaxException ex) {
                        tableEditor.getEditor().dispose();
                        MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                                ex.getDescription(), ex.getMessage());
                        return false;
                    }
                } else {
                    if (column.getData(txtKey) == null) {
                        tableEditor.getEditor().dispose();
                        return false;
                    }
                    column.setData(objKey, null);
                    column.setData(txtKey, null);
                }
                return true;
            }

            private void applyHeader() {
                if (fHeaderState == HeaderState.SEARCH) {
                    stopSearchThread();
                    final TmfFilterAndNode filter = new TmfFilterAndNode(null);
                    for (final TableColumn col : fTable.getColumns()) {
                        final Object filterObj = col.getData(Key.SEARCH_OBJ);
                        if (filterObj instanceof ITmfFilterTreeNode) {
                            filter.addChild((ITmfFilterTreeNode) filterObj);
                        }
                    }
                    if (filter.getChildrenCount() > 0) {
                        fTable.setData(Key.SEARCH_OBJ, filter);
                        fTable.refresh();
                        searchNext();
                        fireSearchApplied(filter);
                    } else {
                        fTable.setData(Key.SEARCH_OBJ, null);
                        fTable.refresh();
                        fireSearchApplied(null);
                    }
                } else if (fHeaderState == HeaderState.FILTER) {
                    final TmfFilterAndNode filter = new TmfFilterAndNode(null);
                    for (final TableColumn col : fTable.getColumns()) {
                        final Object filterObj = col.getData(Key.FILTER_OBJ);
                        if (filterObj instanceof ITmfFilterTreeNode) {
                            filter.addChild((ITmfFilterTreeNode) filterObj);
                        }
                    }
                    if (filter.getChildrenCount() > 0) {
                        applyFilter(filter);
                    } else {
                        clearFilters();
                    }
                }

                tableEditor.getEditor().dispose();
            }
        });

        fTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(final KeyEvent e) {
                e.doit = false;
                if (e.character == SWT.ESC) {
                    stopFilterThread();
                    stopSearchThread();
                    fTable.refresh();
                } else if (e.character == SWT.DEL) {
                    if (fHeaderState == HeaderState.SEARCH) {
                        stopSearchThread();
                        for (final TableColumn column : fTable.getColumns()) {
                            column.setData(Key.SEARCH_OBJ, null);
                            column.setData(Key.SEARCH_TXT, null);
                        }
                        fTable.setData(Key.SEARCH_OBJ, null);
                        fTable.refresh();
                        fireSearchApplied(null);
                    } else if (fHeaderState == HeaderState.FILTER) {
                        clearFilters();
                    }
                } else if (e.character == SWT.CR) {
                    if ((e.stateMask & SWT.SHIFT) == 0) {
                        searchNext();
                    } else {
                        searchPrevious();
                    }
                }
            }
        });
    }

    /**
     * Send an event indicating a filter has been applied.
     *
     * @param filter
     *            The filter that was just applied
     */
    protected void fireFilterApplied(final ITmfFilter filter) {
        broadcast(new TmfEventFilterAppliedSignal(this, fTrace, filter));
    }

    /**
     * Send an event indicating that a search has been applied.
     *
     * @param filter
     *            The search filter that was just applied
     */
    protected void fireSearchApplied(final ITmfFilter filter) {
        broadcast(new TmfEventSearchAppliedSignal(this, fTrace, filter));
    }

    /**
     * Start the filtering thread.
     */
    protected void startFilterThread() {
        synchronized (fFilterSyncObj) {
            final ITmfFilterTreeNode filter = (ITmfFilterTreeNode) fTable.getData(Key.FILTER_OBJ);
            if (fFilterThread == null || fFilterThread.filter != filter) {
                if (fFilterThread != null) {
                    fFilterThread.cancel();
                    fFilterThreadResume = false;
                }
                fFilterThread = new FilterThread(filter);
                fFilterThread.start();
            } else {
                fFilterThreadResume = true;
            }
        }
    }

    /**
     * Stop the filtering thread.
     */
    protected void stopFilterThread() {
        synchronized (fFilterSyncObj) {
            if (fFilterThread != null) {
                fFilterThread.cancel();
                fFilterThread = null;
                fFilterThreadResume = false;
            }
        }
    }

    /**
     * Apply a filter.
     *
     * @param filter
     *            The filter to apply
     */
    protected void applyFilter(ITmfFilter filter) {
        stopFilterThread();
        stopSearchThread();
        fFilterMatchCount = 0;
        fFilterCheckCount = 0;
        fCache.applyFilter(filter);
        fTable.clearAll();
        fTable.setData(Key.FILTER_OBJ, filter);
        /* +1 for header row, +2 for top and bottom filter status rows */
        fTable.setItemCount(3);
        startFilterThread();
        fireFilterApplied(filter);
    }

    /**
     * Clear all currently active filters.
     */
    protected void clearFilters() {
        if (fTable.getData(Key.FILTER_OBJ) == null) {
            return;
        }
        stopFilterThread();
        stopSearchThread();
        fCache.clearFilter();
        fTable.clearAll();
        for (final TableColumn column : fTable.getColumns()) {
            column.setData(Key.FILTER_OBJ, null);
            column.setData(Key.FILTER_TXT, null);
        }
        fTable.setData(Key.FILTER_OBJ, null);
        if (fTrace != null) {
            /* +1 for header row */
            fTable.setItemCount((int) fTrace.getNbEvents() + 1);
        } else {
            /* +1 for header row */
            fTable.setItemCount(1);
        }
        fFilterMatchCount = 0;
        fFilterCheckCount = 0;
        if (fSelectedRank >= 0) {
            /* +1 for header row */
            fTable.setSelection((int) fSelectedRank + 1);
        } else {
            fTable.setSelection(0);
        }
        fireFilterApplied(null);
        updateStatusLine(null);

        // Set original width
        fTable.getColumns()[MARGIN_COLUMN_INDEX].setWidth(0);
        packMarginColumn();
    }

    /**
     * Wrapper Thread object for the filtering thread.
     */
    protected class FilterThread extends Thread {
        private final ITmfFilterTreeNode filter;
        private TmfEventRequest request;
        private boolean refreshBusy = false;
        private boolean refreshPending = false;
        private final Object syncObj = new Object();

        /**
         * Constructor.
         *
         * @param filter
         *            The filter this thread will be processing
         */
        public FilterThread(final ITmfFilterTreeNode filter) {
            super("Filter Thread"); //$NON-NLS-1$
            this.filter = filter;
        }

        @Override
        public void run() {
            if (fTrace == null) {
                return;
            }
            final int nbRequested = (int) (fTrace.getNbEvents() - fFilterCheckCount);
            if (nbRequested <= 0) {
                return;
            }
            request = new TmfEventRequest(ITmfEvent.class, TmfTimeRange.ETERNITY,
                    (int) fFilterCheckCount, nbRequested, ExecutionType.BACKGROUND) {
                @Override
                public void handleData(final ITmfEvent event) {
                    super.handleData(event);
                    if (request.isCancelled()) {
                        return;
                    }
                    boolean refresh = false;
                    if (filter.matches(event)) {
                        final long rank = fFilterCheckCount;
                        final int index = (int) fFilterMatchCount;
                        fFilterMatchCount++;
                        fCache.storeEvent(event, rank, index);
                        refresh = true;
                    } else {
                        if (filter instanceof TmfCollapseFilter) {
                            fCache.updateCollapsedEvent((int) fFilterMatchCount - 1);
                        }
                    }

                    if (refresh || (fFilterCheckCount % 100) == 0) {
                        refreshTable();
                    }
                    fFilterCheckCount++;
                }
            };
            ((ITmfEventProvider) fTrace).sendRequest(request);
            try {
                request.waitForCompletion();
            } catch (final InterruptedException e) {
            }
            refreshTable();
            synchronized (fFilterSyncObj) {
                fFilterThread = null;
                if (fFilterThreadResume) {
                    fFilterThreadResume = false;
                    fFilterThread = new FilterThread(filter);
                    fFilterThread.start();
                }
            }
        }

        /**
         * Refresh the filter.
         */
        public void refreshTable() {
            synchronized (syncObj) {
                if (refreshBusy) {
                    refreshPending = true;
                    return;
                }
                refreshBusy = true;
            }
            Display.getDefault().asyncExec(new Runnable() {
                @Override
                public void run() {
                    if (request.isCancelled()) {
                        return;
                    }
                    if (fTable.isDisposed()) {
                        return;
                    }
                    /*
                     * +1 for header row, +2 for top and bottom filter status
                     * rows
                     */
                    fTable.setItemCount((int) fFilterMatchCount + 3);
                    fTable.refresh();
                    synchronized (syncObj) {
                        refreshBusy = false;
                        if (refreshPending) {
                            refreshPending = false;
                            refreshTable();
                        }
                    }
                }
            });
        }

        /**
         * Cancel this filtering thread.
         */
        public void cancel() {
            if (request != null) {
                request.cancel();
            }
        }
    }

    /**
     * Go to the next item of a search.
     */
    protected void searchNext() {
        synchronized (fSearchSyncObj) {
            if (fSearchThread != null) {
                return;
            }
            final ITmfFilterTreeNode searchFilter = (ITmfFilterTreeNode) fTable.getData(Key.SEARCH_OBJ);
            if (searchFilter == null) {
                return;
            }
            final int selectionIndex = fTable.getSelectionIndex();
            int startIndex;
            if (selectionIndex > 0) {
                /* -1 for header row, +1 for next event */
                startIndex = selectionIndex;
            } else {
                /*
                 * header row is selected, start at top event
                 */
                /* -1 for header row */
                startIndex = Math.max(0, fTable.getTopIndex() - 1);
            }
            final ITmfFilterTreeNode eventFilter = (ITmfFilterTreeNode) fTable.getData(Key.FILTER_OBJ);
            if (eventFilter != null) {
                // -1 for top filter status row
                startIndex = Math.max(0, startIndex - 1);
            }
            fSearchThread = new SearchThread(searchFilter, eventFilter, startIndex, fSelectedRank, Direction.FORWARD);
            fSearchThread.schedule();
        }
    }

    /**
     * Go to the previous item of a search.
     */
    protected void searchPrevious() {
        synchronized (fSearchSyncObj) {
            if (fSearchThread != null) {
                return;
            }
            final ITmfFilterTreeNode searchFilter = (ITmfFilterTreeNode) fTable.getData(Key.SEARCH_OBJ);
            if (searchFilter == null) {
                return;
            }
            final int selectionIndex = fTable.getSelectionIndex();
            int startIndex;
            if (selectionIndex > 0) {
                /* -1 for header row, -1 for previous event */
                startIndex = selectionIndex - 2;
            } else {
                /*
                 * Header row is selected, start at precedent of top event
                 */
                /* -1 for header row, -1 for previous event */
                startIndex = fTable.getTopIndex() - 2;
            }
            final ITmfFilterTreeNode eventFilter = (ITmfFilterTreeNode) fTable.getData(Key.FILTER_OBJ);
            if (eventFilter != null) {
                /* -1 for top filter status row */
                startIndex = startIndex - 1;
            }
            fSearchThread = new SearchThread(searchFilter, eventFilter, startIndex, fSelectedRank, Direction.BACKWARD);
            fSearchThread.schedule();
        }
    }

    /**
     * Stop the search thread.
     */
    protected void stopSearchThread() {
        fPendingGotoRank = -1;
        synchronized (fSearchSyncObj) {
            if (fSearchThread != null) {
                fSearchThread.cancel();
                fSearchThread = null;
            }
        }
    }

    /**
     * Wrapper for the search thread.
     */
    protected class SearchThread extends Job {

        private ITmfFilterTreeNode searchFilter;
        private ITmfFilterTreeNode eventFilter;
        private int startIndex;
        private int direction;
        private long rank;
        private long foundRank = -1;
        private TmfEventRequest request;
        private ITmfTimestamp foundTimestamp = null;

        /**
         * Constructor.
         *
         * @param searchFilter
         *            The search filter
         * @param eventFilter
         *            The event filter
         * @param startIndex
         *            The index at which we should start searching
         * @param currentRank
         *            The current rank
         * @param direction
         *            In which direction should we search, forward or backwards
         */
        public SearchThread(final ITmfFilterTreeNode searchFilter,
                final ITmfFilterTreeNode eventFilter, final int startIndex,
                final long currentRank, final int direction) {
            super(Messages.TmfEventsTable_SearchingJobName);
            this.searchFilter = searchFilter;
            this.eventFilter = eventFilter;
            this.startIndex = startIndex;
            this.rank = currentRank;
            this.direction = direction;
        }

        @Override
        protected IStatus run(final IProgressMonitor monitor) {
            final ITmfTrace trace = fTrace;
            if (trace == null) {
                return Status.OK_STATUS;
            }
            final Display display = Display.getDefault();
            if (startIndex < 0) {
                rank = (int) trace.getNbEvents() - 1;
                /*
                 * -1 for header row, -3 for header and top and bottom filter
                 * status rows
                 */
            } else if (startIndex >= (fTable.getItemCount() - (eventFilter == null ? 1 : 3))) {
                rank = 0;
            } else {
                int idx = startIndex;
                while (foundRank == -1) {
                    final CachedEvent event = fCache.peekEvent(idx);
                    if (event == null) {
                        break;
                    }
                    rank = event.rank;
                    if (searchFilter.matches(event.event) && ((eventFilter == null) || eventFilter.matches(event.event))) {
                        foundRank = event.rank;
                        foundTimestamp = event.event.getTimestamp();
                        break;
                    }
                    if (direction == Direction.FORWARD) {
                        idx++;
                    } else {
                        idx--;
                    }
                }
                if (foundRank == -1) {
                    if (direction == Direction.FORWARD) {
                        rank++;
                        if (rank > (trace.getNbEvents() - 1)) {
                            rank = 0;
                        }
                    } else {
                        rank--;
                        if (rank < 0) {
                            rank = (int) trace.getNbEvents() - 1;
                        }
                    }
                }
            }
            final int startRank = (int) rank;
            boolean wrapped = false;
            while (!monitor.isCanceled() && (foundRank == -1)) {
                int nbRequested = (direction == Direction.FORWARD ? Integer.MAX_VALUE : Math.min((int) rank + 1, trace.getCacheSize()));
                if (direction == Direction.BACKWARD) {
                    rank = Math.max(0, rank - trace.getCacheSize() + 1);
                }
                request = new TmfEventRequest(ITmfEvent.class, TmfTimeRange.ETERNITY,
                        (int) rank, nbRequested, ExecutionType.BACKGROUND) {
                    long currentRank = rank;

                    @Override
                    public void handleData(final ITmfEvent event) {
                        super.handleData(event);
                        if (searchFilter.matches(event) && ((eventFilter == null) || eventFilter.matches(event))) {
                            foundRank = currentRank;
                            foundTimestamp = event.getTimestamp();
                            if (direction == Direction.FORWARD) {
                                done();
                                return;
                            }
                        }
                        currentRank++;
                    }
                };
                ((ITmfEventProvider) trace).sendRequest(request);
                try {
                    request.waitForCompletion();
                    if (request.isCancelled()) {
                        return Status.OK_STATUS;
                    }
                } catch (final InterruptedException e) {
                    synchronized (fSearchSyncObj) {
                        fSearchThread = null;
                    }
                    return Status.OK_STATUS;
                }
                if (foundRank == -1) {
                    if (direction == Direction.FORWARD) {
                        if (rank == 0) {
                            synchronized (fSearchSyncObj) {
                                fSearchThread = null;
                            }
                            return Status.OK_STATUS;
                        }
                        nbRequested = (int) rank;
                        rank = 0;
                        wrapped = true;
                    } else {
                        rank--;
                        if (rank < 0) {
                            rank = (int) trace.getNbEvents() - 1;
                            wrapped = true;
                        }
                        if ((rank <= startRank) && wrapped) {
                            synchronized (fSearchSyncObj) {
                                fSearchThread = null;
                            }
                            return Status.OK_STATUS;
                        }
                    }
                }
            }
            int index = (int) foundRank;
            if (eventFilter != null) {
                index = fCache.getFilteredEventIndex(foundRank);
            }
            /* +1 for header row, +1 for top filter status row */
            final int selection = index + 1 + (eventFilter != null ? +1 : 0);

            display.asyncExec(new Runnable() {
                @Override
                public void run() {
                    if (monitor.isCanceled()) {
                        return;
                    }
                    if (fTable.isDisposed()) {
                        return;
                    }
                    fTable.setSelection(selection);
                    fSelectedRank = foundRank;
                    fSelectedBeginRank = fSelectedRank;
                    fRawViewer.selectAndReveal(fSelectedRank);
                    if (foundTimestamp != null) {
                        broadcast(new TmfSelectionRangeUpdatedSignal(TmfEventsTable.this, foundTimestamp));
                    }
                    fireSelectionChanged(new SelectionChangedEvent(TmfEventsTable.this, getSelection()));
                    synchronized (fSearchSyncObj) {
                        fSearchThread = null;
                    }
                    updateStatusLine(null);
                }
            });
            return Status.OK_STATUS;
        }

        @Override
        protected void canceling() {
            request.cancel();
            synchronized (fSearchSyncObj) {
                fSearchThread = null;
            }
        }
    }

    /**
     * Create the resources.
     */
    private void createResources() {
        fGrayColor = fResourceManager.createColor(ColorUtil.blend(fTable.getBackground().getRGB(), fTable.getForeground().getRGB()));
        fGreenColor = PlatformUI.getWorkbench().getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN);
    }

    /**
     * Initialize the fonts.
     */
    private void initializeFonts() {
        FontRegistry fontRegistry = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getFontRegistry();
        fFont = fontRegistry.get(FONT_DEFINITION_ID);
        fBoldFont = fontRegistry.getBold(FONT_DEFINITION_ID);
        fTable.setFont(fFont);
        /* Column header font cannot be set. See Bug 63038 */
    }

    /**
     * Initialize the colors.
     */
    private void initializeColors() {
        ColorRegistry colorRegistry = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getColorRegistry();
        fHighlightColor = colorRegistry.get(HIGHLIGHT_COLOR_DEFINITION_ID);
    }

    /**
     * @since 1.0
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if ((IThemeManager.CHANGE_CURRENT_THEME.equals(event.getProperty())) ||
                (FONT_DEFINITION_ID.equals(event.getProperty()))) {
            initializeFonts();
            fTable.refresh();
        }
        if ((IThemeManager.CHANGE_CURRENT_THEME.equals(event.getProperty())) ||
                (HIGHLIGHT_COLOR_DEFINITION_ID.equals(event.getProperty()))) {
            initializeColors();
            fTable.refresh();
        }
    }

    /**
     * Pack the columns.
     */
    protected void packColumns() {
        if (fPackDone) {
            return;
        }
        fTable.setRedraw(false);
        try {
            TableColumn tableColumns[] = fTable.getColumns();
            for (int i = 0; i < tableColumns.length; i++) {
                final TableColumn column = tableColumns[i];
                packSingleColumn(i, column);
            }

        } finally {
            // Make sure that redraw is always enabled.
            fTable.setRedraw(true);
        }
        fPackDone = true;
    }

    private void packMarginColumn() {
        TableColumn[] columns = fTable.getColumns();
        if (columns.length > 0) {
            packSingleColumn(0, columns[0]);
        }
    }

    private void packSingleColumn(int i, final TableColumn column) {
        final int headerWidth = column.getWidth();
        column.pack();
        /*
         * Workaround for Linux which doesn't consider the image width of
         * search/filter row in TableColumn.pack() after having executed
         * TableItem.setImage(null) for other rows than search/filter row.
         */
        boolean isCollapseFilter = fTable.getData(Key.FILTER_OBJ) instanceof TmfCollapseFilter;
        if (IS_LINUX && (i == 0) && isCollapseFilter) {
            column.setWidth(column.getWidth() + SEARCH_IMAGE.getBounds().width);
        }

        if (column.getWidth() < headerWidth) {
            column.setWidth(headerWidth);
        }
    }

    /**
     * Get the array of item strings (e.g., what to display in each cell of the
     * table row) corresponding to the columns and trace event passed in
     * parameter. The order of the Strings in the returned array will correspond
     * to the iteration order of 'columns'.
     *
     * <p>
     * To ensure consistent results, make sure only call this within a scope
     * synchronized on 'columns'! If the order of 'columns' changes right after
     * this method is called, the returned value won't be ordered correctly
     * anymore.
     */
    private static String[] getItemStrings(List<TmfEventTableColumn> columns, ITmfEvent event) {
        if (event == null) {
            return EMPTY_STRING_ARRAY;
        }
        synchronized (columns) {
            List<String> itemStrings = new ArrayList<>(columns.size());
            for (TmfEventTableColumn column : columns) {
                ITmfEvent passedEvent = event;
                if (!(column instanceof TmfMarginColumn) && (event instanceof CachedEvent)) {
                    /*
                     * Make sure that the event object from the trace is passed
                     * to all columns but the TmfMarginColumn
                     */
                    passedEvent = ((CachedEvent) event).event;
                }
                if (passedEvent == null) {
                    itemStrings.add(EMPTY_STRING);
                } else {
                    itemStrings.add(column.getItemString(passedEvent));
                }

            }
            return itemStrings.toArray(new String[0]);
        }
    }

    /**
     * Get the contents of the row in the events table corresponding to an
     * event. The order of the elements corresponds to the current order of the
     * columns.
     *
     * @param event
     *            The event printed in this row
     * @return The event row entries
     */
    public String[] getItemStrings(ITmfEvent event) {
        List<TmfEventTableColumn> columns = new ArrayList<>();
        for (int i : fTable.getColumnOrder()) {
            columns.add(fColumns.get(i));
        }
        return getItemStrings(columns, event);
    }

    /**
     * Returns an array of zero-relative integers that map the creation order of
     * the receiver's columns to the order in which they are currently being
     * displayed.
     * <p>
     * Specifically, the indices of the returned array represent the current
     * visual order of the columns, and the contents of the array represent the
     * creation order of the columns.
     *
     * @return the current visual order of the receiver's columns
     * @since 1.0
     */
    public int[] getColumnOrder() {
        return fColumnOrder;
    }

    /**
     * Sets the order that the columns in the receiver should be displayed in to
     * the given argument which is described in terms of the zero-relative
     * ordering of when the columns were added.
     * <p>
     * Specifically, the contents of the array represent the original position
     * of each column at the time its creation.
     *
     * @param order
     *            the new order to display the columns
     * @since 1.0
     */
    public void setColumnOrder(int[] order) {
        if (order == null || order.length != fTable.getColumns().length) {
            return;
        }
        fTable.setColumnOrder(order);
        fColumnOrder = fTable.getColumnOrder();
    }

    /**
     * Notify this table that is got the UI focus.
     */
    public void setFocus() {
        fTable.setFocus();
    }

    /**
     * Assign a new trace to this event table.
     *
     * @param trace
     *            The trace to assign to this event table
     * @param disposeOnClose
     *            true if the trace should be disposed when the table is
     *            disposed
     */
    public void setTrace(final ITmfTrace trace, final boolean disposeOnClose) {
        if ((fTrace != null) && fDisposeOnClose) {
            fTrace.dispose();
        }
        fTrace = trace;
        fPackDone = false;
        fDisposeOnClose = disposeOnClose;

        // Perform the updates on the UI thread
        PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
            @Override
            public void run() {
                fSelectedRank = -1;
                fSelectedBeginRank = -1;
                fTable.removeAll();
                fCache.setTrace(trace); // Clear the cache
                if (trace != null) {
                    if (!fTable.isDisposed()) {
                        if (fTable.getData(Key.FILTER_OBJ) == null) {
                            // +1 for header row
                            fTable.setItemCount((int) trace.getNbEvents() + 1);
                        } else {
                            stopFilterThread();
                            fFilterMatchCount = 0;
                            fFilterCheckCount = 0;
                            /*
                             * +1 for header row, +2 for top and bottom filter
                             * status rows
                             */
                            fTable.setItemCount(3);
                            startFilterThread();
                        }
                    }
                }
                fRawViewer.setTrace(trace);
            }
        });
    }

    /**
     * Assign the status line manager
     *
     * @param statusLineManager
     *            The status line manager, or null to disable status line
     *            messages
     */
    public void setStatusLineManager(IStatusLineManager statusLineManager) {
        if (fStatusLineManager != null && statusLineManager == null) {
            fStatusLineManager.setMessage(EMPTY_STRING);
        }
        fStatusLineManager = statusLineManager;
    }

    private void updateStatusLine(ITmfTimestamp delta) {
        if (fStatusLineManager != null) {
            if (delta != null) {
                fStatusLineManager.setMessage("\u0394: " + delta); //$NON-NLS-1$
            } else {
                fStatusLineManager.setMessage(null);
            }
        }
    }

    // ------------------------------------------------------------------------
    // Event cache
    // ------------------------------------------------------------------------

    /**
     * Notify that the event cache has been updated
     *
     * @param completed
     *            Also notify if the populating of the cache is complete, or
     *            not.
     */
    public void cacheUpdated(final boolean completed) {
        synchronized (fCacheUpdateSyncObj) {
            if (fCacheUpdateBusy) {
                fCacheUpdatePending = true;
                fCacheUpdateCompleted = completed;
                return;
            }
            fCacheUpdateBusy = true;
        }
        // Event cache is now updated. Perform update on the UI thread
        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                if (!fTable.isDisposed()) {
                    fTable.refresh();
                    packColumns();
                }
                if (completed) {
                    populateCompleted();
                }
                synchronized (fCacheUpdateSyncObj) {
                    fCacheUpdateBusy = false;
                    if (fCacheUpdatePending) {
                        fCacheUpdatePending = false;
                        cacheUpdated(fCacheUpdateCompleted);
                    }
                }
            }
        });
    }

    /**
     * Callback for when populating the table is complete.
     */
    protected void populateCompleted() {
        // Nothing by default;
    }

    // ------------------------------------------------------------------------
    // ISelectionProvider
    // ------------------------------------------------------------------------

    @Override
    public void addSelectionChangedListener(ISelectionChangedListener listener) {
        selectionChangedListeners.add(listener);
    }

    @Override
    public ISelection getSelection() {
        if (fTable == null || fTable.isDisposed()) {
            return StructuredSelection.EMPTY;
        }
        List<Object> list = new ArrayList<>(fTable.getSelection().length);
        for (TableItem item : fTable.getSelection()) {
            if (item.getData() != null) {
                list.add(item.getData());
            }
        }
        return new StructuredSelection(list);
    }

    @Override
    public void removeSelectionChangedListener(ISelectionChangedListener listener) {
        selectionChangedListeners.remove(listener);
    }

    @Override
    public void setSelection(ISelection selection) {
        // not implemented
    }

    /**
     * Notifies any selection changed listeners that the viewer's selection has
     * changed. Only listeners registered at the time this method is called are
     * notified.
     *
     * @param event
     *            a selection changed event
     *
     * @see ISelectionChangedListener#selectionChanged
     */
    protected void fireSelectionChanged(final SelectionChangedEvent event) {
        Object[] listeners = selectionChangedListeners.getListeners();
        for (int i = 0; i < listeners.length; ++i) {
            final ISelectionChangedListener l = (ISelectionChangedListener) listeners[i];
            SafeRunnable.run(new SafeRunnable() {
                @Override
                public void run() {
                    l.selectionChanged(event);
                }
            });
        }
    }

    // ------------------------------------------------------------------------
    // Bookmark handling
    // ------------------------------------------------------------------------

    /**
     * Add a bookmark to this event table.
     *
     * @param bookmarksFile
     *            The file to use for the bookmarks
     */
    public void addBookmark(final IFile bookmarksFile) {
        fBookmarksFile = bookmarksFile;
        final TableItem[] selection = fTable.getSelection();
        if (selection.length > 0) {
            final TableItem tableItem = selection[0];
            if (tableItem.getData(Key.RANK) != null) {
                final StringBuffer defaultMessage = new StringBuffer();
                for (int i = 0; i < fTable.getColumns().length; i++) {
                    if (i > 0) {
                        defaultMessage.append(", "); //$NON-NLS-1$
                    }
                    defaultMessage.append(tableItem.getText(i));
                }
                final InputDialog dialog = new MultiLineInputDialog(
                        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                        Messages.TmfEventsTable_AddBookmarkDialogTitle,
                        Messages.TmfEventsTable_AddBookmarkDialogMessage,
                        defaultMessage.toString());
                if (dialog.open() == Window.OK) {
                    final String message = dialog.getValue();
                    try {
                        final IMarker bookmark = bookmarksFile.createMarker(IMarker.BOOKMARK);
                        if (bookmark.exists()) {
                            bookmark.setAttribute(IMarker.MESSAGE, message.toString());
                            final Long rank = (Long) tableItem.getData(Key.RANK);
                            final int location = rank.intValue();
                            bookmark.setAttribute(IMarker.LOCATION, Integer.valueOf(location));
                            fBookmarksMap.put(rank, bookmark.getId());
                            fTable.refresh();
                        }
                    } catch (final CoreException e) {
                        displayException(e);
                    }
                }
            }
        }

    }

    /**
     * Remove a bookmark from this event table.
     *
     * @param bookmark
     *            The bookmark to remove
     */
    public void removeBookmark(final IMarker bookmark) {
        for (final Entry<Long, Long> entry : fBookmarksMap.entries()) {
            if (entry.getValue().equals(bookmark.getId())) {
                fBookmarksMap.remove(entry.getKey(), entry.getValue());
                fTable.refresh();
                return;
            }
        }
    }

    private void toggleBookmark(final Long rank) {
        if (fBookmarksFile == null) {
            return;
        }
        if (fBookmarksMap.containsKey(rank)) {
            final Collection<Long> markerIds = fBookmarksMap.removeAll(rank);
            fTable.refresh();
            try {
                for (long markerId : markerIds) {
                    final IMarker bookmark = fBookmarksFile.findMarker(markerId);
                    if (bookmark != null) {
                        bookmark.delete();
                    }
                }
            } catch (final CoreException e) {
                displayException(e);
            }
        } else {
            addBookmark(fBookmarksFile);
        }
    }

    /**
     * Refresh the bookmarks assigned to this trace, from the contents of a
     * bookmark file.
     *
     * @param bookmarksFile
     *            The bookmark file to use
     */
    public void refreshBookmarks(final IFile bookmarksFile) {
        fBookmarksFile = bookmarksFile;
        if (bookmarksFile == null) {
            fBookmarksMap.clear();
            fTable.refresh();
            return;
        }
        try {
            fBookmarksMap.clear();
            for (final IMarker bookmark : bookmarksFile.findMarkers(IMarker.BOOKMARK, false, IResource.DEPTH_ZERO)) {
                final int location = bookmark.getAttribute(IMarker.LOCATION, -1);
                if (location != -1) {
                    final long rank = location;
                    fBookmarksMap.put(rank, bookmark.getId());
                }
            }
            fTable.refresh();
        } catch (final CoreException e) {
            displayException(e);
        }
    }

    @Override
    public void gotoMarker(final IMarker marker) {
        final int rank = marker.getAttribute(IMarker.LOCATION, -1);
        if (rank != -1) {
            int index = rank;
            if (fTable.getData(Key.FILTER_OBJ) != null) {
                // +1 for top filter status row
                index = fCache.getFilteredEventIndex(rank) + 1;
            } else if (rank >= fTable.getItemCount()) {
                fPendingGotoRank = rank;
            }
            fSelectedRank = rank;
            fSelectedBeginRank = fSelectedRank;
            fTable.setSelection(index + 1); // +1 for header row
            updateStatusLine(null);
        }
    }

    // ------------------------------------------------------------------------
    // Listeners
    // ------------------------------------------------------------------------

    @Override
    public void colorSettingsChanged(final ColorSetting[] colorSettings) {
        fTable.refresh();
    }

    // ------------------------------------------------------------------------
    // Signal handlers
    // ------------------------------------------------------------------------

    /**
     * Handler for the trace updated signal
     *
     * @param signal
     *            The incoming signal
     */
    @TmfSignalHandler
    public void traceUpdated(final TmfTraceUpdatedSignal signal) {
        if ((signal.getTrace() != fTrace) || fTable.isDisposed()) {
            return;
        }
        // Perform the refresh on the UI thread
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                if (!fTable.isDisposed() && (fTrace != null)) {
                    if (fTable.getData(Key.FILTER_OBJ) == null) {
                        /* +1 for header row */
                        fTable.setItemCount((int) fTrace.getNbEvents() + 1);
                        /* +1 for header row */
                        if ((fPendingGotoRank != -1) && ((fPendingGotoRank + 1) < fTable.getItemCount())) {
                            /* +1 for header row */
                            fTable.setSelection((int) fPendingGotoRank + 1);
                            fPendingGotoRank = -1;
                            updateStatusLine(null);
                        }
                    } else {
                        startFilterThread();
                    }
                }
                if (!fRawViewer.isDisposed() && (fTrace != null)) {
                    fRawViewer.refreshEventCount();
                }
            }
        });
    }

    /**
     * Handler for the selection range signal.
     *
     * @param signal
     *            The incoming signal
     * @since 1.0
     */
    @TmfSignalHandler
    public void selectionRangeUpdated(final TmfSelectionRangeUpdatedSignal signal) {
        if ((signal.getSource() != this) && (fTrace != null) && (!fTable.isDisposed())) {

            /*
             * Create a request for one event that will be queued after other
             * ongoing requests. When this request is completed do the work to
             * select the actual event with the timestamp specified in the
             * signal. This procedure prevents the method fTrace.getRank() from
             * interfering and delaying ongoing requests.
             */
            final TmfEventRequest subRequest = new TmfEventRequest(ITmfEvent.class,
                    TmfTimeRange.ETERNITY, 0, 1, ExecutionType.FOREGROUND) {

                TmfTimestamp ts = new TmfTimestamp(signal.getBeginTime());

                @Override
                public void handleData(final ITmfEvent event) {
                    super.handleData(event);
                }

                @Override
                public void handleSuccess() {
                    super.handleSuccess();
                    if (fTrace == null) {
                        return;
                    }

                    /*
                     * Verify if the event is within the trace range and adjust
                     * if necessary
                     */
                    ITmfTimestamp timestamp = ts;
                    if (timestamp.compareTo(fTrace.getStartTime()) == -1) {
                        timestamp = fTrace.getStartTime();
                    }
                    if (timestamp.compareTo(fTrace.getEndTime()) == 1) {
                        timestamp = fTrace.getEndTime();
                    }

                    // Get the rank of the selected event in the table
                    final ITmfContext context = fTrace.seekEvent(timestamp);
                    final long rank = context.getRank();
                    context.dispose();

                    PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
                        @Override
                        public void run() {
                            // Return if table is disposed
                            if (fTable.isDisposed()) {
                                return;
                            }

                            fSelectedRank = rank;
                            fSelectedBeginRank = fSelectedRank;
                            int index = (int) rank;
                            if (fTable.getData(Key.FILTER_OBJ) != null) {
                                /* +1 for top filter status row */
                                index = fCache.getFilteredEventIndex(rank) + 1;
                            }
                            /* +1 for header row */
                            fTable.setSelection(index + 1);
                            fRawViewer.selectAndReveal(rank);
                            updateStatusLine(null);
                        }
                    });
                }
            };

            ((ITmfEventProvider) fTrace).sendRequest(subRequest);
        }
    }

    // ------------------------------------------------------------------------
    // Error handling
    // ------------------------------------------------------------------------

    /**
     * Display an exception in a message box
     *
     * @param e
     *            the exception
     */
    private static void displayException(final Exception e) {
        final MessageBox mb = new MessageBox(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
        mb.setText(e.getClass().getSimpleName());
        mb.setMessage(e.getMessage());
        mb.open();
    }

    /**
     * Refresh the table
     */
    public void refresh() {
        fCache.clear();
        fTable.refresh();
        fTable.redraw();
    }

    /**
     * Margin column for images and special text (e.g. collapse count)
     */
    private static final class TmfMarginColumn extends TmfEventTableColumn {

        private static final @NonNull ITmfEventAspect MARGIN_ASPECT = new ITmfEventAspect() {

            @Override
            public String getName() {
                return EMPTY_STRING;
            }

            @Override
            public String resolve(ITmfEvent event) {
                if (!(event instanceof CachedEvent) || ((CachedEvent) event).repeatCount == 0) {
                    return EMPTY_STRING;
                }
                return "+" + ((CachedEvent) event).repeatCount; //$NON-NLS-1$
            }

            @Override
            public String getHelpText() {
                return EMPTY_STRING;
            }
        };

        /**
         * Constructor
         */
        public TmfMarginColumn() {
            super(MARGIN_ASPECT);
        }
    }

}
