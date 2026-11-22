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

import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * The release package which suits this computer, chosen with the same rules
 * as the download button on vassalengine.org: the matching Windows
 * installer, the universal macOS disk image, and nothing for Linux or
 * anything unrecognised (which has to choose from the download page).
 */
public enum UpdateInstaller {
  WINDOWS_X86_64(true, "windows-x86_64.exe", "UpdateCheckAction.platform_windows_x86_64"), //NON-NLS
  WINDOWS_X86_32(true, "windows-x86_32.exe", "UpdateCheckAction.platform_windows_x86_32"), //NON-NLS
  WINDOWS_AARCH64(true, "windows-aarch64.exe", "UpdateCheckAction.platform_windows_aarch64"), //NON-NLS
  MACOS(false, "macos-universal.dmg", "UpdateCheckAction.platform_macos"); //NON-NLS

  /** Where the release packages are published. */
  public static final String RELEASES_URL =
    "https://github.com/vassalengine/vassal/releases/download/"; //NON-NLS

  private final boolean windows;
  private final String suffix;
  private final String platformKey;

  UpdateInstaller(boolean windows, String suffix, String platformKey) {
    this.windows = windows;
    this.suffix = suffix;
    this.platformKey = platformKey;
  }

  public boolean isWindows() {
    return windows;
  }

  /** @return the translation key naming the platform, e.g. "Windows (64-bit x86)" */
  public String getPlatformKey() {
    return platformKey;
  }

  /** @return the file name of this package for the given release */
  public String getFileName(String version) {
    return "VASSAL-" + version + "-" + suffix; //NON-NLS
  }

  /** @return the download URL of this package for the given release */
  public String getUrl(String version) {
    return RELEASES_URL + version + "/" + getFileName(version);
  }

  /** @return the URL of the SHA-256 checksums published with the given release */
  public static String getChecksumUrl(String version) {
    return RELEASES_URL + version + "/VASSAL-" + version + ".sha256"; //NON-NLS
  }

  /** @return the package for the computer VASSAL is running on, or null */
  public static UpdateInstaller forThisComputer() {
    return detect(
      System.getProperty("os.name", ""), //NON-NLS
      System.getProperty("os.arch", ""), //NON-NLS
      System::getenv
    );
  }

  /**
   * Chooses the package for a computer.
   *
   * <p>On Windows, {@code os.arch} describes the JVM bundled with this
   * build, not the computer, and VASSAL's installers refuse to run on a
   * computer of a different architecture. A 32-bit build on 64-bit Windows
   * runs under WOW64, which sets {@code PROCESSOR_ARCHITEW6432} to the
   * computer's real architecture, so that is checked first.</p>
   *
   * @param osName the {@code os.name} system property
   * @param osArch the {@code os.arch} system property
   * @param env    looks up environment variables
   * @return the package to offer, or null if the download page must be used
   */
  static UpdateInstaller detect(String osName, String osArch, UnaryOperator<String> env) {
    final String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);

    if (os.startsWith("mac")) { //NON-NLS
      // one universal disk image serves Intel and Apple Silicon Macs
      return MACOS;
    }

    if (os.startsWith("windows")) { //NON-NLS
      final String wow = env.apply("PROCESSOR_ARCHITEW6432"); //NON-NLS
      if (wow != null && !wow.isBlank()) {
        final UpdateInstaller real = windowsFor(wow);
        if (real != null) {
          return real;
        }
      }
      return windowsFor(osArch);
    }

    // Linux has several package formats, and anything else is unknown
    return null;
  }

  private static UpdateInstaller windowsFor(String arch) {
    if (arch == null) {
      return null;
    }

    switch (arch.toLowerCase(Locale.ROOT)) {
    case "amd64":   //NON-NLS
    case "x86_64":  //NON-NLS
    case "x64":     //NON-NLS
      return WINDOWS_X86_64;
    case "x86":     //NON-NLS
    case "i386":    //NON-NLS
    case "i486":    //NON-NLS
    case "i586":    //NON-NLS
    case "i686":    //NON-NLS
      return WINDOWS_X86_32;
    case "aarch64": //NON-NLS
    case "arm64":   //NON-NLS
      return WINDOWS_AARCH64;
    default:
      return null;
    }
  }
}
