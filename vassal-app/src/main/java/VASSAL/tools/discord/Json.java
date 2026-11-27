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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Just enough JSON for Discord's local messages: writing flat objects and
 * picking single string values out of replies. VASSAL has no JSON library,
 * and these messages are small and simple.
 */
final class Json {
  private Json() {
  }

  /** @return s as a quoted JSON string */
  static String quote(String s) {
    final StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      switch (c) {
      case '"':
        sb.append("\\\"");
        break;
      case '\\':
        sb.append("\\\\");
        break;
      case '\n':
        sb.append("\\n");
        break;
      case '\r':
        sb.append("\\r");
        break;
      case '\t':
        sb.append("\\t");
        break;
      default:
        if (c < 0x20) {
          sb.append(String.format("\\u%04x", (int) c)); //NON-NLS
        }
        else {
          sb.append(c);
        }
      }
    }
    return sb.append('"').toString();
  }

  /**
   * @return the string value of the first "name": "value" pair in json, or
   * null if there is none. Escapes in the value are not decoded.
   */
  static String stringValue(String json, String name) {
    final Matcher m = Pattern.compile(
      "\"" + Pattern.quote(name) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"" //NON-NLS
    ).matcher(json);
    return m.find() ? m.group(1) : null;
  }

  /** Writes one JSON object, leaving out null values. */
  static final class ObjectWriter {
    private final StringBuilder sb = new StringBuilder("{");

    ObjectWriter field(String name, String value) {
      return value == null ? this : raw(name, quote(value));
    }

    ObjectWriter raw(String name, String json) {
      if (sb.length() > 1) {
        sb.append(',');
      }
      sb.append(quote(name)).append(':').append(json);
      return this;
    }

    @Override
    public String toString() {
      return sb + "}";
    }
  }
}
