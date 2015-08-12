/**********************************************************************
 * Copyright (c) 2015 École Polytechnique de Montréal, Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Contributors:
 *   Patrick-Jeffrey Pollo Guilbert, William Enright,
 *      William Tri-Khiem Truong - Initial API and implementation
 *   Bernd Hufmann - Renamed from ProfileHandler and redesign
 **********************************************************************/
package org.eclipse.tracecompass.internal.lttng2.control.ui.views.dialogs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.remote.core.IRemoteConnection;
import org.eclipse.remote.ui.widgets.RemoteResourceBrowserWidget;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.tracecompass.internal.lttng2.control.core.LttngProfileManager;
import org.eclipse.tracecompass.internal.lttng2.control.ui.Activator;
import org.eclipse.tracecompass.internal.lttng2.control.ui.views.messages.Messages;
import org.eclipse.tracecompass.internal.lttng2.control.ui.views.service.LTTngControlServiceConstants;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

/**
 * Dialog box for collecting parameter for loading a session.
 *
 * @author Bernd Hufmann
 * @author Patrick-Jeffrey Pollo Guilbert
 * @author William Enright
 * @author William Tri-Khiem Truong
 *
 */
public class LoadDialog extends TitleAreaDialog implements ILoadDialog {
    // ------------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------------
    /** The icon file for this dialog box. */
    public static final String IMPORT_ICON_FILE = "icons/elcl16/import_button.png"; //$NON-NLS-1$

    // ------------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------------
    /**
     * The dialog composite.
     */
    private Composite fDialogComposite = null;
    private Button fLocalButton = null;
    private Button fRemoteButton = null;

    private Composite fLocalComposite = null;
    private CheckboxTreeViewer fFolderViewer;

    private Button fForceButton = null;

    private RemoteResourceBrowserWidget fFileWidget;
    private IRemoteConnection fConnection = null;

    private List<IFileStore> fLocalFiles = null;
    private List<IFileStore> fRemoteFiles = null;

    private boolean fIsForce = true;

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------
    /**
     * Constructor
     *
     * @param shell
     *            - a shell for the display of the dialog
     */
    public LoadDialog(Shell shell) {
        super(shell);
        setShellStyle(SWT.RESIZE | getShellStyle());
    }

    @Override
    public List<IFileStore> getRemoteResources() {
        return fRemoteFiles;
    }

    @Override
    public List<IFileStore> getLocalResources() {
        return fLocalFiles;
    }

    @Override
    public boolean isForce() {
        return fIsForce;
    }

    @Override
    public void initialize (IRemoteConnection connection) {
        fConnection = connection;
        fIsForce = true;
        fRemoteFiles = null;
        fLocalFiles = null;
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------
    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(Messages.TraceControl_LoadDialogTitle);
        newShell.setImage(Activator.getDefault().loadIcon(IMPORT_ICON_FILE));
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        // Main dialog panel
        fDialogComposite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, true);
        fDialogComposite.setLayout(layout);
        GridData data = new GridData(GridData.FILL_BOTH);
        data.heightHint = 300;
        fDialogComposite.setLayoutData(data);

