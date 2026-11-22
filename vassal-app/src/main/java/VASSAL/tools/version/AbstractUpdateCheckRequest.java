/*
 * Copyright (c) 2008 by Joel Uckelman
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
import javax.swing.SwingWorker;

import VASSAL.Info;

/**
 * @since 3.1.0
 * @author Joel Uckelman
 */
public abstract class AbstractUpdateCheckRequest
                                      extends SwingWorker<Boolean, Void> {
  private UpdateOffer offer;

  @Override
  protected Boolean doInBackground() throws IOException {
    offer = UpdateOffer.find(Info.getVersion());
    return offer != null;
  }

  /**
   * @return the update to offer, or null if the running version is current
   * or the check has not finished
   * @since 3.8.3
   */
  protected UpdateOffer getOffer() {
    return offer;
  }

  @Override
  protected abstract void done();
}
