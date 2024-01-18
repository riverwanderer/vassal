/*
 *
 * Copyright (c) 2004 by Rodney Kinney
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
package VASSAL.build.module;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.BadDataReport;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.IllegalBuildException;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.command.Command;
import VASSAL.configure.ComponentDescription;
import VASSAL.configure.VisibilityCondition;
import VASSAL.i18n.Resources;
import VASSAL.tools.ArchiveWriter;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.io.ZipArchive;
import VASSAL.tools.menu.ChildProxy;
import VASSAL.tools.menu.MenuItemProxy;
import VASSAL.tools.menu.MenuManager;
import VASSAL.tools.menu.MenuProxy;
import VASSAL.tools.menu.ParentProxy;

import javax.swing.AbstractAction;
import javax.swing.Action;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Defines a saved game that is accessible from the File menu.
 * The game will be loaded in place of a normal New Game
 */
public class PredefinedSetup extends AbstractConfigurable implements GameComponent, ComponentDescription {
  public static final String NAME = "name"; //$NON-NLS-1$
  public static final String FILE = "file"; //$NON-NLS-1$
  public static final String USE_FILE = "useFile"; //$NON-NLS-1$
  public static final String IS_MENU = "isMenu"; //$NON-NLS-1$
  public static final String DESCRIPTION = "description"; //NON-NLS

  protected boolean isMenu;
  protected boolean useFile = true;
  protected String fileName;

  protected MenuItemProxy menuItem;
  protected MenuProxy menu;

  protected VisibilityCondition showFile;
  protected VisibilityCondition showUseFile;
  protected AbstractAction launchAction;
  private final Set<String> refresherOptions = new HashSet<>();

  protected String description;

  public PredefinedSetup() {
    launchAction = new AbstractAction() {
      private static final long serialVersionUID = 1L;

      @Override
      public void actionPerformed(ActionEvent e) {
        launch();
      }
    };
    menuItem = new MenuItemProxy(launchAction, true);

    menu = new MenuProxy(true);

    showFile = () -> !isMenu && useFile;

    showUseFile = () -> !isMenu;
  }

  @Override
  public String[] getAttributeDescriptions() {
    return new String[]{
      Resources.getString(Resources.NAME_LABEL),
      Resources.getString(Resources.DESCRIPTION),
      Resources.getString("Editor.PredefinedSetup.parent_menu"), //$NON-NLS-1$
      Resources.getString("Editor.PredefinedSetup.predefined_file"), //$NON-NLS-1$
      Resources.getString("Editor.PredefinedSetup.saved_game") //$NON-NLS-1$
    };
  }

  @Override
  public Class<?>[] getAttributeTypes() {
    return new Class<?>[]{
      String.class,
      String.class,
      Boolean.class,
      Boolean.class,
      File.class
    };
  }

  @Override
  public String[] getAttributeNames() {
    return new String[]{
      NAME,
      DESCRIPTION,
      IS_MENU,
      USE_FILE,
      FILE
    };
  }

  @Override
  public String getAttributeValueString(String key) {
    if (NAME.equals(key)) {
      return getConfigureName();
    }
    else if (FILE.equals(key)) {
      return fileName;
    }
    else if (USE_FILE.equals(key)) {
      return String.valueOf(useFile);
    }
    else if (IS_MENU.equals(key)) {
      return String.valueOf(isMenu);
    }
    else if (DESCRIPTION.equals(key)) {
      return description;
    }
    else {
      return null;
    }
  }

  @Override
  public void setAttribute(String key, Object value) {
    if (NAME.equals(key)) {
      setConfigureName((String) value);
      menuItem.getAction().putValue(Action.NAME, value);
      menu.setText((String) value);
    }
    else if (USE_FILE.equals(key)) {
      if (value instanceof String) {
        value = Boolean.valueOf((String) value);
      }
      useFile = (Boolean) value;
    }
    else if (FILE.equals(key)) {
      if (value instanceof File) {
        value = ((File) value).getName();
      }
      fileName = (String) value;
    }
    else if (IS_MENU.equals(key)) {
      if (value instanceof String) {
        value = Boolean.valueOf((String) value);
      }
      setMenu((Boolean) value);
    }
    else if (DESCRIPTION.equals(key)) {
      description = (String)value;
    }
  }

  @Override
  public VisibilityCondition getAttributeVisibility(String name) {
    if (FILE.equals(name)) {
      return showFile;
    }
    else if (USE_FILE.equals(name)) {
      return showUseFile;
    }
    else if (IS_MENU.equals(name)) {
      return () -> getBuildables().isEmpty();
    }
    else {
      return super.getAttributeVisibility(name);
    }
  }

  @Override
  public String getDescription() {
    return description;
  }

  public void launch() {
    final GameModule g = GameModule.getGameModule();
    if (!g.getGameState().isNewGameAllowed()) return;

    if (useFile && fileName != null) {
      try {
        g.getGameState().loadGameInBackground(fileName, getSavedGameContents(), true);
        g.setGameFile(fileName, GameModule.GameFileMode.LOADED_GAME);
      }
      catch (IOException e) {
        ErrorDialog.dataWarning(new BadDataReport(this, Resources.getString("Error.not_found", "Setup"), fileName, e)); //$NON-NLS-1$ //$NON-NLS-2$
      }
    }
    else {
      g.setGameFile(fileName, GameModule.GameFileMode.NEW_GAME);
      GameModule.getGameModule().getGameState().setup(false);
      GameModule.getGameModule().getGameState().setup(true);
      GameModule.getGameModule().getGameState().freshenStartupGlobalKeyCommands(GameModule.getGameModule());
    }
  }

