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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A newer VASSAL, and the package of it to offer this computer.
 */
public class UpdateOffer {
  private static final Logger logger = LoggerFactory.getLogger(UpdateOffer.class);

  static final int TIMEOUT_MS = 15_000;

  private final String runningVersion;
  private final String newVersion;
  private final UpdateInstaller installer;

  public UpdateOffer(String runningVersion, String newVersion, UpdateInstaller installer) {
    this.runningVersion = runningVersion;
    this.newVersion = newVersion;
    this.installer = installer;
  }

  public String getRunningVersion() {
    return runningVersion;
  }

  public String getNewVersion() {
    return newVersion;
  }

  /** @return the package to download, or null if the download page must be used */
  public UpdateInstaller getInstaller() {
    return installer;
  }

  /**
   * Checks for a newer VASSAL. This makes network requests, so it must not
   * be called on the EDT.
   *
   * @return the offer, or null if the running version is current
   */
  public static UpdateOffer find(String runningVersion) throws IOException {
    final VersionInfo vi = VersionUtils.getVersionInfo();
    if (VersionUtils.isCurrent(runningVersion)) {
      return null;
    }

    final String newVersion = chooseNewVersion(runningVersion, vi);

    UpdateInstaller installer = UpdateInstaller.forThisComputer();
    if (installer != null && !exists(installer.getUrl(newVersion))) {
      logger.info("No {} found for VASSAL {}", installer, newVersion); //NON-NLS
      installer = null;
    }

    return new UpdateOffer(runningVersion, newVersion, installer);
  }

  /**
   * Someone running a version older than the current release is offered the
   * release; someone already past it (a beta or a development build) is
   * offered the current beta.
   */
  static String chooseNewVersion(String runningVersion, VersionInfo vi) throws IOException {
    final String release = vi.getRelease();
    return VersionUtils.compareVersions(runningVersion, release) < 0 ?
      release : vi.getBeta();
  }

  /** @return whether a HEAD request for the URL succeeds */
  static boolean exists(String url) {
    try {
      final URLConnection c = URI.create(url).toURL().openConnection();
      if (!(c instanceof HttpURLConnection)) {
        return false;
      }
      final HttpURLConnection hc = (HttpURLConnection) c;
      hc.setRequestMethod("HEAD"); //NON-NLS
      hc.setConnectTimeout(TIMEOUT_MS);
      hc.setReadTimeout(TIMEOUT_MS);
      hc.setInstanceFollowRedirects(true);
      try {
        return hc.getResponseCode() == HttpURLConnection.HTTP_OK;
      }
      finally {
        hc.disconnect();
      }
    }
    catch (IOException | IllegalArgumentException e) {
      logger.info("Could not check {}", url, e); //NON-NLS
      return false;
    }
  }

  /** Opens a connection with the update check timeouts. */
  static URLConnection open(String url) throws IOException {
    final URL u = URI.create(url).toURL();
    final URLConnection c = u.openConnection();
    c.setConnectTimeout(TIMEOUT_MS);
    c.setReadTimeout(TIMEOUT_MS);
    return c;
  }
}
