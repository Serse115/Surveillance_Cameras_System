package com;

import javax.swing.SwingUtilities;

/**
 * --ITA-----------ITA------------ITA---------ITA--------------ITA--------ITA--
 * ============================================================================
 * CLASSE PRINCIPALE (ENTRY POINT) - NVR VIDEOSORVEGLIANZA
 * ============================================================================
 *
 * Scopo della classe Main:
 * Rappresenta il punto d'ingresso dell'applicazione (Main entry point).
 * Il suo unico compito è istanziare e avviare la GUI e i flussi di visualizzazione
 * in modo completamente thread-safe rispetto all'Event Dispatch Thread (EDT) di Swing.
 *
 * Flusso di Esecuzione generale del programma:
 * 1. Avvio della JVM e chiamata al metodo convenzionale standard {@code main(String[] args)}.
 * 2. Inoltro dell'inizializzazione alla coda degli eventi di Swing mediante {@link SwingUtilities#invokeLater(Runnable)}.
 * 3. Costruzione dell'oggetto {@link MainViewer}:
 *    - Inizializzazione della finestra JFrame a tutto schermo (MAXIMIZED_BOTH).
 *    - Configurazione del layout a 3 livelli (panoramiche 32:9 e telecamere 16:9 / 4:3).
 *    - Registrazione dei puntatori ai flussi RTSP per ciascuna telecamera.
 * 4. Invocazione di {@link MainViewer#start()}:
 *    - Mostra la finestra a schermo ({@code setVisible(true)}).
 *    - Avvia i motori video vlcj per i flussi secondari (SUB-stream) solo dopo che
 *      i componenti sono diventati "displayable" nel contesto grafico nativo.
 *
 * Dettagli Tecnici & Best Practices:
 * - Thread-Safety: In Swing, qualsiasi creazione o manipolazione di componenti UI
 *   deve avvenire tassativamente all'interno dell'EDT per prevenire deadlock, race condition
 *   e glitch di rendering visivo.
 * - Architettura Minimale: La classe delega interamente logica, business logic, interazioni
 *   e gestione processi a {@link MainViewer}, mantenendo l'entry point snello e manutenibile.
 */
public class Main {

    /**
     * Entry point principale dell'applicazione NVR.
     *
     * @param args Argomenti da riga di comando (non utilizzati).
     */
    public static void main(String[] args) {

        // Delega l'inizializzazione grafica e logica all'Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            MainViewer mainViewer = new MainViewer();           // Crea l'oggetto mainViewer
            mainViewer.start();                                 // E lo fa partire, iniziando il ciclo di vita reale del programma
        });
    }
}

/*
--ENG------------ENG-------------ENG---------ENG--------------ENG--------ENG--
  ============================================================================
  MAIN CLASS (ENTRY POINT) - NVR VIDEOSORVEGLIANZA
  ============================================================================

  Objective of the Main class:
  The class represents the starting point of the whole application (Main entry point).
  Its only duty is to instantiate and start the GUI and the viewing streams
  in a thread-safe environment towards the Swing's Event Dispatch Thread (EDT).

  General execution process of the program:
  1. The JVM starts by calling the main method {@code main(String[] args)}.
  2. Initialization process to the Swing event queue through {@link SwingUtilities#invokeLater(Runnable)}.
  3. MainViewer object creation {@link MainViewer}:
     - Initializing the JFrame to the full screen mode (MAXIMIZED_BOTH).
     - 3 layers layout configuration (32:9 wide view, 19:9 / 4:3 view).
     - Linking to the RTSP stream for each camera.
  4. Calling {@link MainViewer#start()}:
     - Shows the fullscreen window ({@code setVisible(true)}).
     - Starts the vlcj for the secondary streams (SUB-stream) only after the components
       turned as "displayable" in the native graphic environment.

  Technical details and best practices applied:
  - Thread-Safety: Each creation/manipulation/edit of UI components in Swing needs to happen inside the EDT to
    prevent deadlocks, race conditions or graphic glitches.
  - Minimal architecture: The main class acts only as a launch point for the program, every logic, interactions
    or processes are handled in their respective classes to keep the logic bound to its component
*/