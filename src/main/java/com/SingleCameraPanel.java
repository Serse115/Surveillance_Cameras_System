package com;

import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.media.VideoTrackInfo;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * --ITA-----------ITA------------ITA---------ITA--------------ITA--------ITA--
 * ============================================================================
 * MODULO SINGOLA TELECAMERA - RENDERING VIDEO E CONTROLLO LOCALE
 * ============================================================================
 *
 * Scopo:
 * Rappresenta il singolo riquadro visivo nella griglia principale dell'NVR.
 * Incapsula:
 * 1. Il player hardware-accelerato vlcj (EmbeddedMediaPlayerComponent) per il flusso SUB.
 * 2. Il gestore di registrazione asincrono dedicato (VideoRecorder su flusso MAIN o unico).
 * 3. I listener del mouse per attivare la vista a tutto schermo (SUB rapido o MAIN ad alta risoluzione).
 * 4. La logica di auto-riconnessione in caso di perdita del flusso video o riavvio della telecamera.
 *
 * Scelte Architetturali:
 * - Decodifica Nativa Hardware: il canvas video AWT proietta direttamente i fotogrammi
 *   tramite le librerie native libvlc senza caricare la CPU con copie di bitmap Java.
 * - Disaccoppiamento Flussi: il pannello usa il sub-stream (a bassa risoluzione e bitrate ridotto)
 *   per la diretta a griglia, mentre registra il main-stream (4K o ultrawide) su disco tramite FFmpeg.
 */
public class SingleCameraPanel extends JPanel {

    /* Fields */
    // Constants
    private final EmbeddedMediaPlayerComponent mediaPlayerComponent;    // Componente multimediale vlcj con superficie video nativa incorporata
    private final String mainStreamURL;                                 // URL RTSP del flusso ad alta risoluzione (usato per registrazioni e zoom MAIN 4K)
    private final String subStreamURL;                                  // URL RTSP o HTTP del flusso a bassa risoluzione (usato per la diretta continua)
    private final String recordingUrl;                                  // URL effettivo da inoltrare al modulo VideoRecorder
    private final MainViewer mainViewer;                                // Riferimento alla finestra principale per l'aggiornamento di etichette e stato

    // Callback per richiedere il passaggio alla visualizzazione singola ingrandita
    private final Consumer<Boolean> onToggleLayout;                     // Il parametro Boolean indica: true = zoom con stream MAIN, false = zoom con stream SUB
    private final VideoRecorder recorder;                               // Modulo di registrazione associato a questa specifica telecamera
    private final String cameraName;                                    // Nome descrittivo assegnato alla telecamera (es. "1 - CUCINA")
    private static final String[] VLC_LIVE_OPTIONS = {                  // Flag e parametri di ottimizzazione a bassissima latenza per lo streaming VLCJ
            ":network-caching=200", // Buffer di 200ms per assorbire il jitter di rete
            ":clock-jitter=0",      // Disabilita la compensazione progressiva dell'orologio
            ":clock-synchro=0",     // Rimuove l'attesa di sincronizzazione orologio
            ":drop-late-frames",    // Scarta immediatamente i frame in ritardo
            ":skip-frames",         // Salta i pacchetti non completi pur di rimanere in diretta
            ":network-timeout=3000",// Timeout di lettura rete a 3 secondi
            ":rtsp-timeout=3"       // Timeout per le risposte alle richieste RTSP
    };

    // Variables
    private boolean isFullScreenChosen;                                 // Indica se la telecamera sta riproducendo il flusso MAIN o SUB
    private boolean intentionalStop;                                    // Flag che disabilita il timer di riconnessione automatica quando la chiusura è volontaria
    private Timer reconnectTimer;                                       // Timer Swing per tentare la riconnessione dopo una perdita di segnale



