package com;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * * --ITA-----------ITA------------ITA---------ITA--------------ITA--------ITA--
 * ============================================================================
 * MODULO VIDEO RECORDER - GESTIONE PROCESSO ESTERNO FFMPEG
 * ============================================================================
 *
 * Scopo:
 * Gestisce la registrazione continua e non bloccante dei flussi video (RTSP)
 * mediante un processo nativo separato basato sul tool CLI FFmpeg.
 *
 * Principi Architetturali & Tecniche di Ottimizzazione:
 * 1. Stream Copy (-c copy):
 *    Non viene effettuata alcuna ricodifica video o transcodifica da parte della CPU.
 *    I pacchetti compressi in arrivo dalla telecamera vengono scritti direttamente
 *    nel container multimediale (.mkv) su disco, riducendo il consumo della CPU a < 1%.
 * 2. Segmentazione Automatica:
 *    Il flusso viene suddiviso in file video consecutivi (es. 30 secondi ciascuno)
 *    in modo completamente trasparente, evitando la perdita di intere registrazioni in caso
 *    di cadute di corrente o interruzioni improvvise.
 * 3. Thread di Supervisione (Watchdog Automatico):
 *    Un thread dedicato controlla continuamente il processo FFmpeg. In caso di disconnessione
 *    di rete o spegnimento temporaneo della telecamera, FFmpeg termina ma il supervisore
 *    rimane in ascolto ed effettua tentativi di riconnessione ciclici ogni 4 secondi.
 * 4. Chiusura Pulita (Graceful Shutdown):
 *    All'arresto manuale viene inviato il comando 'q' allo stream di input di FFmpeg
 *    seguito da un flush e chiusura dell'output stream, consentendo al muxer Matroska
 *    di scrivere correttamente gli indici temporali e chiudere l'header del file.
 */
// Classe per il Video Recorder in background tramite un altro processo per salvare i video sul disco
public class VideoRecorder {

    /* Fields */
    // Constants
    private final File outputDir;               // Cartella di output per la registrazione
    private final String cameraName;            // Stringa del nome della camera

    // Variables
    private Process ffmpegProcess;                  // Processo FFMPEG per registrare
    private volatile boolean userWantsRecording;    // Flag di controllo dello stato, volatile per garantire la visibilità tra thread concorrenti
    private Thread supervisorThread;                // Thread supervisore che mantiene in vita il ciclo di registrazione e auto-riconnessione
    private Consumer<String> statusListener;        // Callback per aggiornare la GUI di MainViewer e notificare lo stato alla bottombar di MainViewer



    /* Constructors */
    // Main constructor
    /**
     * Inizializza una nuova istanza del registratore per una telecamera specifica
     *
     * @param recorderOutputFolderPath Percorso relativo o assoluto della cartella di destinazione per la registrazione
     * @param cameraName Nome identificativo della telecamera associata per la registrazione
     */
    public VideoRecorder(final String recorderOutputFolderPath, final String cameraName) {
        this.outputDir = new File(recorderOutputFolderPath);                            // Inizializza l'oggetto File per la cartella delle registrazioni
        this.cameraName = cameraName.replaceAll("[^a-zA-Z0-9_-]", "_"); // Pulisce spazi e caratteri non alfanumerici con underscore per evitare errori nei percorsi
        this.userWantsRecording = false;                                                // Imposta inizialmente lo stato della registrazione su inattivo
        if (!this.outputDir.exists()) {                                                 // Se la cartella delle registrazioni non esiste
            if (this.outputDir.mkdirs()) {                                              // La crea
                System.out.println("Cartella per le registrazioni creata!");            // E printa a schermo il risultato
            }
        }
    }



