package ru.geekbrains.skvortsov.cloud_storage;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;

import javax.swing.table.TableColumn;
import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;

public class Net implements Initializable {

    private static final String SERVER_ADDR = "localhost";
    private static final int SERVER_PORT = 8189;

    private DataInputStream in;
    private DataOutputStream out;
    private Socket socket;

    private File clientStorage = new File("client_storage");
    private String fileNameStr;
    private CountDownLatch cdl;
    private CountDownLatch cdl2;
    private CountDownLatch cdl3;
    private byte [][] serverFilesArrBytes;
    private String [] serverFilesArrStr;


    @FXML
    ListView<String> filesTableClient;

    @FXML
    ListView<String> filesTableServer;

    private static Net instance;

    public static Net getInstance(){
        return instance;
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        instance = this;
        startConnect();
        refreshFileListAll();
    }

    /*public Net(){
        start();
    }*/

    public void startConnect() {
        try {
            socket = new Socket(SERVER_ADDR, SERVER_PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

        //TODO Причесать код. Нормально скомпоновать методы и кнопки. В идеале вынести контроллер сцены в отдельный класс.

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("Клиент запущен.");
                    while (true) {
                        System.out.println("Ожидание байт");
                        byte readedByte = in.readByte();

                        //готовимся скачивать файл с сервака
                        if(readedByte == (byte) 11) {
                            System.out.println("Ответная команда на скачивание файла с сервера.");
                            //получаем размер файла
                            long fileSize = in.readLong();
                            long receivedFileLength = 0L;
                            System.out.println("Размер файла: " + fileSize);
                            //готовимся качать файл. готовим буфер, открываем стрим в файл
                            FileOutputStream fos = new FileOutputStream("client_storage/" + fileNameStr);

                            byte [] buffer = new byte[256];
                            int readedBytes;
                            while(receivedFileLength != fileSize) {
                                readedBytes = in.read(buffer);
                                fos.write(buffer, 0, readedBytes);
                                receivedFileLength += readedBytes;
                            }

                            fos.close();

                            System.out.println("Файл скачан.");

                            cdl2.countDown();

                            //готовимся получать список файлов с сервака
                        } else if(readedByte == (byte) 1) {
                            System.out.println("Ответная команда на получение списка файлов с сервера.");
                            //получаем размер байтового массива [][]
                            int serverFilesArrBytesSize = in.readInt();
                            System.out.println("Размер байтового массива [][] списка файлов: " + serverFilesArrBytesSize);
                            //готовимся качать массив
                            serverFilesArrBytes = new byte[serverFilesArrBytesSize][];
                            //int serverFileSize;
                            byte [] serverFile;
                            System.out.println("Скачиваем массив [][] байт.");
                            for (int i = 0; i < serverFilesArrBytesSize; i++){
                                //serverFileSize = in.readInt();
                                serverFile = new byte[in.readInt()];
                                in.read(serverFile);
                                serverFilesArrBytes[i] = serverFile;
                                System.out.println("Файл " + i);
                            }
                            System.out.println("Список файлов с сервера получен.");
                            cdl.countDown();

                         //подтверждение удаления файла на сервере
                        } else if(readedByte == (byte) 2){
                            cdl3.countDown();
                            System.out.println("Файл удален.");
                        }
                        //команда на отключение
                        else if(readedByte == (byte) 0) return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }finally {
                    close();
                }
            }
        });
        //t.setDaemon(true);
        t.start();

        } catch (IOException e) {
            e.printStackTrace();
            close();
        }
    }


    public void downloadFile(String fileName) throws IOException {
        fileNameStr = fileName;
        out.write((byte) 11);
        System.out.println("Отправка командного байта на скачивание файла.");
        System.out.println(fileName);
        byte [] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        out.writeInt(fileNameBytes.length);
        System.out.println("Отправка размера имени файла: " + fileNameBytes.length);
        out.write(fileNameBytes);
        System.out.println("Отправка имени файла.");
    }


    public void sendFile(File file) throws IOException {
        String fileName = file.getName();
        //шлём сигнальный байт
        out.write((byte) 10);
        System.out.println("Отправка командного байта на загрузку файла.");
        //шлём длину имени и само имя файла
        byte [] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        out.writeInt(fileNameBytes.length);
        System.out.println("Отправка размера имени файла: " + fileNameBytes.length);
        out.write(fileNameBytes);
        System.out.println("Отправка имени файла.");
        //шлем размер файла
        out.writeLong(file.length());
        System.out.println("Отправка размера файла: " + file.length());
        //вычитываем и шлем сам файл
        byte [] bufer = new byte [256];
        FileInputStream fis = new FileInputStream(file);
        int readedBytes = 0;
        System.out.println("Отправка файла.");
        while ((readedBytes = fis.read(bufer)) > 0) {
            out.write(bufer, 0, readedBytes);
            //System.out.println(".");
        }
        fis.close();
        System.out.println("Файл передан.");
        
    }


    public void close() {
        try {
            if(out != null) out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if(in != null) in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if(socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Клиент отключен.");
    }


    public void refreshFileListAll(){

        refreshClientFileList();
        refreshServerFileList();

    }


    public void refreshClientFileList(){
        //клиентский комп
        filesTableClient.getItems().clear();
        String[] clientFiles = clientStorage.list();
        if(clientFiles != null) {
            for (String fileName : clientFiles) {
                filesTableClient.getItems().add(fileName);
            }
        }
    }


    public void refreshServerFileList(){
        //облако

        //отправка запроса на получение списка
        cdl = new CountDownLatch(1);
        try {
            out.writeByte((byte) 1);
            cdl.await();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.WARNING, "Не удалось запросить список файлов у сервера.", ButtonType.OK);
            alert.showAndWait();
        }

        serverFilesArrStr = new String[serverFilesArrBytes.length];
        for (int i = 0; i < serverFilesArrStr.length; i++) {
            serverFilesArrStr[i] = new String(serverFilesArrBytes[i], StandardCharsets.UTF_8);
        }

        filesTableServer.getItems().clear();
        if(serverFilesArrStr != null) {
            for (String fileName : serverFilesArrStr) {
                filesTableServer.getItems().add(fileName);
            }
        }
    }

///////////////кнопки//////////////////////////

    public void btnRefresh(ActionEvent actionEvent) {
        refreshFileListAll();
    }


    public void exit(){
        try {
            out.write((byte) 0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Platform.exit();
    }

    public void btnExit(ActionEvent actionEvent) {

        exit();

    }


    public void btnSendFile(ActionEvent actionEvent) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if(!filesTableClient.isFocused()) return;
                File file = new File(clientStorage + "/" + filesTableClient.getSelectionModel().getSelectedItem());
                try {
                    Alert alertInfo = new Alert(Alert.AlertType.INFORMATION,"Загрузка файла...");
                    alertInfo.show();

                    sendFile(file);
                    refreshServerFileList();

                    alertInfo.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Не удалось отправить файл.", ButtonType.OK);
                    alert.showAndWait();
                }
            }
        });

    }

    public void btnDownloadFile(ActionEvent actionEvent) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if(!filesTableServer.isFocused()) return;
                try {
                    Alert alertInfo = new Alert(Alert.AlertType.INFORMATION,"Скачивание файла...");

                    alertInfo.show();

                    cdl2 = new CountDownLatch(1);
                    downloadFile(filesTableServer.getSelectionModel().getSelectedItem());
                    cdl2.await();
                    refreshClientFileList();

                    alertInfo.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Не удалось скачать файл.", ButtonType.OK);
                    alert.showAndWait();
                }
            }
        });


    }


    public void btnDel(ActionEvent actionEvent) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if(filesTableClient.isFocused()){
                    File file = new File(clientStorage + "/" + filesTableClient.getSelectionModel().getSelectedItem());
                    file.delete();
                    refreshClientFileList();
                }
                if(filesTableServer.isFocused()){

                    String fileName = filesTableServer.getSelectionModel().getSelectedItem();
                    byte [] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
                    cdl3 = new CountDownLatch(1);

                    try {
                        out.write((byte) 2);
                        out.writeInt(fileNameBytes.length);
                        out.write(fileNameBytes);
                        cdl3.await();
                        refreshServerFileList();

                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                        Alert alert = new Alert(Alert.AlertType.WARNING, "Не удалось удалить файл.", ButtonType.OK);
                        alert.showAndWait();
                    }
                }
            }
        });
    }
}
