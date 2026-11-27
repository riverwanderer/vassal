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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordActivityTest {
  @Test
  void writesDiscordJson() {
    final DiscordActivity a = DiscordActivity.builder()
      .details("C&C Napoleonics · Waterloo")
      .state("Online · 2 players")
      .startTimestamp(1700000000L)
      .largeImage("https://example.com/box.png", "C&C Napoleonics 3.0")
      .smallImage("vassal", "Vassal 3.9.0")
      .button("Get the module", "https://vassalengine.org/library/projects/ccn")
      .build();
    assertEquals(
      "{\"details\":\"C&C Napoleonics · Waterloo\",\"state\":\"Online · 2 players\"," +
      "\"timestamps\":{\"start\":1700000000}," +
      "\"assets\":{\"large_image\":\"https://example.com/box.png\",\"large_text\":\"C&C Napoleonics 3.0\"," +
      "\"small_image\":\"vassal\",\"small_text\":\"Vassal 3.9.0\"}," +
      "\"buttons\":[{\"label\":\"Get the module\",\"url\":\"https://vassalengine.org/library/projects/ccn\"}]}",
      a.toJson());
  }

  @Test
  void escapesQuotesAndBackslashes() {
    final DiscordActivity a = DiscordActivity.builder().details("A \"quoted\" \\ name").build();
    assertEquals("{\"details\":\"A \\\"quoted\\\" \\\\ name\"}", a.toJson());
  }

  @Test
  void dropsTooShortAndCutsTooLongText() {
    final DiscordActivity a = DiscordActivity.builder()
      .details("x")
      .state("y".repeat(200))
      .build();
    assertNull(a.getDetails());
    assertEquals(DiscordActivity.MAX_TEXT, a.getState().length());
    assertTrue(a.getState().endsWith("…"));
  }

  @Test
  void keepsOnlyTwoValidButtons() {
    final DiscordActivity a = DiscordActivity.builder()
      .button("", "https://a.example")
      .button("Not a web address", "file:///etc/passwd")
      .button("This label is far too long for Discord", "https://b.example")
      .button("One", "https://one.example")
      .button("Two", "http://two.example")
      .button("Three", "https://three.example")
      .build();
    assertEquals(2, a.getButtons().size());
    assertEquals("One", a.getButtons().get(0).getLabel());
    assertEquals("Two", a.getButtons().get(1).getLabel());
  }

  @Test
  void equalActivitiesAreEqual() {
    final DiscordActivity a = DiscordActivity.builder().details("Same").state("Solo").build();
    assertEquals(a, a.toBuilder().build());
    assertFalse(a.equals(a.toBuilder().state("Online").build()));
  }

  @Test
  void readsStringValues() {
    assertEquals("READY", Json.stringValue("{\"cmd\":\"DISPATCH\", \"evt\" : \"READY\"}", "evt"));
    assertNull(Json.stringValue("{\"evt\":null}", "evt"));
  }
}