    /* Constructors */
    /**
     * Costruttore completo che accetta i due flussi (SUB e MAIN) distinti.
     *
     * @param name Nome identificativo della telecamera.
     * @param subStreamURL URL del flusso a bassa risoluzione per la diretta.
     * @param mainStreamURL URL del flusso ad alta risoluzione per registrazione e zoom.
     * @param mainViewer Riferimento all'interfaccia principale per la sincronizzazione.
     * @param onToggleLayout Callback di notifica per il cambio di layout verso lo zoom.
     */
    public SingleCameraPanel(String name, String subStreamURL, String mainStreamURL, MainViewer mainViewer, Consumer<Boolean> onToggleLayout) {
        this.cameraName = name;                                                         // Assegna il nome descrittivo del canale
        this.subStreamURL = subStreamURL;                                               // Assegna l'URL del flusso leggero per la visualizzazione continuativa
        this.mainStreamURL = (mainStreamURL != null) ? mainStreamURL : subStreamURL;    // Se l'URL del mainStream è null, esegue il fallback su subStreamURL
        this.recordingUrl = (mainStreamURL != null) ? mainStreamURL : subStreamURL;     // Imposta la sorgente per il modulo di registrazione
        this.mainViewer = mainViewer;                                                   // Salva il riferimento al visualizzatore centrale
        this.onToggleLayout = onToggleLayout;                                           // Salva il callback per la gestione del click e l'apertura a tutto schermo
        this.isFullScreenChosen = false;                                                // Inizializza lo stato dello schermo su modalità griglia standard
        this.intentionalStop = false;                                                   // Inizializza il flag di chiusura volontaria a false
        this.mediaPlayerComponent = new EmbeddedMediaPlayerComponent();                 // Istanzia il componente multimediale vlcj per la riproduzione nativa
        this.setLayout(new BorderLayout());                                             // Imposta il layout del pannello a BorderLayout per riempire completamente lo spazio

        // Aggiunge la superficie di rendering del player al centro del pannello
        super.add(this.mediaPlayerComponent, BorderLayout.CENTER);      // Centro del border layout

        // Istanzia il registratore associandolo alla cartella locale "registrazioni"
        this.recorder = new VideoRecorder("registrazioni", this.cameraName);

        // Collega il listener del registratore per propagare i messaggi di stato alla GUI
        this.recorder.setStatusListener(status -> {
            if (this.mainViewer != null) {                  // Se il mainviewer esiste e non è nullo
                this.mainViewer.updateCameraRecordingState(this.cameraName, status);        // Modifica lo stato della registrazione della telecamera
            }
        });

        // Configura i listener di eventi del motore video vlcj
        this.mediaPlayerComponent.mediaPlayer().events().addMediaPlayerEventListener(       // Aggiunge un listener
                new uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter() {              // Usa il Media Player Adapter
                    @Override                                                               // Ed overrida il metodo video output
                    public void videoOutput(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer, int newCount) {
                        if (newCount > 0) {                                                 // Quando il video genera il primo fotogramma a schermo
                            if (reconnectTimer != null && reconnectTimer.isRunning()) {     // Se era in corso un timer di retry per perdita di segnale, lo arresta
                                reconnectTimer.stop();
                            }
                            Timer timer = new Timer(1500, e -> printMediaStats(mediaPlayer));   // Attende 1.5 secondi per dare tempo al decoder di stabilizzare le dimensioni
                            timer.setRepeats(false);                                                  // Setta la ripetizione a false
                            timer.start();                                                            // E fa partire il timer
                        }
                    }

                    @Override                                           // Ulteriore override, in questo caso del metodo errore
                    public void error(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                        handleStreamLoss("Errore flusso RTSP");         // Intercetta errori di rete o connessione RTSP fallita
                    }

                    @Override                                           // Ulteriore override, in questo caso del metodo finished
                    public void finished(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                        handleStreamLoss("Flusso RTSP terminato inaspettatamente"); // Intercetta la chiusura inaspettata del flusso da parte del server RTSP
                    }
                }
        );

        // Intercetta i click del mouse sulla superficie video del canvas AWT
        this.mediaPlayerComponent.videoSurfaceComponent().addMouseListener(new MouseAdapter() {
            @Override                                       // Override, in questo caso del metodo mouseClicked con parametro l'evento mouse e
            public void mouseClicked(MouseEvent e) {

                // Click con tasto destro: visualizzazione zoom rapida con lo stream SUB
                if (SwingUtilities.isRightMouseButton(e)) {     // Se cliccato con tasto dx
                    if (onToggleLayout != null) {               // Se il toggle layout is not null
                        onToggleLayout.accept(false);        // Mantiene lo stream SUB quando passa a schermo intero
                    }
                }
                // Click con tasto sinistro: visualizzazione zoom in alta risoluzione con stream MAIN
                else if (SwingUtilities.isLeftMouseButton(e)) { // Se cliccato il tasto sx
                    if (onToggleLayout != null) {               // Se il toggle layout is not null
                        onToggleLayout.accept(true);         // Cambia lo stream in MAIN quando passa a schermo intero
                    }
                }
            }
        });
    }


