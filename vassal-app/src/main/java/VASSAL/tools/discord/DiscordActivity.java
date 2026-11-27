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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What to show on the player's Discord status: an immutable value built with
 * {@link #builder()}.
 *
 * Discord shows the application name as the headline, then {@link #getDetails()},
 * then {@link #getState()}, a large picture with a small badge in its corner,
 * an elapsed-time counter and up to two link buttons.
 *
 * Text that Discord would refuse is fixed here rather than sent: text lines
 * shorter than two characters are dropped and long ones are cut to 128
 * characters; buttons need a 1 to 32 character label and an http(s) web
 * address of at most 512 characters, and only the first two are kept.
 */
public final class DiscordActivity {
  static final int MAX_TEXT = 128;
  static final int MAX_BUTTON_LABEL = 32;
  static final int MAX_URL = 512;
  static final int MAX_BUTTONS = 2;

  private final String details;
  private final String state;
  private final String largeImage;
  private final String largeText;
  private final String smallImage;
  private final String smallText;
  private final Long startTimestamp;
  private final List<Button> buttons;

  private DiscordActivity(Builder b) {
    details = text(b.details);
    state = text(b.state);
    largeImage = image(b.largeImage);
    largeText = largeImage == null ? null : text(b.largeText);
    smallImage = image(b.smallImage);
    smallText = smallImage == null ? null : text(b.smallText);
    startTimestamp = b.startTimestamp;
    final List<Button> l = new ArrayList<>();
    for (final Button button : b.buttons) {
      if (l.size() < MAX_BUTTONS && button.isValid()) {
        l.add(button);
      }
    }
    buttons = Collections.unmodifiableList(l);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** @return a builder holding a copy of this activity's values */
  public Builder toBuilder() {
    final Builder b = new Builder()
      .details(details)
      .state(state)
      .largeImage(largeImage, largeText)
      .smallImage(smallImage, smallText)
      .startTimestamp(startTimestamp);
    buttons.forEach(x -> b.button(x.getLabel(), x.getUrl()));
    return b;
  }

  public String getDetails() {
    return details;
  }

  public String getState() {
    return state;
  }

  public String getLargeImage() {
    return largeImage;
  }

  public String getLargeText() {
    return largeText;
  }

  public String getSmallImage() {
    return smallImage;
  }

  public String getSmallText() {
    return smallText;
  }

  /** @return when the elapsed-time counter started, in seconds since the epoch, or null for none */
  public Long getStartTimestamp() {
    return startTimestamp;
  }

  public List<Button> getButtons() {
    return buttons;
  }

  private static String text(String s) {
    if (s == null) {
      return null;
    }
    s = s.strip();
    if (s.length() < 2) {
      return null;
    }
    return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT - 1).strip() + "\u2026" : s;
  }

  private static String image(String s) {
    if (s == null) {
      return null;
    }
    s = s.strip();
    return s.isEmpty() || s.length() > MAX_URL ? null : s;
  }

  static boolean isWebAddress(String s) {
    return s != null && (s.startsWith("https://") || s.startsWith("http://")); //NON-NLS
  }

  /** @return the activity as Discord's JSON activity object */
  public String toJson() {
    final Json.ObjectWriter w = new Json.ObjectWriter();
    w.field("details", details); //NON-NLS
    w.field("state", state); //NON-NLS
    if (startTimestamp != null) {
      w.raw("timestamps", "{\"start\":" + startTimestamp + "}"); //NON-NLS
    }
    if (largeImage != null || smallImage != null) {
      final Json.ObjectWriter a = new Json.ObjectWriter();
      a.field("large_image", largeImage); //NON-NLS
      a.field("large_text", largeText); //NON-NLS
      a.field("small_image", smallImage); //NON-NLS
      a.field("small_text", smallText); //NON-NLS
      w.raw("assets", a.toString()); //NON-NLS
    }
    if (!buttons.isEmpty()) {
      final StringBuilder sb = new StringBuilder("[");
      for (final Button b : buttons) {
        if (sb.length() > 1) {
          sb.append(',');
        }
        final Json.ObjectWriter bw = new Json.ObjectWriter();
        bw.field("label", b.getLabel()); //NON-NLS
        bw.field("url", b.getUrl()); //NON-NLS
        sb.append(bw);
      }
      w.raw("buttons", sb.append(']').toString()); //NON-NLS
    }
    return w.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DiscordActivity)) {
      return false;
    }
    final DiscordActivity a = (DiscordActivity) o;
    return Objects.equals(details, a.details) &&
      Objects.equals(state, a.state) &&
      Objects.equals(largeImage, a.largeImage) &&
      Objects.equals(largeText, a.largeText) &&
      Objects.equals(smallImage, a.smallImage) &&
      Objects.equals(smallText, a.smallText) &&
      Objects.equals(startTimestamp, a.startTimestamp) &&
      buttons.equals(a.buttons);
  }

  @Override
  public int hashCode() {
    return Objects.hash(details, state, largeImage, largeText, smallImage, smallText, startTimestamp, buttons);
  }

  @Override
  public String toString() {
    return toJson();
  }

  /** A link button under the status. */
  public static final class Button {
    private final String label;
    private final String url;

    public Button(String label, String url) {
      this.label = label == null ? null : label.strip();
      this.url = url == null ? null : url.strip();
    }

    public String getLabel() {
      return label;
    }

    public String getUrl() {
      return url;
    }

    boolean isValid() {
      return label != null && !label.isEmpty() && label.length() <= MAX_BUTTON_LABEL &&
        isWebAddress(url) && url.length() <= MAX_URL;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Button)) {
        return false;
      }
      final Button b = (Button) o;
      return Objects.equals(label, b.label) && Objects.equals(url, b.url);
    }

    @Override
    public int hashCode() {
      return Objects.hash(label, url);
    }
  }

  /** Builds a {@link DiscordActivity}. */
  public static final class Builder {
    private String details;
    private String state;
    private String largeImage;
    private String largeText;
    private String smallImage;
    private String smallText;
    private Long startTimestamp;
    private final List<Button> buttons = new ArrayList<>();

    private Builder() {
    }

    /** @param details the second line, under the application name */
    public Builder details(String details) {
      this.details = details;
      return this;
    }

    /** @param state the third line */
    public Builder state(String state) {
      this.state = state;
      return this;
    }

    /**
     * @param image an art asset name uploaded to the Discord application, or an https web address
     * @param hoverText shown when the picture is hovered over
     */
    public Builder largeImage(String image, String hoverText) {
      largeImage = image;
      largeText = hoverText;
      return this;
    }

    /**
     * @param image an art asset name uploaded to the Discord application, or an https web address
     * @param hoverText shown when the badge is hovered over
     */
    public Builder smallImage(String image, String hoverText) {
      smallImage = image;
      smallText = hoverText;
      return this;
    }

    /** @param seconds start of the elapsed-time counter, in seconds since the epoch; null for none */
    public Builder startTimestamp(Long seconds) {
      startTimestamp = seconds;
      return this;
    }

    /** Adds a link button. Discord shows at most two. */
    public Builder button(String label, String url) {
      buttons.add(new Button(label, url));
      return this;
    }

    public DiscordActivity build() {
      return new DiscordActivity(this);
    }
  }
}
