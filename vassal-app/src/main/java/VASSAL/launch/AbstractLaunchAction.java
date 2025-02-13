/*
 * Copyright (c) 2008-2009 by Joel Uckelman
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */

package VASSAL.launch;

import VASSAL.Info;
import VASSAL.build.module.ExtensionsManager;
import VASSAL.build.module.GlobalOptions;
import VASSAL.build.module.metadata.AbstractMetaData;
import VASSAL.build.module.metadata.MetaDataFactory;
import VASSAL.build.module.metadata.ModuleMetaData;
import VASSAL.configure.DirectoryConfigurer;
import VASSAL.i18n.Resources;
import VASSAL.preferences.Prefs;
import VASSAL.preferences.ReadOnlyPrefs;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.ProblemDialog;
import VASSAL.tools.ThrowableUtils;
import VASSAL.tools.WarningDialog;
import VASSAL.tools.concurrent.FutureUtils;
import VASSAL.tools.deprecation.RemovalAndDeprecationChecker;
import VASSAL.tools.filechooser.FileChooser;
import VASSAL.tools.filechooser.ModuleFileFilter;
import VASSAL.tools.io.ProcessLauncher;
import VASSAL.tools.io.ProcessWrapper;
import VASSAL.tools.lang.MemoryUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipFile;

/**
 *
 * The base class for {@link javax.swing.Action}s which launch processes from the
 * {@link ModuleManagerWindow}.
 *
 * @author Joel Uckelman
 * @since 3.1.0
 */
public abstract class AbstractLaunchAction extends AbstractAction {
  private static final long serialVersionUID = 1L;

  private static final Logger logger =
    LoggerFactory.getLogger(AbstractLaunchAction.class);

  //
  // memory-related constants
  //
  protected static final int PHYS_MEMORY;
  public static final int DEFAULT_MAXIMUM_HEAP = 1024;
  protected static final int FAILSAFE_MAXIMUM_HEAP = 128;

  static {
    // Determine how much physical RAM this machine has
    // Assume 4GB if we can't determine how much RAM there is
    final long physMemoryBytes = MemoryUtils.getPhysicalMemory();
    PHYS_MEMORY = physMemoryBytes <= 0 ? 4096 : (int)(physMemoryBytes >> 20);
  }

  protected final Window window;
  protected final String entryPoint;
  protected final LaunchRequest lr;

  private static final UseTracker useTracker = new UseTracker();

  public static UseTracker getUseTracker() {
    return useTracker;
  }

  public AbstractLaunchAction(String name, Window window,
                              String entryPoint, LaunchRequest lr) {
    super(name);

    this.window = window;
    this.entryPoint = entryPoint;
    this.lr = lr;
  }

  /**
   * @return <code>true</code> if any files are in use
   */
  public static boolean anyInUse() {
    return useTracker.anyInUse();
  }

  /**
   * @param file the file to check
   * @return <code>true</code> if the file is in use
   */
  public static boolean isInUse(File file) {
    return useTracker.isInUse(file);
  }

  /**
   * @param file the file to check
   * @return <code>true</code> if the file is being edited
   */
  public static boolean isEditing(File file) {
    return useTracker.isEditing(file);
  }

  protected static void incrementUsed(File file) {
    useTracker.incrementUsed(file);
  }

  protected static void decrementUsed(File file) {
    useTracker.decrementUsed(file);
  }

  protected static void markEditing(File file) {
    useTracker.markEditing(file);
  }

  protected static void unmarkEditing(File file) {
    useTracker.unmarkEditing(file);
  }

  /** {@inheritDoc} */
  @Override
  public void actionPerformed(ActionEvent e) {
    ModuleManagerWindow.getInstance().setWaitCursor(true);
    getLaunchTask().execute();
  }

  protected abstract LaunchTask getLaunchTask();

  protected File promptForFile() {
    // prompt the user to pick a file
    final FileChooser fc = FileChooser.createFileChooser(window,
      (DirectoryConfigurer)
        Prefs.getGlobalPrefs().getOption(Prefs.MODULES_DIR_KEY));

    addFileFilters(fc);

    // loop until cancellation or we get an existing file
    if (fc.showOpenDialog() == FileChooser.APPROVE_OPTION) {
      lr.module = fc.getSelectedFile();
      if (lr.module != null) {
        if (lr.module.exists()) {
          final AbstractMetaData metadata =
            MetaDataFactory.buildMetaData(lr.module);
          if (!(metadata instanceof ModuleMetaData)) {
            ErrorDialog.show(
              "Error.invalid_vassal_module", lr.module.getAbsolutePath()); //NON-NLS
            logger.error(
              "-- Load of {} failed: Not a Vassal module", //NON-NLS
              lr.module.getAbsolutePath()
            );
            lr.module = null;
          }
        }
        else {
          lr.module = null;
        }
// FIXME: do something to warn about nonexistent file
//        FileNotFoundDialog.warning(window, lr.module);
      }
    }

    return lr.module;
  }

