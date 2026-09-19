package com;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// Classe per il Video Recorder in background tramite un altro processo per salvare i video sul disco
public class VideoRecorder {

    /* Fields */
    // Constants
    private final File outputDir;               // Cartella di output per la registrazione

    // Variables
    private Process ffmpegProcess;              // Processo che utilizza l'exe ffmpeg per registrare i dati
    private boolean isRecording;                // Boolean per definire lo stato di ripresa (Vero/Falso)



    /* Constructors */
    // Main constructor
    public VideoRecorder(final String recorderOutputFolderPath) {

        this.outputDir = new File(recorderOutputFolderPath);    // Creazione del file all'interno della cartella scelta per le registrazioni
        boolean dirExists = false;                   // Boolean per definire se la cartella esiste
        if (!this.outputDir.exists()) {              // Se la directory scelta come output non esiste
            dirExists = this.outputDir.mkdirs();     // La crea e setta il boolean a true
        }

        if (dirExists) {                             // Se la cartella viene creata, lancia il popup
            System.out.println("Cartella per le registrazioni creata!");
        }
    }



    /* Methods */
    // Start recording method per iniziare la registrazione
    // Il parametro streamUrl corrisponde all'URL dello stream
    // Il parametro segmentSeconds corrisponde alla durata degli spezzoni in secondi
    public synchronized void startRecording(final String streamUrl, final int segmentSeconds) throws IOException {

        System.out.println("La cartella delle registrazioni si trova in: " + new File("registrazioni").getAbsolutePath());

        if (this.isRecording) {             // Se sta già registrando lancia il popup per dire che la registrazione è già attiva
            System.out.println("La registrazione è già attiva!");
        }
        else {                              // Altrimenti crea timestamp ed il file pattern per il file di salvataggio video
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());      // Timestamp per la registrazione video
            //String filePattern = new File(this.outputDir, "Camera1_" + timestamp + "_%03d.mp4").getAbsolutePath();  // File pattern registrazione reale
            String filePattern = new File(this.outputDir, "Camera1_" + timestamp + "_%03d.mkv").getAbsolutePath();  // File pattern registrazione test

            List<String> command = new ArrayList<>();       // Lista di comandi terminale da effettuare
            command.add("ffmpeg.exe");                      // Aggiungi l'avvio dell'eseguibile ffmpeg presente nella cartella root
            command.add("-i");                              // Aggiungi la flag che indica l'input
            command.add(streamUrl);                         // Aggiungi la flag di input
            command.add("-c");                              // Aggiungi la flag per copiare direttamente senza codifica/decodifica
            command.add("copy");                            // Aggiungi il comando copy
            command.add("-f");                              // Aggiungi la flag per spezzare il file in segmenti contigui
            command.add("segment");                         // Aggiungi il comando segment
            command.add("-segment_time");                   // Aggiungi il comando segment time
            command.add(String.valueOf(segmentSeconds));    // Aggiungi il valore della durata scelta per il blocco
            command.add("-reset_timestamps");               // Resetta i timestamps alla fine
            command.add("1");                               // ...
            command.add(filePattern);                       // Aggiungi quindi il file di Output con pattern numerato progressivo

            // Crea il nuovo processo con l'oggetto ProcessBuilder
            ProcessBuilder pb = new ProcessBuilder(command);        // A cui si passa il command generato prima

            // Re-dirige tutti i possibili output ed errori di ffmpeg sul terminale java come log in console
            pb.inheritIO();

            System.out.println("Avvio registrazione su: " + filePattern);       // Popup di informazione avvio registrazione
            ffmpegProcess = pb.start();                                         // Avvio del processo con ffmpeg
            this.isRecording = true;                                            // Impostazione boolean su true (sta registrando)
        }
    }

    // Stop recording method per fermare la registrazione in corso
    public synchronized void stopRecording() {

        if (this.isRecording || this.ffmpegProcess != null) {    // Se il boolean di registrazione è true o se il processo di ffmpeg è vivo ed attivo

            System.out.println("Arresto della registrazione in corso...");          // Lancia il popup di arresto della registrazione
            try {
                this.ffmpegProcess.getOutputStream().write("q\n".getBytes());   // Invia 'q' sullo stdin di ffmpeg per chiudere l'indice mp4
                this.ffmpegProcess.getOutputStream().flush();                   // Flusha lo stream output
                this.ffmpegProcess.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);   // Concede 3 secondi di tempo
            }
            catch (Exception e) {
                ffmpegProcess.destroyForcibly();            // Se non risponde entro quei 3 secondi viene chiuso forzatamente
            }

            this.isRecording = false;                                           // Setta il boolean della registrazione a false
            System.out.println("Registrazione terminata con successo!");        // E lancia il popup per la terminazione con successo
        }
    }

    // Getter per il boolean della registrazione
    public boolean isRecording() {
        return this.isRecording;
    }
}