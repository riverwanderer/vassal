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
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for the Discord desktop app's IPC socket, so tests need no
 * Discord. Answers the handshake with READY and each SET_ACTIVITY with its
 * nonce, or with an ERROR when told to.
 */
class FakeDiscord implements AutoCloseable {
  final Path socket;
  final BlockingQueue<String> activities = new LinkedBlockingQueue<>();
  final List<String> handshakes = new CopyOnWriteArrayList<>();
  final AtomicInteger connections = new AtomicInteger();
  volatile boolean refuseActivity;

  private final ServerSocketChannel server;
  private final List<SocketChannel> clients = new CopyOnWriteArrayList<>();
  private final Thread thread;

  FakeDiscord(Path dir) throws IOException {
    socket = dir.resolve("discord-ipc-0");
    Files.deleteIfExists(socket);
    server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
    server.bind(UnixDomainSocketAddress.of(socket));
    thread = new Thread(this::accept, "fake-discord");
    thread.setDaemon(true);
    thread.start();
  }

  DiscordPipe.Opener opener() {
    return () -> DiscordPipe.openUnix(List.of(socket.getParent()));
  }

  /** @return the next activity JSON sent, or null if none within the time */
  String next(long millis) throws InterruptedException {
    return activities.poll(millis, TimeUnit.MILLISECONDS);
  }

  /** Drops every open connection, as when Discord quits. */
  void dropClients() throws IOException {
    for (final SocketChannel c : clients) {
      c.close();
    }
    clients.clear();
  }

  private void accept() {
    try {
      while (true) {
        final SocketChannel c = server.accept();
        clients.add(c);
        connections.incrementAndGet();
        final Thread t = new Thread(() -> serve(c), "fake-discord-client");
        t.setDaemon(true);
        t.start();
      }
    }
    catch (IOException e) {
      // closed
    }
  }

  private void serve(SocketChannel c) {
    try (c) {
      final InputStream in = Channels.newInputStream(c);
      final OutputStream out = Channels.newOutputStream(c);
      while (true) {
        final byte[] hdr = in.readNBytes(8);
        if (hdr.length < 8) {
          return;
        }
        final ByteBuffer b = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
        final int op = b.getInt();
        final String json = new String(in.readNBytes(b.getInt()), StandardCharsets.UTF_8);
        if (op == DiscordConnection.OP_HANDSHAKE) {
          handshakes.add(json);
          write(out, "{\"cmd\":\"DISPATCH\",\"data\":{\"v\":1},\"evt\":\"READY\",\"nonce\":null}");
        }
        else {
          final String nonce = Json.stringValue(json, "nonce");
          if (refuseActivity) {
            write(out, "{\"cmd\":\"SET_ACTIVITY\",\"data\":{\"code\":4000,\"message\":\"nope\"},\"evt\":\"ERROR\",\"nonce\":\"" + nonce + "\"}");
          }
          else {
            final int start = json.indexOf("\"activity\":") + "\"activity\":".length();
            final int end = json.lastIndexOf("},\"nonce\"");
            activities.add(json.substring(start, end));
            write(out, "{\"cmd\":\"SET_ACTIVITY\",\"data\":null,\"evt\":null,\"nonce\":\"" + nonce + "\"}");
          }
        }
      }
    }
    catch (IOException e) {
      // dropped
    }
  }

  private static void write(OutputStream out, String json) throws IOException {
    final byte[] body = json.getBytes(StandardCharsets.UTF_8);
    final ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
    b.putInt(DiscordConnection.OP_FRAME).putInt(body.length).put(body);
    out.write(b.array());
    out.flush();
  }

  @Override
  public void close() throws IOException {
    server.close();
    dropClients();
    Files.deleteIfExists(socket);
  }
}
