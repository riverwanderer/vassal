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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A connection to the local Discord desktop app's IPC endpoint: a named pipe
 * on Windows, a Unix domain socket elsewhere.
 */
public interface DiscordPipe extends Closeable {
  InputStream getInputStream();

  OutputStream getOutputStream();

  /** Opens a {@link DiscordPipe}, or returns null if Discord isn't there. */
  @FunctionalInterface
  interface Opener {
    DiscordPipe open() throws IOException;
  }

  /** @return an opener that finds the Discord desktop app's endpoint on this computer */
  static Opener systemOpener() {
    final boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); //NON-NLS
    return windows ? DiscordPipe::openWindows : () -> openUnix(unixDirectories(System.getenv()));
  }

  /** Tries {@code \\.\pipe\discord-ipc-0} to {@code -9}. */
  static DiscordPipe openWindows() {
    for (int i = 0; i < 10; i++) {
      try {
        final RandomAccessFile f = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw"); //NON-NLS
        return new StreamPipe(
          new InputStream() {
            @Override
            public int read() throws IOException {
              return f.read();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
              return f.read(b, off, len);
            }
          },
          new OutputStream() {
            @Override
            public void write(int b) throws IOException {
              f.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
              f.write(b, off, len);
            }
          },
          f
        );
      }
      catch (IOException e) {
        // not this one
      }
    }
    return null;
  }

  /** Tries discord-ipc-0 to -9 in each directory in turn. */
  static DiscordPipe openUnix(List<Path> dirs) throws IOException {
    IOException last = null;
    for (int i = 0; i < 10; i++) {
      for (final Path dir : dirs) {
        final Path p = dir.resolve("discord-ipc-" + i); //NON-NLS
        if (!Files.exists(p)) {
          continue;
        }
        try {
          final SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
          try {
            ch.connect(UnixDomainSocketAddress.of(p));
          }
          catch (IOException e) {
            ch.close();
            throw e;
          }
          return new StreamPipe(Channels.newInputStream(ch), Channels.newOutputStream(ch), ch);
        }
        catch (IOException e) {
          // a stale socket left by a Discord that has quit; try the next
          last = e;
        }
      }
    }
    if (last != null) {
      throw last;
    }
    return null;
  }

  /**
   * Where Discord puts its socket: {@code $XDG_RUNTIME_DIR}, {@code $TMPDIR}
   * (macOS), {@code $TMP} and {@code $TEMP}, each also with the Flatpak and
   * Snap subfolders, then {@code /tmp}.
   */
  static List<Path> unixDirectories(Map<String, String> env) {
    final List<Path> dirs = new ArrayList<>();
    for (final String var : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) { //NON-NLS
      final String s = env.get(var);
      if (s != null && !s.isEmpty()) {
        final Path base = Path.of(s);
        dirs.add(base);
        dirs.add(base.resolve("app/com.discordapp.Discord")); //NON-NLS
        dirs.add(base.resolve("snap.discord")); //NON-NLS
      }
    }
    dirs.add(Path.of("/tmp")); //NON-NLS
    return dirs;
  }

  /** A pipe made of two streams and the thing to close. */
  final class StreamPipe implements DiscordPipe {
    private final InputStream in;
    private final OutputStream out;
    private final Closeable resource;

    public StreamPipe(InputStream in, OutputStream out, Closeable resource) {
      this.in = in;
      this.out = out;
      this.resource = resource;
    }

    @Override
    public InputStream getInputStream() {
      return in;
    }

    @Override
    public OutputStream getOutputStream() {
      return out;
    }

    @Override
    public void close() throws IOException {
      resource.close();
    }
  }
}
