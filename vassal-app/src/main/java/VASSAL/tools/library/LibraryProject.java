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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Web addresses in the VASSAL Module Library for a library project.
 *
 * Modules store only the project's ID (the slug the library uses in its
 * addresses, which cannot change once the project exists). Every address is
 * built here, so if the library's layout changes only this class needs to.
 */
public final class LibraryProject {
  public static final String LIBRARY_BASE = "https://vassalengine.org/library"; //NON-NLS
  public static final String API_BASE = "https://vassalengine.org/api/v1"; //NON-NLS

  private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}"); //NON-NLS

  private LibraryProject() {
  }

  /** @return true if id looks like a library project ID */
  public static boolean isValidId(String id) {
    return id != null && VALID_ID.matcher(id).matches();
  }

  /** @return the project's page in the library, or null if id isn't valid */
  public static String getPageUrl(String id) {
    return isValidId(id) ? LIBRARY_BASE + "/projects/" + encode(id) : null; //NON-NLS
  }

  /** @return the library API address for the project's details, or null if id isn't valid */
  public static String getApiUrl(String id) {
    return isValidId(id) ? API_BASE + "/projects/" + encode(id) : null; //NON-NLS
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