  public InputStream getSavedGameContents() throws IOException {
    return GameModule.getGameModule().getDataArchive().getInputStream(fileName);
  }

  private ChildProxy<?> getMenuInUse() {
    return isMenu ? menu : menuItem;
  }

  private void setMenu(boolean isMenu) {
    if (isMenu == this.isMenu) return;

    final ChildProxy<?> inUse = getMenuInUse();
    final ParentProxy parent = inUse.getParent();

    if (parent != null) {
      // swap our items if one is already in the menu
      final ChildProxy<?> notInUse = this.isMenu ? menuItem : menu;

      parent.insert(notInUse, parent.getIndex(inUse));
      parent.remove(inUse);
    }

    this.isMenu = isMenu;
  }

  @Override
  public void addTo(Buildable parent) {
    if (parent instanceof GameModule) {
      MenuManager.getInstance().addToSection("PredefinedSetup", getMenuInUse()); //$NON-NLS-1$
    }
    else if (parent instanceof PredefinedSetup) {
      final PredefinedSetup setup = (PredefinedSetup) parent;
      setup.menu.add(getMenuInUse());
    }
    MenuManager.getInstance().removeAction("GameState.new_game"); //$NON-NLS-1$
    GameModule.getGameModule().getGameState().addGameComponent(this);
    GameModule.getGameModule().getWizardSupport().addPredefinedSetup(this);
  }

  @Override
  public void removeFrom(Buildable parent) {
    if (parent instanceof GameModule) {
      MenuManager.getInstance()
        .removeFromSection("PredefinedSetup", getMenuInUse()); //$NON-NLS-1$
    }
    else if (parent instanceof PredefinedSetup) {
      final PredefinedSetup setup = (PredefinedSetup) parent;
      setup.menu.remove(getMenuInUse());
    }
    GameModule.getGameModule().getGameState().removeGameComponent(this);
    GameModule.getGameModule().getWizardSupport().removePredefinedSetup(this);
  }

  @Override
  public Class<?>[] getAllowableConfigureComponents() {
    return isMenu ? new Class<?>[]{PredefinedSetup.class} : new Class<?>[0];
  }

  public static String getConfigureTypeName() {
    return Resources.getString("Editor.PredefinedSetup.component_type"); //$NON-NLS-1$
  }

  @Deprecated(since = "2023-11-10", forRemoval = true)
  public void refresh(Set<String> options) throws IOException, IllegalBuildException {
    refreshWithStatus(options);
  }

  public int refreshWithStatus(Set<String> options) throws IOException, IllegalBuildException {
    if (!options.isEmpty()) {
      this.refresherOptions.clear();
      this.refresherOptions.addAll(options);
    }
    final GameModule mod = GameModule.getGameModule();
    final GameState gs = mod.getGameState();
    final GameRefresher gameRefresher = new GameRefresher(mod);

    // since we're going to block the GUI, let's give some feedback
    gameRefresher.log(GameRefresher.SEPARATOR); //$NON-NLS-1$
    gameRefresher.log("Updating Predefined Setup: " + this.getAttributeValueString(NAME) + " (" + fileName + ")"); //$NON-NLS-1$S

    // get a stream to the saved game in the module file
    gs.setupRefresh();
    gs.loadGameInForeground(fileName, getSavedGameContents());
    mod.getPlayerWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

    // call the gameRefresher
    gameRefresher.execute(refresherOptions, null);

    // save the refreshed game into a temporary file
    final File tmpFile = File.createTempFile("vassal", null);
    final ZipArchive tmpZip = new ZipArchive(tmpFile);
    gs.saveGameRefresh(tmpZip);
    gs.updateDone();

    // write the updated saved game file into the module file
    final ArchiveWriter aw = mod.getArchiveWriter();
    aw.removeFile(fileName);
    aw.addFile(tmpZip.getFile().getPath(), fileName);
    gs.closeGame();

    mod.getPlayerWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

    // return number of refresh anomaly warnings reported
    return gameRefresher.warnings();
  }

  @Override
  public HelpFile getHelpFile() {
    return HelpFile.getReferenceManualPage("GameModule.html", "PredefinedSetup"); //$NON-NLS-1$ //$NON-NLS-2$
  }

  public boolean isMenu() {
    return isMenu;
  }

  public boolean isUseFile() {
    return useFile;
  }

  public String getFileName() {
    return fileName;
  }

  @Override
  public Command getRestoreCommand() {
    return null;
  }

  @Override
  public void setup(boolean gameStarting) {
    launchAction.setEnabled(!gameStarting);
  }

  @Override
  public String toString() {
    return "PredefinedSetup{" + //NON-NLS
      "name='" + name + '\'' + //NON-NLS
      ", menu='" + isMenu + '\'' + //NON-NLS
      '}';
  }

  /**
   * {@link VASSAL.search.SearchTarget}
   * @return a list of the Configurable's string/expression fields if any (for search)
   */
  @Override
  public List<String> getExpressionList() {
    return List.of(name);
  }
}
  