    /* Methods */
    // CreateReolinkMethod Factory method per telecamere Reolink PoE/IP
    /**
     * Costruisce automaticamente gli indirizzi RTSP standard per canale SUB e MAIN.
     * @param name Nome descrittivo da mostrare.
     * @param ip Indirizzo IP locale della telecamera.
     * @param user Nome utente di accesso all'interfaccia RTSP.
     * @param pass Password di accesso.
     * @param mainViewer Riferimento a MainViewer.
     * @param onToggleLayout Callback di cambio layout.
     * @return Nuova istanza configurata di SingleCameraPanel.
     */
    public static SingleCameraPanel createReolink(String name, String ip, String user, String pass, MainViewer mainViewer,
                                                  Consumer<Boolean> onToggleLayout) {
        String sub = "rtsp://" + user + ":" + pass + "@" + ip + ":554/h264Preview_01_sub";      // SUB Flow di dati (qualità minore)
        String main = "rtsp://" + user + ":" + pass + "@" + ip + ":554/h264Preview_01_main";    // MAIN Flow di dati (qualità maggiore)
        return new SingleCameraPanel(name, sub, main, mainViewer, onToggleLayout);              // Ritorna l'oggetto SingoleCameraPanel creato
    }

    // CreateSingleStream method to create a generic stream of data for a generic camera
    /**
     * Factory method per telecamere a flusso singolo (es. smartphone, webcam HTTP/MJPEG o flussi di test).
     *
     * @param name Nome descrittivo.
     * @param url URL sorgente del flusso.
     * @param mainViewer Riferimento a MainViewer.
     * @param onToggleLayout Callback di cambio layout.
     * @return Nuova istanza configurata di SingleCameraPanel.
     */
    public static SingleCameraPanel createSingleStream(String name, String url, MainViewer mainViewer, Consumer<Boolean> onToggleLayout) {
        return new SingleCameraPanel(name, url, url, mainViewer, onToggleLayout);       // Ritorna l'oggetto SingleCameraPanel creato
    }

    // StartSubStream method per avviare la riproduzione classica con tutte le telecamere a schermo oppure per la registrazione
    /**
     * Avvia la riproduzione live continua con il flusso leggero (SUB).
     */
    public void startSubStream() {
        this.isFullScreenChosen = false;        // Seleziona lo schermo intero come false per mostrare tutte le telecamere insieme con stream SUB
        this.mediaPlayerComponent.mediaPlayer().media().play(this.subStreamURL, VLC_LIVE_OPTIONS);      // Mostra il contenuto dello stream SUB con le opzioni VLC

        if (this.mainViewer != null) {          // Se il mainViewer esiste e non è nullo
            this.mainViewer.updateStreamStatusAndQualityLabel("SUB (640x480)"); // Aggiorna il label dello stato e qualità con i dati dello stream SUB
        }
    }

    // StartMainStream method per iniziare invece la riproduzione dello stream MAIN
    /**
     * Avvia la riproduzione live con il flusso ad alta risoluzione (MAIN).
     */
    private void startMainStream() {
        this.isFullScreenChosen = true;         // Seleziona lo schermo intero come true per mostrare la telecamera scelta a schermo intero con qualità MAIN
        this.mediaPlayerComponent.mediaPlayer().media().play(this.mainStreamURL, VLC_LIVE_OPTIONS);     // Mostra il contenuto dello stream MAIN con le opzioni VLC

        if (this.mainViewer != null) {          // Se il mainViewer esiste e non è nullo
            this.mainViewer.updateStreamStatusAndQualityLabel("Stream: MAIN - ZOOM - (2560x1920)");   // Aggiorna il label dello stato e qualità con i dati dello stream sub
        }
    }

    // Release method per fermare il processo di visione e registrazione
    /**
     * Rilascia ordinatamente le risorse native di VLC, ferma le registrazioni e i timer.
     */
    public void release() {
        this.intentionalStop = true;            // Marca la chiusura come intenzionale per impedire riconnessioni automatiche
        this.recorder.stopRecording();          // Arresta il processo FFmpeg associato se attivo
        this.reconnectTimer = null;             // Annulla il riferimento al timer di riconnessione
        this.mediaPlayerComponent.release();    // Rilascia le risorse hardware native allocate da libvlc
    }

