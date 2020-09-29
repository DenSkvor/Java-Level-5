        import java.io.IOException;
        import java.io.RandomAccessFile;
        import java.net.InetSocketAddress;
        import java.nio.ByteBuffer;
        import java.nio.channels.*;
        import java.nio.charset.StandardCharsets;
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.nio.file.Paths;
        import java.util.Iterator;

public class Server {


    static int cnt = 1;

    public static void main(String[] args) throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(8189));

        server.configureBlocking(false);

        Selector selector = Selector.open();
        server.register(selector, SelectionKey.OP_ACCEPT);

        while (server.isOpen()) {
            selector.select();
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey current = iterator.next();
                if (current.isAcceptable()) {
                    handleAccept(current, selector);
                }
                if (current.isReadable()) {
                    handleRead(current, selector);
                }
                iterator.remove();
            }
        }
    }

    private static void handleRead(SelectionKey current, Selector selector) throws IOException {
        SocketChannel channel = (SocketChannel) current.channel();
        System.out.println("Message handled!");
        ByteBuffer buffer = ByteBuffer.allocate(256);
        StringBuilder s = new StringBuilder();
        int x;
                //получаем имя файла
                while((x = channel.read(buffer)) > 0) {
                    buffer.flip();
                    byte[] bytes = new byte[buffer.limit()];
                    buffer.get(bytes);
                    s.append(new String(bytes));
                    buffer.clear();
                }

                //создаем файл
                Path uploadDirectoryPath = Paths.get("user" + cnt + "_storage");
                Path uploadFilePath = Paths.get(uploadDirectoryPath.toString(), s.toString());
                if(!Files.exists(uploadDirectoryPath)) Files.createDirectory(uploadDirectoryPath);
                if(!Files.exists(uploadFilePath)) Files.createFile(uploadFilePath);
                s.setLength(0);

                //записываем файл
                RandomAccessFile raf = new RandomAccessFile(uploadFilePath.toString(), "rw");
                FileChannel fileChannel = raf.getChannel();
                while((x = channel.read(buffer)) > 0) {
                    buffer.flip();
                    fileChannel.write(buffer);
                    buffer.clear();
                }
                fileChannel.close();
                raf.close();


    }


    private static void handleAccept(SelectionKey current, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel)current.channel()).accept();
        channel.configureBlocking(false);
        System.out.println("Client accepted!");
        channel.register(selector, SelectionKey.OP_READ, "user" + (cnt++));
    }
}