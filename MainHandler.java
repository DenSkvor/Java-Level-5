package ru.geekbrains.skvortsov.cloud_storage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainHandler extends ChannelInboundHandlerAdapter {

    public enum State {
        WAIT_COMMAND, WAIT_NAME_LENGTH, WAIT_NAME, WAIT_FILE_LENGTH, WAIT_FILE, FILE_TRANSFER
    }

    public enum OperationType {
        RECEIVE_FILE_TO_SERVER, SEND_FILE_TO_CLIENT, SEND_SERVER_FILES, DELETE_FILE
    }

    private SocketChannel socketChannel;
    private State currentState = State.WAIT_COMMAND;
    private OperationType operationType = null;
    byte readed;
    private int fileNameLength;
    private long fileLength;
    private long receivedFileLength;
    private BufferedOutputStream out;
    private ByteBuf buf;
    private ByteBuf intBuffer;
    private ByteBuf longBuffer;
    private Path pathFile;
    private File serverStorage = new File ("server_storage");

    public MainHandler(SocketChannel socketChannel){
        this.socketChannel = socketChannel;
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Клиент " + socketChannel + " подключился.");
        intBuffer = ByteBufAllocator.DEFAULT.directBuffer(4);
        longBuffer = ByteBufAllocator.DEFAULT.directBuffer(8);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Клиент " + socketChannel + " отключился.");
        intBuffer.release();
        longBuffer.release();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        buf = ((ByteBuf) msg);
        System.out.println("\nПоступило сообщение от клинта.");
        System.out.println(buf.readableBytes());
        //TODO добавить сюда пул на 1 поток и вынести методы на скачивание/передачу в отдельный класс
        while (buf.readableBytes() > 0) {
            System.out.println("Вычитываем буфер.");
            System.out.println(buf.readableBytes());
            System.out.println(currentState);
            System.out.println(operationType);
            if (currentState == State.WAIT_COMMAND) {
                System.out.println("Определение типа команды.");
                readed = buf.readByte();
                System.out.println(buf.readableBytes());
            }

            //загрузить на сервак
            if (readed == (byte) 10 || operationType == OperationType.RECEIVE_FILE_TO_SERVER) {
                System.out.println("Команда на загрузку файла с клиента на сервер.");
                if(operationType == null) operationType = OperationType.RECEIVE_FILE_TO_SERVER;
                receiveFileToServer();
            }

            //скачать с сервака
            else if (readed == (byte) 11 || operationType == OperationType.SEND_FILE_TO_CLIENT){
                System.out.println("Команда на скачивание файла с сервера.");
                if(operationType == null) operationType = OperationType.SEND_FILE_TO_CLIENT;
                sendFileToClient();

            }

            //запросить у сервака список файлов
            else if (readed == (byte) 1 || operationType == OperationType.SEND_SERVER_FILES) {
                System.out.println("Команда на предоставление списка файлов.");
                if(operationType == null) operationType = OperationType.SEND_SERVER_FILES;
                sendFileListToClient();

            }

            //запросить у сервака удаление файла
            else if(readed == (byte) 2 || operationType == OperationType.DELETE_FILE){
                System.out.println("Команда на предоставление списка файлов.");
                if(operationType == null) operationType = OperationType.DELETE_FILE;
                deleteFile();
            }

            //отключение клиента
            else if(readed == (byte) 0){
                System.out.println("Команда на отключение клиента.");
                ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(1);
                buffer.writeByte((byte) 0);
                socketChannel.writeAndFlush(buffer);
                socketChannel.close();
            }
            else {
                System.out.println("ERROR: Неверный командный байт: " + readed);
            }
        }
        if (buf.readableBytes() == 0) {
            System.out.println("Буфер полностью вычитан.");
            buf.release();
            System.out.println("Буфер обнулен.");
        }
    }



    private void receiveFileToServer() throws IOException {
        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_NAME_LENGTH;
            receivedFileLength = 0L;
            System.out.println("Старт загрузки файла на сервер.");
        }
        if (currentState == State.WAIT_NAME_LENGTH) {
            if(buf.readableBytes() < 4){
                while (buf.readableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                return;
            } else if(intBuffer.readableBytes() < 4 && intBuffer.readableBytes() > 0){
                while (intBuffer.writableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                if(intBuffer.readableBytes() < 4) return;
            }

            System.out.println("Получение длины имени файла.");

            fileNameLength = intBuffer.readableBytes() == 4 ? intBuffer.readInt() : buf.readInt();
            if(intBuffer.writableBytes() == 0) intBuffer.clear();
            System.out.println("Длина имени файла: " + fileNameLength);
            currentState = State.WAIT_NAME;

        }
        if (currentState == State.WAIT_NAME) {
            System.out.println("Получение имени файла.");
            if (buf.readableBytes() > 0) {
                byte[] fileName = new byte[fileNameLength];
                buf.readBytes(fileName);
                String fileNameStr = new String(fileName, StandardCharsets.UTF_8);
                System.out.println("Имя файла: " + fileNameStr);
                out = new BufferedOutputStream(new FileOutputStream("server_storage/" + fileNameStr));
                currentState = State.WAIT_FILE_LENGTH;
            }
        }


        if (currentState == State.WAIT_FILE_LENGTH) {
            if(buf.readableBytes() < 8){
                while (buf.readableBytes() > 0) {
                    longBuffer.writeByte(buf.readByte());
                }
                return;
            } else if(longBuffer.readableBytes() < 8 && longBuffer.readableBytes() > 0){
                while (longBuffer.writableBytes() > 0) {
                    longBuffer.writeByte(buf.readByte());
                }
                if(longBuffer.readableBytes() < 8) return;
            }

            System.out.println("Получение размера файла.");

            fileLength = longBuffer.readableBytes() == 8 ? longBuffer.readLong() : buf.readLong();
            if(longBuffer.writableBytes() == 0) longBuffer.clear();
            System.out.println("Размер файла: " + fileLength);
            currentState = State.WAIT_FILE;

        }


        if (currentState == State.WAIT_FILE) {
            System.out.println("Получение файла.");
            //byte [] buffer = new byte [256];
            while (buf.readableBytes() > 0) {
                //int rb1 = buf.readableBytes();
                //buf.readBytes(buffer);
                //int rb2 = buf.readableBytes();
                out.write(buf.readByte());
                //out.write(buffer);
                //System.out.println(".");
                receivedFileLength++;
                if (fileLength == receivedFileLength) {
                    currentState = State.WAIT_COMMAND;
                    operationType = null;
                    System.out.println("Файл передан.");
                    out.close();
                    break;
                }
            }
        }
    }


    private void sendFileToClient() throws IOException {
        //принять имя файла
        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_NAME_LENGTH;
            receivedFileLength = 0L;
            System.out.println("Старт передачи файла клиенту.");
        }
        if (currentState == State.WAIT_NAME_LENGTH) {
            if(buf.readableBytes() < 4){
                while (buf.readableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                return;
            } else if(intBuffer.readableBytes() < 4 && intBuffer.readableBytes() > 0){
                while (intBuffer.writableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                if(intBuffer.readableBytes() < 4) return;
            }

            System.out.println("Получение длины имени файла.");

            fileNameLength = intBuffer.readableBytes() == 4 ? intBuffer.readInt() : buf.readInt();
            if(intBuffer.writableBytes() == 0) intBuffer.clear();
            System.out.println("Длина имени файла: " + fileNameLength);
            currentState = State.WAIT_NAME;

        }
        //получить имя и создать Path по имени
        if (currentState == State.WAIT_NAME) {
            if (buf.readableBytes() > 0) {
                System.out.println("Получение имени файла.");
                byte[] fileName = new byte[fileNameLength];
                buf.readBytes(fileName);
                String fileNameStr = new String(fileName, StandardCharsets.UTF_8);
                System.out.println("Имя файла: " + fileNameStr);
                pathFile = Paths.get("server_storage",fileNameStr);

                currentState = State.FILE_TRANSFER;
            }
        }
        //создать буфер
        //записать в буфер командный байт, длину файла, файл
        if(currentState == State.FILE_TRANSFER) {
            FileRegion region = new DefaultFileRegion(pathFile.toFile(), 0, Files.size(pathFile));
            System.out.println(Files.size(pathFile));
            ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(1 + 8);
            buffer.writeByte((byte) 11);
            buffer.writeLong(Files.size(pathFile));
            System.out.println("Отправка командного сообщения.");
            socketChannel.writeAndFlush(buffer);
            System.out.println("Отправка файла.");
            System.out.println(region.count());
            ChannelFuture transferOperationFuture = socketChannel.writeAndFlush(region);

            currentState = State.WAIT_COMMAND;
            operationType = null;
            System.out.println("Файл отправлен.");
        }
    }


    private void sendFileListToClient(){
        //конвертируем список файлов в массив[][] байт
        String[] serverFiles = serverStorage.list();
        byte [][] serverFilesArrBytes = new byte[serverFiles.length][];
        for (int i = 0; i < serverFiles.length; i++) {
            serverFilesArrBytes[i] = serverFiles[i].getBytes(StandardCharsets.UTF_8);
        }

        currentState = State.FILE_TRANSFER;

        if(currentState == State.FILE_TRANSFER) {
            //подготовка буфера с командным байтом и длиной массива [][]
            System.out.println("Подготовка командного буфера.");
            ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(1 + 4);
            buffer.writeByte((byte) 1);
            buffer.writeInt(serverFiles.length);
            System.out.println("Отправка командного буфера.");
            socketChannel.writeAndFlush(buffer);

            for (int i = 0; i < serverFiles.length; i++){
                System.out.println("Подготовка буфера с размером и именем файла");
                buffer = ByteBufAllocator.DEFAULT.directBuffer(4 + serverFilesArrBytes[i].length);
                buffer.writeInt(serverFilesArrBytes[i].length);
                buffer.writeBytes(serverFilesArrBytes[i]);
                System.out.println("Отправка буфера с размером и именем файла");
                socketChannel.writeAndFlush(buffer);
            }

            currentState = State.WAIT_COMMAND;
            operationType = null;
            System.out.println("Список отправлен.");
        }
    }


    public void deleteFile(){
        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_NAME_LENGTH;
        }
        if (currentState == State.WAIT_NAME_LENGTH) {
            if(buf.readableBytes() < 4){
                while (buf.readableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                return;
            } else if(intBuffer.readableBytes() < 4 && intBuffer.readableBytes() > 0){
                while (intBuffer.writableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                if(intBuffer.readableBytes() < 4) return;
            }

            System.out.println("Получение длины имени файла.");

            fileNameLength = intBuffer.readableBytes() == 4 ? intBuffer.readInt() : buf.readInt();
            if(intBuffer.writableBytes() == 0) intBuffer.clear();
            System.out.println("Длина имени файла: " + fileNameLength);
            currentState = State.WAIT_NAME;

        }
        if (currentState == State.WAIT_NAME) {
            System.out.println("Получение имени файла.");
            if (buf.readableBytes() > 0) {
                byte[] fileName = new byte[fileNameLength];
                buf.readBytes(fileName);
                String fileNameStr = new String(fileName, StandardCharsets.UTF_8);
                System.out.println("Имя файла: " + fileNameStr);

                File file = new File(serverStorage.getName() + "/" + fileNameStr);
                file.delete();
                System.out.println("Файл " + fileNameStr + " удален.");

                ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(1);
                buffer.writeByte((byte) 2);
                socketChannel.writeAndFlush(buffer);

                currentState = State.WAIT_COMMAND;
                operationType = null;

            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

}



