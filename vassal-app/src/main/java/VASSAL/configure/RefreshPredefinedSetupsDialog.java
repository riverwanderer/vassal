/*
 *
 * Copyright (c) 2005 by Rodney Kinney
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
package VASSAL.configure;

import VASSAL.build.GameModule;
import VASSAL.build.module.Documentation;
import VASSAL.build.module.GameRefresher;
import VASSAL.build.module.ModuleExtension;
import VASSAL.build.module.PredefinedSetup;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.i18n.Resources;
import VASSAL.preferences.Prefs;
import VASSAL.tools.DataArchive;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.swing.FlowLabel;
import VASSAL.tools.swing.SwingUtils;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

public class RefreshPredefinedSetupsDialog extends JDialog {
  private static final Logger logger = LoggerFactory.getLogger(RefreshPredefinedSetupsDialog.class);
  private static final long serialVersionUID = 1L;
  private JButton refreshButton;
  private JCheckBox nameCheck;
  private JCheckBox labelerNameCheck;
  private JCheckBox layerNameCheck;
  private JCheckBox rotateNameCheck;
  private JCheckBox testModeOn;
  private JCheckBox deletePieceNoMap;
  private JCheckBox refreshDecks;
  private JCheckBox deleteOldDecks;
  private JCheckBox addNewDecks;
  private JTextField pdsFilterBox;
  private String pdsFilter;
  private JCheckBox alertOn;
  private final Set<String> options = new HashSet<>();

  public RefreshPredefinedSetupsDialog(Frame owner) throws HeadlessException {
    super(owner, false);
    setTitle(Resources.getString("Editor.RefreshPredefinedSetupsDialog.title"));
    initComponents();
  }

  private void initComponents() {
    setLayout(new MigLayout("", "[fill]")); // NON-NLS

    final JPanel panel = new JPanel(new MigLayout("hidemode 3,wrap 1," + ConfigurerLayout.STANDARD_GAPY, "[fill]")); // NON-NLS
    panel.setBorder(BorderFactory.createEtchedBorder());

    final FlowLabel header = new FlowLabel(Resources.getString("GameRefresher.predefined_header"));
    panel.add(header);

    final JPanel buttonsBox = new JPanel(new MigLayout("ins 0", "push[]rel[]rel[]push")); // NON-NLS
    refreshButton = new JButton(Resources.getString("General.run"));
    refreshButton.addActionListener(e -> refreshPredefinedSetups());
    refreshButton.setEnabled(true);

    final JButton helpButton = new JButton(Resources.getString("General.help"));

    HelpFile hf = null;
    try {
      hf = new HelpFile(null, new File(
        new File(Documentation.getDocumentationBaseDir(), "ReferenceManual"),
        "SavedGameUpdater.html"));
    }
    catch (MalformedURLException ex) {
      ErrorDialog.bug(ex);
    }

    helpButton.addActionListener(new ShowHelpAction(hf.getContents(), null));

    final JButton closeButton = new JButton(Resources.getString("General.cancel"));

    closeButton.addActionListener(e -> dispose());

    buttonsBox.add(refreshButton, "tag ok,sg 1"); // NON-NLS
    buttonsBox.add(closeButton, "tag cancel,sg 1"); // NON-NLS
    buttonsBox.add(helpButton, "tag help,sg 1");     // NON-NLS

    nameCheck = new JCheckBox(Resources.getString("GameRefresher.use_basic_name"));
    panel.add(nameCheck);

    labelerNameCheck = new JCheckBox(Resources.getString("GameRefresher.use_labeler_descr"), true);
    panel.add(labelerNameCheck);
    layerNameCheck = new JCheckBox(Resources.getString("GameRefresher.use_layer_descr"), true);
    panel.add(layerNameCheck);
    rotateNameCheck = new JCheckBox(Resources.getString("GameRefresher.use_rotate_descr"), true);
    panel.add(rotateNameCheck);

    refreshDecks = new JCheckBox(Resources.getString("GameRefresher.refresh_decks"), false);
    refreshDecks.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        deleteOldDecks.setVisible(refreshDecks.isSelected());
        addNewDecks.setVisible(refreshDecks.isSelected());
      }
    });
    panel.add(refreshDecks);

    deleteOldDecks = new JCheckBox(Resources.getString("GameRefresher.delete_old_decks"), false);
    panel.add(deleteOldDecks, "gapx 10");

    addNewDecks = new JCheckBox(Resources.getString("GameRefresher.add_new_decks"), false);
    panel.add(addNewDecks, "gapx 10");

    // Separate less-accessed functions
    // FIXME: The separator disappears if the window is resized or too small when deck options are made visible.
    final JSeparator sep = new JSeparator(JSeparator.HORIZONTAL);
    panel.add(sep);

    testModeOn = new JCheckBox(Resources.getString("GameRefresher.test_mode"), false);
    panel.add(testModeOn);

    deletePieceNoMap = new JCheckBox(Resources.getString("GameRefresher.delete_piece_no_map"), true);
    panel.add(deletePieceNoMap);

    alertOn = new JCheckBox(Resources.getString("Editor.RefreshPredefinedSetups.alertOn"), false);
    panel.add(alertOn);

    // PDS can be set to refresh specific items only, based on a regex
    final JPanel filterPanel = new JPanel(new MigLayout(ConfigurerLayout.STANDARD_INSETS_GAPY, "[]rel[grow,fill,push]")); // NON-NLS
    filterPanel.add(new JLabel(Resources.getString("Editor.RefreshPredefinedSetups.filter_prompt")), "");
    pdsFilterBox = new HintTextField(32, Resources.getString("Editor.RefreshPredefinedSetups.filter_hint"));
    filterPanel.add(pdsFilterBox, "wrap");
    panel.add(filterPanel, "");

    panel.add(buttonsBox, "grow"); // NON-NLS
    add(panel, "grow"); // NON-NLS

    setLocationRelativeTo(getOwner());
    SwingUtils.repack(this);

    // Default actions on Enter/ESC
    SwingUtils.setDefaultButtons(getRootPane(), refreshButton, closeButton);

    deleteOldDecks.setVisible(refreshDecks.isSelected());
    addNewDecks.setVisible(refreshDecks.isSelected());
  }

  protected void  setOptions() {
    pdsFilter = pdsFilterBox.getText();

    options.clear();
    if (nameCheck.isSelected()) {
      options.add(GameRefresher.USE_NAME); //$NON-NLS-1$
    }
    if (labelerNameCheck.isSelected()) {
      options.add(GameRefresher.USE_LABELER_NAME); //$NON-NLS-1$
    }
    if (layerNameCheck.isSelected()) {
      options.add(GameRefresher.USE_LAYER_NAME); //$NON-NLS-1$
    }
    if (rotateNameCheck.isSelected()) {
      options.add(GameRefresher.USE_ROTATE_NAME); //$NON-NLS-1$
    }
    if (testModeOn.isSelected()) {
      options.add(GameRefresher.TEST_MODE); //$NON-NLS-1$
    }
    if (deletePieceNoMap.isSelected()) {
      options.add(GameRefresher.DELETE_NO_MAP); //$NON-NLS-1$
    }
    if (refreshDecks.isSelected()) {
      options.add(GameRefresher.REFRESH_DECKS); //NON-NLS
      if (deleteOldDecks.isSelected()) {
        options.add(GameRefresher.DELETE_OLD_DECKS); //NON-NLS
      }
      if (addNewDecks.isSelected()) {
        options.add(GameRefresher.ADD_NEW_DECKS); //NON-NLS
      }
    }

  }

  public void log(String message) {
    GameModule.getGameModule().warn(message);
    logger.info(message);
  }

  public boolean isTestMode() {
    return options.contains(GameRefresher.TEST_MODE); //$NON-NLS-1$
  }


  private boolean hasAlreadyRun = false;

  private void refreshPredefinedSetups() {
    if (hasAlreadyRun) {
      return;
    }

    hasAlreadyRun = true;
    refreshButton.setEnabled(false);

    setOptions();
    if (isTestMode()) {
      log(Resources.getString("GameRefresher.refresh_counters_test_mode"));
    }

    // Are we running a refresh on a main module or on an extension
    Boolean isRefreshOfExtension = true;
    final GameModule mod = GameModule.getGameModule();
    final DataArchive dataArchive = mod.getDataArchive();

    // pre-pack regex pattern in case filter string is not found directly
    Pattern p = null;

    if (pdsFilter != null) {
      // warn that filtering is active
      log("~" + Resources.getString("Editor.RefreshPredefinedSetups.setups_filter", ConfigureTree.noHTML(pdsFilter)));

      try {
        // matching, assuming Regex
        p = Pattern.compile(".*" + pdsFilter + ".*", CASE_INSENSITIVE);
      }
      catch (java.util.regex.PatternSyntaxException e) {
          // something went wrong, treat regex as embedded literal
        p = Pattern.compile(".*\\Q" + pdsFilter + "\\T.*", CASE_INSENSITIVE);
        log(Resources.getString("Editor.RefreshPredefinedSetups.filter_fallback")); //NON-NLS
      }
      pdsFilter = pdsFilter.toLowerCase();
    }

    final List<ModuleExtension>  moduleExtensionList = mod.getComponentsOf(ModuleExtension.class);
    if (moduleExtensionList.isEmpty()) {
      isRefreshOfExtension = false;
    }

    // FIXME: Rather than rely on the PDS structure, consider processing all .vsav files in the module (relevant for custom scenario choosers)
    final List<PredefinedSetup>  modulePdsAndMenus = mod.getAllDescendantComponentsOf(PredefinedSetup.class);
    final List<PredefinedSetup>  modulePds = new ArrayList<>();

    for (final PredefinedSetup pds : modulePdsAndMenus) {
      if (!pds.isMenu() && pds.isUseFile()) {
        //Exclude scenario folders (isMenu == true)
        // and exclude any "New game" entries (no predefined setup) (isUseFile == false)
        // !! Some New Game entries have UseFile = true and filename empty. Check file name too
        // PDS filtering option is implemented here...
        final String pdsName = pds.getAttributeValueString(pds.NAME);
        final String pdsFile = pds.getFileName();

        if (pdsFile != null && !pdsFile.isBlank()
                && (pdsFilter == null
                || (pdsName != null && (pdsName.toLowerCase().contains(pdsFilter) || (p != null && p.matcher(pdsName).matches())))
                || (pdsFile.toLowerCase().contains(pdsFilter) || (p != null && p.matcher(pdsFile).matches())))) {

          Boolean isExtensionPDS = true;

          try {
            isExtensionPDS =  !dataArchive.contains(pdsFile);
          }
          catch (final IOException e) {
            ErrorDialog.bug(e);
          }
          if (isExtensionPDS == isRefreshOfExtension) modulePds.add(pds);
        }
      }
    }

    // List out the items found
    log("`" + modulePds.size() + " " + Resources.getString(Resources.getString("GameRefresher.predefined_setups_found")));
    for (final PredefinedSetup pds : modulePds) log(pds.getAttributeValueString(pds.NAME) + " (" + pds.getFileName() + ")");

    final int pdsCount = modulePds.size();
    int i = 0;
    int refreshCount = 0;
    int flaggedFiles = 0;
    final Instant startTime = Instant.now();
    final Long memoryInUseAtStart = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024);

    // FIXME: It would be nice to split the refresh into two parts here, to allow cancel before the refresh commences
    // FIXME: A functioning cancel button would be useful here

    // Process the refreshes
    for (final PredefinedSetup pds : modulePds) {
      GameModule.getGameModule().getGameState().setup(false);  //BR// Ensure we clear any existing game data/listeners/objects out.
      GameModule.getGameModule().setRefreshingSemaphore(true); //BR// Raise the semaphore that suppresses GameState.setup()
      final String pdsFile = pds.getFileName();

      // Refresher window title updated to provide progress report
      final int pct = i * 100 / pdsCount;
      this.setTitle(Resources.getString("Editor.RefreshPredefinedSetupsDialog.progress", ++i, pdsCount, pct,
              (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024)));

      if (i > 1 && pdsFileProcessed(modulePds.subList(0, i - 1), pdsFile)) {
        // Skip duplicate file (already refreshed)
        log(Resources.getString(Resources.getString("Editor.RefreshPredefinedSetupsDialog.skip", pds.getAttributeValueString(pds.NAME), pdsFile)));
      }
      else {
        try {
          if (pds.refresh(options) > 0) flaggedFiles++;
          refreshCount++;
        }
        catch (final IOException e) {
          ErrorDialog.bug(e);
        }
        finally {
          GameModule.getGameModule().setRefreshingSemaphore(false); //BR// Make sure we definitely lower the semaphore
        }
      }
    }

    // Clean up and close the window
    if (!isTestMode() && pdsCount > 0) {
      if (alertOn.isSelected()) { // sound alert
        final SoundConfigurer c = (SoundConfigurer) Prefs.getGlobalPrefs().getOption("wakeUpSound");
        c.play();
      }

      GameModule.getGameModule().setDirty(true);  // ensure prompt to save when a refresh happened
    }

    GameModule.getGameModule().getGameState().setup(false); //BR// Clear out whatever data (pieces, listeners, etc.) left over from final game loaded.

    refreshButton.setEnabled(true);
    dispose(); // done with all that

    final Duration duration = Duration.between(startTime, Instant.now());

    log("|<b>" + Resources.getString("Editor.RefreshPredefinedSetups.end", refreshCount, flaggedFiles));

    log(Resources.getString("Editor.RefreshPredefinedSetups.stats",
            ofPattern("HH:mm:ss").format(LocalTime.ofSecondOfDay(duration.getSeconds())),
            memoryInUseAtStart,
            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024)));
  }

  private boolean pdsFileProcessed(List<PredefinedSetup> modulePds, String file) {
    for (final PredefinedSetup pds : modulePds) {
      if (pds.getFileName().equals(file)) return true;
    }
    return false;
  }

}
