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
        WAIT_COMMAND, WAIT_NAME_LENGTH, WAIT_NAME, WAIT_FILE_LENGTH, WAIT_FILE, FILE_TRANSFER, WAIT_STORAGE_URL_LENGTH, WAIT_STORAGE_URL
    }

    private State currentState = State.WAIT_COMMAND;
    byte readed;
    int fileNameLength;
    long fileLength;
    private long receivedFileLength;
    BufferedOutputStream out;
    private ByteBuf intBuffer;
    private ByteBuf longBuffer;
    private String clientCloudStorage;

    public MainHandler(){
        intBuffer = ByteBufAllocator.DEFAULT.directBuffer(4);
        longBuffer = ByteBufAllocator.DEFAULT.directBuffer(8);
    }


    /*@Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Клиент " + socketChannel + " подключился.");
        intBuffer = ByteBufAllocator.DEFAULT.directBuffer(4);
        longBuffer = ByteBufAllocator.DEFAULT.directBuffer(8);
    }*/

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Клиент " + ctx.channel() + " отключился.");
        intBuffer.release();
        longBuffer.release();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = ((ByteBuf) msg);
        System.out.println("\nПоступило сообщение от клинта.");
        System.out.println("В байтбуфе байт: " + buf.readableBytes());
        //TODO добавить сюда пул на 1 поток и вынести методы на скачивание/передачу в отдельный класс
        while (buf.readableBytes() > 0) {
            System.out.println("Вычитываем байтбуф.");
            System.out.println("Текущий статус: " + currentState);

            if (currentState == State.WAIT_COMMAND) {
                System.out.println("Определение типа команды.");
                readed = buf.readByte();
                System.out.println("В байтбуфе осталось байт: " + buf.readableBytes());
            }

            //загрузить на сервер
            if (readed == (byte) 10) {
                System.out.println("Команда на загрузку файла с клиента на сервер.");
                receiveFileToServer(buf);
            }

            //скачать с сервера
            else if (readed == (byte) 11){
                System.out.println("Команда на скачивание файла с сервера.");
                sendFileToClient(buf, ctx);
            }

            //запросить у сервера список файлов
            else if (readed == (byte) 1) {
                System.out.println("Команда на предоставление списка файлов.");
                sendFileListToClient(ctx);
            }

            //запросить у сервера удаление файла
            else if(readed == (byte) 2){
                System.out.println("Команда на предоставление списка файлов.");
                deleteFile(buf, ctx);
            }

            //получение из хендлера авторизации адреса папки клиента в облаке
            else if(readed == (byte) 21){
                System.out.println("Команда на получение из хендлера авторизации адреса папки клиента в облаке.");
                receiveClientCloudStorageUrl(buf);
            }

            //отключение клиента
            else if(readed == (byte) 0){
                System.out.println("Команда на отключение клиента.");
                disconnectClient(ctx);
            }
            else {
                System.out.println("ERROR: Неверный командный байт: " + readed);
            }
        }
        if (buf.readableBytes() == 0) {
            System.out.println("Байтбуф полностью вычитан.");
            buf.release();
            System.out.println("Байтбуф обнулен.");
        }
    }



    private void receiveFileToServer(ByteBuf buf) throws IOException {

        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_NAME_LENGTH;
            receivedFileLength = 0L;
            System.out.println("Старт загрузки файла на сервер.");
        }
        if (currentState == State.WAIT_NAME_LENGTH) {
            if(buf.readableBytes() == 0) return;
            if(buf.readableBytes() < 4){
                while (buf.readableBytes() > 0 && intBuffer.writableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                if(intBuffer.readableBytes() < 4) return;
            } else if(intBuffer.readableBytes() > 0){
                while (intBuffer.writableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                //if(intBuffer.readableBytes() < 4) return;
            }

            System.out.println("Получение длины имени файла.");

            fileNameLength = intBuffer.readableBytes() == 4 ? intBuffer.readInt() : buf.readInt();
            if(intBuffer.writableBytes() == 0) intBuffer.clear();
            System.out.println("Длина имени файла: " + fileNameLength);
            currentState = State.WAIT_NAME;

        }
        if (currentState == State.WAIT_NAME) {
            System.out.println("Получение имени файла.");//TODO реализовать случай, когда в байтбуфе не всё имя (по аналогии с инт и лонг)
            if (buf.readableBytes() > 0) {
                byte[] fileName = new byte[fileNameLength];
                buf.readBytes(fileName);
                String fileNameStr = new String(fileName, StandardCharsets.UTF_8);
                System.out.println("Имя файла: " + fileNameStr);
                out = new BufferedOutputStream(new FileOutputStream(clientCloudStorage + "/" + fileNameStr));
                currentState = State.WAIT_FILE_LENGTH;
            }
        }

        if (currentState == State.WAIT_FILE_LENGTH) {
            if(buf.readableBytes() == 0) return;
            if(buf.readableBytes() < 8){
                while (buf.readableBytes() > 0 && longBuffer.writableBytes() > 0) {
                    longBuffer.writeByte(buf.readByte());
                }
                if(longBuffer.readableBytes() < 8) return;
            } else if(longBuffer.readableBytes() > 0){
                while (longBuffer.writableBytes() > 0) {
                    longBuffer.writeByte(buf.readByte());
                }
                //if(longBuffer.readableBytes() < 8) return;
            }

            System.out.println("Получение размера файла.");

            fileLength = longBuffer.readableBytes() == 8 ? longBuffer.readLong() : buf.readLong();
            if(longBuffer.writableBytes() == 0) longBuffer.clear();
            System.out.println("Размер файла: " + fileLength);
            currentState = State.WAIT_FILE;

        }

        if (currentState == State.WAIT_FILE) {
            System.out.println("Получение файла.");
            while (buf.readableBytes() > 0) {
                out.write(buf.readByte());
                //System.out.println(".");
                receivedFileLength++;
                if (fileLength == receivedFileLength) {
                    currentState = State.WAIT_COMMAND;
                    System.out.println("Файл передан.");
                    out.close();
                    break;
                }
            }
        }
    }


    private void sendFileToClient(ByteBuf buf, ChannelHandlerContext ctx) throws IOException {
        File file = null;
        //принять имя файла
        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_NAME_LENGTH;
            System.out.println("Старт передачи файла клиенту.");
        }
        if (currentState == State.WAIT_NAME_LENGTH) {
            if(buf.readableBytes() == 0) return;
            if(buf.readableBytes() < 4){
                while (buf.readableBytes() > 0 && intBuffer.writableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                if(intBuffer.readableBytes() < 4) return;
            } else if(intBuffer.readableBytes() > 0){
                while (intBuffer.writableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                //if(intBuffer.readableBytes() < 4) return;
            }

            System.out.println("Получение длины имени файла.");

            fileNameLength = intBuffer.readableBytes() == 4 ? intBuffer.readInt() : buf.readInt();
            if(intBuffer.writableBytes() == 0) intBuffer.clear();
            System.out.println("Длина имени файла: " + fileNameLength);
            currentState = State.WAIT_NAME;

        }
        //получить имя и создать File по имени
        if (currentState == State.WAIT_NAME) {
            if (buf.readableBytes() > 0) {
                System.out.println("Получение имени файла.");
                byte[] fileName = new byte[fileNameLength];
                buf.readBytes(fileName);
                String fileNameStr = new String(fileName, StandardCharsets.UTF_8);
                System.out.println("Имя файла: " + fileNameStr);
                file = new File(clientCloudStorage + "/" + fileNameStr);
                //pathFile = Paths.get(clientCloudStorage,fileNameStr);

                currentState = State.FILE_TRANSFER;
            }
        }
        //создать буфер
        //записать в буфер командный байт, длину файла, файл
        if(currentState == State.FILE_TRANSFER) {
            FileRegion region = new DefaultFileRegion(file, 0, file.length());
            System.out.println("Размер файла: " + file.length());
            ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(1 + 8);
            buffer.writeByte((byte) 11);
            buffer.writeLong(file.length());
            System.out.println("Отправка командного сообщения.");
            ctx.channel().writeAndFlush(buffer);
            System.out.println("Отправка файла.");
            System.out.println("Размер region: " + region.count());
            ChannelFuture transferOperationFuture = ctx.channel().writeAndFlush(region);

            currentState = State.WAIT_COMMAND;
            System.out.println("Файл отправлен.");
        }
    }


    private void sendFileListToClient(ChannelHandlerContext ctx){
        //конвертируем список файлов в массив[][] байт
        String[] serverFiles = new File(clientCloudStorage).list();
        System.out.println("Список файлов сервера: " + Arrays.toString(serverFiles));
        byte [][] serverFilesArrBytes = new byte[serverFiles.length][];
        for (int i = 0; i < serverFiles.length; i++) {
            serverFilesArrBytes[i] = serverFiles[i].getBytes(StandardCharsets.UTF_8);
        }

        //подготовка буфера с командным байтом и длиной массива [][]
        System.out.println("Подготовка командного буфера.");
        ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(1 + 4);
        buffer.writeByte((byte) 1);
        buffer.writeInt(serverFiles.length);
        System.out.println("Отправка командного буфера.");
        ctx.channel().writeAndFlush(buffer);

        for (int i = 0; i < serverFiles.length; i++){
            System.out.println("Подготовка буфера с размером и именем файла");
            buffer = ByteBufAllocator.DEFAULT.directBuffer(4 + serverFilesArrBytes[i].length);
            buffer.writeInt(serverFilesArrBytes[i].length);
            buffer.writeBytes(serverFilesArrBytes[i]);
            System.out.println("Отправка буфера с размером и именем файла");
            ctx.channel().writeAndFlush(buffer);
        }

        currentState = State.WAIT_COMMAND;
        System.out.println("Список отправлен.");

    }


    public void deleteFile(ByteBuf buf, ChannelHandlerContext ctx){

        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_NAME_LENGTH;
        }
        if (currentState == State.WAIT_NAME_LENGTH) {
            if(buf.readableBytes() == 0) return;
            if(buf.readableBytes() < 4){
                while (buf.readableBytes() > 0 && intBuffer.writableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                if(intBuffer.readableBytes() < 4) return;
            } else if(intBuffer.readableBytes() > 0){
                while (intBuffer.writableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                //if(intBuffer.readableBytes() < 4) return;
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

                File file = new File(clientCloudStorage + "/" + fileNameStr);
                file.delete();
                System.out.println("Файл " + fileNameStr + " удален.");

                ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(1);
                buffer.writeByte((byte) 2);
                ctx.channel().writeAndFlush(buffer);

                currentState = State.WAIT_COMMAND;

            }
        }
    }

    private void receiveClientCloudStorageUrl(ByteBuf buf){
        int urlLength = 0;
        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_STORAGE_URL_LENGTH;

            System.out.println("Старт получения адреса папки.");
        }
        if (currentState == State.WAIT_STORAGE_URL_LENGTH) {
            if(buf.readableBytes() == 0) return;
            if(buf.readableBytes() < 4){
                while (buf.readableBytes() > 0 && intBuffer.writableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                if(intBuffer.readableBytes() < 4) return;
            } else if(intBuffer.readableBytes() > 0){
                while (intBuffer.writableBytes() > 0) {
                    intBuffer.writeByte(buf.readByte());
                }
                //if(intBuffer.readableBytes() < 4) return;
            }

            System.out.println("Получение длины адреса.");

            urlLength = intBuffer.readableBytes() == 4 ? intBuffer.readInt() : buf.readInt();
            if(intBuffer.writableBytes() == 0) intBuffer.clear();
            System.out.println("Длина адреса: " + urlLength);
            currentState = State.WAIT_STORAGE_URL;

        }
        if (currentState == State.WAIT_STORAGE_URL) {
            System.out.println("Получение адреса.");
            if (buf.readableBytes() > 0) {
                byte[] urlBytes = new byte[urlLength];
                buf.readBytes(urlBytes);
                String urlStr = new String(urlBytes, StandardCharsets.UTF_8);
                System.out.println("Адрес: " + urlStr);
                clientCloudStorage = urlStr;

                currentState = State.WAIT_COMMAND;
            }
        }
    }

    private void disconnectClient(ChannelHandlerContext ctx){
        ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(1);
        buffer.writeByte((byte) 0);
        ctx.channel().writeAndFlush(buffer);
        ctx.channel().close();
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

}



