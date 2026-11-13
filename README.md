-------------------------------------- ITA ---------------------------------------------- ITA ----------------------------------------------------------------
# NVR Videosorveglianza - Monitor Locale (Java Swing + VLCJ + FFmpeg) - Con al momento Struttura Offline/Modem a Circuito chiuso/Non connesso ad Internet 

QUESTO PROGETTO E' UN PROTOTIPO DI SOFTWARE DI GESTIONE DI TELECAMERE DI SICUREZZA, NON PENSATO PER ESSERE UN SOFTWARE DI LIVELLO ENTERPRISE/PROFESSIONALE O PER RIMPIAZZARE ALCUN SOFTWARE CON LICENZA GIA' PRESENTE SUL MERCATO.

Un sistema NVR (Network Video Recorder) desktop leggero, modulare e ad alte prestazioni sviluppato in **Java Swing** per il monitoraggio in tempo reale e la registrazione continua di telecamere IP (ottimizzato per telecamere PoE Reolink 16:9, 4:3 e panoramiche 32:9, oltre a flussi generici RTSP/HTTP).

## Caratteristiche Principali

* **Layout Asimmetrico Multi-View a 3 Righe:** Griglia progettata su misura per ospitare contemporaneamente telecamere con rapporti d'aspetto eterogenei senza bande nere residue:
  * **Riga Superiore (28% altezza):** 2 slot affiancati (50% / 50%) ideali per telecamere panoramiche a 180° (es formato 32:9).
  * **Riga Centrale (44% altezza):** 3 slot calibrati (42% / 33% / 25%) per combinare flussi panoramici, standard 16:9 e 4:3.
  * **Riga Inferiore (28% altezza):** 2 slot affiancati (50% / 50%) per ulteriori telecamere panoramiche.
* **Disaccoppiamento Diretta / Registrazione (Zero Sforzo CPU):**
  * **Diretta Live a Schermo:** Utilizza **VLCJ** (`EmbeddedMediaPlayerComponent`) con accelerazione hardware nativa sui flussi secondari (`SUB` stream) a bassissima latenza.
  * **Registrazione su Disco:** Delega il salvataggio dei flussi principali ad alta risoluzione (`MAIN` stream 4K/5K) a processi esterni **FFmpeg** con modalità *stream copy* (`-c copy`), azzerando il carico di ricodifica sulla CPU.
* **Vista Singola (Zoom Su una Telecamera):** Cliccando su qualsiasi riquadro si passa a tutto schermo tramite `CardLayout` su un player dedicato (tasto sinistro per il flusso `MAIN` in alta risoluzione, tasto destro per il flusso `SUB` rapido).
* **Registrazione Segmentata e Watchdog Automatico:** I video vengono salvati in formato `.mkv` spezzati in segmenti regolari (es. 30 secondi) con chiusura pulita (`graceful shutdown`) e riconnessione automatica in caso di perdita di segnale.
* **Configurazione Esternalizzata:** Indirizzi IP, URL e credenziali sono separati dal codice sorgente tramite file `config.properties`.

---

## Requisiti di Sistema

* **Java JDK:** Versione 17 o superiore.
* **Maven:** Per la gestione delle dipendenze (`vlcj`).
* **VLC Media Player (64-bit):** Installato sul sistema (necessario affinché `vlcj` carichi le librerie native `libvlc`).
* **FFmpeg:** L'eseguibile `ffmpeg.exe` deve essere posizionato nella cartella principale del progetto (o aggiunto al `PATH` di sistema).
* **Potenza di calcolo:** L'applicazione è stata sviluppata su un laptop con AMD Ryzen5 2500U con Radeon Vega Mobile Gfx, 16GB DDR4 2400 MHz, SSD.