  protected void addFileFilters(FileChooser fc) {
    fc.addChoosableFileFilter(new ModuleFileFilter());
  }

  protected boolean checkRemovedAndDeprecated(File f) throws IOException {
    // Check for usage of removed and deprecated classes, methods, fields
    final RemovalAndDeprecationChecker rdc = new RemovalAndDeprecationChecker();
    final Pair<Map<String, Map<String, String>>, Map<String, Map<String, String>>> rd;
    try (ZipFile zf = new ZipFile(f.getAbsolutePath())) {
      rd = rdc.check(zf);
    }

    final Map<String, Map<String, String>> removed = rd.getLeft();
    if (!removed.isEmpty()) {
      final String msg =
        "Removed classes, methods, and fields in " + f.toString() +
        "\n(used by => removed item, version when removed\n" +
        RemovalAndDeprecationChecker.formatResult(removed);

      logger.error(msg);

      FutureUtils.wait(ProblemDialog.showDetails(
        JOptionPane.ERROR_MESSAGE,
        msg,
        "Dialogs.removed_code"
      ));
      // Using anything removed is fatal
      return false;
    }

    final Map<String, Map<String, String>> deprecated = rd.getRight();
    if (!deprecated.isEmpty()) {
      // always show deprecation dialog in edit mode
      boolean showDialog = lr.mode == LaunchRequest.Mode.EDIT ||
                           lr.mode == LaunchRequest.Mode.EDIT_EXT;
      final LocalDate sixMonthsFromNow = LocalDate.now().plusMonths(6);

      // convert deprecation date to date eligible for removal (+1 year)
      final DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
      for (final Map.Entry<String, Map<String, String>> e1: deprecated.entrySet()) {
        for (final Map.Entry<String, String> e2: e1.getValue().entrySet()) {
          final LocalDate removalDate = LocalDate.parse(e2.getValue(), fmt).plusYears(1);
          // show deprecation dialog if anything is within 6 months of removal
          if (removalDate.isBefore(sixMonthsFromNow)) {
            showDialog = true;
          }

          e2.setValue(removalDate.toString());
        }
      }

      final String msg =
        "Deprecated classes, methods, and fields in " + f.toString() +
        "\n(used by => removed item, date eligible for removal)\n" +
        RemovalAndDeprecationChecker.formatResult(deprecated);

      logger.warn(msg);

      if (showDialog) {
        FutureUtils.wait(ProblemDialog.showDetails(
          JOptionPane.WARNING_MESSAGE,
          msg,
          "Dialogs.deprecated_code"
        ));
      }
    }

    return true;
  }

  protected class LaunchTask extends SwingWorker<Void, Void> {
    // lr might be modified before the task is over, keep a local copy
    protected final LaunchRequest lr =
      new LaunchRequest(AbstractLaunchAction.this.lr);

