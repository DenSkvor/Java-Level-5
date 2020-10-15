package ru.geekbrains.skvortsov.cloud_storage;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.sql.SQLException;

public class Server {

    public void startServer() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try{
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline().addLast(new AuthHandler()).addLast(new MainHandler());
                        }
                    });
            ChannelFuture chf = sb.bind(8189).sync();
            System.out.println("Сервер подключен.");

            AuthService.getInstance().start();
            //System.out.println("Сервер авторизации подключен.");

            chf.channel().closeFuture().sync();
        } catch (InterruptedException | ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }finally {
            AuthService.getInstance().stop();
            //System.out.println("Сервер авторизации отключен.");

            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            System.out.println("Сервер отключен.");
        }

    }

    public static void main(String[] args) throws InterruptedException {
        new Server().startServer();
    }
}