L'architettura dell'applicazione è ottimizzata per consumare pochissime risorse computazionali grazie a due scelte tecniche precise: la decodifica video hardware delegata direttamente a VLCJ tramite EmbeddedMediaPlayerComponent per la diretta a schermo e il salvataggio dei flussi su disco tramite FFmpeg in modalità stream copy (-c copy), che scrive i pacchetti compressi senza ricodifica da parte della CPU.
In base a questo carico di lavoro reale (gestione simultanea fino a 6 canali, con flussi SUB a schermo e flussi MAIN registrati su disco), i requisiti hardware si suddividono su due fasce principali: 
* **1. Requisiti Minimi (Funzionamento Base Fluido/ Solo Monitoraggio e Registrazione):**
* Ideali se la macchina viene dedicata esclusivamente all'NVR senza eseguire altre applicazioni pesanti in contemporanea:
* **Processore (CPU):** Quad-Core (4 core / 4 o 8 thread) di fascia recente o di media generazione (es. Intel Core i3 / i5 di 8ª-10ª generazione, AMD Ryzen 3 / Ryzen 5 serie 2000/3000, o Intel N100). Con questa configurazione l'applicazione gira stabilmente, lascia meno margine per il multitasking spinto.
* **Scheda Video (GPU / iGPU):** Grafica integrata moderna con supporto alla decodifica hardware H.264/H.265 (es. Intel UHD / QuickSync, AMD Radeon Vega). Non è richiesta alcuna scheda video dedicata.
* **Memoria RAM:** 8 GB (la JVM con Swing, i buffer nativi di libvlc e i 5-6 processi FFmpeg richiedono complessivamente tra 1,2 GB e 2 GB di RAM).
* **Archiviazione (Storage):** SSD o HDD meccanico con una velocità di scrittura costante di almeno 10–15 MB/s (i 5-6 flussi in stream copy generano circa 4–6 MB/s complessivi su disco).
* **Rete:** Scheda Ethernet Gigabit (1000 Mbps) cablata verso lo switch PoE delle telecamere (da evitare il Wi-Fi per flussi RTSP continui).
---
* **2. Requisiti Consigliati (Funzionamento Ottimale Potenziato, Multitasking Considerevole e Sviluppi Futuri):**
* Ideali per mantenere il sistema con un utilizzo CPU sotto il 15–20% e pronto per ospitare in futuro lo zoom digitale software o un server web Spring Boot + Svelte in background:
* **Processore (CPU):** 6 o 8 Core / 12 o 16 Thread (es. AMD Ryzen 5 5500U/6600H/7530U, Ryzen 7 5800H/7730U oppure Intel Core i5-1240P / i5-12450H / i5-13420H). Offre un'ampia riserva di calcolo per eseguire contemporaneamente browser, container o servizi web senza mai rallentare la visualizzazione live.
* **Scheda Video (GPU / iGPU):** Grafica integrata con motore multimediale di ultima generazione (Intel Iris Xe / QuickSync oppure AMD Radeon 660M/680M/Vega) e uscita video HDMI 2.0/2.1 per gestire fluidamente monitor Full HD, 2K o 4K a 60 Hz.
* **Memoria RAM:** 16 GB (o 32 GB se si prevede di far girare macchine virtuali, Docker o il futuro backend web), preferibilmente configurata in Dual Channel.
* **Archiviazione (Storage) a Doppio Disco:**
* ***Disco Primario:** SSD NVMe da 256/512 GB per Sistema Operativo e applicazione Java.
* ***Disco Secondario Dedicato:** Un secondo SSD o Hard Disk dedicato esclusivamente alla cartella registrazioni/, così da isolare i cicli continui di scrittura 24/7 di FFmpeg dal disco di sistema.
* **Rete:** Porta Ethernet cablata da 1 Gbps o 2.5 Gbps.

---

## Struttura del Progetto

```text
NVR-Monitor-Locale/
├── pom.xml                       # Dipendenze Maven (VLCJ, ecc.)
├── ffmpeg.exe                    # Motore CLI per la registrazione senza ricodifica
├── config.properties             # File locale con IP e credenziali (escluso da Git)
├── config.properties.example     # Template di configurazione
├── registrazioni/                # Cartella di output dei file .mkv (generata in automatico)
└── src/main/java/com/
    ├── Main.java                               # Entry point thread-safe (Swing EDT)
    ├── MainViewer.java                         # Controller principale, layout a 3 righe e barra comandi
    ├── SingleCameraPanel.java                  # Pannello singola camera, player SUB e gestione eventi
    ├── VideoRecorder.java                      # Gestore processi FFmpeg, segmentazione e watchdog
    ├── ConfigManager.java                      # Caricamento parametri da config.properties
    └── src/main/java/com/graphicsIcons
        ├── BlackSquareIcon.java                               # Componente per L'icona per iniziare la registrazione
        └── RedCircleIcon.java                                 # Componente per L'icona della registrazione in corso
```

---

## Configurazione e Avvio
* **1. Clona la repository**
* **2. Prepara il file di configurazione e collega le telecamere al Circuito**
* **3. Inserisci FFmpeg nella cartella radice accanto a pom.xml**
* **4. Compila ed esegui**