    /* Methods */
    // Start recording method per iniziare la registrazione
    /**
     * Avvia il ciclo di registrazione in un thread di background indipendente
     *
     * @param streamUrl URL completo della sorgente multimediale (es. rtsp://... o http://...)
     * @param segmentSeconds Durata scelta per ciascun file video (.mkv) in secondi
     */
    public synchronized void startRecording(final String streamUrl, final int segmentSeconds) {
        if (this.userWantsRecording) {                              // Se la registrazione è già in esecuzione in modo da evitare doppi avvii
            System.out.println("La registrazione è già attiva!");   // Notifica che la registrazione è già attiva
            return;                                                 // Poi esce dalla funzione
        }

        this.userWantsRecording = true;     // Imposta il flag per indicare che l'utente ha richiesto la registrazione

        this.supervisorThread = new Thread(() -> {     // Crea il thread di supervisione (Watchdog) per monitorare il ciclo di vita del processo FFmpeg
            System.out.println("-> [RECORDER " + cameraName + "] Supervisore registrazione avviato.");  // Avviso del supervisore avviato

            while (this.userWantsRecording) {       // Finchè la registrazione rimane attiva
                try {
                    String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());  // Timestamp per data ed ora
                    String filePattern = new File(this.outputDir, this.cameraName + "_" + timestamp + "_%03d.mkv").getAbsolutePath();   // Pattern nome file con la directory di output, il nome della camera ed il timestamp

                    List<String> command = new ArrayList<>();   // Lista di stringhe di comandi da passare a FFMPEG per la corretta registrazione
                    command.add("ffmpeg.exe");          // Indica l'eseguibile ffmpeg.exe (deve trovarsi nel PATH di sistema o nella cartella principale del progetto)
                    command.add("-hide_banner");        // Nascondi il banner/logo della telecamera
                    command.add("-loglevel");           // Per i log
                    command.add("warning");             // Mostra soltanto i warning piu' importanti

                    // 1. Parametri di input differenziati per protocollo
                    if (streamUrl.toLowerCase().startsWith("rtsp://")) {        // Per in particolare il protocollo rtsp
                        command.add("-rtsp_transport");                         // Forza il trasporto rtsp
                        command.add("tcp");                                     // Sul protocollo TCP per evitare perdite di dati come in UDP
                        command.add("-timeout");                                // Aggiungendo un timeout massimo di connessione
                        command.add("5000000");                                 // Di 5 secondi
                    }

                    // 1.5 Parametri e Flag di correzione: genera i timestamp mancanti e scarta i pacchetti corrotti
                    command.add("-fflags");                                     // Aggiungi appunto la flag
                    command.add("+genpts+discardcorrupt");                      // Per generare i timestamp mancanti e scartare i corrotti
                    command.add("-avoid_negative_ts");                          // Evita i timestamp negativi
                    command.add("make_zero");                                   // Re-inizializzando a 0
                    command.add("-i");                                          // Specifica l'indirizzo della sorgente video da catturare
                    command.add(streamUrl);                                     // Stream dell'Input URL

                    // 2. Copia flusso
                    command.add("-c");                                          // Flag di impostazione per la copia del flusso
                    command.add("copy");                                        // Copia solamente, niente codifica o ricodifica, zero processi pesanti su CPU

                    // 3. Segmentazione
                    command.add("-f");                                          // Flag di impostazione per la segmentazione del flusso
                    command.add("segment");                                     // Muxer di segmentazione per dividere l'output in blocchi temporali
                    command.add("-segment_time");                               // Seleziona la durata di ogni blocco di segmento di video
                    command.add(String.valueOf(segmentSeconds));                // Imposta tale valore di durata al valore di secondi indicato come parametro di funzione
                    command.add("-reset_timestamps");                           // Resetta i timestamps a 0 all'inizio di ogni singolo file
                    command.add("1");                                           //

                    // 4. Muxer Matroska: forza la scrittura di un blocco indice ogni 2 MB per prevenire file illeggibili
                    command.add("-cluster_size_limit");                         // Imposta il cluster size limit per la grandezza massima dei blocchi
                    command.add("2M");                                          // Imposta a 2 MegaByte

                    command.add(filePattern);                                   // Aggiungi il file pattern al percorso di destinazione

                    ProcessBuilder pb = new ProcessBuilder(command);            // Crea il ProcessBuilder con la lista di comandi
                    pb.inheritIO();                                             // Inoltra lo stream I/O del processo FFmpeg alla console standard dell'applicazione

                    System.out.println("-> [RECORDER " + this.cameraName + "] Avvio FFmpeg su: " + filePattern);
                    synchronized (this) {                       // Synchronized per Thread Safe
                        this.ffmpegProcess = pb.start();        // Avvia fisicamente il processo di sistema memorizzandone il riferimento
                    }

                    this.notifyStatus("STATO: REGISTRAZIONE ATTIVA SU " + this.cameraName + "!");   // Modifica la label sulla bottom bar per indicare lo stato di registrazione attiva

                    // Attende che il processo termini (per caduta cavo o per stop utente)
                    int exitCode = this.ffmpegProcess.waitFor();        // Variabile che prende l'exit code
                    System.out.println("-> [RECORDER " + this.cameraName + "] FFmpeg terminato con codice: " + exitCode);   // Print dell'exit code

                } catch (IOException e) {               // Se prende un'eccezione di IO
                    System.out.println("-> [RECORDER " + this.cameraName + "] Errore avvio FFmpeg: " + e.getMessage());     // Printa tale messaggio
                }
                catch (InterruptedException e) {        // Altrimenti se prende un'interruzione di tipo Thread
                    break;                              // Esci direttamente con break
                }

                if (this.userWantsRecording) {          // Se l'utente VUOLE ancora registrare ma FFmpeg è uscito (es. cavo staccato)
                    this.notifyStatus("STATO: RICONNESSIONE REGISTRAZIONE IN CORSO...");        // Modifica lo stato della label della bottombar per mostrare il tentativo di riconnessione
                    System.out.println("-> [RECORDER WATCHDOG] Telecamera " + this.cameraName + " non raggiungibile. Nuovo tentativo tra 4 secondi...");    // Printa lo stato della telecamera secondo il watchdog
                    try {
                        Thread.sleep(4000);                // Riprova a oltranza ogni 4 secondi
                    }
                    catch (InterruptedException e) {            // Se l'eccezione è di tipo thread
                        break;                                  // Esci direttamente con break
                    }
                }
            }

            System.out.println("-> [RECORDER " + this.cameraName + "] Supervisore registrazione terminato.");   // Se tutto va bene, printa la corretta fine della registrazione
        }, "Recorder-"+ this.cameraName);               // Con il nome della telecamera per capire quale abbia finito

