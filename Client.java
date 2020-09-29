import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Client {

    public static void main(String[] args) throws IOException, InterruptedException {

        SocketChannel channel = SocketChannel.open();
        SocketAddress socketAddr = new InetSocketAddress("localhost", 8189);
        channel.connect(socketAddr);

        Path path = Paths.get(".idea","1","123.txt");
        RandomAccessFile raf = new RandomAccessFile(path.toString(),"rw");
        FileChannel fileChannel = raf.getChannel();
        ByteBuffer bBuffer = ByteBuffer.allocate(256);

        //отправка имени
        byte [] bb = path.getFileName().toString().getBytes();
        for (int i = 0; i < bb.length; i++) {
            bBuffer.put(bb[i]);
            if(bBuffer.position() == (bBuffer.capacity()-1) || (i == bb.length-1)){
                bBuffer.flip();
                channel.write(bBuffer);
                bBuffer.clear();
            }
        }
        //отправка файла
        int read = 0;
        while ((read = fileChannel.read(bBuffer)) > 0){
            bBuffer.flip();
            channel.write(bBuffer);
            bBuffer.clear();
        }

    }

}
