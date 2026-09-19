package com;

import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Objects;

// Classe per il il pannello a schermo con i video presi direttamente dalla sorgente
public class MainViewer {

    /**** ****/
    /* Fields */
    // Constants
    //static final String RTSP_URL = "rtsp://192.168.0.101:8080/h264_pcm.sdp";        // RTSP URL DA CUI PRENDERE I DATI VIDEO
    private static final String RTSP_URL = "http://192.168.0.101:8080/video";        // RTSP URL DA CUI PRENDERE I DATI VIDEO

    // Variables
    private final JFrame mainFrame;                 // com.Main Swing frame per la visualizzazione
    private final EmbeddedMediaPlayerComponent mediaPlayerComponent;    // Componente embedded per il video player
    private final VideoRecorder videoRecorder;      // Componente Oggetto per la registrazione su cartella su disco
    private final JPanel bottomBar;                 // BottomBar per i controlli a fondo schermo
    private final JLabel statusLabel;               // Label con lo stato del player e della connessione alla fonte video
    private final JButton reconnectButton;          // Tasto per la riconnessione alla sorgente video
    private final JButton startRecordingButton;     // Tasto per iniziare la registrazione su cartella su disco
    private final JButton stopRecordingButton;      // Tasto per fermare la registrazione su cartella su disco
    private final JLabel timerLabel;                // Label per il timer per mostrare quanti secondi sono stati registrati
    private final Timer recordingTimer;             // Timer della registrazione in corso
    private int secondsRecorded;                    // Numero di secondi registrati




    /**** ****/
    /* Constructor */
    public MainViewer() {

        // Componente/Oggetto videoRecorder
        this.videoRecorder = new VideoRecorder("registrazioni");

        // Numero di secondi registrati
        this.secondsRecorded = 0;

        // JFrame come componente principale della grafica
        this.mainFrame = new JFrame("NVR VIDEOSORVEGLIANZA - MONITOR LOCALE");      // New frame
        this.mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);                  // Set default closing operation
        this.mainFrame.setSize(1280, 720);                                  // Set size del frame
        this.mainFrame.setLocationRelativeTo(null);                                     // Set relative location sullo schermo

        // Immagine per il Logo dell'applicazione
        ImageIcon logo = new ImageIcon(                                                                 // Oggetto Icona per il logo
                Objects.requireNonNull(MainViewer.class.getResource("/Logo_Sorveglianza.jpg"))    // Prende la risorsa dal path
        );
        Image appLogo = logo.getImage();                    // Trasforma la risorsa nel logo
        this.mainFrame.setIconImage(appLogo);               // Setta il logo dell'applicazione

        // Timer label
        // Label che indica il tempo di registrazione
        this.timerLabel = new JLabel("• REC 00:00");            // Timer Label per la registrazione
        this.timerLabel.setForeground(Color.RED);                   // Sfondo rosso
        this.timerLabel.setFont(new Font("Monospaced", Font.BOLD, 14));     // Font della scritta
        this.timerLabel.setVisible(false);                                            // Non visibile se la registrazione non è in corso

        // Media player integrato in Swing
        this.mediaPlayerComponent = new EmbeddedMediaPlayerComponent();         // Embedded media player

        // Bottom bar a fondo schermo
        this.bottomBar = new JPanel(new BorderLayout());        // Bottom bar con border layout