    // PrintMediaStats method per printare le statistiche del flusso
    /**
     * Ispeziona e stampa su console le statistiche reali del flusso video agganciato.
     *
     * @param mediaPlayer Riferimento al player attivo.
     */
    private void printMediaStats(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {

        // Tenta la lettura della risoluzione effettiva dal canale video
        Dimension dim = mediaPlayer.video().videoDimension();
        int width = (dim != null) ? dim.width : 0;
        int height = (dim != null) ? dim.height : 0;

        // Se non disponibile, interroga i metadati delle tracce video
        if (width == 0 || height == 0) {
            List<VideoTrackInfo> videoTracks = mediaPlayer.media().info().videoTracks();
            if (!videoTracks.isEmpty()) {
                width = videoTracks.get(0).width();
                height = videoTracks.get(0).height();
            }
        }

        // Estrae il nome del codec di compressione
        String codec = "h264";
        List<VideoTrackInfo> videoTracks = mediaPlayer.media().info().videoTracks();
        if (!videoTracks.isEmpty()) {
            codec = videoTracks.get(0).codecName();
        }

        // Printa le statistiche con il nome della telecamera, il tipo di flusso, la risoluzione ed il codec
        System.out.println("-> [MEDIA STATS " + this.cameraName + "] CANALE: "
                + (this.isFullScreenChosen ? "MAIN" : "SUB")
                + " | Risoluzione effettiva: " + width + "x" + height
                + " | Codec: " + codec);
    }

    // HandleStreamLoss method per gestire la perdita dello stream
    /**
     * Gestisce la perdita del segnale video avviando un timer di riallineamento periodico.
     *
     * @param reason Descrizione dell'evento anomalo intercettato.
     */
    private void handleStreamLoss(String reason) {

        // Se la disconnessione non è stata richiesta dall'utente, esegue il riallineamento
        if (!this.intentionalStop) {
            System.out.println("[WARNING - " + this.cameraName + "] " + reason + ". Tentativo di riconnessione automatico...");

            // Se il timer non è attivo, ne avvia uno con cadenza di 3 secondi
            if (this.reconnectTimer == null || !this.reconnectTimer.isRunning()) {
                this.reconnectTimer = new Timer(3000, e -> {
                    SwingUtilities.invokeLater(() -> {
                        System.out.println("-> [RETRY " + this.cameraName + "] Reset player e tentativo riallineamento stream...");

                        // Arresta e svuota il buffer di ricezione interno
                        this.mediaPlayerComponent.mediaPlayer().controls().stop();

                        // Riavvia la riproduzione sul canale che era correntemente selezionato
                        if (this.isFullScreenChosen) {
                            this.mediaPlayerComponent.mediaPlayer().media().play(this.mainStreamURL, VLC_LIVE_OPTIONS);
                        } else {
                            this.mediaPlayerComponent.mediaPlayer().media().play(this.subStreamURL, VLC_LIVE_OPTIONS);
                        }
                    });
                });
                this.reconnectTimer.start();
            }
        }
    }

    // StartRecording method per la registrazione sul disco
    /**
     * Avvia la registrazione su disco generando segmenti da 30 secondi ciascuno.
     */
    public void startRecording() {
        this.recorder.startRecording(this.recordingUrl, 30);
    }

    // StopRecording method per fermare la registrazione su disco
    /**
     * Ferma la registrazione attiva su disco.
     */
    public void stopRecording() {
        this.recorder.stopRecording();
    }

    // IsRecording getter
    /**
     * Verifica se il registratore di questa telecamera è attivo.
     *
     * @return true se in registrazione, false altrimenti.
     */
    public boolean isRecording() {
        return this.recorder.isRecording();
    }

    // GetCameraName method che restituisce il nome della telecamera selezionata
    /**
     * Restituisce il nome descrittivo della telecamera.
     *
     * @return String con il nome.
     */
    public String getCameraName() {
        return this.cameraName;
    }

    // GetMainStreamURL method che restituisce il flusso Main ad alta qualità
    /**
     * Restituisce l'URL del flusso MAIN.
     *
     * @return URL RTSP/HTTP.
     */
    public String getMainStreamURL() {
        return this.mainStreamURL;
    }

    // GetSubStreamURL method che restituisce il flusso Sub a qualità normale
    /**
     * Restituisce l'URL del flusso SUB.
     *
     * @return URL RTSP/HTTP.
     */
    public String getSubStreamURL() {
        return this.subStreamURL;
    }
}

/*
 --ENG------------ENG-------------ENG---------ENG--------------ENG--------ENG--
 * ============================================================================
 * SINGLE CAMERA MODULE - VIDEO RENDERING AND LOCAL CHECK
 * ============================================================================
 *
 * Objective:
 * Show the single panel of the camera in the main visual component of the NVR.
 * It Encapsulates:
 * 1. Hardware-accelerated Player vlcj (EmbeddedMediaPlayerComponent) for the SUB data flow.
 * 2. The Dedicated Asynchronous Recordings handler (VideoRecorder on MAIN flow).
 * 3. Mouse Listeners to activate the full-screen view (Rapid SUB or High-resolution MAIN).
 * 4. Auto-reconnecting logic in case of video flow loss or cameras reset.
 *
 * Architecture choices:
 * - Native Hardware Decoding: video canvas AWT projects the photograms directly
 *   through the native libvlc libraries without loading up the cpu with bitmap copies.
 * - Flows Separation: the panel uses the sub-stream for the multi-view grid on screen,
 *   while using the main-stream for the single view at fullscreen or to save the video on storage through FFMPEG.
 */