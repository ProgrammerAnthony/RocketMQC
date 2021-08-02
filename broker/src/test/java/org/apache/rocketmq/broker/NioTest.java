package org.apache.rocketmq.broker;

import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

/**
 * <p>Description: </p>
 *
 * @author Anthony
 * @date 2021-08-02 21:11
 */
public class NioTest {

    @Test
    public void testWrite() throws IOException {
        FileOutputStream fos = new FileOutputStream("testNIO.txt");
        FileChannel channel = fos.getChannel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        for (int i = 0; i < 10; i++) {
            byteBuffer.put((byte) i);
            System.out.println("write : " + (byte) i);
        }
        byteBuffer.flip();
        channel.write(byteBuffer);

        fos.close();

    }

    @Test
    public void testRead() throws IOException {
        FileInputStream fis = new FileInputStream("testNIO.txt");
        FileChannel fileChannel = fis.getChannel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        while (true) {
            int n = fileChannel.read(byteBuffer);
            System.out.println("read..." + n);
            if (n == -1) {
                break;
            }

        }
        byteBuffer.flip();

        while (byteBuffer.hasRemaining()) {
            System.out.println("read : " + byteBuffer.get());
        }

        fis.close();
    }


    @Test
    public void testCopyFile() throws IOException {
        FileInputStream fis = new FileInputStream("testNIO.txt");
        FileOutputStream fos = new FileOutputStream("testNIOCopy.txt");
        FileChannel inChannel = fis.getChannel();
        FileChannel outChannel = fos.getChannel();
        // 此时position=0,limit=capacity
        ByteBuffer byteBuffer = ByteBuffer.allocate(3);
        while (true) {
            // read前，position=0，limit=3, 读到buffer里面去
            int n = inChannel.read(byteBuffer);
            // read后，position=3，limit=3
            System.out.println("n = " + n);
            if (n == -1) {
                break;
            }
            // flip后，position=0，limit=3，经过3次之后，position=0,limit=1,因为这次只读1个元素
            byteBuffer.flip();
            outChannel.write(byteBuffer);
            // clear()把position=0, limit=3;方便下一次读取使用buffer
            byteBuffer.clear();
        }

        // 除了用上面的方式外，还有一个更快捷的方法：transferTo,transferFrom
        inChannel.transferTo(0, inChannel.size(), outChannel);
        // 或者是
        outChannel.transferFrom(inChannel, 0, inChannel.size());

    }


    // 分片,与原来的buffer共享数据
    @Test
    private void testslice() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(6);
        for (int i = 1; i <= 6; i++) {
            byteBuffer.put((byte) i);
        }

        byteBuffer.clear();
        while (byteBuffer.hasRemaining()) {
            System.out.println(byteBuffer.get());
        }

        System.out.println("----------");

        // 取数组的第3个和第4个元素，limit指向的是最后一个元素的下一个
        byteBuffer.position(2);
        byteBuffer.limit(4);
        ByteBuffer slice = byteBuffer.slice();
        for (int i = 0; i < slice.capacity(); i++) {
            // 这里的get是绝对的，不影响position的值
            slice.put((byte) (slice.get(i) * 10));
        }

        byteBuffer.clear();
        while (byteBuffer.hasRemaining()) {
            System.out.println(byteBuffer.get());
        }

    }


    public class SocketNIOServer {
        public void main(String[] args) throws IOException {
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.configureBlocking(false);
            ssc.socket().bind(new InetSocketAddress(9999));
            Selector selector = Selector.open();
            ssc.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                // 如果没有请求过来会在这里阻塞
                int n = selector.select();
                if (0 == n) {
                    continue;
                }

                Iterator iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = (SelectionKey) iterator.next();

                    // 如果处于accept状态，则可以连接了
                    if (selectionKey.isAcceptable()) {
                        // 获取Socket连接的通道
                        SocketChannel socketChannel = ((ServerSocketChannel) selectionKey.channel()).accept();
                        System.out.println("request coming...");
                        socketChannel.configureBlocking(false);
                        // 建立连接后，就可以开始读socket发过来的消息了
                        socketChannel.register(selector, SelectionKey.OP_READ);
                    }

                    // 如果处于可读取数据状态-接受客户端数据
                    else if (selectionKey.isReadable()) {
                        System.out.println("reading data...");
                        // 读数据
                        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                        socketChannel.configureBlocking(false);
                        while (true) {
                            // 当客户端断开后才会返回-1，否则没有读到数据时返回0
                            int s = socketChannel.read(byteBuffer);
                            System.out.println("s=" + s);
                            if (s == 0 || s == -1) {
                                break;
                            }
                            byteBuffer.flip();
                            while (byteBuffer.hasRemaining()) {
                                System.out.print((char) byteBuffer.get());
                            }
                            System.out.println("\n--------");
                            byteBuffer.clear();
                        }
                        //socketChannel.close();

                        // 读完就给客户端回消息
                        socketChannel.register(selector, SelectionKey.OP_WRITE);

                    }

                    // 如果处于可以可写入数据状态--发送数据给客户端
                    else if (selectionKey.isWritable()) {
                        System.out.println("writing data...");
                        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                        socketChannel.configureBlocking(false);
                        ByteBuffer byteBuffer = ByteBuffer.wrap("I'm server, I send u some messages.".getBytes());
                        socketChannel.write(byteBuffer);
                        socketChannel.close();
                    }

                    // 删除已使用过的selectionKey
                    iterator.remove();
                }
            }
        }
    }

    public class SocketNIOClient {
        public void main(String[] args) throws IOException {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress("127.0.0.1", 9999));

            Selector selector = Selector.open();
            socketChannel.register(selector, SelectionKey.OP_CONNECT);

            while (true) {
                int n = selector.select();
                if (0 == n) {
                    continue;
                }

                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();

                    // 连接服务端
                    if (selectionKey.isConnectable()) {
                        System.out.println("client connect...");
                        SocketChannel socketChannel1 = (SocketChannel) selectionKey.channel();

                        // 如果正在连接，则完成连接
                        if (socketChannel1.isConnectionPending()) {
                            socketChannel1.finishConnect();
                        }
                        socketChannel1.configureBlocking(false);
                        // 发送数据到服务端
                        ByteBuffer byteBuffer = ByteBuffer.wrap("I'm Client. I send u".getBytes());
                        socketChannel1.write(byteBuffer);

                        // 接下来是监听服务端的回应了
                        socketChannel1.register(selector, SelectionKey.OP_READ);

                    }

                    // 接受服务器的回应
                    else if (selectionKey.isReadable()) {
                        System.out.println("client reading...");
                        SocketChannel socketChannel1 = (SocketChannel) selectionKey.channel();
                        socketChannel1.configureBlocking(false);

                        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

                        while (true) {
                            int s = socketChannel1.read(byteBuffer);
                            System.out.println("s=" + s);
                            if (s == 0 || s == -1) {
                                break;
                            }
                            byteBuffer.flip();
                            while (byteBuffer.hasRemaining()) {
                                System.out.print((char) byteBuffer.get());
                            }
                            System.out.println("\n******");
                            byteBuffer.clear();
                        }
                        socketChannel1.close();
                    }
                    iterator.remove();
                }
            }
        }

    }


}
