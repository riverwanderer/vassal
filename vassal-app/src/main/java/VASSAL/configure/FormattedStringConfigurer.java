/*
 * Copyright (c) 2000-2009 by Rodney Kinney & Brent Easton
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
/*
 * FormattedStringConfigurer.
 * Extended version of StringConfigure that provides a drop down list of options that can
 * be inserted into the string
 */
package VASSAL.configure;

import VASSAL.i18n.Resources;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.text.JTextComponent;

public class FormattedStringConfigurer extends StringConfigurer implements ActionListener, FocusListener {
    private static final long serialVersionUID = 1L;

    private final transient DefaultComboBoxModel<String> optionsModel;
    private transient JComboBox<String> dropList;
    private transient boolean processingSelection;

    public FormattedStringConfigurer(String key, String name) {
        this(key, name, new String[0]);
    }

    public FormattedStringConfigurer(String[] options) {
        this(null, "", options);
    }

    public FormattedStringConfigurer(String key, String name, String[] options) {
        super(key, name);
        optionsModel = new DefaultComboBoxModel<>();
        processingSelection = false;
        setOptions(options);
    }

    /**
     * Set the list of options available for insertion
     * @param options array of options
     */
    public void setOptions(String[] options) {
        optionsModel.removeAllElements();
        optionsModel.addElement(Resources.getString("Editor.FormattedStringConfigurer.insert"));
        if (options != null) {
            for (String option : options) {
                if (option != null) {
                    optionsModel.addElement(option);
                }
            }
        }
        setListVisibility();
    }

    /**
     * @return the current list of options (excluding the initial "insert" prompt)
     */
    public String[] getOptions() {
        String[] s = new String[optionsModel.getSize() - 1]; // Exclude "insert" prompt
        for (int i = 0; i < s.length; i++) {
            s[i] = optionsModel.getElementAt(i + 1);
        }
        return s;
    }

    @Override
    public Component getControls() {
        if (p == null) {
            super.getControls();

            nameField.addFocusListener(this);
            
            dropList = new JComboBox<String>(optionsModel) {
                private static final long serialVersionUID = 1L;

                @Override
                public void setPopupVisible(boolean visible) {
                    if (!processingSelection) {
                        super.setPopupVisible(visible);
                    }
                }
            };
            dropList.setSelectedIndex(0);
            dropList.setEnabled(false);
            dropList.addActionListener(this);

            setListVisibility();
            p.add(dropList, "grow 0,right"); // NON-NLS
        }
        return p;
    }

    /**
     * Show the dropdown list only when there are options to select
     */
    private void setListVisibility() {
        if (dropList != null) {
            dropList.setVisible(optionsModel.getSize() > 1);
        }
    }

    /*
     * Drop-down list has been clicked, insert selected option onto string
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == dropList && dropList.isPopupVisible()) {
            processingSelection = true;
            try {
                int selectedIndex = dropList.getSelectedIndex();
                if (selectedIndex > 0) {
                    String item = "$" + optionsModel.getElementAt(selectedIndex) + "$";
                    
                    JTextComponent textComp = nameField;
                    int start = textComp.getSelectionStart();
                    int end = textComp.getSelectionEnd();
                    String text = textComp.getText();
                    
                    String newText = text.substring(0, start) + item + text.substring(end);
                    textComp.setText(newText);
                    
                    textComp.setCaretPosition(start + item.length());
                    
                    noUpdate = true;
                    setValue(newText);
                    noUpdate = false;
                }
            } finally {
                processingSelection = false;
                dropList.setSelectedIndex(0);
            }
            
            nameField.requestFocusInWindow();
            nameField.setCaretPosition(nameField.getCaretPosition());
        }
    }

    /*
     * Focus gained on text field, so enable insert drop-down
     * and make sure it says 'Insert'
     */
    @Override
    public void focusGained(FocusEvent e) {
        if (e.getSource() == nameField && dropList != null) {
            dropList.setSelectedIndex(0);
            dropList.setEnabled(true);
        }
    }

    /*
     * Focus lost on text field, so disable insert drop-down
     */
    @Override
    public void focusLost(FocusEvent e) {
        if (e.getSource() == nameField && dropList != null && !dropList.isFocusOwner() && !processingSelection) {
            dropList.setEnabled(false);
        }
    }
}
