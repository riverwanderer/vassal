/*
 * Copyright (c) 2026 by the VASSAL Development Team
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
package VASSAL.tools.discord;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JFrame;
import javax.swing.Timer;

import VASSAL.Info;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.GameState;
import VASSAL.chat.ChatServerConnection;
import VASSAL.chat.Player;
import VASSAL.chat.Room;
import VASSAL.command.Command;
import VASSAL.configure.TranslatingStringEnumConfigurer;
import VASSAL.i18n.Resources;
import VASSAL.preferences.Prefs;
import VASSAL.tools.library.LibraryProject;

/**
 * Shows what the player is doing in VASSAL on their Discord status.
 *
 * Started by the Player for each module it opens (never by the Module
 * Manager or the Editor). Each open module is its own process with its own
 * connection to Discord; whichever window the player last brought to the
 * front is the one shown.
 *
 * What is shown depends on the global preference "Show on my Discord
 * status": nothing, the game only, the game and the battle (the default), or
 * everything (which adds the other players' names). It can be turned off
 * completely with {@code -DVASSAL.discord=false}.
 */
public class DiscordStatus implements GameComponent {
  /** Vassal's Discord application, "Vassal". The ID is public. */
  public static final String APPLICATION_ID = "1575791165649715240"; //NON-NLS
  /** The Vassal logo, uploaded to the application's Rich Presence art assets */
  public static final String VASSAL_IMAGE = "vassal"; //NON-NLS

  public static final String PREF_KEY = "DiscordStatus"; //NON-NLS
  public static final String LEVEL_NOTHING = "nothing"; //NON-NLS
  public static final String LEVEL_GAME = "game"; //NON-NLS
  public static final String LEVEL_BATTLE = "battle"; //NON-NLS
  public static final String LEVEL_EVERYTHING = "everything"; //NON-NLS

  public static final String SYSTEM_PROPERTY = "VASSAL.discord"; //NON-NLS

  private static final int REFRESH_MILLIS = 15_000;

  private final GameModule module;
  private final TranslatingStringEnumConfigurer pref;
  private DiscordPresence presence;
  private final long moduleStart = System.currentTimeMillis() / 1000;
  private long gameStart;
  private boolean gameWasStarted;

  /**
   * Starts showing the module on the player's Discord status, unless
   * turned off with {@code -DVASSAL.discord=false}.
   */
  public static void start(GameModule module) {
    if (!"false".equalsIgnoreCase(System.getProperty(SYSTEM_PROPERTY))) { //NON-NLS
      new DiscordStatus(module);
    }
  }