        this.supervisorThread.setDaemon(true);                // Imposta il thread come demone per non bloccare la chiusura dell'intera JVM
        this.supervisorThread.start();                        // Avvia l'esecuzione del thread supervisore
    }

    // Stop recording method per fermare la registrazione
    /**
     * Arresta la registrazione in corso in modo pulito e controllato (Graceful Shutdown).
     **/
    public synchronized void stopRecording() {
        if (!this.userWantsRecording) {     // Se non ci sono registrazioni in corso
            return;                         // Esci ed arresta la registrazione immediatamente con un return
        }

        System.out.println("Arresto della registrazione dalla camera " + this.cameraName + " in corso..."); // Printa il messaggio d'arresto
        this.userWantsRecording = false;        // Segnala al supervisore d'interrompere il ciclo infinito e non effettuare altri tentativi

        if (this.ffmpegProcess != null && this.ffmpegProcess.isAlive()) {   // Se il Processo FMPEG esiste correttamente ed è attivo
            try {
                // 1. Tenta la chiusura ordinata
                this.ffmpegProcess.getOutputStream().write('q');    // Invia il carattere 'q' nello standard input di FFmpeg per richiedere l'uscita ordinata
                this.ffmpegProcess.getOutputStream().flush();          // Flusha l'invio del carattere
                this.ffmpegProcess.getOutputStream().close();          // EOF pulito per far finalizzare l'header dell'MKV

                // 2. Concedi fino a 4 secondi per finalizzare l'indice su disco
                boolean finished = this.ffmpegProcess.waitFor(4, java.util.concurrent.TimeUnit.SECONDS);    // Attende fino a 4 secondi per permettere la scrittura finale dell'indice e dell'header MKV
                if (!finished) {                                                                    // Se non ha finito entro il timeout
                    this.ffmpegProcess.destroy();                                                   // Chiusura ordinata forzata con destroy
                    this.ffmpegProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);   // E controlla che venga terminato in altri 2 secondi
                }
            }
            catch (Exception e) {                           // Se viene lanciata un'eccezione, a causa del fallimento della chiusura ordinata, catturala
                this.ffmpegProcess.destroyForcibly();       // E termina il processo FFMPEG forzatamente con destroyForcibly
            }
            finally {                                       // Infine
                this.ffmpegProcess = null;                  // Porta il processo FFMPEG a null
            }
        }

        if (this.supervisorThread != null) {      // Se il thread supervisore è ancora in esecuzione o in sleep
            this.supervisorThread.interrupt();    // Lo interrompe
            this.supervisorThread = null;         // E lo mette a null
        }

        this.notifyStatus("STATO: REGISTRAZIONE FERMATA. STATO IDLE IN CORSO...");      // Modifica lo stato del label della bottombar con il nuovo stato
        System.out.println("Registrazione dalla camera " + this.cameraName + " terminata con successo!");   // Printa lo stato di terminazione della telecamera
    }

    // Set status listener method
    /**
     * Registra un callback per ricevere aggiornamenti testuali sullo stato del recorder.
     *
     * @param listener Consumer a cui passare i messaggi di stato.
     */
    public void setStatusListener(Consumer<String> listener) {
        this.statusListener = listener;
    }

    // Notify status method per modificare lo stato del label nella bottombar
    /**
     * Invia un messaggio di notifica al listener registrato se presente.
     *
     * @param msg Messaggio testuale di stato.
     */
    private void notifyStatus(String msg) {
        if (this.statusListener != null) {
            this.statusListener.accept(msg);
        }
    }

    // Getter per il boolean della registrazione
    /**
     * Verifica se la registrazione per questa telecamera è correntemente attiva.
     *
     * @return true se la registrazione è in corso, false altrimenti.
     */
    public boolean isRecording() {
        return this.userWantsRecording;
    }
}

