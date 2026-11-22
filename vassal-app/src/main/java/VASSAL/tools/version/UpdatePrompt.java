/*
 * Copyright (c) 2026 by The VASSAL Development Team
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
package VASSAL.tools.version;

import java.awt.Desktop;
import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import VASSAL.i18n.Resources;
import VASSAL.tools.BrowserSupport;
import VASSAL.tools.swing.ProgressDialog;

/**
 * Tells the user about a newer VASSAL and, where the package for this
 * computer is known, downloads it, checks it against the release's
 * published SHA-256 checksums and starts it: the Windows installer, or the
 * macOS disk image in Finder. Whatever cannot be done automatically falls
 * back to the browser, and then to the download page.
 */
public final class UpdatePrompt {
  private static final Logger logger = LoggerFactory.getLogger(UpdatePrompt.class);

  public static final String DOWNLOAD_PAGE = "https://vassalengine.org/download.html"; //NON-NLS

  private UpdatePrompt() {}

  /**
   * Shows the update offer. Must be called on the EDT.
   *
   * @param parent the Module Manager window, which is closed to quit VASSAL
   * @param offer  the update to offer
   */
  public static void show(Frame parent, UpdateOffer offer) {
    final UpdateInstaller installer = offer.getInstaller();
    if (installer == null) {
      // no package we can pick for this computer, so ask as we always have
      if (JOptionPane.showConfirmDialog(
          parent,
          Resources.getString("UpdateCheckAction.update_available_message"),
          Resources.getString("UpdateCheckAction.update_available_title"),
          JOptionPane.YES_NO_OPTION,
          JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
        BrowserSupport.openURL(DOWNLOAD_PAGE);
      }
      return;
    }

    final String platform = Resources.getString(installer.getPlatformKey());
    final Object[] options = {
      Resources.getString("UpdateCheckAction.download_and_install", platform),
      Resources.getString("UpdateCheckAction.other_downloads"),
      Resources.getString("UpdateCheckAction.not_now")
    };

    final int choice = JOptionPane.showOptionDialog(
      parent,
      Resources.getString("UpdateCheckAction.update_available_version_message",
        offer.getNewVersion(), offer.getRunningVersion()),
      Resources.getString("UpdateCheckAction.update_available_title"),
      JOptionPane.DEFAULT_OPTION,
      JOptionPane.QUESTION_MESSAGE,
      null,
      options,
      options[0]
    );

    if (choice == 0) {
      download(parent, offer.getNewVersion(), installer);
    }
    else if (choice == 1) {
      BrowserSupport.openURL(DOWNLOAD_PAGE);
    }
  }

  private static void download(Frame parent, String version, UpdateInstaller installer) {
    final ProgressDialog progress = new ProgressDialog(
      parent,
      Resources.getString("UpdateCheckAction.downloading_title"),
      Resources.getString("UpdateCheckAction.downloading_message", version)
    );
    progress.setLocationRelativeTo(parent);

    final Download dl = new Download(parent, progress, version, installer);
    progress.addActionListener(e -> dl.cancel(true));
    dl.execute();
    progress.setVisible(true);
  }

  /** Downloads and verifies the package off the EDT. */
  private static class Download extends SwingWorker<Path, Integer> {
    private final Frame parent;
    private final ProgressDialog progress;
    private final String version;
    private final UpdateInstaller installer;

    Download(Frame parent, ProgressDialog progress, String version, UpdateInstaller installer) {
      this.parent = parent;
      this.progress = progress;
      this.version = version;
      this.installer = installer;
    }

    @Override
    protected Path doInBackground() throws IOException {
      final String name = installer.getFileName(version);
      final String expected = fetchChecksum(version, name);

      final Path dir = Files.createTempDirectory("vassal-update-"); //NON-NLS
      final Path file = dir.resolve(name);

      final MessageDigest sha256;
      try {
        sha256 = MessageDigest.getInstance("SHA-256"); //NON-NLS
      }
      catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException(e); // every JVM has SHA-256
      }

      final URLConnection c = UpdateOffer.open(installer.getUrl(version));
      final long length = c.getContentLengthLong();

      try (InputStream in = c.getInputStream();
           OutputStream out = Files.newOutputStream(file)) {
        final byte[] buf = new byte[64 * 1024];
        long total = 0;
        int lastPercent = -1;
        int n;
        while ((n = in.read(buf)) != -1) {
          if (isCancelled()) {
            throw new InterruptedIOException();
          }
          out.write(buf, 0, n);
          sha256.update(buf, 0, n);
          total += n;
          if (length > 0) {
            final int percent = (int) (100 * total / length);
            if (percent != lastPercent) {
              lastPercent = percent;
              publish(percent);
            }
          }
        }
      }

      final String actual = Hex.encodeHexString(sha256.digest());
      if (!actual.equalsIgnoreCase(expected)) {
        Files.deleteIfExists(file);
        throw new IOException("Checksum mismatch for " + name + //NON-NLS
          ": expected " + expected + ", got " + actual); //NON-NLS
      }

      return file;
    }

    @Override
    protected void process(List<Integer> chunks) {
      progress.setProgress(chunks.get(chunks.size() - 1));
    }

    @Override
    protected void done() {
      progress.dispose();

      final Path file;
      try {
        file = get();
      }
      catch (CancellationException | InterruptedException e) {
        return;
      }
      catch (ExecutionException e) {
        logger.warn("Update download failed", e.getCause()); //NON-NLS
        JOptionPane.showMessageDialog(
          parent,
          Resources.getString("UpdateCheckAction.download_failed_message"),
          Resources.getString("UpdateCheckAction.download_failed_title"),
          JOptionPane.WARNING_MESSAGE
        );
        openInBrowser(parent, version, installer);
        return;
      }

      install(parent, version, installer, file);
    }
  }

