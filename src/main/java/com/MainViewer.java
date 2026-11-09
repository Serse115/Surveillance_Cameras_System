package com;

import com.GraphicsIcons.BlackSquareIcon;
import com.GraphicsIcons.RedCircleIcon;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

/**
 * --ITA-----------ITA------------ITA---------ITA--------------ITA--------ITA--
 * ============================================================================
 * CLASSE MAINVIEWER - NVR CONTROLLER & COMPOSITORE GRAFICO MULTI-VIEW
 * ============================================================================
 *
 * Scopo:
 * Rappresenta la finestra principale e il centro di controllo dell'intero sistema NVR.
 * Coordina la visualizzazione sincrona dei flussi di rete (RTSP/HTTP), la barra
 * dei controlli operativi inferiori e il cambio dinamico di visualizzazione.
 *
 * Struttura Architetturale:
 * 1. CardLayout (mainContainerPanel):
 *    - "GRID_VIEW": visualizzazione multiview a mosaico a 3 righe asimmetriche.
 *    - "SINGLE_VIEW": zoom esclusivo su un singolo flusso tramite player dedicato.
 *
 * 2. Geometria del Layout a 3 Righe (GridBagLayout):
 *    - Riga Superiore (28% altezza): GridLayout(1, 2) per 2 telecamere panoramiche Duo 2V (32:9).
 *    - Riga Centrale (44% altezza): GridBagLayout con larghezze 42%, 33%, 25% per integrare
 *      la Duo 2V di supporto, la RLC-820A (16:9) e la RLC-520A (4:3) senza bande nere residue.
 *    - Riga Inferiore (28% altezza): GridLayout(1, 2) per ulteriori slot ultrawide.
 *
 * 3. Player Dedicato per lo Zoom:
 *    - Disaccoppiato dai player della griglia per consentire il passaggio istantaneo al flusso
 *      MAIN (ad alta risoluzione) senza interrompere o alterare i thread di rendering live dei canali SUB.
 *
 * 4. Controllo Unificato della Registrazione:
 *    - Pulsanti dedicati per singola telecamera e pulsante master "TUTTE" per avviare o arrestare
 *      i processi di background FFmpeg tramite notifica asincrona.
 */
public class MainViewer {

    /* Fields */
    // Constants
    private final JFrame mainFrame;                         // Finestra principale nativa del sistema operativo
    private final SingleCameraPanel[] cameraPanels;         // Array contenitore per i pannelli delle telecamere attive o configurabili
    private final JPanel bottomBar;                         // Barra comandi posizionata in basso (SOUTH) con controlli, stato e pulsanti REC
    private final CardLayout cardLayout;                    // Gestore del layout per alternare tra la vista a griglia e la vista zoomata singola
    private final JPanel mainContainerPanel;                // Pannello contenitore principale soggetto al CardLayout
    private final JPanel gridPanel;                         // Pannello contenitore della griglia complessiva a 3 righe
    private final JPanel topRowPanel;                       // Contenitore della riga 1 (in alto): alloggia fino a due telecamere panoramiche a 180°
    private final JPanel middleRowPanel;                    // Contenitore della riga 2 (centrale): alloggia tre telecamere con proporzioni calibrate
    private final JPanel bottomRowPanel;                    // Contenitore della riga 3 (in basso): alloggia fino a due telecamere panoramiche
    private final JPanel singleViewPanel;                   // Pannello dedicato visualizzato quando viene selezionata una singola telecamera
    private final EmbeddedMediaPlayerComponent singleMediaPlayer;   // Player multimediale vlcj dedicato esclusivamente alla riproduzione a schermo intero
    private final JLabel statusLabel;                       // Etichetta testuale dinamica per le notifiche di stato e operazioni del sistema
    private final JButton reconnectButton;                  // Pulsante per forzare il riavvio manuale dei flussi SUB di tutte le telecamere
    private final JButton[] cameraRecordButtons;            // Array dei pulsanti REC specifici per ciascun canale telecamera
    private final JButton toggleAllRecordButton;            // Pulsante master toggle per avviare o fermare simultaneamente la registrazione di tutte le camere
    private final JLabel qualityLabel;                      // Etichetta informativa visualizzata a destra con indicazione della risoluzione/stream attivo
    private final Icon ICON_REC = new RedCircleIcon(15);        // Icona vettoriale: cerchio rosso indicante registrazione non attiva o pronta all'avvio
    private final Icon ICON_STOP = new BlackSquareIcon(15);     // Icona vettoriale: quadrato nero indicante registrazione in corso / pulsante STOP
    private static final int TOTAL_CAMERAS = 6;             // Numero totale massimo di canali telecamera gestibili dal layout