        createSelectionGroup();
        createOptionComposite();
        fLocalComposite = null;
        fFileWidget = null;
        createLocalComposite();
        setMessage(Messages.TraceControl_SelectProfileText);
        return fDialogComposite;
    }

    private void createSelectionGroup() {
        Composite group = new Composite(fDialogComposite, SWT.BORDER);
        group.setLayout(new GridLayout(2, true));
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        fLocalButton = new Button(group, SWT.RADIO);
        fLocalButton.setText(Messages.TraceControl_LocalButtonText);
        fLocalButton.setLayoutData(new GridData(GridData.FILL_BOTH));
        fLocalButton.setSelection(true);

        fRemoteButton = new Button(group, SWT.RADIO);
        fRemoteButton.setText(Messages.TraceControl_RemoteButtonText);
        fRemoteButton.setLayoutData(new GridData(GridData.FILL_BOTH));

        fLocalButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (fLocalButton.getSelection()) {
                    disposeRemoteComposite();
                    createLocalComposite();
                    fRemoteFiles = null;
                    fDialogComposite.layout();
                    enableLocalButtons();
                }
            }
        });

        fRemoteButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (fRemoteButton.getSelection()) {
                    disposeLocalComposite();
                    createRemoteComposite();
                    fLocalFiles = null;
                    fDialogComposite.layout();
                    enableRemoteButtons();
                }
            }
        });
    }

    private void createLocalComposite() {
        if (fLocalComposite == null) {
            fLocalComposite = new Composite(fDialogComposite, SWT.BORDER);
            fLocalComposite.setLayout(new GridLayout(2, false));
            fLocalComposite.setLayoutData(new GridData(GridData.FILL_BOTH));

            Composite viewerComposite = new Composite(fLocalComposite, SWT.NONE);
            viewerComposite.setLayout(new GridLayout(1, false));
            viewerComposite.setLayoutData(new GridData(GridData.FILL_BOTH));

            fFolderViewer = new CheckboxTreeViewer(viewerComposite);
            fFolderViewer.setContentProvider(new ProfileContentProvider());
            fFolderViewer.setLabelProvider(new ProfileLabelProvider());
            fFolderViewer.setInput(LttngProfileManager.getProfiles());

            GridData data = new GridData(GridData.FILL_BOTH);
            Tree tree = fFolderViewer.getTree();
            tree.setLayoutData(data);

            fFolderViewer.addCheckStateListener(new ICheckStateListener() {
                @Override
                public void checkStateChanged(CheckStateChangedEvent event) {
                    enableLocalButtons();
                }
            });
        }
    }

    /**
     * Disposes the remote composite (if existing)
     */
    private void disposeLocalComposite() {
        if (fLocalComposite != null) {
            fLocalComposite.dispose();
            fLocalComposite = null;
        }
    }

    private void createRemoteComposite() {
        if (fFileWidget == null) {
            fFileWidget = new RemoteResourceBrowserWidget(fDialogComposite, SWT.BORDER, RemoteResourceBrowserWidget.SHOW_HIDDEN_CHECKBOX);
            fFileWidget.setLayoutData(new GridData(GridData.FILL_BOTH));
            fFileWidget.setInitialPath(LTTngControlServiceConstants.DEFAULT_PATH);
            fFileWidget.setConnection(fConnection);
            fFileWidget.addSelectionChangedListener(new ISelectionChangedListener() {
                @Override
                public void selectionChanged(SelectionChangedEvent event) {
                    enableRemoteButtons();
                }
            });
            getButton(IDialogConstants.OK_ID).setEnabled(true);
        }
    }

    /**
     * Disposes the remote composite (if existing)
     */
    private void disposeRemoteComposite() {
        if (fFileWidget != null) {
            fFileWidget.dispose();
            fFileWidget = null;
        }
    }

    private void createOptionComposite() {
        Composite group = new Composite(fDialogComposite, SWT.BORDER);
        group.setLayout(new GridLayout(1, true));
        group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        fForceButton = new Button(group, SWT.CHECK);
        fForceButton.setText(Messages.TraceControl_ForceButtonText);
        fForceButton.setSelection(true);
    }

    private void enableLocalButtons() {
        Object[] checked = fFolderViewer.getCheckedElements();
        boolean enabled = (checked != null) && (checked.length > 0);
        Button okButton = getButton(IDialogConstants.OK_ID);
        if (okButton != null) {
            okButton.setEnabled(enabled);
        }
    }

    private void enableRemoteButtons() {
        List<IFileStore> resources = fFileWidget.getResources();
        boolean enabled = (resources != null) && (resources.size() > 0);
        Button okButton = getButton(IDialogConstants.OK_ID);
        if (okButton != null) {
            okButton.setEnabled(enabled);
        }
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
        Button button = createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, false);
        button.setEnabled(false);
    }

    @Override
    protected void okPressed() {
        fIsForce = fForceButton.getSelection();
        if (fFileWidget != null) {
            fRemoteFiles = fFileWidget.getResources();
            if (fRemoteFiles.size() > 0) {
                super.okPressed();
            }
            return;
        }

        Object[] files = fFolderViewer.getCheckedElements();
        List<IFileStore> stores = new ArrayList<>();
        for (Object file : files) {
            if (file instanceof File) {
                stores.add(EFS.getLocalFileSystem().fromLocalFile((File) file));
            }
        }
        if (stores.size() != 0) {
            fLocalFiles = stores;
            super.okPressed();
        }
    }

    /**
     * Helper class for the contents of a folder in a tracing project
     *
     * @author Bernd Hufmann
     */
    public static class ProfileContentProvider implements ITreeContentProvider {
        @Override
        public Object[] getChildren(Object o) {

            if (o instanceof File[]) {
                return (File[]) o;
            }
            File store = (File) o;
            if (store.isDirectory()) {
                return store.listFiles();
            }
            return new Object[0];
        }

        @Override
        public Object getParent(Object element) {
            return ((File) element).getParent();
        }

        @Override
        public void dispose() {
        }

        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        }

        @Override
        public Object[] getElements(Object inputElement) {
            return getChildren(inputElement);
        }

        @Override
        public boolean hasChildren(Object element) {
            return ((File) element).isDirectory();
        }
    }

    static class ProfileLabelProvider extends LabelProvider {
        @Override
        public String getText(Object element) {
            return ((File) element).getName();
        }

        @Override
        public Image getImage(Object element) {
            if (((File) element).isDirectory()) {
                return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FOLDER);
            }
            return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FILE);
        }
    }
}