    @Override
    public Void doInBackground() throws InterruptedException,
                                        IOException {
// FIXME: this should be in an abstract method and farmed out to subclasses
      // send some basic information to the log
      if (lr.module != null) {
        logger.info("Loading module file {}", lr.module.getAbsolutePath()); //NON-NLS

        // check for removed and deprecated elements
        if (!checkRemovedAndDeprecated(lr.module)) {
          return null;
        }

        final ExtensionsManager mgr = new ExtensionsManager(lr.module);
        for (final File ext : mgr.getActiveExtensions()) {
          if (!checkRemovedAndDeprecated(ext)) {
            return null;
          }
        }

        // read maximum heap size from global prefs
        final int max_tiler_heap = getHeapSize(
          Prefs.getGlobalPrefs(),
          ModuleManager.TILER_MAXIMUM_HEAP,
          3*PHYS_MEMORY/4
        );

        // slice tiles for module
        final String aname = lr.module.getAbsolutePath();
        final ModuleMetaData meta = new ModuleMetaData(new ZipFile(aname));
        final String hstr =
          DigestUtils.sha1Hex(meta.getName() + "_" + meta.getVersion());

        final File cdir = new File(Info.getCacheDir(), "tiles/" + hstr);

        final TilingHandler th = new TilingHandler(
          aname,
          cdir,
          new Dimension(256, 256),
          max_tiler_heap
        );

        try {
          th.sliceTiles();
        }
        catch (CancellationException e) {
          cancel(true);
          return null;
        }

        // slice tiles for extensions
        for (final File ext : mgr.getActiveExtensions()) {
          final TilingHandler eth = new TilingHandler(
            ext.getAbsolutePath(),
            cdir,
            new Dimension(256, 256),
            max_tiler_heap
          );

          try {
            eth.sliceTiles();
          }
          catch (CancellationException e) {
            cancel(true);
            return null;
          }
        }
      }

      if (lr.game != null) {
        logger.info("Loading game file {}", lr.game.getAbsolutePath()); //NON-NLS
      }

      if (lr.importFile != null) {
        logger.info(
          "Importing module file {}", //NON-NLS
          lr.importFile.getAbsolutePath()
        );
      }
// end FIXME

      // set default heap size
      int maximumHeap = DEFAULT_MAXIMUM_HEAP;

      String moduleName = null;

// FIXME: this should be in an abstract method and farmed out to subclasses,
// rather than a case structure for each kind of thing which may be loaded.
      // find module-specific heap settings, if any
      if (lr.module != null) {
        final AbstractMetaData data = MetaDataFactory.buildMetaData(lr.module);

        if (data == null) {
          ErrorDialog.show(
            "Error.invalid_vassal_file", lr.module.getAbsolutePath()); //NON-NLS
          return null;
        }

        if (data instanceof ModuleMetaData) {
          moduleName = ((ModuleMetaData) data).getName();

          // log the module name
          logger.info("Loading module {}", moduleName); //NON-NLS

          // read module prefs
          final ReadOnlyPrefs p = new ReadOnlyPrefs(moduleName);

          // read maximum heap size from module prefs
          maximumHeap = getHeapSize(
            p, GlobalOptions.MAXIMUM_HEAP, DEFAULT_MAXIMUM_HEAP
          );

          // log the JVM maximum heap
          logger.info("JVM maximum heap size: {} MB", maximumHeap); //NON-NLS

        }
      }
      else if (lr.importFile != null) {
        final Prefs p = Prefs.getGlobalPrefs();

        // read maximum heap size from global prefs
        maximumHeap = getHeapSize(
          p, ModuleManager.CONVERTER_MAXIMUM_HEAP, DEFAULT_MAXIMUM_HEAP
        );
      }
// end FIXME

      //
      // Heap size sanity checks: fall back to failsafe heap sizes in
      // case the given initial or maximum heap is not usable.
      //

// FIXME: The heap size messages are too nonspecific. They should
// differentiate between loading a module and importing a module,
// since the heap sizes are set in different places for those two
// actions.
      // maximum heap must fit in physical RAM
      if (maximumHeap > PHYS_MEMORY) {
        maximumHeap = FAILSAFE_MAXIMUM_HEAP;

        FutureUtils.wait(WarningDialog.show(
          "Warning.maximum_heap_too_large", //NON-NLS
          FAILSAFE_MAXIMUM_HEAP
        ));
      }
      // maximum heap must be at least the failsafe size
      else if (maximumHeap < FAILSAFE_MAXIMUM_HEAP) {
        maximumHeap = FAILSAFE_MAXIMUM_HEAP;

        FutureUtils.wait(WarningDialog.show(
          "Warning.maximum_heap_too_small", //NON-NLS
          FAILSAFE_MAXIMUM_HEAP
        ));
      }

      final int initialHeap = maximumHeap;

      final List<String> argumentList = buildArgumentList(moduleName);
      final String[] args = argumentList.toArray(new String[0]);

      // try to start a child process with the given heap sizes
      args[1] = "-Xms" + initialHeap + "M"; //NON-NLS
      args[2] = "-Xmx" + maximumHeap + "M"; //NON-NLS

      ProcessWrapper proc = new ProcessLauncher().launch(args);
      try {
        proc.future.get(1000L, TimeUnit.MILLISECONDS);
      }
      catch (CancellationException e) {
        cancel(true);
        return null;
      }
      catch (ExecutionException e) {
        logger.error("", e);
      }
      catch (TimeoutException e) {
        // this is expected
      }

      // if launch failed, use conservative heap sizes
      if (proc.future.isDone()) {
        args[1] = "-Xms" + FAILSAFE_MAXIMUM_HEAP + "M"; //NON-NLS
        args[2] = "-Xmx" + FAILSAFE_MAXIMUM_HEAP + "M"; //NON-NLS
        proc = new ProcessLauncher().launch(args);

        try {
          proc.future.get(1000L, TimeUnit.MILLISECONDS);
        }
        catch (ExecutionException e) {
          logger.error("", e);
        }
        catch (TimeoutException e) {
          // this is expected
        }

        if (proc.future.isDone()) {
          throw new IOException("failed to start child process");
        }
        else {
          FutureUtils.wait(WarningDialog.show(
            "Warning.maximum_heap_too_large", //NON-NLS
            FAILSAFE_MAXIMUM_HEAP
          ));
        }
      }

      final ModuleManagerWindow mmw = ModuleManagerWindow.getInstance();
      if (lr.module != null) {
        mmw.addModule(lr.module);
      }
      mmw.setWaitCursor(false);

      try {
        proc.future.get();
      }
      catch (ExecutionException e) {
        logger.error("", e);
      }

      return null;
    }

