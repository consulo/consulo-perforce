// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.perforce.perforce.jobs;

import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.platform.Platform;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.ClickListener;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.action.CustomComponentAction;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Toolbar action shown as a text field with an icon; pressing Enter in the field or clicking the icon calls {@link #perform()}.
 */
@SuppressWarnings("deprecation")
public abstract class TextFieldAction extends AnAction implements CustomComponentAction, DumbAware {
    protected final JTextField myField;
    private final LocalizeValue myDescription;
    private final Image myIcon;

    protected TextFieldAction(LocalizeValue text, LocalizeValue description, Image icon, int initSize) {
        super(text, description, icon);
        myDescription = description;
        myIcon = icon;
        myField = new JTextField(initSize);
        myField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    perform();
                }
            }
        });
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        perform();
    }

    public void perform() {
    }

    @Override
    public JComponent createCustomComponent(Presentation presentation, String place) {
        // honestly borrowed from SearchTextField

        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(TargetAWT.to(myIcon));
        label.setOpaque(true);
        label.setBackground(myField.getBackground());
        myField.setOpaque(true);
        panel.add(myField, BorderLayout.WEST);
        panel.add(label, BorderLayout.EAST);
        myField.setToolTipText(myDescription.get());
        label.setToolTipText(myDescription.get());
        Border originalBorder;
        if (Platform.current().os().isMac()) {
            originalBorder = BorderFactory.createLoweredBevelBorder();
        }
        else {
            originalBorder = myField.getBorder();
        }

        panel.setBorder(new CompoundBorder(JBUI.Borders.empty(4, 0, 4, 0), originalBorder));

        myField.setOpaque(true);
        myField.setBorder(JBUI.Borders.empty(0, 5, 0, 5));

        new ClickListener() {
            @Override
            public boolean onClick(MouseEvent e, int clickCount) {
                perform();
                return true;
            }
        }.installOn(label);

        return panel;
    }
}
