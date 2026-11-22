/*
 *
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
package VASSAL.launch;

import java.util.concurrent.ExecutionException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import VASSAL.tools.version.AbstractUpdateCheckRequest;
import VASSAL.tools.version.UpdatePrompt;

/**
 * @since 3.1.0
 * @author Joel Uckelman
 */
public class UpdateCheckRequest extends AbstractUpdateCheckRequest {

  private static final Logger logger =
    LoggerFactory.getLogger(UpdateCheckRequest.class);

  @Override
  protected void done() {
    try {
      final Boolean update = get();
      if (update) {
        // running version is obsolete
        UpdatePrompt.show(ModuleManagerWindow.getInstance(), getOffer());
      }
    }
    catch (InterruptedException | ExecutionException e) {
      logger.error("", e);
    }
  }
}
