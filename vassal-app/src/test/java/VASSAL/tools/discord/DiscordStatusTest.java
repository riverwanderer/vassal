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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordStatusTest {
  private static DiscordStatus.StatusInfo info(String level) {
    final DiscordStatus.StatusInfo i = new DiscordStatus.StatusInfo();
    i.level = level;
    i.moduleName = "C&C Napoleonics";
    i.moduleVersion = "3.0";
    i.vassalVersion = "3.9.0";
    i.start = 1700000000L;
    return i;
  }

  @Test
  void noGameYet() {
    final DiscordActivity a = info(DiscordStatus.LEVEL_BATTLE).toActivity();
    assertEquals("C&C Napoleonics", a.getDetails());
    assertEquals("Choosing a game", a.getState());
    assertEquals(DiscordStatus.VASSAL_IMAGE, a.getLargeImage());
    assertEquals(Long.valueOf(1700000000L), a.getStartTimestamp());
  }

  @Test
  void soloGameShowsScenarioAtBattleLevel() {
    final DiscordStatus.StatusInfo i = info(DiscordStatus.LEVEL_BATTLE);
    i.gameStarted = true;
    i.scenario = "Waterloo";
    final DiscordActivity a = i.toActivity();
    assertEquals("C&C Napoleonics · Waterloo", a.getDetails());
    assertEquals("Solo", a.getState());
  }

  @Test
  void gameLevelHidesScenario() {
    final DiscordStatus.StatusInfo i = info(DiscordStatus.LEVEL_GAME);
    i.gameStarted = true;
    i.scenario = "Waterloo";
    assertEquals("C&C Napoleonics", i.toActivity().getDetails());
  }

  @Test
  void onlineShowsCountButNamesOnlyAtEverything() {
    final DiscordStatus.StatusInfo i = info(DiscordStatus.LEVEL_BATTLE);
    i.gameStarted = true;
    i.online = true;
    i.otherPlayers = List.of("Alice", "Bob");
    assertEquals("Online · 3 players", i.toActivity().getState());

    i.level = DiscordStatus.LEVEL_EVERYTHING;
    assertEquals("Online with Alice, Bob", i.toActivity().getState());
  }

  @Test
  void logPlayback() {
    final DiscordStatus.StatusInfo i = info(DiscordStatus.LEVEL_BATTLE);
    i.gameStarted = true;
    i.replaying = true;
    i.scenario = "turn 3";
    final DiscordActivity a = i.toActivity();
    assertEquals("C&C Napoleonics · Reviewing a log", a.getDetails());
    assertNull(a.getState());
  }

  @Test
  void modulePictureAndLibraryButton() {
    final DiscordStatus.StatusInfo i = info(DiscordStatus.LEVEL_BATTLE);
    i.picture = "https://example.com/ccn.png";
    i.libraryProjectId = "ccn";
    final DiscordActivity a = i.toActivity();
    assertEquals("https://example.com/ccn.png", a.getLargeImage());
    assertEquals("C&C Napoleonics 3.0", a.getLargeText());
    assertEquals(DiscordStatus.VASSAL_IMAGE, a.getSmallImage());
    assertEquals(1, a.getButtons().size());
    assertEquals("https://vassalengine.org/library/projects/ccn", a.getButtons().get(0).getUrl());
  }

  @Test
  void notAWebAddressFallsBackToLogo() {
    final DiscordStatus.StatusInfo i = info(DiscordStatus.LEVEL_BATTLE);
    i.picture = "images/box.png";
    final DiscordActivity a = i.toActivity();
    assertEquals(DiscordStatus.VASSAL_IMAGE, a.getLargeImage());
    assertNull(a.getSmallImage());
    assertTrue(a.getButtons().isEmpty());
  }

  @Test
  void scenarioNameFromFile() {
    assertEquals("Waterloo", DiscordStatus.scenarioName("/games/Waterloo.vsav"));
    assertEquals("turn 3", DiscordStatus.scenarioName("turn 3.vlog"));
    assertNull(DiscordStatus.scenarioName(""));
  }
}
