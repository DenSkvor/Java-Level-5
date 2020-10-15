package ru.geekbrains.skvortsov.cloud_storage;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import java.nio.charset.StandardCharsets;


public class AuthHandler extends ChannelInboundHandlerAdapter {

    public enum State {
        WAIT_COMMAND, WAIT_LOGIN_LENGTH, WAIT_LOGIN, WAIT_PASSWORD_LENGTH, WAIT_PASSWORD, CHECK_AUTH_DATA
    }

    private State currentState = State.WAIT_COMMAND;
    private ByteBuf intBuffer;
    private byte readed;
    private int loginLength;
    private int passwordLength;
    private String loginStr;
    private String passwordStr;


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Клиент " + ctx.channel() + " подключился.");
        intBuffer = ByteBufAllocator.DEFAULT.directBuffer(4);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("Клиент " + ctx.channel() + " отключился.");
        intBuffer.release();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        ByteBuf buf = ((ByteBuf) msg);
        System.out.println("\nПоступило сообщение от клинта.");
        System.out.println("В байтбуфе байт: " + buf.readableBytes());

        while (buf.readableBytes() > 0) {
            System.out.println("Вычитываем байтбуф.");
            System.out.println("Текущий статус: " + currentState);

            if (currentState == State.WAIT_COMMAND) {
                System.out.println("Определение типа команды.");
                readed = buf.readByte();
                System.out.println("В байтбуфе осталось байт: " + buf.readableBytes());
            }

            //запрос на авторизацию
            if (readed == (byte) 20) {
                System.out.println("Команда на авторизацию.");
                authRequest(buf, ctx);
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


    private void authRequest(ByteBuf buf, ChannelHandlerContext ctx) {

        //получение длины логина
        if(currentState == State.WAIT_COMMAND) {
            currentState = State.WAIT_LOGIN_LENGTH;
            System.out.println("Старт получения логина/пароля клиента.");
            System.out.println("В байтбуфе байт: " + buf.readableBytes());
        }

        if (currentState == State.WAIT_LOGIN_LENGTH) {
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

            System.out.println("Получение длины логина.");
            System.out.println("В интбуфе байт: " + intBuffer.readableBytes());
            System.out.println("В байтбуфе байт: " + buf.readableBytes());
            loginLength = intBuffer.readableBytes() == 4 ? intBuffer.readInt() : buf.readInt();
            if(intBuffer.writableBytes() == 0) intBuffer.clear();
            System.out.println("Длина логина: " + loginLength);
            currentState = State.WAIT_LOGIN;
        }

        //получение логина
        if (currentState == State.WAIT_LOGIN) {
            if (buf.readableBytes() > 0) {
                System.out.println("Получение логина.");
                byte[] loginBytes = new byte[loginLength];
                buf.readBytes(loginBytes);
                loginStr = new String(loginBytes, StandardCharsets.UTF_8);
                System.out.println("Логин: " + loginStr);
                currentState = State.WAIT_PASSWORD_LENGTH;
            }
        }

        //получение длины пароля
        if(currentState == State.WAIT_PASSWORD_LENGTH) {
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

            System.out.println("Получение длины пароля.");
            passwordLength = intBuffer.readableBytes() == 4 ? intBuffer.readInt() : buf.readInt();
            if(intBuffer.writableBytes() == 0) intBuffer.clear();
            System.out.println("Длина пароля: " + passwordLength);
            currentState = State.WAIT_PASSWORD;
        }

        //получение пароля
        if (currentState == State.WAIT_PASSWORD) {
            if (buf.readableBytes() > 0) {
                System.out.println("Получение пароля.");
                byte[] passwordBytes = new byte[passwordLength];
                buf.readBytes(passwordBytes);
                passwordStr = new String(passwordBytes, StandardCharsets.UTF_8);
                System.out.println("Пароль: " + passwordStr);
                currentState = State.CHECK_AUTH_DATA;
            }
        }

        //проверка полученных данных на наличие в базе и получение адреса папки клиента
        System.out.println("Проверка полученных данных на наличие в базе и получения адреса папки клиента");
        if(currentState == State.CHECK_AUTH_DATA){
            String storageUrl = AuthService.getInstance().getStorageByLoginAndPass(loginStr,passwordStr);
            System.out.println("Адрес: " + storageUrl);
            //если данные не найдены, отсылаем командный байт об ошибке
            if(storageUrl == null){
                ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(1);
                buffer.writeByte((byte) 22);
                ctx.channel().writeAndFlush(buffer);
                System.out.println("Данные не найдены.");
            //если данные найдены, отсылаем командный байт о передаче адреса папки клиента в облаке и передаем сам адрес в виде строки в следующий хендлер
            } else {
                System.out.println("Данные найдены.");
                ByteBuf buffer = ByteBufAllocator.DEFAULT.directBuffer(1);
                buffer.writeByte((byte) 21);
                ctx.channel().writeAndFlush(buffer);

                byte[] storageUrlBytes = storageUrl.getBytes(StandardCharsets.UTF_8);
                ByteBuf buffer2 = ByteBufAllocator.DEFAULT.directBuffer(1 + 4 + storageUrlBytes.length);
                buffer2.writeByte((byte) 21);
                buffer2.writeInt(storageUrlBytes.length);
                buffer2.writeBytes(storageUrlBytes);
                ctx.fireChannelRead(buffer2);

                ctx.channel().pipeline().remove(this);
            }
        }
        currentState = State.WAIT_COMMAND;
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