    // Parametri vlcj per lo streaming live: latenza minima e scarto dei frame ritardati
    private static final String[] VLC_LIVE_OPTIONS = {
            ":network-caching=200", // Buffer di 200 ms per contrastare il jitter di rete
            ":clock-jitter=0",      // Disattiva il recupero progressivo del clock
            ":clock-synchro=0",     // Rimuove i vincoli rigidi di sincronizzazione del clock
            ":drop-late-frames",    // Scarta immediatamente i frame che arrivano oltre il tempo limite
            ":skip-frames",         // Permette il salto rapido dei pacchetti intermedi
            ":network-timeout=3000",// Timeout di lettura dalla rete (3 secondi)
            ":rtsp-timeout=3"       // Timeout per i comandi di handshake RTSP
    };

    // Variables
    private boolean isSingleViewActive;         // Flag booleano di tracciamento dello stato di visualizzazione singola attivo



    /* Constructors */
    /**
     * Inizializza l'architettura grafica, la gerarchia dei contenitori,
     * le 3 righe asimmetriche, i player video e i controlli della barra inferiore.
     */
    public MainViewer() {
        this.statusLabel = new JLabel("STATO: TRASMISSIONE A SCHERMO...");     // Inizializza l'etichetta di stato con il messaggio iniziale di boot
        this.isSingleViewActive = false;                                            // Imposta inizialmente la modalità di visualizzazione sulla griglia standard
        this.cardLayout = new CardLayout();                                         // Inizializza il CardLayout per consentire il passaggio tra griglia e zoom
        this.mainContainerPanel = new JPanel(this.cardLayout);

        // --- INIZIALIZZAZIONE DELLA GRIGLIA A 3 LIVELLI ---
        // Il contenitore principale usa GridBagLayout per distribuire l'altezza delle 3 righe
        this.gridPanel = new JPanel(new GridBagLayout());
        this.gridPanel.setBackground(Color.BLACK);

        // Riga Superiore: GridLayout a 1 riga e 2 colonne (50% e 50% di larghezza)
        this.topRowPanel = new JPanel(new GridLayout(1, 2, 4, 4));
        this.topRowPanel.setBackground(Color.BLACK);

        // Riga Centrale: GridBagLayout per dosare larghezze differenti per telecamere eterogenee
        this.middleRowPanel = new JPanel(new GridBagLayout());
        this.middleRowPanel.setBackground(Color.BLACK);

        // Riga Inferiore: GridLayout a 1 riga e 2 colonne (50% e 50% di larghezza)
        this.bottomRowPanel = new JPanel(new GridLayout(1, 2, 4, 4));
        this.bottomRowPanel.setBackground(Color.BLACK);

        // 1. CONFIGURAZIONE VINCOLI RIGA 1 (Top: 28% dell'altezza dello schermo)
        GridBagConstraints gbcTop = new GridBagConstraints();
        gbcTop.gridx = 0;
        gbcTop.gridy = 0;
        gbcTop.weightx = 1.0;
        gbcTop.weighty = 0.28; // 28% dell'altezza verticale totale
        gbcTop.fill = GridBagConstraints.BOTH;
        gbcTop.insets = new Insets(0, 0, 2, 0); // Margine inferiore di 2 pixel
        this.gridPanel.add(this.topRowPanel, gbcTop);

        // 2. CONFIGURAZIONE VINCOLI RIGA 2 (Middle: 44% dell'altezza dello schermo)
        GridBagConstraints gbcMid = new GridBagConstraints();
        gbcMid.gridx = 0;
        gbcMid.gridy = 1;
        gbcMid.weightx = 1.0;
        gbcMid.weighty = 0.44; // 44% dell'altezza per favorire i formati 16:9 e 4:3
        gbcMid.fill = GridBagConstraints.BOTH;
        gbcMid.insets = new Insets(2, 0, 2, 0); // Margini superiore e inferiore di 2 pixel
        this.gridPanel.add(this.middleRowPanel, gbcMid);

        // 3. CONFIGURAZIONE VINCOLI RIGA 3 (Bottom: 28% dell'altezza dello schermo)
        GridBagConstraints gbcBottom = new GridBagConstraints();
        gbcBottom.gridx = 0;
        gbcBottom.gridy = 2;
        gbcBottom.weightx = 1.0;
        gbcBottom.weighty = 0.28; // 28% dell'altezza verticale totale
        gbcBottom.fill = GridBagConstraints.BOTH;
        gbcBottom.insets = new Insets(2, 0, 0, 0); // Margine superiore di 2 pixel
        this.gridPanel.add(this.bottomRowPanel, gbcBottom);

        // --- INIZIALIZZAZIONE DELLA VISTA SINGOLA (ZOOM DEDICATO) ---
        this.singleMediaPlayer = new EmbeddedMediaPlayerComponent();
        this.singleViewPanel = new JPanel(new BorderLayout());
        this.singleViewPanel.setBackground(Color.BLACK);
        this.singleViewPanel.add(this.singleMediaPlayer, BorderLayout.CENTER);

        // Aggiunge un listener sulla superficie video del player di zoom:
        // qualsiasi click (destro o sinistro) chiude la vista zoom e torna alla griglia
        this.singleMediaPlayer.videoSurfaceComponent().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) || SwingUtilities.isLeftMouseButton(e)) {
                    exitSingleView();
                }
            }
        });

        // Registrazione delle due schermate nel CardLayout
        this.mainContainerPanel.add(this.gridPanel, "GRID_VIEW");
        this.mainContainerPanel.add(this.singleViewPanel, "SINGLE_VIEW");

        // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        // --- ISTANZIAZIONE DEI PANNELLI TELECAMERA ---
        this.cameraPanels = new SingleCameraPanel[TOTAL_CAMERAS];

        // SLOT 0: DUO 2V #1 (Cucina)
        String cam0Ip = ConfigManager.get("cam.0.ip");
        if (cam0Ip != null && !cam0Ip.isBlank()) {
            this.cameraPanels[0] = SingleCameraPanel.createReolink(
                    ConfigManager.get("cam.0.name"),
                    cam0Ip,
                    ConfigManager.get("cam.0.user"),
                    ConfigManager.get("cam.0.pass"),
                    this,
                    isMain -> showSingleView(this.cameraPanels[0], isMain)
            );
        }

        // SLOT 1: STREAM SECONDARIO / TELEFONO
        String cam1Url = ConfigManager.get("cam.1.url");
        if (cam1Url != null && !cam1Url.isBlank()) {
            this.cameraPanels[1] = SingleCameraPanel.createSingleStream(
                    ConfigManager.get("cam.1.name"),
                    cam1Url,
                    this,
                    isMain -> showSingleView(this.cameraPanels[1], false)
            );
        }

        // SLOT 2: DUO 2V #3 (Centro Sx - Angolo ad L: al momento non assegnata)
        this.cameraPanels[2] = null;
        // SLOT 2: Angolo ad L (Inattivo finché non configurato)
        //String cam2Ip = ConfigManager.get("cam.2.ip");
        //if (cam2Ip != null && !cam2Ip.isBlank()) {
        //    this.cameraPanels[2] = SingleCameraPanel.createReolink(
        //            ConfigManager.get("cam.2.name"),
        //            cam2Ip,
        //            ConfigManager.get("cam.2.user"),
        //            ConfigManager.get("cam.2.pass"),
        //            this,
        //            isMain -> showSingleView(this.cameraPanels[2], isMain)
        //    );
        //} else {
        //    this.cameraPanels[2] = null;
        //}

        // SLOT 3: RLC-820A (Cameretta)
        String cam3Ip = ConfigManager.get("cam.3.ip");
        if (cam3Ip != null && !cam3Ip.isBlank()) {
            this.cameraPanels[3] = SingleCameraPanel.createReolink(
                    ConfigManager.get("cam.3.name"),
                    cam3Ip,
                    ConfigManager.get("cam.3.user"),
                    ConfigManager.get("cam.3.pass"),
                    this,
                    isMain -> showSingleView(this.cameraPanels[3], isMain)
            );
        }

        // SLOT 4: RLC-520A (Portone)
        String cam4Ip = ConfigManager.get("cam.4.ip");
        if (cam4Ip != null && !cam4Ip.isBlank()) {
            this.cameraPanels[4] = SingleCameraPanel.createReolink(
                    ConfigManager.get("cam.4.name"),
                    cam4Ip,
                    ConfigManager.get("cam.4.user"),
                    ConfigManager.get("cam.4.pass"),
                    this,
                    isMain -> showSingleView(this.cameraPanels[4], isMain)
            );
        }

        // SLOT 5: Slot ausiliario non assegnato
        this.cameraPanels[5] = null;
        // SLOT 5: Angolo ad L (Inattivo finché non configurato)
        //String cam5Ip = ConfigManager.get("cam.5.ip");
        //if (cam5Ip != null && !cam5Ip.isBlank()) {
        //    this.cameraPanels[5] = SingleCameraPanel.createReolink(
        //            ConfigManager.get("cam.5.name"),
        //            cam5Ip,
        //            ConfigManager.get("cam.5.user"),
        //            ConfigManager.get("cam.5.pass"),
        //            this,
        //            isMain -> showSingleView(this.cameraPanels[5], isMain)
        //    );
        //} else {
        //    this.cameraPanels[5] = null;
        //}
        // -+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

        // --- DISTRIBUZIONE DEI COMPONENTI NELLE TRE RIGHE ---

        // 1. POPOLAMENTO RIGA SUPERIORE (2 slot da 50% di larghezza ciascuno)
        if (this.cameraPanels[1] != null) {
            this.topRowPanel.add(this.cameraPanels[1]);
        } else {
            this.topRowPanel.add(this.createPlaceholder(1));
        }
        this.topRowPanel.add(this.createPlaceholder(2)); // Secondo slot ultrawide

        // 2. POPOLAMENTO RIGA CENTRALE (pesi orizzontali asimmetrici: 42%, 33%, 25%)
        double[] middleWeights = {0.42, 0.33, 0.25};
        for (int i = 0; i < 3; i++) {
            int slotIdx = i + 2; // Copre gli indici 2, 3 e 4 dell'array cameraPanels
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = i;
            gbc.gridy = 0;
            gbc.weightx = middleWeights[i]; // Assegna la larghezza percentuale calibrata
            gbc.weighty = 1.0;             // Occupa l'intera altezza della riga centrale
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(0, (i == 0 ? 0 : 2), 0, (i == 2 ? 0 : 2));

            // Se la telecamera è presente la inserisce, altrimenti genera un placeholder
            Component comp = (this.cameraPanels[slotIdx] != null)
                    ? this.cameraPanels[slotIdx]
                    : this.createPlaceholder(slotIdx + 1);
            this.middleRowPanel.add(comp, gbc);
        }

        // 3. POPOLAMENTO RIGA INFERIORE (2 slot da 50% di larghezza ciascuno)
        if (this.cameraPanels[0] != null) {
            this.bottomRowPanel.add(this.cameraPanels[0]);
        } else {
            this.bottomRowPanel.add(this.createPlaceholder(6));
        }
        this.bottomRowPanel.add(this.createPlaceholder(7));

        // --- INIZIALIZZAZIONE JFRAME PRINCIPALE ---
        this.mainFrame = new JFrame("NVR VIDEOSORVEGLIANZA - MONITOR LOCALE");
        this.mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Massimizza la finestra all'avvio su entrambi gli assi (schermo intero con bordi)
        this.mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        // Dimensione di fallback nel caso in cui la finestra venga s-massimizzata
        this.mainFrame.setSize(1380, 720);
        this.mainFrame.setLocationRelativeTo(null);

        // Caricamento dell'icona personalizzata dell'applicazione se presente nei file di risorsa
        try {
            ImageIcon logo = new ImageIcon(
                    Objects.requireNonNull(MainViewer.class.getResource("/Logo_Sorveglianza.jpg"))
            );
            this.mainFrame.setIconImage(logo.getImage());
        } catch (Exception ignored) {
            // Se l'icona non è presente o il percorso non è valido, prosegue con l'icona di default
        }

        // --- COSTRUZIONE DELLA BARRA DEI CONTROLLI INFERIORE (BOTTOM BAR) ---
        this.bottomBar = new JPanel(new BorderLayout(8, 0));
        this.bottomBar.setBorder(new EmptyBorder(4, 8, 4, 8)); // Margini interni di padding
        this.bottomBar.setBackground(new Color(235, 235, 238)); // Grigio chiaro moderno

        // 1. SEZIONE SINISTRA (WEST): Pulsante di Riconnessione ed Etichetta di Stato
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        leftPanel.setOpaque(false);

        this.reconnectButton = new JButton("RICONNETTI");
        this.reconnectButton.setFont(new Font("SansSerif", Font.PLAIN, 11));
        this.reconnectButton.setFocusable(false);
        this.reconnectButton.setMargin(new Insets(2, 8, 2, 8));
        this.reconnectButton.addActionListener(e -> {
            this.statusLabel.setText("STATO: RICONNESSIONE IN CORSO...");

            // Scorre tutte le telecamere istanziate e riavvia il flusso SUB
            for (SingleCameraPanel cam : this.cameraPanels) {
                if (cam != null) {
                    cam.startSubStream();
                }
            }
            this.statusLabel.setText("STATO: TRASMISSIONE A SCHERMO...");
        });

        this.statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        leftPanel.add(this.reconnectButton);
        leftPanel.add(new JSeparator(SwingConstants.VERTICAL));
        leftPanel.add(this.statusLabel);

        // 2. SEZIONE CENTRALE (CENTER): Pulsante Master TUTTE e Pulsanti REC di Canale
        JPanel centerRecPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2));
        centerRecPanel.setOpaque(false);

        this.toggleAllRecordButton = new JButton("TUTTE");
        this.toggleAllRecordButton.setFont(new Font("SansSerif", Font.BOLD, 11));
        this.toggleAllRecordButton.setIcon(this.ICON_REC);
        this.toggleAllRecordButton.setFocusable(false);
        this.toggleAllRecordButton.setMargin(new Insets(2, 8, 2, 8));
        this.toggleAllRecordButton.addActionListener(e -> this.toggleAllRecordings());
        centerRecPanel.add(this.toggleAllRecordButton);

        // Separatore verticale tra il pulsante TUTTE e i singoli pulsanti delle telecamere
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(2, 20));
        centerRecPanel.add(sep);

        // Creazione dinamica dei pulsanti di registrazione per ciascun canale
        this.cameraRecordButtons = new JButton[TOTAL_CAMERAS];
        for (int i = 0; i < TOTAL_CAMERAS; i++) {
            final int camIndex = i;
            SingleCameraPanel cam = this.cameraPanels[i];

            JButton btn = new JButton();
            btn.setFont(new Font("SansSerif", Font.PLAIN, 11));
            btn.setFocusable(false);
            btn.setMargin(new Insets(2, 6, 2, 6));

            if (cam != null) {
                btn.setText(cam.getCameraName());
                btn.setIcon(this.ICON_REC);
                btn.setEnabled(true);
                btn.addActionListener(e -> toggleSingleCameraRecording(camIndex));
            } else {
                btn.setText((i + 1) + " - (N.D.)");
                btn.setEnabled(false); // Disabilita i canali non collegati
            }

            this.cameraRecordButtons[i] = btn;
            centerRecPanel.add(btn);
        }

        // 3. SEZIONE DESTRA (EAST): Indicatore della qualità e del tipo di flusso attivo
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        rightPanel.setOpaque(false);
        this.qualityLabel = new JLabel("Stream: SUB (640x480)");
        this.qualityLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        this.qualityLabel.setForeground(Color.DARK_GRAY);
        rightPanel.add(this.qualityLabel);

        // Assembla le tre sottosezioni nella barra inferiore
        this.bottomBar.add(leftPanel, BorderLayout.WEST);
        this.bottomBar.add(centerRecPanel, BorderLayout.CENTER);
        this.bottomBar.add(rightPanel, BorderLayout.EAST);

        // Assembla i contenitori principali nel frame primario
        this.mainFrame.setLayout(new BorderLayout());
        this.mainFrame.add(this.mainContainerPanel, BorderLayout.CENTER);
        this.mainFrame.add(this.bottomBar, BorderLayout.SOUTH);

        // Registra il listener per la chiusura dell'applicazione con rilascio ordinato delle risorse
        this.mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Rilascia le istanze delle telecamere attive arrestando i processi FFmpeg
                for (SingleCameraPanel cam : cameraPanels) {
                    if (cam != null) {
                        cam.release();
                    }
                }
                // Rilascia le risorse native del player vlcj dello zoom
                singleMediaPlayer.release();

                // Termina il processo della Virtual Machine Java
                System.exit(0);
            }
        });
    }

    // CreatePlaceHolder method per generare il pannello placeholder per gli slot ancora da inizializzare
    /**
     * Genera un pannello segnaposto (placeholder) per gli slot privi di telecamera.
     * Crea un riquadro con sfondo scuro, bordo sottile ed etichetta centrale grigia.
     *
     * @param index Numero dello slot logico visualizzato a schermo (es. "1 - ASSENTE").
     * @return Istanza di JPanel configurata graficamente come placeholder.
     */
    private JPanel createPlaceholder(int index) {

        JPanel placeHolder = new JPanel(new BorderLayout());         // Pannello contenitore con BorderLayout per centrare l'etichetta di testo
        placeHolder.setBackground(new Color(22, 22, 24));   // Sfondo grigio molto scuro coerente con il tema dark della griglia video

        // Etichetta centrale informativa
        JLabel label = new JLabel(index + " - ASSENTE", SwingConstants.CENTER);     // Label allineata al centro
        label.setForeground(new Color(90, 90, 95));                             // Grigio attenuato per non distrarre
        label.setFont(new Font("SansSerif", Font.BOLD, 13));                  // Font sans-serif leggibile

        placeHolder.add(label, BorderLayout.CENTER);                            // Inserimento dell'etichetta al centro del placeholder
        placeHolder.setBorder(BorderFactory.createLineBorder(Color.BLACK));     // Bordo nero di separazione per definire i confini nella griglia

        return placeHolder;
    }

    // ShowSingleView method per attivare la modalità Visione singola con la versione SUB o MAIN
    /**
     * Attiva la modalità zoom a schermo intero su una specifica telecamera.
     * Recupera l'URL appropriato (MAIN o SUB), lo avvia sul player dedicato
     * e richiede al CardLayout di mostrare la scheda "SINGLE_VIEW".
     *
     * @param cam Istanza del pannello della telecamera selezionata.
     * @param useMainStream true per caricare il flusso MAIN ad alta risoluzione,
     *                      false per visualizzare il flusso SUB a caricamento rapido.
     */
    private void showSingleView(SingleCameraPanel cam, boolean useMainStream) {

        if (cam == null) {          // Controllo di guardia: se il pannello della telecamera è nullo
            return;                 // Interrompe l'operazione
        }

        this.isSingleViewActive = true;                                                 // Imposta il flag per indicare che la vista zoom a schermo intero è attiva
        String url = useMainStream ? cam.getMainStreamURL() : cam.getSubStreamURL();    // Seleziona la sorgente appropriata in base alla richiesta dell'utente
        this.singleMediaPlayer.mediaPlayer().media().play(url, VLC_LIVE_OPTIONS);       // Avvia la riproduzione sul player dedicato passando le opzioni a bassa latenza
        this.cardLayout.show(this.mainContainerPanel, "SINGLE_VIEW");             // Effettua la transizione della GUI portando in primo piano la vista singola
        String labelText = useMainStream ? "MAIN - ZOOM (4K)" : "SUB - ZOOM (640x480)"; // Compone il testo descrittivo per l'etichetta di qualità in basso a destra
        this.updateStreamStatusAndQualityLabel(labelText);                              // Aggiorna l'etichetta grafica della barra inferiore
    }

    // ExitSingleView method per uscire dalla modalità schermo intero e tornare alla modalità a griglia con le altre telecamere
    /**
     * Chiude la visualizzazione a tutto schermo e ripristina la visualizzazione a griglia.
     * Arresta il player dedicato per non sprecare cicli di decodifica e CPU in background.
     */
    private void exitSingleView() {

        if (!this.isSingleViewActive) {     // Se la vista singola non era attiva
            return;                         // Esce per evitare operazioni ridondanti
        }

        this.isSingleViewActive = false;                            // Reimposta lo stato interno sulla modalità griglia
        this.singleMediaPlayer.mediaPlayer().controls().stop();     // Ferma la riproduzione del flusso ad alta risoluzione liberando le risorse native
        this.cardLayout.show(this.mainContainerPanel, "GRID_VIEW");     // Riporta il CardLayout sulla schermata a mosaico principale
        this.updateStreamStatusAndQualityLabel("SUB (640x480)");     // Ripristina l'indicazione dello stream predefinito per la modalità griglia
    }

    // ToggleSingleCameraRecording method per iniziare la registrazione su una singola telecamera
    /**
     * Commuta lo stato di registrazione (Start / Stop) per un singolo canale telecamera.
     * Aggiorna contestualmente l'icona e il colore del relativo pulsante nella barra inferiore.
     *
     * @param camIndex Indice logico della telecamera all'interno dell'array cameraPanels.
     */
    private void toggleSingleCameraRecording(int camIndex) {

        SingleCameraPanel cam = this.cameraPanels[camIndex];        // Recupera l'istanza della telecamera corrispondente all'indice fornito

        if (cam == null) {      // Se lo slot non contiene una telecamera attiva
            return;             // Interrompe l'esecuzione
        }

        JButton btn = this.cameraRecordButtons[camIndex];       // Recupera il riferimento al relativo pulsante REC nella barra inferiore

        // Se la telecamera non sta attualmente registrando, avvia il processo FFmpeg
        if (!cam.isRecording()) {
            cam.startRecording();                                   // Avvia FFmpeg in background su thread separato
            btn.setIcon(this.ICON_STOP);                            // Imposta l'icona del quadrato nero per lo STOP
            btn.setForeground(new Color(200, 30, 30));      // Colora il testo di rosso brillante
        }
        else {
            // Se la telecamera sta già registrando, richiede l'arresto ordinato
            cam.stopRecording();                                // Invia 'q' al processo FFmpeg e chiude il file
            btn.setIcon(this.ICON_REC);                         // Ripristina l'icona del cerchio rosso pronto a registrare
            btn.setForeground(Color.BLACK);                     // Ripristina il colore del testo nero standard
        }

        this.updateMasterButtonState();             // Ricalcola e sincronizza lo stato globale del pulsante master "TUTTE"
    }

    // ToggleAllRecordings method per attivare la registrazione di tutti i canali di videocamere attivi al momento allo stesso tempo
    /**
     * Commuta globalmente lo stato di registrazione di tutti i canali attivi:
     * - Se almeno una telecamera sta registrando, invia il segnale di stop a tutte.
     * - Se nessuna telecamera sta registrando, avvia la registrazione su tutte le telecamere collegate.
     */
    private void toggleAllRecordings() {
        boolean anyRecording = false;                           // Default per le registrazioni a false
        for (SingleCameraPanel cam : this.cameraPanels) {       // Scansione preliminare per verificare se è attiva almeno una registrazione
            if (cam != null && cam.isRecording()) {
                anyRecording = true;
                break;
            }
        }

        // Scenario 1: Se c'è almeno un canale attivo, l'azione richiesta è l'arresto generale
        if (anyRecording) {
            for (int i = 0; i < this.cameraPanels.length; i++) {                    // Check per ogni telecamera
                SingleCameraPanel cam = this.cameraPanels[i];
                if (cam != null && cam.isRecording()) {                             // Se la telecamera non è nulla e sta registrando
                    cam.stopRecording();                                            // Arresta il processo FFmpeg della singola telecamera
                    this.cameraRecordButtons[i].setIcon(this.ICON_REC);             // Ripristina l'icona REC
                    this.cameraRecordButtons[i].setForeground(Color.BLACK);         // Testo nero standard
                }
            }
        }
        else {
            // Scenario 2: Nessuna telecamera sta registrando, quindi avvia la registrazione su tutte
            for (int i = 0; i < this.cameraPanels.length; i++) {
                SingleCameraPanel cam = this.cameraPanels[i];
                if (cam != null) {
                    cam.startRecording();                                                       // Avvia FFmpeg sul canale MAIN o sul flusso disponibile
                    this.cameraRecordButtons[i].setIcon(this.ICON_STOP);                        // Imposta l'icona STOP
                    this.cameraRecordButtons[i].setForeground(new Color(200, 30, 30)); // Testo rosso
                }
            }
        }

        this.updateMasterButtonState();     // Allinea l'icona e il testo del pulsante master "TUTTE" / "STOP"
    }

    // UpdateMasterButtonState method per modificare lo stato della registrazione per tutte le telecamere
    /**
     * Sincronizza lo stato visivo (testo, colore e icona) del pulsante master TUTTE
     * in base alla presenza o assenza di registrazioni attive nel parco telecamere.
     */
    private void updateMasterButtonState() {

        boolean anyRecording = false;
        for (SingleCameraPanel cam : this.cameraPanels) {           // Per ogni telecamera attiva
            if (cam != null && cam.isRecording()) {                 // Verifica se esiste almeno un processo di registrazione attivo
                anyRecording = true;
                break;
            }
        }

        // Se c'è almeno una registrazione in corso, il pulsante master diventa un'opzione di STOP[cite: 10]
        if (anyRecording) {
            this.toggleAllRecordButton.setIcon(this.ICON_STOP);                         // Icona quadrato nero
            this.toggleAllRecordButton.setText("STOP");                                 // Testo chiaro indicante l'azione
            this.toggleAllRecordButton.setForeground(new Color(200, 30, 30));  // Evidenziato in rosso
        }
        else {
            // Se tutti i canali sono fermi, il pulsante master si prepara all'avvio generale
            this.toggleAllRecordButton.setIcon(this.ICON_REC);                          // Icona cerchio rosso
            this.toggleAllRecordButton.setText("TUTTE");                                // Testo originale
            this.toggleAllRecordButton.setForeground(Color.BLACK);                      // Colore standard
        }
    }

    // UpdateCameraRecordingState method per modificare lo stato del nome della telecamera e del suo stato
    /**
     * Aggiorna in modo thread-safe il testo dell'etichetta di stato nella barra inferiore.
     * Utilizza SwingUtilities.invokeLater per garantire l'esecuzione corretta nell'EDT.
     *
     * @param cameraName Nome della telecamera che ha generato l'evento.
     * @param status Stringa contenente la descrizione dello stato o dell'errore.
     */
    public void updateCameraRecordingState(String cameraName, String status) {
        SwingUtilities.invokeLater(() -> this.statusLabel.setText(status));     // Inoltra l'aggiornamento grafico alla coda degli eventi di Swing
    }

    // Start method per rendere attivi e visibili tutti i componenti
    /**
     * Avvia formalmente l'applicazione NVR:
     * 1. Rende visibile a schermo la finestra JFrame massimizzata.
     * 2. Avvia la riproduzione dei flussi SUB su tutte le telecamere configurate,
     *    garantendo che la superficie grafica AWT sia pronta e visualizzabile.
     */
    public void start() {
        this.mainFrame.setVisible(true);            // Mostra la finestra principale (rende i componenti "displayable")

        // Itera su tutte le telecamere istanziate e lancia il flusso secondario (SUB) di diretta
        for (SingleCameraPanel cam : this.cameraPanels) {
            if (cam != null) {
                cam.startSubStream();                       // Avvia libvlc sul canale SUB leggero
            }
        }
    }

    // UpdateStreamStatusAndQualityLabel method per aggiornare l'etichetta che mostra la qualità video corrente in basso a dx
    /**
     * Aggiorna in modo thread-safe l'etichetta della qualità video posta in basso a destra.
     * Normalizza la stringa rimuovendo prefissi ridondanti per evitare ripetizioni visive.
     *
     * @param statusText Testo descrittivo della risoluzione e del flusso selezionato.
     */
    public void updateStreamStatusAndQualityLabel(String statusText) {

        // Rimuove l'eventuale prefisso duplicato "Stream: " per mantenere la visualizzazione pulita
        String cleanText = statusText.startsWith("Stream: ") ? statusText.substring(8) : statusText;

        // Esegue l'assegnazione sull'EDT per preservare la consistenza grafica dell'interfaccia
        SwingUtilities.invokeLater(() -> this.qualityLabel.setText("Stream: " + cleanText));
    }
}

