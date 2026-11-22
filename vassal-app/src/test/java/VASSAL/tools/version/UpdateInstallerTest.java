package VASSAL.tools.version;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UpdateInstallerTest {
  private static UpdateInstaller detect(String os, String arch, Map<String, String> env) {
    return UpdateInstaller.detect(os, arch, env::get);
  }

  @Test
  public void testWindows() {
    assertEquals(UpdateInstaller.WINDOWS_X86_64, detect("Windows 11", "amd64", Map.of()));
    assertEquals(UpdateInstaller.WINDOWS_X86_32, detect("Windows 10", "x86", Map.of()));
    assertEquals(UpdateInstaller.WINDOWS_AARCH64, detect("Windows 11", "aarch64", Map.of()));
  }

  @Test
  public void testWindows32BitBuildOn64BitWindows() {
    // a 32-bit VASSAL under WOW64 is offered the installer for the computer
    assertEquals(UpdateInstaller.WINDOWS_X86_64,
      detect("Windows 10", "x86", Map.of("PROCESSOR_ARCHITEW6432", "AMD64")));
    assertEquals(UpdateInstaller.WINDOWS_AARCH64,
      detect("Windows 11", "x86", Map.of("PROCESSOR_ARCHITEW6432", "ARM64")));
    // an unrecognised value falls back to the JVM's architecture
    assertEquals(UpdateInstaller.WINDOWS_X86_32,
      detect("Windows 10", "x86", Map.of("PROCESSOR_ARCHITEW6432", "IA64")));
  }

  @Test
  public void testMac() {
    assertEquals(UpdateInstaller.MACOS, detect("Mac OS X", "aarch64", Map.of()));
    assertEquals(UpdateInstaller.MACOS, detect("Mac OS X", "x86_64", Map.of()));
  }

  @Test
  public void testDownloadPagePlatforms() {
    assertNull(detect("Linux", "amd64", Map.of()));
    assertNull(detect("FreeBSD", "amd64", Map.of()));
    assertNull(detect("Windows 11", "riscv64", Map.of()));
    assertNull(detect("", "", Map.of()));
  }

  @Test
  public void testUrls() {
    assertEquals(
      "https://github.com/vassalengine/vassal/releases/download/3.8.2/VASSAL-3.8.2-windows-x86_64.exe",
      UpdateInstaller.WINDOWS_X86_64.getUrl("3.8.2"));
    assertEquals(
      "https://github.com/vassalengine/vassal/releases/download/3.8.0-beta3/VASSAL-3.8.0-beta3-macos-universal.dmg",
      UpdateInstaller.MACOS.getUrl("3.8.0-beta3"));
    assertEquals(
      "https://github.com/vassalengine/vassal/releases/download/3.8.2/VASSAL-3.8.2.sha256",
      UpdateInstaller.getChecksumUrl("3.8.2"));
  }

  private static final VersionInfo VI = new VersionInfo() {
    @Override
    public String getRelease() {
      return "3.8.2";
    }

    @Override
    public String getBeta() {
      return "3.9.0-beta1";
    }
  };

  @Test
  public void testChooseNewVersion() throws IOException {
    assertEquals("3.8.2", UpdateOffer.chooseNewVersion("3.8.1", VI));
    assertEquals("3.8.2", UpdateOffer.chooseNewVersion("3.8.0-beta3", VI));
    assertEquals("3.9.0-beta1", UpdateOffer.chooseNewVersion("3.9.0-SNAPSHOT", VI));
  }

  @Test
  public void testFindChecksum() {
    final String a = "a".repeat(64);
    final String b = "B".repeat(64);
    final List<String> lines = List.of(
      a + "  VASSAL-3.8.2-linux.tar.bz2",
      b + " *VASSAL-3.8.2-windows-x86_64.exe",
      "not a checksum  VASSAL-3.8.2-macos-universal.dmg"
    );
    assertEquals(a, UpdatePrompt.findChecksum(lines, "VASSAL-3.8.2-linux.tar.bz2"));
    assertEquals("b".repeat(64), UpdatePrompt.findChecksum(lines, "VASSAL-3.8.2-windows-x86_64.exe"));
    assertNull(UpdatePrompt.findChecksum(lines, "VASSAL-3.8.2-macos-universal.dmg"));
    assertNull(UpdatePrompt.findChecksum(lines, "VASSAL-3.8.2-windows-aarch64.exe"));
  }
}
