package com;

import javax.swing.*;

// com.Main class
public class Main {

    // com.Main method come inizio dell'applicazione
    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {
            MainViewer mainViewer = new MainViewer();       // Crea l'oggetto com.MainViewer
            mainViewer.start();                             // Fa partire l'oggetto e le sue funzionalità
        });
    }
}