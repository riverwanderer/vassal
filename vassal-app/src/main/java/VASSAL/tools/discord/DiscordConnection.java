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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One session with the Discord desktop app over its local IPC endpoint.
 *
 * Each message is a frame: a little-endian 32-bit opcode, a little-endian
 * 32-bit length and that many bytes of UTF-8 JSON. The session opens with a
 * HANDSHAKE naming the Discord application; Discord answers READY, after
 * which SET_ACTIVITY commands set or (with a null activity) clear the status.
 * Rich Presence needs no AUTHORIZE step.
 *
 * Not thread safe: {@link DiscordPresence} uses it from one thread.
 */
public class DiscordConnection implements AutoCloseable {
  static final int OP_HANDSHAKE = 0;
  static final int OP_FRAME = 1;
  static final int OP_CLOSE = 2;
  static final int OP_PING = 3;
  static final int OP_PONG = 4;

  private static final int MAX_FRAME = 64 * 1024;

  private static final ScheduledExecutorService WATCHDOG =
    Executors.newSingleThreadScheduledExecutor(r -> {
      final Thread t = new Thread(r, "vassal-discord-watchdog"); //NON-NLS
      t.setDaemon(true);
      return t;
    });

  private final DiscordPipe pipe;
  private final long timeoutMillis;
  private final long pid;

  /**
   * Opens a session: sends the handshake and waits for READY.
   *
   * @param pipe an open pipe; closed on failure
   * @param applicationId the Discord application ID
   * @param timeoutMillis how long to wait for each reply before giving up
   * @throws IOException if Discord refuses or doesn't answer
   */
  public DiscordConnection(DiscordPipe pipe, String applicationId, long timeoutMillis) throws IOException {
    this.pipe = pipe;
    this.timeoutMillis = timeoutMillis;
    this.pid = ProcessHandle.current().pid();
    try {
      write(OP_HANDSHAKE, "{\"v\":1,\"client_id\":" + Json.quote(applicationId) + "}"); //NON-NLS
      final String reply = readReply();
      if (!"READY".equals(Json.stringValue(reply, "evt"))) { //NON-NLS
        throw new IOException("Discord did not accept the handshake: " + reply); //NON-NLS
      }
    }
    catch (IOException e) {
      close();
      throw e;
    }
  }

  /**
   * Sets the status, or clears it if activity is null.
   *
   * @throws IOException if the pipe fails or Discord answers with an error
   */
  public void setActivity(DiscordActivity activity) throws IOException {
    final String nonce = UUID.randomUUID().toString();
    write(OP_FRAME,
      "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":" + pid + //NON-NLS
      ",\"activity\":" + (activity == null ? "null" : activity.toJson()) + //NON-NLS
      "},\"nonce\":" + Json.quote(nonce) + "}"); //NON-NLS

    // Discord may send other events first; wait for the answer to ours
    while (true) {
      final String reply = readReply();
      if ("ERROR".equals(Json.stringValue(reply, "evt"))) { //NON-NLS
        throw new DiscordException(reply);
      }
      if (nonce.equals(Json.stringValue(reply, "nonce"))) { //NON-NLS
        return;
      }
    }
  }

  @Override
  public void close() {
    try {
      pipe.close();
    }
    catch (IOException ignored) {
      // nothing more to do
    }
  }

  private void write(int op, String json) throws IOException {
    final byte[] body = json.getBytes(StandardCharsets.UTF_8);
    final ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
    b.putInt(op).putInt(body.length).put(body);
    final OutputStream out = pipe.getOutputStream();
    out.write(b.array());
    out.flush();
  }

  /** Reads frames until one is a reply, answering pings on the way. */
  private String readReply() throws IOException {
    while (true) {
      final ScheduledFuture<?> timeout = WATCHDOG.schedule(this::close, timeoutMillis, TimeUnit.MILLISECONDS);
      final int op;
      final String json;
      try {
        final InputStream in = pipe.getInputStream();
        final ByteBuffer hdr = ByteBuffer.wrap(readFully(in, 8)).order(ByteOrder.LITTLE_ENDIAN);
        op = hdr.getInt();
        final int len = hdr.getInt();
        if (len < 0 || len > MAX_FRAME) {
          throw new IOException("Bad frame length " + len); //NON-NLS
        }
        json = new String(readFully(in, len), StandardCharsets.UTF_8);
      }
      finally {
        timeout.cancel(false);
      }

      switch (op) {
      case OP_FRAME:
        return json;
      case OP_PING:
        write(OP_PONG, json);
        break;
      case OP_CLOSE:
        throw new DiscordException(json);
      default:
        // ignore anything else
      }
    }
  }

  private static byte[] readFully(InputStream in, int n) throws IOException {
    final byte[] b = in.readNBytes(n);
    if (b.length < n) {
      throw new EOFException("Discord closed the connection"); //NON-NLS
    }
    return b;
  }

  /** Discord answered with an error or closed the session. */
  public static class DiscordException extends IOException {
    private static final long serialVersionUID = 1L;

    public DiscordException(String reply) {
      super("Discord replied: " + reply); //NON-NLS
    }
  }
}
