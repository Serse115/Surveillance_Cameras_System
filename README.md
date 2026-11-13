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

* Click Sinistro su una telecamera: Apre la vista a schermo intero con flusso MAIN (Alta Risoluzione / 4K).
* Click Destro su una telecamera: Apre la vista a schermo intero con flusso SUB (Caricamento rapido).
* Click su schermata ingrandita con stesso tasto con il quale è stata aperta: Torna alla griglia multi-view.
* Pulsante TUTTE / STOP: Avvia o arresta contemporaneamente la registrazione su tutte le telecamere configurate.
* Pulsanti singola camera: Avvia o arresta la registrazione su un singolo canale.
* Pulsante RICONNETTI: Forza il riallineamento di tutti i flussi live a schermo.


THIS PROJECT IS A PROTOTYPE OF A SECURITY CAMERA MANAGEMENT SOFTWARE, NOT MEANT TO BE A PROFESSIONAL ENTERPRISE-LEVEL SOFTWARE OR ANY KIND OF REPLACEMENT TO ALREADY EXISTING COMPANY-LICENSED SOFTWARE.
THIS PROJECT IS A MAVEN-JAVA-BASED SOFTWARE, USING JAVA17 FOR THE LOGIC AND JAVA-SWING FOR THE GRAPHICS.
To work properly, the project needs the ffmpeg.exe executable File in the same folder where the pom.xml is located in, such file can be downloaded online, for size reasons it cannot be uploaded directly on here, the file used for this project during development is 98MB heavy.
The credentials for the Initialization process of the cameras are placed in a file which is called config.properties, on here it's shown a config.exeample.properties as a simplified exeample version just to have an idea on how to configure it.
STILL WORK IN PROGRESS