## Controlli Rapidi

* **Click Sinistro su una telecamera:** Apre la vista a schermo intero con flusso MAIN (Alta Risoluzione / 4K).
* **Click Destro su una telecamera:** Apre la vista a schermo intero con flusso SUB (Caricamento rapido).
* **Click su schermata ingrandita con stesso tasto con il quale è stata aperta:** Torna alla griglia multi-view.
* **Pulsante TUTTE / STOP:** Avvia o arresta contemporaneamente la registrazione su tutte le telecamere configurate.
* **Pulsanti singola camera:** Avvia o arresta la registrazione su un singolo canale.
* **Pulsante RICONNETTI:** Forza il riallineamento di tutti i flussi live a schermo.

---

## ANCORA IN SVILUPPO

---

-------------------------------------- ENG ---------------------------------------------- ENG ----------------------------------------------------------

# Local NVR Surveillance Monitor (Java Swing + VLCJ + FFmpeg)

THIS PROJECT IS A PROTOTYPE OF A SECURITY CAMERA MANAGEMENT SOFTWARE, NOT MEANT TO BE A PROFESSIONAL ENTERPRISE-LEVEL SOFTWARE OR ANY KIND OF REPLACEMENT TO ALREADY EXISTING COMPANY-LICENSED SOFTWARE.

A lightweight, modular, and high-performance desktop **NVR (Network Video Recorder)** built with **Java Swing** for real-time monitoring and continuous recording of IP cameras (optimized for Reolink PoE cameras in 16:9, 4:3, and 32:9 ultrawide aspect ratios, as well as generic RTSP/HTTP streams).

## Key Features

* **3-Row Asymmetric Multi-View Layout:** Custom-calibrated grid designed to display cameras with mixed aspect ratios simultaneously without wasted black bars:
  * **Top Row (28% height):** 2 side-by-side slots (50% / 50%) tailored for 180° panoramic cameras (e.g. 32:9 feeds).
  * **Middle Row (44% height):** 3 weighted slots (42% / 33% / 25%) combining panoramic, 16:9 and 4:3 feeds.
  * **Bottom Row (28% height):** 2 side-by-side slots (50% / 50%) for additional panoramic channels.
* **Live / Recording Decoupling (Minimal CPU Footprint):**
  * **Live Display:** Uses **VLCJ** (`EmbeddedMediaPlayerComponent`) with native hardware acceleration on low-latency secondary streams (`SUB` stream).
  * **Disk Recording:** Offloads high-resolution (`MAIN` stream 4K/5K) recording to external **FFmpeg** processes using *stream copy* (`-c copy`), avoiding CPU-heavy video transcoding.
* **Instant Single-Camera Fullscreen:** Clicking any camera tile switches via `CardLayout` to a dedicated fullscreen player (Left-click for high-res `MAIN` stream, Right-click for fast `SUB` stream).
* **Segmented Recording & Auto-Watchdog:** Saves video streams into `.mkv` segments (e.g., 30 seconds each) with graceful shutdown (`q` + EOF) and automatic reconnection loops if a camera drops offline.
* **Externalized Configuration:** Camera IPs, URLs, and credentials are decoupled from the source code via `config.properties`.

---

## System Requirements

* **Java JDK:** Version 17 or higher.
* **Maven:** For dependency management (`vlcj`).
* **VLC Media Player (64-bit):** Installed on the host machine (required by `vlcj` for native `libvlc` bindings).
* **FFmpeg:** The `ffmpeg.exe` binary must be placed in the project root directory (or available in the system `PATH`).
* **Computational Power:** The application was developed on a laptop based on an AMD Ryzen5 2500U with Radeon Vega Mobile Gfx, 16GB DDR4 2400 MHz, SSD.
  -