  private DiscordStatus(GameModule module) {
    this.module = module;

    pref = new TranslatingStringEnumConfigurer(
      PREF_KEY,
      Resources.getString("Prefs.discord_status"), //NON-NLS
      new String[] {LEVEL_NOTHING, LEVEL_GAME, LEVEL_BATTLE, LEVEL_EVERYTHING},
      new String[] {
        "Prefs.discord_nothing", //NON-NLS
        "Prefs.discord_game", //NON-NLS
        "Prefs.discord_battle", //NON-NLS
        "Prefs.discord_everything" //NON-NLS
      },
      LEVEL_BATTLE
    );
    Prefs.getGlobalPrefs().addOption(Resources.getString("Prefs.general_tab"), pref); //NON-NLS
    pref.addPropertyChangeListener(e -> refresh(false));

    module.getGameState().addGameComponent(this);

    final JFrame frame = module.getPlayerWindow();
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowActivated(WindowEvent e) {
        refresh(true);
      }
    });

    // picks up changes nothing tells us about, such as players joining the room
    final Timer timer = new Timer(REFRESH_MILLIS, e -> refresh(false));
    timer.setRepeats(true);
    timer.start();

    refresh(false);
  }

  @Override
  public void setup(boolean gameStarting) {
    refresh(false);
  }

  @Override
  public Command getRestoreCommand() {
    return null;
  }

  private String getLevel() {
    final String v = pref.getValueString();
    return v == null ? LEVEL_BATTLE : v;
  }

  /**
   * Works out the status and hands it to the Discord thread.
   * @param reassert send it even if unchanged, so this module becomes the one shown
   */
  private void refresh(boolean reassert) {
    final String level = getLevel();
    if (LEVEL_NOTHING.equals(level)) {
      if (presence != null) {
        presence.stop();
        presence = null;
      }
      return;
    }

    final DiscordActivity activity = compute(level);
    if (presence == null) {
      presence = new DiscordPresence(APPLICATION_ID);
    }
    if (reassert) {
      presence.reassert(activity);
    }
    else {
      presence.set(activity);
    }
  }

  private DiscordActivity compute(String level) {
    final GameState gs = module.getGameState();
    final boolean started = gs.isGameStarted();
    if (started && !gameWasStarted) {
      gameStart = System.currentTimeMillis() / 1000;
    }
    gameWasStarted = started;

    final StatusInfo info = new StatusInfo();
    info.level = level;
    info.moduleName = module.getLocalizedGameName();
    info.moduleVersion = module.getGameVersion();
    info.vassalVersion = Info.getVersion();
    info.picture = module.getDiscordImage();
    info.libraryProjectId = module.getLibraryProjectId();
    info.gameStarted = started;
    info.replaying = module.getGameFileMode() == GameModule.GameFileMode.REPLAYING_GAME;
    info.scenario = scenarioName(module.getGameFile());
    info.online = module.isMultiplayerConnected();
    info.otherPlayers = otherPlayers();
    info.start = started ? gameStart : moduleStart;
    return info.toActivity();
  }

  /** @return the save or log file's name without its extension, or null */
  static String scenarioName(String gameFile) {
    if (gameFile == null || gameFile.isBlank()) {
      return null;
    }
    String name = Path.of(gameFile).getFileName().toString();
    final int dot = name.lastIndexOf('.');
    if (dot > 0) {
      name = name.substring(0, dot);
    }
    return name;
  }

  /** @return the names of the other players in our server room, or null if not in one */
  private List<String> otherPlayers() {
    if (!(module.getServer() instanceof ChatServerConnection)) {
      return null;
    }
    final ChatServerConnection server = (ChatServerConnection) module.getServer();
    if (!server.isConnected()) {
      return null;
    }
    final Room room = server.getRoom();
    if (room == null) {
      return null;
    }
    final Player me = server.getUserInfo();
    return room.getPlayerList().stream()
      .filter(p -> me == null || !p.equals(me))
      .map(Player::getName)
      .collect(Collectors.toList());
  }

  /**
   * Everything the status is made from. Separate from the module so the
   * rules can be tested on their own.
   */
  static class StatusInfo {
    String level = LEVEL_BATTLE;
    String moduleName;
    String moduleVersion;
    String vassalVersion;
    String picture;
    String libraryProjectId;
    boolean gameStarted;
    boolean replaying;
    String scenario;
    boolean online;
    List<String> otherPlayers;
    long start;

    DiscordActivity toActivity() {
      final boolean battle = LEVEL_BATTLE.equals(level) || LEVEL_EVERYTHING.equals(level);
      final boolean everything = LEVEL_EVERYTHING.equals(level);

      String details = moduleName;
      final String state;

      if (!gameStarted) {
        state = Resources.getString("Discord.choosing_game"); //NON-NLS
      }
      else if (replaying) {
        if (battle) {
          details = join(moduleName, Resources.getString("Discord.reviewing_log")); //NON-NLS
          state = null;
        }
        else {
          state = Resources.getString("Discord.reviewing_log"); //NON-NLS
        }
      }
      else {
        if (battle && scenario != null) {
          details = join(moduleName, scenario);
        }
        if (online) {
          final int others = otherPlayers == null ? 0 : otherPlayers.size();
          if (everything && others > 0) {
            state = Resources.getString("Discord.online_with", String.join(", ", otherPlayers)); //NON-NLS
          }
          else if (others > 0) {
            state = Resources.getString("Discord.online_players", others + 1); //NON-NLS
          }
          else {
            state = Resources.getString("Discord.online"); //NON-NLS
          }
        }
        else {
          state = Resources.getString("Discord.solo"); //NON-NLS
        }
      }

      final DiscordActivity.Builder b = DiscordActivity.builder()
        .details(details)
        .state(state)
        .startTimestamp(start);

      final String vassal = Resources.getString("Discord.vassal_version", vassalVersion); //NON-NLS
      final String moduleText = moduleVersion == null || moduleVersion.isBlank() ?
        moduleName : moduleName + " " + moduleVersion;
      if (DiscordActivity.isWebAddress(picture)) {
        b.largeImage(picture, moduleText).smallImage(VASSAL_IMAGE, vassal);
      }
      else {
        b.largeImage(VASSAL_IMAGE, moduleText + " · " + vassal);
      }

      final String page = LibraryProject.getPageUrl(libraryProjectId);
      if (page != null) {
        b.button(Resources.getString("Discord.get_module"), page); //NON-NLS
      }
      return b.build();
    }

    private static String join(String a, String b) {
      return a == null ? b : a + " · " + b;
    }
  }
}
