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

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordPresenceTest {
  private static final String APP = "1234";

  @TempDir
  Path dir;

  private DiscordPresence presence;
  private FakeDiscord discord;

  @AfterEach
  void tearDown() throws Exception {
    if (presence != null) {
      presence.stop();
    }
    if (discord != null) {
      discord.close();
    }
  }

  private static DiscordActivity activity(String details) {
    return DiscordActivity.builder().details(details).state("Solo").build();
  }

  private DiscordPresence start(DiscordPipe.Opener opener, long minInterval, long retry) {
    presence = new DiscordPresence(APP, opener, minInterval, retry, retry * 2, 2_000, System::currentTimeMillis);
    return presence;
  }

  @Test
  void handshakeThenActivity() throws Exception {
    discord = new FakeDiscord(dir);
    start(discord.opener(), 0, 1_000).set(activity("CCN · Waterloo"));

    final String sent = discord.next(2_000);
    assertNotNull(sent);
    assertTrue(sent.contains("\"details\":\"CCN · Waterloo\""), sent);
    assertEquals(1, discord.handshakes.size());
    assertTrue(discord.handshakes.get(0).contains("\"client_id\":\"1234\""));
  }

  @Test
  void unchangedStatusIsNotResent() throws Exception {
    discord = new FakeDiscord(dir);
    start(discord.opener(), 0, 1_000);
    presence.set(activity("Same"));
    assertNotNull(discord.next(2_000));
    presence.set(activity("Same"));
    assertNull(discord.next(300));
  }

  @Test
  void reassertResendsUnchangedStatus() throws Exception {
    discord = new FakeDiscord(dir);
    start(discord.opener(), 0, 1_000);
    presence.set(activity("Same"));
    assertNotNull(discord.next(2_000));
    presence.reassert(activity("Same"));
    assertNotNull(discord.next(2_000));
  }

  @Test
  void rateLimitSendsOnlyTheNewest() throws Exception {
    discord = new FakeDiscord(dir);
    start(discord.opener(), 700, 1_000);
    presence.set(activity("First"));
    assertTrue(discord.next(2_000).contains("First"));

    final long t0 = System.currentTimeMillis();
    presence.set(activity("Second"));
    presence.set(activity("Third"));
    final String sent = discord.next(3_000);
    assertNotNull(sent);
    assertTrue(sent.contains("Third"), sent);
    assertTrue(System.currentTimeMillis() - t0 >= 500, "sent too soon");
    assertNull(discord.next(1_000), "older status was sent too");
  }

  @Test
  void clearSendsNullActivity() throws Exception {
    discord = new FakeDiscord(dir);
    start(discord.opener(), 0, 1_000);
    presence.set(activity("Playing"));
    assertNotNull(discord.next(2_000));
    presence.clear();
    assertEquals("null", discord.next(2_000));
  }

  @Test
  void noDiscordFailsSilentlyAndRetries() throws Exception {
    final AtomicInteger attempts = new AtomicInteger();
    start(() -> {
      attempts.incrementAndGet();
      return null;
    }, 0, 300);
    presence.set(activity("Nobody listening"));
    Thread.sleep(1_100);
    final int n = attempts.get();
    assertTrue(n >= 2 && n <= 5, "attempts: " + n);
  }

  @Test
  void connectsWhenDiscordStartsLater() throws Exception {
    final AtomicLong open = new AtomicLong();
    start(() -> open.get() == 0 ? null : discord.opener().open(), 0, 300);
    presence.set(activity("Waiting"));
    Thread.sleep(400);
    discord = new FakeDiscord(dir);
    open.set(1);
    final String sent = discord.next(3_000);
    assertNotNull(sent);
    assertTrue(sent.contains("Waiting"));
  }

  @Test
  void reconnectsAfterDiscordDropsTheConnection() throws Exception {
    discord = new FakeDiscord(dir);
    start(discord.opener(), 0, 300);
    presence.set(activity("One"));
    assertNotNull(discord.next(2_000));
    discord.dropClients();
    Thread.sleep(200);
    presence.set(activity("Two"));
    final String sent = discord.next(3_000);
    assertNotNull(sent);
    assertTrue(sent.contains("Two"));
    assertEquals(2, discord.connections.get());
  }

  @Test
  void errorReplyIsRetriedLater() throws Exception {
    discord = new FakeDiscord(dir);
    discord.refuseActivity = true;
    start(discord.opener(), 0, 300);
    presence.set(activity("Refused"));
    Thread.sleep(200);
    discord.refuseActivity = false;
    final String sent = discord.next(3_000);
    assertNotNull(sent);
    assertTrue(sent.contains("Refused"));
  }

  @Test
  void stopIsQuiet() throws Exception {
    discord = new FakeDiscord(dir);
    start(discord.opener(), 0, 1_000);
    presence.stop();
    presence.set(activity("After stop"));
    assertNull(discord.next(500));
  }
}