        JPanel leftPanelBottomBar = new JPanel(new FlowLayout(FlowLayout.LEFT));    // Panel a sx con il flowlayout sulla sx
        JPanel rightPanelBottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));  // Panel a dx con il flowlayout sulla dx

        // Label per lo stato della connessione del componente alla sorgente video
        this.statusLabel = new JLabel("Stato: Trasmissione a schermo dalla sorgente...");      // Label per indicare lo stato della connessione

        // Tasto per la riconnessione
        this.reconnectButton = new JButton("Riconnetti...");                            // Inizializza il tasto ed il nome
        this.reconnectButton.setFocusable(false);                                           // Setta il parametro focusable a false
        this.reconnectButton.addActionListener(e -> {                                       // Aggiunge l'action listener al tasto
            this.statusLabel.setText("Stato: Riconnessione in corso alla sorgente...");     // Setta il testo per indicare la riconnessione
            this.playStream(RTSP_URL);                                                      // Fa ripartire la funzione playStream
            this.statusLabel.setText("Stato: Trasmissione a schermo dalla sorgente...");    // Setta il testo per indicare la riconnessione
        });

        // Tasto per fermare la registrazione (Inizializzazione tasto e suo stato)
        this.stopRecordingButton = new JButton("Ferma registrazione");              // Inizializza il tasto ed il nome
        this.stopRecordingButton.setFocusable(false);                                   // Setta il parametro focusable a false
        this.stopRecordingButton.setEnabled(false);                                     // Non cliccabile se la registrazione non è in corso

        // Timer Swing che scatta ogni 1000ms ovvero 1 secondo
        // Con delay di 1 secondo
        this.recordingTimer = new Timer(1000, e -> {
            secondsRecorded++;                                                    // Aumenta il numero di secondi registrati

            // Calcolo di minuti e secondi
            int minutes = secondsRecorded / 60;
            int seconds = secondsRecorded % 60;

            // Formattazione a 2 cifre (01:00/00:30...)
            String timeString = String.format("• REC: %02d:%02d", minutes, seconds);    // Stringa con formato da mostrare con i dati
            timerLabel.setText(timeString);                                             // Aggiorna la timer label
        });

        // Tasto per iniziare la registrazione
        this.startRecordingButton = new JButton("Inizia registrazione");            // Inizializza il tasto ed il nome
        this.startRecordingButton.setFocusable(false);                                  // Setta il parametro focusable a false
        this.startRecordingButton.addActionListener(e -> {                              // Aggiunge l'action listener al tasto
            try {
                this.videoRecorder.startRecording(RTSP_URL, 30);          // Inizia la registrazione con segmenti di 30 secondi
                this.startRecordingButton.setEnabled(false);                            // Non cliccabile se la registrazione è in corso
                this.stopRecordingButton.setEnabled(true);                              // Cliccabile ora che la registrazione è in corso
                this.statusLabel.setText("Stato: Registrazione attiva con blocchi da 30 Secondi!");     // Aggiornamento label status

                // Reset ed avvio del timer di registrazione
                this.secondsRecorded = 0;                               // Reset del numero di secondi registrati
                this.timerLabel.setText("• REC 00:00");                 // Aggiorna il timer label
                this.timerLabel.setVisible(true);                       // Setta il parametro visibile a true
                this.recordingTimer.start();                            // Fa partire il timer di registrazione
            }
            catch (IOException ex) {
                ex.printStackTrace();
                new MyOptionPane("Errore Avvio FFmpeg!", 0, "Errore");
            }
        });

        // Tasto per fermare la registrazione (Add Action Listener)
        this.stopRecordingButton.addActionListener(e -> {                       // Aggiunge l'action listener al tasto
            this.videoRecorder.stopRecording();                                 // Ferma la registrazione dell'oggetto video recorder
            this.startRecordingButton.setEnabled(true);                         // Cliccabile ora che la registrazione non è in corso
            this.stopRecordingButton.setEnabled(false);                         // Non cliccabile se la registrazione non è in corso
            this.statusLabel.setText("Stato: Registrazione fermata.");          // Aggiornamento label status

            // Ferma e nasconde il timer di registrazione
            this.recordingTimer.stop();                             // Ferma il timer
            this.timerLabel.setVisible(false);                      // Setta il parametro visible a false
            this.statusLabel.setText("Stato: Trasmissione a schermo dalla sorgente...");     // Setta il testo per indicare la riconnessione
        });

        leftPanelBottomBar.add(this.reconnectButton);           // Aggiunge il tasto di riconnessione al pannello a sx in fondo
        leftPanelBottomBar.add(this.startRecordingButton);      // Aggiunge il tasto di inizio registrazione al pannello a sx in fondo
        leftPanelBottomBar.add(this.stopRecordingButton);       // Aggiunge il tasto di stop registrazione al pannello a sx in fondo
        leftPanelBottomBar.add(this.statusLabel);               // Aggiunge il label dello stato al pannello a sx in fondo

        rightPanelBottomBar.add(this.timerLabel);               // Aggiunge il timer label al pannello a dx in fondo

        this.bottomBar.add(leftPanelBottomBar, BorderLayout.WEST);                 // Aggiunge il pannello di sx alla barra in fondo
        this.bottomBar.add(rightPanelBottomBar, BorderLayout.EAST);                // Aggiunge il pannello di dx alla barra in fondo

        // Definizione per il layout di Swing
        this.mainFrame.setLayout(new BorderLayout());                           // Borderlayout per il frame principale
        this.mainFrame.add(this.mediaPlayerComponent, BorderLayout.CENTER);     // Aggiunta del media player component al centro del frame
        this.mainFrame.add(this.bottomBar, BorderLayout. SOUTH);                // Aggiunta della bottom bar in basso nel frame

        // Rilascio delle risorse della libreria VLC alla chiusura
        this.mainFrame.addWindowListener(new WindowAdapter() {              // Aggiunta di Windows Listener al Frame principale
            @Override                                                       // Override del metodo windowClosing
            public void windowClosing(WindowEvent e) {       // Metodo windowClosing da overridare
                if (videoRecorder.isRecording()) {                                  // Se il video recorder era in fase di registrazione
                    videoRecorder.stopRecording();                                  // Ferma la registrazione
                    recordingTimer.stop();                                          // Ferma il timer di registrazione
                }
                mediaPlayerComponent.release();                             // Rilascio risorse
                System.exit(0);                                       // Chiude con System exit
            }
        });
    }



    /**** ****/
    /* Methods */
    // Play Stream method per configurare i flag ottimali per la migliore latenza possibile in locale
    public void playStream(final String url) {

        // Aggiunta dei flags ottimali
        String[] vlcOptions = {
                ":network-caching=1000",             // Latenza network a 100ms
                //":network-caching=200",             // Latenza network ridotta a 200ms
                ":clock-jitter=0",
                ":clock-synchro=0",
                ":rtsp-tcp",                        // Forza l'uso di TCP
                ":no-audio",                        // Niente audio al momento per risparmiare risorse
                //":live-caching=200"                 // Latenza live ridotta a 200ms
        };

        this.mediaPlayerComponent.mediaPlayer().media().play(url, vlcOptions);      // PLAY Con le flag selezionate in precedenza
    }

    // Start method per far partire il Frame ed il MediaPlayerComponent
    public void start() {
        this.mainFrame.setVisible(true);        // Rende il frame visibile ed attivo
        this.playStream(RTSP_URL);              // Inizio riproduzione dello stream dalla sorgente dati
    }
}