  /**
   * Finds the package's checksum in the release's .sha256 file, which has
   * the {@code sha256sum} format "checksum  name", one file per line.
   */
  private static String fetchChecksum(String version, String name) throws IOException {
    final URLConnection c = UpdateOffer.open(UpdateInstaller.getChecksumUrl(version));
    try (BufferedReader r = new BufferedReader(
           new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
      final String sum = findChecksum(r.lines().toList(), name);
      if (sum == null) {
        throw new IOException("No checksum for " + name); //NON-NLS
      }
      return sum;
    }
  }

  static String findChecksum(List<String> lines, String name) {
    for (final String line : lines) {
      final String[] f = line.trim().split("\\s+", 2); //NON-NLS
      if (f.length == 2) {
        // sha256sum marks files read in binary mode with '*'
        final String file = f[1].startsWith("*") ? f[1].substring(1) : f[1];
        if (file.equals(name) && f[0].matches("[0-9A-Fa-f]{64}")) { //NON-NLS
          return f[0].toLowerCase(Locale.ROOT);
        }
      }
    }
    return null;
  }

  private static void install(Frame parent, String version, UpdateInstaller installer, Path file) {
    final String tips = Resources.getString(installer.isWindows() ?
      "UpdateCheckAction.ready_windows_message" :
      "UpdateCheckAction.ready_macos_message", version);

    final Object[] options = {
      Resources.getString("UpdateCheckAction.install"),
      Resources.getString(Resources.CANCEL)
    };

    if (JOptionPane.showOptionDialog(
        parent,
        tips,
        Resources.getString("UpdateCheckAction.ready_title"),
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.INFORMATION_MESSAGE,
        null,
        options,
        options[0]) != 0) {
      return;
    }

    try {
      openFile(file, installer.isWindows());
    }
    catch (IOException | RuntimeException e) {
      logger.warn("Could not start {}", file, e); //NON-NLS
      JOptionPane.showMessageDialog(
        parent,
        Resources.getString("UpdateCheckAction.start_failed_message", file.toString()),
        Resources.getString("UpdateCheckAction.download_failed_title"),
        JOptionPane.WARNING_MESSAGE
      );
      return;
    }

    if (installer.isWindows()) {
      // the installer waits for VASSAL to close before it replaces it
      quit(parent);
    }
    else if (JOptionPane.showConfirmDialog(
        parent,
        Resources.getString("UpdateCheckAction.quit_now_message"),
        Resources.getString("UpdateCheckAction.quit_now_title"),
        JOptionPane.YES_NO_OPTION,
        JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
      quit(parent);
    }
  }

  /**
   * Opens the package as the user would by double-clicking it. On Windows
   * this goes through the shell, so the installer can ask for
   * administrator rights.
   */
  private static void openFile(Path file, boolean windows) throws IOException {
    if (Desktop.isDesktopSupported() &&
        Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
      Desktop.getDesktop().open(file.toFile());
    }
    else if (windows) {
      new ProcessBuilder("cmd", "/c", "start", "", file.toString()).start(); //NON-NLS
    }
    else {
      new ProcessBuilder("open", file.toString()).start(); //NON-NLS
    }
  }

  private static void openInBrowser(Frame parent, String version, UpdateInstaller installer) {
    BrowserSupport.openURL(installer.getUrl(version));
    JOptionPane.showMessageDialog(
      parent,
      Resources.getString(installer.isWindows() ?
        "UpdateCheckAction.browser_windows_message" :
        "UpdateCheckAction.browser_macos_message", version),
      Resources.getString("UpdateCheckAction.update_available_title"),
      JOptionPane.INFORMATION_MESSAGE
    );
  }

  /** Quits VASSAL the same way as closing the Module Manager. */
  private static void quit(Frame parent) {
    parent.dispatchEvent(new WindowEvent(parent, WindowEvent.WINDOW_CLOSING));
  }
}
