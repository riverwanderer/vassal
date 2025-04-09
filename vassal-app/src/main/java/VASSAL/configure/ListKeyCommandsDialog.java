/*
 *
 * Copyright (c) 2022 by the Vassal Team, Joel Uckelman, Brian Reynolds
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

package VASSAL.configure;

import VASSAL.build.AbstractBuildable;
import VASSAL.build.AbstractConfigurable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Documentation;
import VASSAL.build.module.KeyNamer;
import VASSAL.build.module.PrototypeDefinition;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.widget.PieceSlot;
import VASSAL.counters.Decorator;
import VASSAL.counters.GamePiece;
import VASSAL.i18n.Resources;
import VASSAL.launch.EditorWindow;
import VASSAL.search.SearchTarget;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.NamedKeyStroke;
import VASSAL.tools.icon.IconFamily;
import VASSAL.tools.lang.Pair;
import VASSAL.tools.swing.SwingUtils;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Editor tool for finding all the key commands (and named key commands) in a module, and applying sorts/filters to them.
 */
public class ListKeyCommandsDialog extends JDialog {
  private static final long serialVersionUID = 1L;
  private static final int KEY_COMMAND_COLUMN = 0;
  private static final int COLUMN_COUNT = 6;
  private static final int NAMED_COMMAND_COLUMN = 1;
  private static final int TYPE_COLUMN = 2;
  private static final int NAME_COLUMN = 3;
  private static final int PATH_COLUMN = 4;
  private static final int DESC_COLUMN = 5;
  private final EditorWindow owner;
  private final MyTableModel tmod;

  public ListKeyCommandsDialog(EditorWindow owner, List<Pair<String[], AbstractConfigurable>> rows) {
    super((Frame)null, Resources.getString("Editor.ListKeyCommands.list_key_commands"), true);

    this.owner = owner;

    final JTextField filter = new JTextField(25);

    tmod = new MyTableModel(rows);
    final JTable table = new JTable(tmod) {
      // Tooltips for table cells
      @Override
      public String getToolTipText(MouseEvent e) {
        final java.awt.Point p = e.getPoint();
        final int rowIndex = rowAtPoint(p);
        final int colIndex = columnAtPoint(p);
        final Object obj = getValueAt(rowIndex, colIndex);
        return (obj != null) ? obj.toString() : null;
      }
    };

    setPreferredSize(new Dimension(1900, 800));

    // Is there a better way to get decent starting column sizes?
    table.getColumnModel().getColumn(KEY_COMMAND_COLUMN).setPreferredWidth(114);
    table.getColumnModel().getColumn(NAMED_COMMAND_COLUMN).setPreferredWidth(140);
    table.getColumnModel().getColumn(TYPE_COLUMN).setPreferredWidth(200);
    table.getColumnModel().getColumn(NAME_COLUMN).setPreferredWidth(200);
    table.getColumnModel().getColumn(PATH_COLUMN).setPreferredWidth(400);
    table.getColumnModel().getColumn(DESC_COLUMN).setPreferredWidth(800);

    // Popup menu provides right-click context menu to copy
    final JPopupMenu pm = new JPopupMenu();
    pm.add(new CopyAction(table));
    pm.add(new JumpAction(table));

    table.addMouseListener(new MouseAdapter() {

      private void doMouseStuff(MouseEvent e) {
        final int r = table.rowAtPoint(e.getPoint());
        if (r >= 0 && r < table.getRowCount()) {
          if (!table.getSelectionModel().isSelectedIndex(r)) {
            table.setRowSelectionInterval(r, r);
          }
        }
        else {
          table.clearSelection();
        }

        final int rowindex = table.getSelectedRow();
        if (rowindex < 0)
          return;

        if (e.isPopupTrigger()) {
          doPopup(e);
        }
      }

      @Override
      public void mousePressed(MouseEvent e) {
        doMouseStuff(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        doMouseStuff(e);
      }

      protected void doPopup(MouseEvent e) {
        pm.show(e.getComponent(), e.getX(), e.getY());
      }
    });

    final TableRowSorter<MyTableModel> trs = new TableRowSorter<>(tmod);
    table.setRowSorter(trs);
    trs.setSortsOnUpdates(true);

    trs.setRowFilter(new RowFilter<TableModel, Integer>() {
      @Override
      public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
        // show row on an empty filter
        final String f = filter.getText();
        if (f == null) {
          return true;
        }

        // show row containing the filter as a substring
        for (int i = entry.getValueCount() - 1; i >= 0; i--) {
          if (i == PATH_COLUMN) continue; // Don't include path column in filter
          final String v = entry.getStringValue(i);
          if (v != null && v.toLowerCase().contains(f.toLowerCase())) {
            return true;
          }
        }
        return false;
      }
    });

    final JPanel panel = new JPanel(new MigLayout("fill", "[]", "[]rel[]unrel[]"));  //NON-NLS

    panel.setPreferredSize(new Dimension(1400, 700));

    panel.add(filter, "split"); //NON-NLS

    filter.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        update();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        update();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        update();
      }

