package pl.umcs.oop.demo2;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.*;

public class Server {
    // Ścieżka do katalogu z obrazami i bazą danych
    private static final String IMAGES_DIR = "images";
    private static final String DB_PATH = IMAGES_DIR + File.separator + "index.db";
    private static final int PORT = 5000;

    // Promień filtra ustawiany przez GUI
    private static volatile int filterRadius = 3;

    public static void main(String[] args) {
        // Uruchomienie GUI w osobnym wątku
        SwingUtilities.invokeLater(Server::createAndShowGUI);

        // Utworzenie katalogu na obrazy, jeśli nie istnieje
        File imagesDir = new File(IMAGES_DIR);
        if (!imagesDir.exists()) imagesDir.mkdir();

        // Inicjalizacja bazy danych
        initDatabase();

        // Główna pętla serwera
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serwer nasłuchuje na porcie " + PORT);
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Połączono z klientem: " + clientSocket.getInetAddress());

                    // 1. Odbiór pliku PNG
                    String fileName = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".png";
                    File receivedFile = new File(IMAGES_DIR, fileName);
                    receiveFile(clientSocket.getInputStream(), receivedFile);

                    // 2. Zapis pliku już wykonany powyżej

                    // 3. Odczyt promienia z GUI (zmienna filterRadius)

                    // 4. Przekształcenie obrazu algorytmem box blur
                    File blurredFile = new File(IMAGES_DIR, "blurred_" + fileName);
                    long start = System.currentTimeMillis();
                    BoxBlur.blur(receivedFile, blurredFile, filterRadius);
                    long delay = System.currentTimeMillis() - start;

                    // 5. Zapis podsumowania do bazy danych
                    saveToDatabase(blurredFile.getPath(), filterRadius, delay);

                    // 6. Odesłanie przekształconego pliku klientowi
                    sendFile(clientSocket.getOutputStream(), blurredFile);

                    System.out.println("Obsłużono klienta, oczekiwanie na kolejnego...");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Tworzy GUI do ustawiania promienia filtra
    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Ustaw promień filtra (nieparzysty 1-15)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 100);

        JSlider slider = new JSlider(1, 15, filterRadius);
        slider.setMajorTickSpacing(2);
        slider.setPaintTicks(true);
        slider.setSnapToTicks(true);

        JLabel label = new JLabel("Promień: " + filterRadius);

        slider.addChangeListener(e -> {
            int value = slider.getValue();
            // Tylko nieparzyste wartości
            if (value % 2 == 0) value++;
            if (value > 15) value = 15;
            slider.setValue(value);
            filterRadius = value;
            label.setText("Promień: " + filterRadius);
        });

        frame.setLayout(new BorderLayout());
        frame.add(slider, BorderLayout.CENTER);
        frame.add(label, BorderLayout.SOUTH);
        frame.setVisible(true);
    }
    // Odbiera plik od klienta przez InputStream i zapisuje go na dysku.
    // Najpierw odczytuje rozmiar pliku (long), następnie pobiera dokładnie tyle bajtach
    private static void receiveFile(InputStream in, File file) throws IOException {
        DataInputStream dataIn = new DataInputStream(in);
        long fileSize = dataIn.readLong();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            long totalRead = 0;
            int read;
            // Czytaj dane aż do pobrania całego pliku
            while (totalRead < fileSize && (read = dataIn.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) != -1) {
                fos.write(buffer, 0, read);
                totalRead += read;
            }
        }
    }

    // Wysyła plik do klienta przez OutputStream.
    // Najpierw wysyła rozmiar pliku (long), następnie zawartość pliku w bajtach.
    private static void sendFile(OutputStream out, File file) throws IOException {
        DataOutputStream dataOut = new DataOutputStream(out);
        dataOut.writeLong(file.length());// Wyślij rozmiar pliku do klienta
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int read;
            // Wysyłaj dane pliku w kawałkach
            while ((read = fis.read(buffer)) != -1) {
                dataOut.write(buffer, 0, read);
            }
        }
        dataOut.flush();
    }

    // Inicjalizuje bazę danych SQLite
    private static void initDatabase() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            String sql = "CREATE TABLE IF NOT EXISTS images (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "path TEXT," +
                    "size INTEGER," +
                    "delay INTEGER)";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Zapisuje informację o przetwarzaniu do bazy danych
    private static void saveToDatabase(String path, int size, long delay) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            String sql = "INSERT INTO images (path, size, delay) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, path);
                pstmt.setInt(2, size);
                pstmt.setLong(3, delay);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}