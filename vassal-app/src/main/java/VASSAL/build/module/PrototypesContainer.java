/*
 *
 * Copyright (c) 2004 by Rodney Kinney
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
package VASSAL.build.module;

import VASSAL.build.AbstractBuildable;
import VASSAL.build.AbstractConfigurable;
import VASSAL.build.AbstractFolder;
import VASSAL.build.Buildable;
import VASSAL.build.Configurable;
import VASSAL.build.GameModule;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.folder.PrototypeFolder;
import VASSAL.build.widget.PieceSlot;
import VASSAL.configure.Configurer;
import VASSAL.i18n.ComponentI18nData;
import VASSAL.i18n.Resources;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Container for definitions of Game Piece prototypes.
 * Actual definition is in inner class
 * {@link VASSAL.build.module.PrototypeDefinition}
 */
public class PrototypesContainer extends AbstractConfigurable {
  private static PrototypesContainer instance;
  private final Map<String, PrototypeDefinition> definitions = new HashMap<>();

  /**
   * Return an unmodifiable Collection of the current Prototype Definitions
   * @return PrototypeDefinition Collection
   */
  public Collection<PrototypeDefinition> getDefinitions() {
    return Collections.unmodifiableCollection(definitions.values());
  }

  @Override
  public String[] getAttributeDescriptions() {
    return new String[0];
  }

  @Override
  public Class<?>[] getAttributeTypes() {
    return new Class<?>[0];
  }

  @Override
  public String[] getAttributeNames() {
    return new String[0];
  }

  @Override
  public String getAttributeValueString(String key) {
    return null;
  }

  @Override
  public void setAttribute(String key, Object value) {
  }

  @Override
  public Configurer getConfigurer() {
    return null;
  }

  @Override
  public void addTo(Buildable parent) {

  }

  @Override
  public Class<?>[] getAllowableConfigureComponents() {
    return new Class<?>[]{ PrototypeFolder.class, PrototypeDefinition.class };
  }

  public static String getConfigureTypeName() {
    return Resources.getString("Editor.PrototypesContainer.component_type"); //$NON-NLS-1$
  }


  private void rebuildPrototypeMap(AbstractBuildable target) {
    for (final Buildable b : target.getBuildables()) {
      if (b instanceof PrototypeDefinition) {
        addDefinition((PrototypeDefinition)b);
      }
      else if (b instanceof AbstractBuildable) {
        rebuildPrototypeMap((AbstractBuildable)b);
      }
    }
  }


  public void addDefinition(PrototypeDefinition def) {
    definitions.put(def.getConfigureName(), def);
    def.addPropertyChangeListener(evt -> {
      if (Configurable.NAME_PROPERTY.equals(evt.getPropertyName())) {
        // When a prototype is renamed we need to rebuild the prototype map, so that if there was a duplicate of the same name it will re-establish its presence
        definitions.clear();
        rebuildPrototypeMap(GameModule.getGameModule());
      }
    });
  }

  @Override
  public void add(Buildable b) {
    super.add(b);
    if (b instanceof PrototypeDefinition) {
      addDefinition((PrototypeDefinition) b);
    }
  }

  @Override
  public HelpFile getHelpFile() {
    return HelpFile.getReferenceManualPage("Prototypes.html"); //$NON-NLS-1$
  }

  @Override
  public void removeFrom(Buildable parent) {
  }

  public static PrototypesContainer findInstance() {
    if (instance == null) {
      final Iterator<PrototypesContainer> i =
        GameModule.getGameModule()
          .getComponentsOf(PrototypesContainer.class)
          .iterator();
      if (i.hasNext()) {
        instance = i.next();
      }
      else {
        return null;
      }
    }
    return instance;
  }

  private void resetCache(AbstractBuildable target) {
    for (final Buildable b : target.getBuildables()) {
      if (b instanceof PrototypeDefinition) {
        ((PrototypeDefinition)b).clearCache();
      }
      else if (b instanceof AbstractFolder) {
        resetCache((AbstractBuildable)b);
      }
    }
  }


  private void resetPieceCache(AbstractBuildable target) {
    for (final Buildable b : target.getBuildables()) {
      if (b instanceof PieceSlot) {
        ((PieceSlot)b).clearCache();
      }
      else if (b instanceof AbstractBuildable) {
        resetPieceCache((AbstractBuildable)b);
      }
    }
  }

  public void resetCache() {
    resetCache(this);

    resetPieceCache(GameModule.getGameModule());
  }

  public static PrototypeDefinition getPrototype(String name) {
    findInstance();
    return (instance == null) ? null : instance.definitions.get(name);
  }

  @Override
  public ComponentI18nData getI18nData() {
    final ComponentI18nData data = super.getI18nData();
    data.setPrefix(""); //$NON-NLS-1$
    return data;
  }

  @Override
  public boolean isMandatory() {
    return true;
  }

  @Override
  public boolean isUnique() {
    return true;
  }
}