The application's architecture is optimized to consume very few computational resources thanks to two specific technical choices: hardware video decoding delegated directly to VLCJ via EmbeddedMediaPlayerComponent for live screen display, and saving streams to disk via FFmpeg in stream copy (-c copy) mode, which writes compressed packets without CPU re-encoding.   Based on this real-world workload (simultaneous management of up to 6 channels, with SUB streams displayed on screen and MAIN streams recorded to disk), the hardware requirements are divided into two main tiers:
* **1. Minimum Requirements (Smooth Basic Operation / Monitoring and Recording Only):**
* Ideal if the machine is dedicated exclusively to the NVR without running other heavy applications simultaneously:
* **Processor (CPU):** Quad-Core (4 cores / 4 or 8 threads) of recent or mid-range generation (e.g., 8th–10th Gen Intel Core i3 / i5, AMD Ryzen 3 / Ryzen 5 2000/3000 series, or Intel N100). With this configuration, the application runs stably, leaving less headroom for heavy multitasking.
* **Graphics Card (GPU / iGPU):** Modern integrated graphics with hardware H.264/H.265 decoding support (e.g., Intel UHD / QuickSync, AMD Radeon Vega). No dedicated graphics card is required.
* **RAM Memory:** 8 GB (the JVM with Swing, native libvlc buffers, and the 5–6 FFmpeg processes require approximately 1.2 GB to 2 GB of RAM in total).
* **Storage:** SSD or mechanical HDD with a sustained write speed of at least 10–15 MB/s (the 5–6 streams in stream copy mode generate roughly 4–6 MB/s total disk write throughput).
* **Network:** Wired Gigabit Ethernet (1000 Mbps) interface connected to the cameras' PoE switch (Wi-Fi should be avoided for continuous RTSP streams).
---
* **2. Recommended Requirements (Enhanced Optimal Performance, Heavy Multitasking, and Future Developments):**
* Ideal for keeping the system under 15–20% CPU usage and ready to host future software digital zoom or a Spring Boot + Svelte web server in the background:
* **Processor (CPU):** 6 or 8 Cores / 12 or 16 Threads (e.g., AMD Ryzen 5 5500U/6600H/7530U, Ryzen 7 5800H/7730U, or Intel Core i5-1240P / i5-12450H / i5-13420H). Provides ample computing headroom to simultaneously run browsers, containers, or web services without ever slowing down the live feed.
* **Graphics Card (GPU / iGPU):** Integrated graphics with a latest-generation media engine (Intel Iris Xe / QuickSync or AMD Radeon 660M/680M/Vega) and HDMI 2.0/2.1 video output to smoothly drive Full HD, 2K, or 4K monitors at 60 Hz.
* **RAM Memory:** 16 GB (or 32 GB if you plan to run virtual machines, Docker, or the future web backend), preferably configured in Dual Channel.
* **Dual-Drive Storage:**
* **Primary Drive:** 256/512 GB NVMe SSD for the Operating System and the Java application.
* **Dedicated Secondary Drive:** A second SSD or Hard Drive dedicated exclusively to the registrazioni/ folder, isolating FFmpeg's continuous 24/7 write cycles from the system drive.
* **Network:** Wired 1 Gbps or 2.5 Gbps Ethernet port.
  
---

## Project Structure

```text
NVR-Monitor-Locale/
├── pom.xml                       # Maven dependencies (VLCJ, etc.)
├── ffmpeg.exe                    # CLI engine for zero-transcode stream recording
├── config.properties             # Local file with IPs & credentials (ignored by Git)
├── config.properties.example     # Configuration template
├── registrazioni/                # Output folder for .mkv recordings (auto-created)
└── src/main/java/com/
    ├── Main.java                 # Thread-safe application entry point (Swing EDT)
    ├── MainViewer.java           # Main UI controller, 3-row grid & control bar
    ├── SingleCameraPanel.java    # Individual camera tile, SUB player & mouse events
    ├── VideoRecorder.java        # FFmpeg process manager, segmentation & watchdog
    └── ConfigManager.java        # Loads external settings from config.properties
       ├── BlackSquareIcon.java                               # Icon Component for the recording not running
       └── RedCircleIcon.java                                 # Icon Component for the recording running

```

---

## Configurazione e Avvio
* **1. Clone the repository**
* **2. Configure your configuration file and link all the cameras to the devices**
* **3. Add FFmpeg in the root folder where the pom.xml is**
* **4. Build and run**

---

## Controls
* **Left-Click on a camera:** Opens fullscreen view using the MAIN (High-Resolution / 4K) stream.
* **Right-Click on a camera:** Opens fullscreen view using the SUB (Fast-load) stream.
* **Click on fullscreen view with the same button used to go fullscreen:** Returns to the multi-camera grid.
* **Button ALL / STOP button:** Starts or stops recording across all active cameras simultaneously.
* **Individual camera buttons:** Toggles recording for a specific channel.
* **Button RECONNECT:** Forces a stream reset and reconnects all live tiles.


---

## STILL IN DEVELOPMENT

---
