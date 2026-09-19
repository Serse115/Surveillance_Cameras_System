package com;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/******** Customized option pane for the "pop-up messages" to the user ********/
public class MyOptionPane {

    /* Fields */
    // Empty


    /**** Constructors ****/
    // Main constructor for the popup message
    public MyOptionPane(final String message, final int messageType, final String information, int displayTimeMillis) {

        JOptionPane optionPane = new JOptionPane();             // Creating the option pane
        optionPane.setMessage(message);                         // Setting the message
        optionPane.setMessageType(messageType);                 // Setting the message type
        optionPane.setOptionType(JOptionPane.DEFAULT_OPTION);   // Set the default option of buttons
        unFocusTheButton(optionPane);                           // Unfocus method to take off the annoying highlighting of it

        JDialog dialog = optionPane.createDialog(null, information);        // Dialogue pane with the information passed as a parameter

        // Auto closing timer object
        Timer timer = new Timer(displayTimeMillis, e -> {   // Set the timer with a parameter and gives it a functionality
            if (dialog.isDisplayable()) {                   // If the dialog pane is still displayable and active
                dialog.dispose();                           // Dispose of the pane
            }
        });
        timer.setRepeats(false);                            // Stops the recurring timer to not repeat itself

        // If the user clicks ok to close or waits 3 seconds, the pane is supposed to close
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (timer.isRunning()) {                    // If it's closed but the timer is still running
                    timer.stop();                           // Stop it
                }
            }
        });

        timer.start();                      // Start the timer
        dialog.setVisible(true);            // Set the pane visible
    }

    // Secondary constructor for the popup message (The main one used in every class that implements it)
    public MyOptionPane(final String message, final int messageType, final String information) {
        this(message, messageType, information, 3500);          // Using the Primary constructor to set the parameter of the timer
    }


    /**** Methods ****/
    // Recursive un-focus button to turn off the highlight of the ok option button in the pane
    private static void unFocusTheButton(Component component) {
        if (component instanceof JButton) {                                   // If the component is a JButton
            component.setFocusable(false);                                    // un-focus it
        }
        else if (component instanceof Container) {                            // Else
            for (Component c : ((Container) component).getComponents()) {     // recursively look through the components to find the button 
                unFocusTheButton(c);                                          // and un-focus it
            }
        }
    }
}