    private int strToInt(Object val, int defaultVal) {
      if (val == null) {
        return defaultVal;
      }

      try {
        return Integer.parseInt(val.toString());
      }
      catch (NumberFormatException ex) {
        return -1;
      }
    }

    protected int getHeapSize(ReadOnlyPrefs p, String key, int defaultHeap) {
      // read heap size, if it exists
      return strToInt(p.getStoredValue(key), defaultHeap);
    }

    protected int getHeapSize(Prefs p, String key, int defaultHeap) {
      // read heap size, if it exists
      return strToInt(p.getValue(key), defaultHeap);
    }

    @Override
    protected void done() {
      try {
        get();
      }
      catch (CancellationException e) {
        // this means that loading was cancelled
      }
      catch (ExecutionException e) {
        if (SystemUtils.IS_OS_WINDOWS &&
            ThrowableUtils.getAncestor(IOException.class, e) != null) {
          final String msg = e.getMessage();
          if (msg.contains("jre\\bin\\java") && msg.contains("CreateProcess")) {
            ErrorDialog.showDetails(
              e,
              ThrowableUtils.getStackTrace(e),
              "Error.possible_windows_av_interference",
              msg
            );
            return;
          }
        }
        ErrorDialog.bug(e);
      }
      catch (InterruptedException e) {
        ErrorDialog.bug(e);
      }
      finally {
        ModuleManagerWindow.getInstance().setWaitCursor(false);
      }
    }

    private List<String> buildArgumentList(String moduleName) {
      final List<String> result = new ArrayList<>();

      result.add(Info.getJavaBinPath().getAbsolutePath());
      result.add("");   // reserved for initial heap
      result.add("");   // reserved for maximum heap

      result.addAll(new CustomVmOptions().getCustomVmOptions());

      // pass on the user's home, if it's set
      final String userHome = System.getProperty("user.home");
      if (userHome != null) result.add("-Duser.home=" + userHome); //NON-NLS

      // pass on the user's working dir, if it's set
      final String userDir = System.getProperty("user.dir");
      if (userDir != null) result.add("-Duser.dir=" + userDir); //NON-NLS

      // pass on VASSAL's conf dir, if it's set
      final String vConf = System.getProperty("VASSAL.conf");
      if (vConf != null) result.add("-DVASSAL.conf=" + vConf); //NON-NLS

      // set the classpath
      result.add("-cp"); //NON-NLS
      result.add(System.getProperty("java.class.path"));

      if (SystemUtils.IS_OS_MAC) {
        // set the MacOS dock parameters

        // use the module name for the dock if we found a module name
// FIXME: should "Unnamed module" be localized?
        final String d_name = moduleName != null && moduleName.length() > 0
          ? moduleName : Resources.getString("Editor.AbstractLaunchAction.unnamed_module"); //NON-NLS

        // get the path to the app icon
        final String d_icon = new File(Info.getBaseDir(),
          "Contents/Resources/VASSAL.icns").getAbsolutePath();

        result.add("-Xdock:name=" + d_name); //NON-NLS
        result.add("-Xdock:icon=" + d_icon); //NON-NLS

        // Apple Silicon Macs need FBOs disabled in OpenGL, at least until
        // Metal in Java 17
        final Boolean disableOGLFBO =
          (Boolean) Prefs.getGlobalPrefs().getValue(Prefs.DISABLE_OGL_FBO);
        if (Boolean.TRUE.equals(disableOGLFBO)) {
          result.add("-Dsun.java2d.opengl=true"); //NON-NLS
          result.add("-Dsun.java2d.opengl.fbobject=false"); //NON-NLS
        }
      }
      else if (SystemUtils.IS_OS_WINDOWS) {
        // Disable the 2D to Direct3D pipeline?
        final Boolean disableD3d =
          (Boolean) Prefs.getGlobalPrefs().getValue(Prefs.DISABLE_D3D);
        if (Boolean.TRUE.equals(disableD3d)) {
          result.add("-Dsun.java2d.d3d=false"); //NON-NLS
        }
      }

      result.add(entryPoint);
      result.addAll(Arrays.asList(lr.toArgs()));
      return result;
    }
  }
}
