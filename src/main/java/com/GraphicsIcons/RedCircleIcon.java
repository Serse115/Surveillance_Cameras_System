package com.GraphicsIcons;

import javax.swing.*;
import java.awt.*;

public class RedCircleIcon implements Icon {

    /* Fields */
    // Constants
    private final int size;



    /* Constructors */
    public RedCircleIcon(final int size) {
        this.size = size;
    }



    /* Methods */
    // Metodo per disegnare l'icona
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(220, 40, 40));
        g2.fillOval(x + 2, y + 2, this.size - 4, this.size - 4);
        g2.dispose();
    }

    // Getter per la larghezza dell'icona
    @Override
    public int getIconWidth() {
        return this.size;
    }

    // Getter per l'altezza dell'icona
    @Override
    public int getIconHeight() {
        return this.size;
    }
}