/*
--ENG------------ENG-------------ENG---------ENG--------------ENG--------ENG--
* ============================================================================
* VIDEO RECORDER MODULE- EXTERNAL FFMPEG PROCESS HANDLING
* ============================================================================
*
* Objective:
* Handles the continuous recording and not-blocking video flows (RTSP) processes
* through a native process based on CLI FFMPEG tools.
*
* Architecture Principles & Optimization techniques:
* 1. Stream Copy (-c copy):
*    Doesn't do any re-coding of every kind from the CPU.
* Principi Architetturali & Tecniche di Ottimizzazione:
* 1. Stream Copy (-c copy):
*    No re-coding of any kind of such process is done CPU-wise.
*    Compressed packages coming from the camera are transcribed directly, no encoding, saves CPU cycles
*    in the multimedia container (.mkv) on the storage.
* 2. Automatic Segmentation:
*    The data flow gets divided into consecutive files (30 seconds each)
*    transparently, avoiding any possible data loss or even entire recording losses
*    in case of power cuts or sudden interruptions of any kind.
* 3. Supervisor Thread (Automatic Watchdog):
*    A dedicated Thread to continuously check the FFMPEG process. In a disconnection scenario
*    based on network or even a sudden malfunction of the camera, FFMPEG shuts down
*    but the supervisor keeps on listening, trying to reconnect every 4 seconds.
* 4. Graceful shutdown:
*    On the manual shutdown, a 'q' command gets sent to the input stream of FFMPEG process
*    followed by a flush and the closing of the output stream, allowing the Matroska muxer
*    to correctly write the temporal indexes and close the file header.
*/