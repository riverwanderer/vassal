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

import java.io.IOException;
import java.util.Objects;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the player's Discord status up to date without ever making the
 * caller wait.
 *
 * Callers hand the latest status to {@link #set} or {@link #clear} and return
 * at once. A low-priority daemon thread, {@code vassal-discord}, does all the
 * talking to Discord: it sends only the newest status (a newer one replaces
 * an older one not yet sent), sends nothing if the status hasn't changed,
 * and sends at most one update every {@code minInterval} milliseconds.
 *
 * Failures are silent. If Discord isn't running, or the connection drops or
 * is refused, the thread tries again after {@code shortRetry} milliseconds,
 * and after {@link #SHORT_RETRIES} failures in a row only every
 * {@code longRetry}. The first failure is noted once in the error log;
 * the player sees nothing.
 */
public class DiscordPresence {
  private static final Logger log = LoggerFactory.getLogger(DiscordPresence.class);

  public static final long DEFAULT_MIN_INTERVAL = 5_000;
  public static final long DEFAULT_SHORT_RETRY = 60_000;
  public static final long DEFAULT_LONG_RETRY = 300_000;
  public static final long DEFAULT_TIMEOUT = 10_000;
  static final int SHORT_RETRIES = 5;

  private final String applicationId;
  private final DiscordPipe.Opener opener;
  private final long minInterval;
  private final long shortRetry;
  private final long longRetry;
  private final long timeout;
  private final LongSupplier clock;

  private final Object lock = new Object();

  // guarded by lock
  private Update pending;
  private boolean running = true;
  private Thread thread;

  // used only by the worker thread
  private DiscordConnection connection;
  private DiscordActivity sent;
  private boolean everSent;
  private long nextSend;
  private long nextConnect;
  private int failures;
  private boolean logged;

  /** A status waiting to be sent; activity null means clear. */
  private static final class Update {
    final DiscordActivity activity;
    final boolean force;

    Update(DiscordActivity activity, boolean force) {
      this.activity = activity;
      this.force = force;
    }
  }

  /** Uses the Discord app on this computer, with the default timings. */
  public DiscordPresence(String applicationId) {
    this(applicationId, DiscordPipe.systemOpener(), DEFAULT_MIN_INTERVAL,
      DEFAULT_SHORT_RETRY, DEFAULT_LONG_RETRY, DEFAULT_TIMEOUT, System::currentTimeMillis);
  }

  DiscordPresence(String applicationId, DiscordPipe.Opener opener, long minInterval,
                  long shortRetry, long longRetry, long timeout, LongSupplier clock) {
    this.applicationId = Objects.requireNonNull(applicationId);
    this.opener = Objects.requireNonNull(opener);
    this.minInterval = minInterval;
    this.shortRetry = shortRetry;
    this.longRetry = longRetry;
    this.timeout = timeout;
    this.clock = clock;
  }

  /**
   * Shows this status, unless it is the one already showing.
   * Returns at once.
   */
  public void set(DiscordActivity activity) {
    offer(new Update(Objects.requireNonNull(activity), false));
  }

  /**
   * Shows this status even if it is the one already showing. Used when this
   * window becomes active, so that among several running modules the one in
   * use is the one shown.
   */
  public void reassert(DiscordActivity activity) {
    offer(new Update(Objects.requireNonNull(activity), true));
  }

  /** Clears the status. Returns at once. */
  public void clear() {
    offer(new Update(null, false));
  }

  /**
   * Stops the thread and closes the connection to Discord, which then
   * clears the status itself.
   */
  public void stop() {
    final Thread t;
    synchronized (lock) {
      running = false;
      pending = null;
      t = thread;
      lock.notifyAll();
    }
    if (t != null) {
      t.interrupt();
    }
  }

  private void offer(Update u) {
    synchronized (lock) {
      if (!running) {
        return;
      }
      pending = u;
      if (thread == null) {
        thread = new Thread(this::run, "vassal-discord"); //NON-NLS
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
      }
      lock.notifyAll();
    }
  }

  private void run() {
    try {
      while (true) {
        final Update u;
        synchronized (lock) {
          while (running) {
            if (pending != null) {
              final long wait = Math.max(nextSend, connection == null ? nextConnect : 0) - clock.getAsLong();
              if (wait <= 0) {
                break;
              }
              lock.wait(wait);
            }
            else {
              lock.wait();
            }
          }
          if (!running) {
            break;
          }
          u = pending;
          pending = null;
        }
        send(u);
      }
    }
    catch (InterruptedException e) {
      // stopping
    }
    finally {
      disconnect();
    }
  }

  private void send(Update u) {
    if (everSent && !u.force && Objects.equals(u.activity, sent)) {
      return;
    }
    if (connection == null) {
      if (u.activity == null) {
        // nothing showing, so nothing to clear
        sent = null;
        everSent = true;
        return;
      }
      try {
        final DiscordPipe pipe = opener.open();
        if (pipe == null) {
          throw new IOException("Discord is not running"); //NON-NLS
        }
        connection = new DiscordConnection(pipe, applicationId, timeout);
      }
      catch (IOException | RuntimeException e) {
        failed(u, e);
        return;
      }
    }

    try {
      connection.setActivity(u.activity);
      sent = u.activity;
      everSent = true;
      failures = 0;
      nextSend = clock.getAsLong() + minInterval;
    }
    catch (IOException | RuntimeException e) {
      disconnect();
      failed(u, e);
    }
  }

  private void failed(Update u, Exception e) {
    if (!logged) {
      logged = true;
      log.info("Discord status not shown: {}", e.getMessage()); //NON-NLS
    }
    failures++;
    nextConnect = clock.getAsLong() + (failures > SHORT_RETRIES ? longRetry : shortRetry);
    synchronized (lock) {
      // keep it for the next try, unless something newer has arrived
      if (pending == null && u.activity != null) {
        pending = u;
      }
    }
  }

  private void disconnect() {
    if (connection != null) {
      connection.close();
      connection = null;
    }
    // a new connection starts with nothing showing
    sent = null;
    everSent = false;
  }
}
