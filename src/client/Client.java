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
    System.out.print("Sending message to server: ");

    String[] messages = new String[] {"Time goes fast.", "What now?", "Bye"};
    for (String message : messages) {
      ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
      Future result = socketChannel.write(buffer);
      while (!result.isDone()) {
        System.out.println("... ");
      }
      System.out.println(message);
      buffer.clear();
      Thread.sleep(3000);
    }
    socketChannel.close();
  }
}