/*
 * --ENG------------ENG-------------ENG---------ENG--------------ENG--------ENG--
 * ============================================================================
 * MAINVIEWER CLASS - NVR CONTROLLER & MULTI-VIEW GRAPHIC COMPOSER
 * ============================================================================
 *
 * Objective:
 * To represent the main window and the main control center of the entire NVR system
 * To coordinate the asynchronous visualization of the networks (RTSP/HTTP), the bar
 * for the inferior operative controls and the dynamic change of visualization.
 *
 * Architectural Structure:
 * 1. CardLayout (mainContainerPanel):
 *    - "GRID VIEW": Multiview with 3 asymmetrical lines
 *    - "SINGLE VIEW": Exclusive zoom on a single flow through the dedicated player
 * 2. Layout Display on 3 lines (GridBagLayout):
 *    - Upper line (28% of height): GridLayout(1, 2) for the 2 panoramic cameras Duo 2V (32:9).
 *    - Central line (44% of height): GridBagLayout with widths 42%, 33%, 25% to integrate
 *      the Duo 2V of support, the RLC-820A (16:9) and the RLC-520A (4:3) without residual black space.
 *    - Bottom line (28% of height): GridLayout(1, 2) for further slots ultrawide level.
 * 3. Dedicated player for the zoom:
 *    - Separated from the grid's players to allow the instantaneous passing of the MAIN flow (High Resolution)
 *     without interrupting or changing the live rendering threads of the SUB flows.
 * 4. Unified Control of the registration:
 *    - Dedicated buttons for each camera and a master button for all of them: to start or stop the recording process
 *     of FFMPEG through an asynchronous notification.
 */