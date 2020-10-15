package ru.geekbrains.skvortsov.cloud_storage;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;

public class Controller implements Initializable{

    @FXML
    ListView<String> filesListClient;

    @FXML
    ListView<String> filesListServer;

    @FXML
    TextField loginField;

    @FXML
    PasswordField passwordField;

    @FXML
    Button connectButton;

    @FXML
    HBox connectBox;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Net.getInstance().startConnect();
        Net.getInstance().refreshClientFileList(filesListClient);
    }

    ///////////////кнопки//////////////////////////

    public void btnRefresh(ActionEvent actionEvent) {
        if(!Net.getInstance().getConnectStatus()) return;
        Net.getInstance().refreshFileListAll(filesListClient, filesListServer);
    }


    public void btnExit(ActionEvent actionEvent) {
        Net.getInstance().exit();
    }


    public void btnSendFile(ActionEvent actionEvent) {
        if(!filesListClient.isFocused() || !Net.getInstance().getConnectStatus()) return;
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                File file = new File(Net.getInstance().getClientLocalStorage().getName() + "/" + filesListClient.getSelectionModel().getSelectedItem());
                try {
                    Alert alertInfo = new Alert(Alert.AlertType.INFORMATION,"Загрузка файла...");
                    alertInfo.show();

                    Net.getInstance().sendFile(file);
                    Net.getInstance().refreshServerFileList(filesListServer);

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
        if(!filesListServer.isFocused() || !Net.getInstance().getConnectStatus()) return;
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                try {
                    Alert alertInfo = new Alert(Alert.AlertType.INFORMATION,"Скачивание файла...");
                    alertInfo.show();

                    //cdl2 = new CountDownLatch(1);
                    Net.getInstance().downloadFile(filesListServer.getSelectionModel().getSelectedItem());
                    //cdl2.await();
                    Net.getInstance().refreshClientFileList(filesListClient);

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
        if(!Net.getInstance().getConnectStatus()) return;
        Platform.runLater(new Runnable() {
            @Override
            public void run() {

                try {
                    Net.getInstance().deleteFile(filesListClient, filesListServer);

                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Не удалось удалить файл.", ButtonType.OK);
                    alert.showAndWait();
                }
            }
        });
    }

    public void btnConnect(ActionEvent actionEvent) {
        if(loginField.getText().equals("") || passwordField.getText().equals("")) return;
        Platform.runLater(new Runnable() {
            @Override
            public void run() {

                try {
                    Alert alertInfo = new Alert(Alert.AlertType.INFORMATION,"Подключение...");
                    alertInfo.show();

                    Net.getInstance().connect(loginField, passwordField);

                    alertInfo.close();
                    if(Net.getInstance().getConnectStatus()) {
                        //clientLocalStorage = new File("client_local_storage_" + login);
                        //if (!clientLocalStorage.exists()) clientLocalStorage.createNewFile();
                        connectBox.managedProperty().bind(connectBox.visibleProperty());
                        connectBox.setVisible(false);
                        Net.getInstance().refreshServerFileList(filesListServer);
                    }
                    else {
                        Alert alert = new Alert(Alert.AlertType.WARNING, "Неверный логин/пароль.", ButtonType.OK);
                        alert.showAndWait();
                    }

                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

    }

}
