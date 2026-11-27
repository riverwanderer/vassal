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
package VASSAL.tools.library;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LibraryProjectTest {
  @Test
  void buildsAddresses() {
    assertEquals("https://vassalengine.org/library/projects/ccn", LibraryProject.getPageUrl("ccn"));
    assertEquals("https://vassalengine.org/api/v1/projects/Commands_and_Colors_Napoleonics",
      LibraryProject.getApiUrl("Commands_and_Colors_Napoleonics"));
  }

  @Test
  void refusesBadIds() {
    assertNull(LibraryProject.getPageUrl(""));
    assertNull(LibraryProject.getPageUrl(null));
    assertNull(LibraryProject.getPageUrl("../admin"));
    assertNull(LibraryProject.getPageUrl("has space"));
  }
}
