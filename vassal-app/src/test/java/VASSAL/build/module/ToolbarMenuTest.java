package VASSAL.build.module;

import VASSAL.build.AbstractToolbarItem;
import VASSAL.build.GameModule;
import VASSAL.build.module.folder.MapSubFolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ToolbarMenuTest {

  private MockedStatic<GameModule> staticGm;

  @BeforeEach
  public void setUp() {
    staticGm = Mockito.mockStatic(GameModule.class);
    final GameModule gm = mock(GameModule.class);
    staticGm.when(GameModule::getGameModule).thenReturn(gm);
    when(gm.getGameState()).thenReturn(mock(GameState.class));
  }

  @AfterEach
  public void tearDown() {
    staticGm.close();
  }

  @Test
  public void removeFromShouldWorkWithoutToolbar() {
    // A folder with no ancestor yet, as when the Editor builds a pasted
    // folder before inserting it, so addTo() finds no toolbar.
    final MapSubFolder folder = new MapSubFolder();
    final ToolbarMenu menu = new ToolbarMenu();
    menu.addTo(folder);

    assertDoesNotThrow(() -> menu.removeFrom(folder));
  }

  /*
   * The nesting tests use one toolbar holding, as a module's toolbar would:
   *
   *   buttons "A" and "B", and Toolbar Menus "Parent", "Child", "Grandchild"
   *
   *   Parent     -> [Child]
   *   Child      -> [A, Grandchild]
   *   Grandchild -> [B]
   *
   * so Parent's popup is:  Child > ( A, Grandchild > ( B ) )
   *
   * Menus are built on the EDT, as in VASSAL, and pending rebuilds queued by
   * the ToolbarMenu container listeners are flushed before each assertion.
   */

  private JToolBar toolbar;
  private JButton buttonA;
  private JButton buttonB;
  private ToolbarMenu parent;
  private ToolbarMenu child;
  private ToolbarMenu grandchild;

  private ToolbarMenu makeMenu(String name, String... items) {
    final ToolbarMenu m = new ToolbarMenu();
    m.setAttribute(AbstractToolbarItem.BUTTON_TEXT, name);
    m.setAttribute(ToolbarMenu.MENU_ITEMS, items);
    return m;
  }

  private void buildNestedMenus() throws Exception {
    toolbar = new JToolBar();
    buttonA = new JButton("A");
    buttonB = new JButton("B");
    parent = makeMenu("Parent", "Child");
    child = makeMenu("Child", "A", "Grandchild");
    grandchild = makeMenu("Grandchild", "B");

    toolbar.add(buttonA);
    toolbar.add(buttonB);
    for (final ToolbarMenu m : new ToolbarMenu[] { parent, child, grandchild }) {
      m.toolbar = toolbar;
      toolbar.add(m.getLaunchButton());
    }

    // Build innermost first, as the deferred builds settle in a real module
    onEdt(() -> {
      grandchild.buildMenu();
      child.buildMenu();
      parent.buildMenu();
    });
  }

  /** Run on the EDT, then wait for any rebuilds it queued to run too */
  private static void onEdt(Runnable r) throws Exception {
    SwingUtilities.invokeAndWait(r);
    SwingUtilities.invokeAndWait(() -> { });
    SwingUtilities.invokeAndWait(() -> { });
  }

  private static JMenuItem find(MenuElement container, String text) {
    for (final MenuElement e : container.getSubElements()) {
      if (e instanceof JMenuItem && text.equals(((JMenuItem) e).getText())) {
        return (JMenuItem) e;
      }
      final JMenuItem found = find(e, text);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /** Parent's view of the item: Parent > Child > ... */
  private JMenuItem inParent(String text) {
    final JMenuItem item = find(parent.menu, text);
    assertTrue(item != null, "Parent menu has no item " + text);
    return item;
  }

  @Test
  public void nestedMenusMirrorChildStructure() throws Exception {
    buildNestedMenus();

    final JPopupMenu popup = parent.menu;
    assertEquals(1, popup.getComponentCount());
    final JMenu childMenu = assertInstanceOf(JMenu.class, popup.getComponent(0));
    assertEquals("Child", childMenu.getText());

    // Child > ( A, Grandchild > ( B ) ) -- Grandchild must stay a sub-menu
    assertEquals(2, childMenu.getMenuComponentCount());
    assertEquals("A", ((JMenuItem) childMenu.getMenuComponent(0)).getText());
    final JMenu gcMenu = assertInstanceOf(JMenu.class, childMenu.getMenuComponent(1));
    assertEquals("Grandchild", gcMenu.getText());
    assertEquals(1, gcMenu.getMenuComponentCount());
    assertEquals("B", ((JMenuItem) gcMenu.getMenuComponent(0)).getText());

    // Every listed button is hidden from the toolbar
    assertFalse(buttonA.isVisible());
    assertFalse(buttonB.isVisible());
    assertFalse(child.getLaunchButton().isVisible());
    assertFalse(grandchild.getLaunchButton().isVisible());
  }

  @Test
  public void disablingButtonInChildMenuDisablesParentCopy() throws Exception {
    buildNestedMenus();
    assertTrue(inParent("A").isEnabled());

    onEdt(() -> buttonA.setEnabled(false));
    assertFalse(find(child.menu, "A").isEnabled(), "Child's own item");
    assertFalse(inParent("A").isEnabled(), "Parent's copy of Child's item");

    onEdt(() -> buttonA.setEnabled(true));
    assertTrue(inParent("A").isEnabled());
  }

  @Test
  public void disablingButtonInGrandchildMenuDisablesParentCopy() throws Exception {
    buildNestedMenus();
    assertTrue(inParent("B").isEnabled());

    onEdt(() -> buttonB.setEnabled(false));
    assertFalse(inParent("B").isEnabled());

    onEdt(() -> buttonB.setEnabled(true));
    assertTrue(inParent("B").isEnabled());
  }

  @Test
  public void disablingNestedToolbarMenuDisablesItsSubMenus() throws Exception {
    buildNestedMenus();

    onEdt(() -> grandchild.getLaunchButton().setEnabled(false));
    assertFalse(find(child.menu, "Grandchild").isEnabled());
    assertFalse(inParent("Grandchild").isEnabled());

    onEdt(() -> child.getLaunchButton().setEnabled(false));
    assertFalse(inParent("Child").isEnabled());

    onEdt(() -> {
      grandchild.getLaunchButton().setEnabled(true);
      child.getLaunchButton().setEnabled(true);
    });
    assertTrue(inParent("Child").isEnabled());
    assertTrue(inParent("Grandchild").isEnabled());
  }

  @Test
  public void disabledStateSurvivesRebuild() throws Exception {
    buildNestedMenus();

    // Disable things, then rebuild everything, as setup() does at game start
    onEdt(() -> {
      buttonA.setEnabled(false);
      buttonB.setEnabled(false);
      grandchild.getLaunchButton().setEnabled(false);
      child.getLaunchButton().setEnabled(false);
    });
    onEdt(() -> {
      grandchild.buildMenu();
      child.buildMenu();
      parent.buildMenu();
    });

    assertFalse(inParent("Child").isEnabled());
    assertFalse(inParent("A").isEnabled());
    assertFalse(inParent("Grandchild").isEnabled());
    assertFalse(inParent("B").isEnabled());
  }

  @Test
  public void propertyGateOnNestedMenuDisablesParentSubMenu() throws Exception {
    buildNestedMenus();

    // The "Disable button when property is true" path, minus the property lookup
    child.setAttribute(AbstractToolbarItem.CAN_DISABLE, Boolean.TRUE);
    onEdt(() -> child.disableIfTrue(true));
    assertFalse(inParent("Child").isEnabled());

    onEdt(() -> child.disableIfTrue(false));
    assertTrue(inParent("Child").isEnabled());
  }
}
