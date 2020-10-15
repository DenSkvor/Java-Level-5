package ru.geekbrains.skvortsov.cloud_storage;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;

public class Net{

    private static final String SERVER_ADDR = "localhost";
    private static final int SERVER_PORT = 8189;

    private DataInputStream in;
    private DataOutputStream out;
    private Socket socket;

    private File file = new File("client_local_storage"); //todo для демонстрации сделать каждому юзеру по своей папке
    private File clientLocalStorage = new File(file.getAbsolutePath());//todo почему джарник не видит директорию, если она не рядом?
    private String fileNameStr;
    private CountDownLatch cdl;
    private CountDownLatch cdl2;
    private CountDownLatch cdl3;
    private CountDownLatch cdl4;
    private byte [][] serverFilesArrBytes;
    private String [] serverFilesArrStr;
    private boolean connectStatus = false;

    public boolean getConnectStatus(){
        return connectStatus;
    }

    public File getClientLocalStorage(){
        return clientLocalStorage;
    }


    private static Net instance;

    public static Net getInstance(){
        if(instance == null) return instance = new Net();
        else return instance;
    }


    private Net(){
        //startConnect();
        //refreshClientFileList();
    }

    public void startConnect() {
        try {
            socket = new Socket(SERVER_ADDR, SERVER_PORT);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

        //TODO еще причесать код.

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
                            FileOutputStream fos = new FileOutputStream(clientLocalStorage.getName() + "/" + fileNameStr);

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
                        //подтверждение авторизации
                        else if(readedByte == (byte) 21){
                            connectStatus = true;
                            cdl4.countDown();

                            System.out.println("Подключение к облаку успешно осуществлено.");
                        }

                        else if(readedByte == (byte) 22){
                            cdl4.countDown();
                            System.out.println("Неудачная попытка подключения к облаку. Неверный логин/пароль.");
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


    public void downloadFile(String fileName) throws IOException, InterruptedException {
        cdl2 = new CountDownLatch(1);
        fileNameStr = fileName;
        out.write((byte) 11);
        System.out.println("Отправка командного байта на скачивание файла.");
        System.out.println(fileName);
        byte [] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        out.writeInt(fileNameBytes.length);
        System.out.println("Отправка размера имени файла: " + fileNameBytes.length);
        out.write(fileNameBytes);
        System.out.println("Отправка имени файла.");
        cdl2.await();
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
        byte [] bufer = new byte [8192];
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

    public void deleteFile(ListView<String> filesListClient, ListView<String> filesListServer) throws IOException, InterruptedException {
        if(filesListClient.isFocused()){
            File file = new File(clientLocalStorage.getName() + "/" + filesListClient.getSelectionModel().getSelectedItem());
            file.delete();
            refreshClientFileList(filesListClient);
        }
        if(filesListServer.isFocused()){

            String fileName = filesListServer.getSelectionModel().getSelectedItem();
            byte [] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
            cdl3 = new CountDownLatch(1);

            out.write((byte) 2);
            out.writeInt(fileNameBytes.length);
            out.write(fileNameBytes);
            cdl3.await();
            refreshServerFileList(filesListServer);

        }
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

    public void connect(TextField loginField, PasswordField passwordField) throws IOException, InterruptedException {
        cdl4 = new CountDownLatch(1);
        String login = loginField.getText().toLowerCase();
        String password = passwordField.getText();

        out.write((byte)20);
        out.writeInt(login.length());
        out.write(login.getBytes(StandardCharsets.UTF_8));

        out.writeInt(password.length());
        out.write(password.getBytes(StandardCharsets.UTF_8));

        System.out.println("Логин: " + login);
        System.out.println("Пароль: " + password);
        cdl4.await();

    }


    public void refreshFileListAll(ListView<String> filesListClient, ListView<String> filesListServer){

        refreshClientFileList(filesListClient);
        refreshServerFileList(filesListServer);

    }

    public void refreshClientFileList(ListView<String> filesListClient){
        //клиентский компьютер
        filesListClient.getItems().clear();
        String[] clientFiles = clientLocalStorage.list();
        if(clientFiles != null) {
            for (String fileName : clientFiles) {
                filesListClient.getItems().add(fileName);
            }
        }
    }


    public void refreshServerFileList(ListView<String> filesListServer){
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

        filesListServer.getItems().clear();
        if(serverFilesArrStr != null) {
            for (String fileName : serverFilesArrStr) {
                filesListServer.getItems().add(fileName);
            }
        }
    }

    public void exit(){
        try {
            out.write((byte) 0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Platform.exit();
    }

}
