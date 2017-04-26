package client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Created by jhooba on 2017-04-24.
 */
public class Client {
  public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
    new Client().go();
  }

  private void go() throws IOException, ExecutionException, InterruptedException {
    AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
    Future future = socketChannel.connect(new InetSocketAddress("localhost", 3883));
    future.get();

    System.out.println("Client is started: " + socketChannel.isOpen());

    String[] messages = new String[] {"Time goes fast.", "What now?", "Meow", "Bye"};
    ByteBuffer inputBuffer = ByteBuffer.allocate(2048);
    for (String message : messages) {
      ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
      Future result = socketChannel.write(buffer);
      while (true) {
        if (result.isDone()) break;
      }
      System.out.print("Send: ");
      System.out.println(message);
      buffer.clear();

      result = socketChannel.read(inputBuffer);
      while (true) {
        if (result.isDone()) break;
      }
      byte[] buf = new byte[inputBuffer.position()];
      inputBuffer.rewind();
      inputBuffer.get(buf);
      System.out.print("Receive: ");
      System.out.println(new String(buf));
      inputBuffer.clear();

      Thread.sleep(3000);
    }
    socketChannel.close();
  }
}