      private void update() {
        trs.allRowsChanged();
      }
    });

    final NoInsetButton clear = new NoInsetButton("no", IconFamily.XSMALL, "Editor.clear"); //NON-NLS
    clear.addActionListener(e -> filter.setText(null));
    panel.add(clear, "wrap"); //NON-NLS

    final JScrollPane scroll = new JScrollPane(table);
    panel.add(scroll, "grow, push, wrap"); //NON-NLS

    final JButton ok = new JButton(Resources.getString("General.ok"));
    ok.addActionListener(e -> {
      dispose();
      owner.clearListKeyCommands();
    });

    final JButton help = new JButton(Resources.getString("General.help"));
    help.addActionListener(e -> help());

    final JPanel buttonPanel = new JPanel(new MigLayout("ins 0", "push[]rel[]push", "")); // NON-NLS
    buttonPanel.add(ok, "tag ok, sg 1"); //$NON-NLS-1$//
    buttonPanel.add(help, "tag help, sg 1"); //NON-NLS
    panel.add(buttonPanel, "growx"); // NON-NLS

    setLayout(new MigLayout("insets dialog, fill")); // NON-NLS
    add(panel, "grow"); // NON-NLS

    setModal(false);

    SwingUtils.repack(this);

    this.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        dispose();
        owner.clearListKeyCommands();
      }
    });
  }

  public void updateConfigurable(AbstractConfigurable target) {
    tmod.updateConfigurable(target);
  }

  public void deleteConfigurable(AbstractConfigurable target) {
    tmod.deleteConfigurable(target);
  }


  // Copy action for right click context menu
  public static class CopyAction extends AbstractAction {
    private static final long serialVersionUID = 1L;
    private final JTable table;

    public CopyAction(JTable table) {
      this.table = table;
      putValue(NAME, Resources.getString("General.copy"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      table.getActionMap().get("copy").actionPerformed(new ActionEvent(table, e.getID(), ""));
    }
  }

  public class JumpAction extends AbstractAction {
    private static final long serialVersionUID = 1L;
    private final JTable table;

    public JumpAction(JTable table) {
      this.table = table;
      putValue(NAME, Resources.getString("Editor.ListKeyCommands.jump_to_definition"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      int rowIndex = table.getSelectedRow();
      if (rowIndex < 0) return;

      rowIndex = table.convertRowIndexToModel(rowIndex);

      final AbstractConfigurable jumpTo = ((MyTableModel)(table.getModel())).getSourceFor(rowIndex);
      owner.getTree().jumpToTarget(jumpTo);
      owner.toFront();
    }
  }

  private static class MyTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;

    private final List<Pair<String[], AbstractConfigurable>> rows;

    public MyTableModel(List<Pair<String[], AbstractConfigurable>> rows) {
      this.rows = rows;
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return COLUMN_COUNT;
    }

    public AbstractConfigurable getSourceFor(int row) {
      return rows.get(row).second;
    }

    public void deleteConfigurable(AbstractConfigurable target) {
      int lowest = -1;
      int highest = -1;
      for (int i = rows.size() - 1; i >= 0; i--) {
        if (!rows.get(i).second.equals(target)) continue;
        rows.remove(i);
        lowest = i;
        if (highest < 0) {
          highest = i;
        }
      }
      if (highest >= 0) {
        fireTableRowsDeleted(lowest, highest);
      }
    }

    public void updateConfigurable(AbstractConfigurable target) {
      deleteConfigurable(target);
      final int size = rows.size();
      checkForKeyCommands(target, rows);
      if (rows.size() > size) {
        fireTableRowsInserted(size, rows.size() - 1);
      }
    }

    @Override
    public String getColumnName(int col) {
      switch (col) {
      case KEY_COMMAND_COLUMN:
        return Resources.getString("Editor.ListKeyCommands.key_command");
      case NAMED_COMMAND_COLUMN:
        return Resources.getString("Editor.ListKeyCommands.named_command");
      case TYPE_COLUMN:
        return Resources.getString("Editor.ListKeyCommands.source_type");
      case NAME_COLUMN:
        return Resources.getString("Editor.ListKeyCommands.source_name");
      case PATH_COLUMN:
        return Resources.getString("Editor.ListKeyCommands.source_path");
      case DESC_COLUMN:
        return Resources.getString("Editor.ListKeyCommands.source_description");
      default:
        return "";
      }
    }

    @Override
    public Object getValueAt(int row, int column) {
      return row < rows.size() ? rows.get(row).first[column] : null;
    }
  }

  /**
   * For a given search target (either an AbstractConfigurable or a trait(Decorator) of a game piece),
   * pull out its list of key commands and add them to our table's list.
   * @param target search target to check
   * @param list our table's list of column strings to be appended to
   * @param configurable If search target is a trait, this is the original AbstractConfigurable (e.g. a PrototypeDefinition, a Piece Slot, etc) that it came from.
   */
  private static void checkSearchTarget(SearchTarget target, List<Pair<String[], AbstractConfigurable>> list, AbstractConfigurable configurable) {
    final List<NamedKeyStroke> keys = target.getNamedKeyStrokeList();
    if (keys != null) {
      for (final NamedKeyStroke k : keys) {
        if (k != null) {
          String cmd_key = null;
          String cmd_name = null;

          if (k.isNamed()) {
            cmd_name = k.getName();
          }
          else {
            cmd_key = KeyNamer.getKeyString(k.getStroke());
          }

          if (!StringUtils.isEmpty(cmd_key) || !StringUtils.isEmpty(cmd_name)) {
            String src_name = null;
            String src_desc = null;
            String src_type = null;

            // Depending on whether this is an AbstractConfigurable (i.e. Editor component) or part of a GamePiece, pull out appropriate descriptor fields.
            if (target instanceof AbstractConfigurable) {
              src_name = ((AbstractConfigurable)target).getConfigureName();

              if (target instanceof ComponentDescription) {
                src_desc = ((ComponentDescription)target).getDescription();
              }

              src_type = ((AbstractConfigurable)target).getTypeName();
            }
            else if (target instanceof GamePiece) {
              if (configurable instanceof PrototypeDefinition)  {
                src_name = Resources.getString("Editor.ListKeyCommands.prototype") + ": " + configurable.getConfigureName();
              }
              else {
                src_name = ((GamePiece) target).getName();
              }

              if (target instanceof Decorator) {
                src_desc = ((Decorator) target).getDescriptionField();
                src_type = ((Decorator) target).getBaseDescription();
              }
            }

            String src_path = "";
            Buildable path = configurable.getAncestor();
            while ((path instanceof AbstractBuildable) && !(path instanceof GameModule)) {
              if (path instanceof AbstractConfigurable) {
                String node = ((AbstractConfigurable) path).getConfigureName();
                if ((node == null) || node.isBlank()) {
                  node = "[" + ((AbstractConfigurable) path).getTypeName() + "]";
                }
                if (!src_path.isEmpty()) {
                  src_path += " > ";
                }
                src_path += node;
              }
              path = ((AbstractBuildable)path).getAncestor();
            }

            list.add(Pair.of(new String[] { cmd_key, cmd_name, src_type, src_name, src_path, src_desc }, configurable));
          }
        }
      }
    }
  }

  /**
   * For a given AbstractConfigurable, determine if it is one of the "Game Piece" types.
   * If it IS, parse each individual Decorator to check as a search target. Otherwise, for
   * non-game-piece configurables, just check the configurable itself.
   * @param target AbstractConfigurable to check
   * @param list Our table list to which to add any key commands found
   */
  private static void checkForKeyCommands(AbstractConfigurable target, List<Pair<String[], AbstractConfigurable>> list) {
    GamePiece p;
    boolean protoskip;
    if (target instanceof GamePiece) {
      p = (GamePiece) target;
      protoskip = false;
    }
    else if (target instanceof PieceSlot) {
      p = ((PieceSlot)target).getPiece();
      protoskip = false;
    }
    else if (target instanceof PrototypeDefinition) {
      p = ((PrototypeDefinition)target).getPiece();
      protoskip = true;
    }
    else  {
      checkSearchTarget(target, list, target);
      return;
    }

    // We're going to search Decorator from inner-to-outer (BasicPiece-on-out), so that user sees the traits hit in
    // the same order they're listed in the PieceDefiner window. So we first traverse them in the "normal" direction
    // outer to inner and make a list in the order we want to traverse it (for architectural reasons, just traversing
    // with getOuter() would take us inside of prototypes inside a piece, which we don't want).
    final List<GamePiece> pieces = new ArrayList<>();
    pieces.add(p);
    while (p instanceof Decorator) {
      p = ((Decorator) p).getInner();
      pieces.add(p);
    }
    Collections.reverse(pieces);

    for (final GamePiece piece : pieces) {
      if (!protoskip) { // Skip the fake "Basic Piece" on a Prototype definition
        if (piece instanceof SearchTarget) {
          checkSearchTarget((SearchTarget)piece, list, target);
        }
      }
      protoskip = false;
    }
  }

  /**
   * Recursively scans the module for key commands and adds appropriate entries to our table's list of key commands
   * @param target current target component
   * @param list our table's list of column strings, to which entries will be appended
   */
  private static void recursivelyFindKeyCommands(AbstractBuildable target, List<Pair<String[], AbstractConfigurable>> list) {
    for (final Buildable b : target.getBuildables()) {
      if (b instanceof AbstractConfigurable) {
        checkForKeyCommands((AbstractConfigurable)b, list);
      }

      if (b instanceof AbstractBuildable) {
        recursivelyFindKeyCommands((AbstractBuildable)b, list);
      }
    }
  }

  /**
   * Begins a recursive search of the entire module for key commands
   * @return list of column entries for our table
   */
  public static List<Pair<String[], AbstractConfigurable>> findAllKeyCommands() {
    final List<Pair<String[], AbstractConfigurable>> keyCommandList = new ArrayList<>();
    recursivelyFindKeyCommands(GameModule.getGameModule(), keyCommandList);
    return keyCommandList;
  }

  /**
   * Show help html
   */
  private void help() {
    HelpFile hf = null;
    try {
      hf = new HelpFile(null, new File(
        new File(Documentation.getDocumentationBaseDir(), "ReferenceManual"),
        "ListKeyCommands.html"));
    }
    catch (MalformedURLException ex) {
      ErrorDialog.bug(ex);
    }

    if (hf != null) {
      (new ShowHelpAction(hf.getContents(), null)).actionPerformed(null);
    }
  }
}
