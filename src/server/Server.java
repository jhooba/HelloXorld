package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by jhooba on 2017-04-24.
 */
public class Server {
  private final ExecutorService threadGroup;
  private final AsynchronousServerSocketChannel serverChannel;
  private Thread currentThread;

  public static void main(String[] args) throws IOException {
    new Server().go();
  }

  private Server() throws IOException {
    threadGroup = Executors.newFixedThreadPool(2);
    AsynchronousChannelGroup channelGroup = AsynchronousChannelGroup.withThreadPool(threadGroup);
    serverChannel = AsynchronousServerSocketChannel.open(channelGroup);
  }

  private void go() throws IOException {
    currentThread = Thread.currentThread();

    InetSocketAddress hostAddress = new InetSocketAddress(3883);
    serverChannel.bind(hostAddress);

    System.out.println("Server channel bound to port: " + hostAddress.getPort());
    System.out.println("Waiting for client to connect...");

    String attachment1 = "First Connection";
    serverChannel.accept(attachment1, new AcceptCompletionHandler());
    try {
      currentThread.join();
    } catch (InterruptedException ignored) {
    }
    System.out.println("Exiting the server");
  }

  private class AcceptCompletionHandler implements CompletionHandler<AsynchronousSocketChannel, String> {
    @Override
    public void completed(AsynchronousSocketChannel result, String attachment) {
      System.out.println("Completed: " + attachment);
      // accept the next connection
      attachment = "Next Connection";
      System.out.println("Waiting for - " + attachment);
      serverChannel.accept(attachment, this);

      // handle this connection
      ByteBuffer inputBuffer = ByteBuffer.allocate(2048);
      result.read(inputBuffer, attachment, new ReadCompletionHandler(inputBuffer, result));
    }

    @Override
    public void failed(Throwable exc, String attachment) {
      System.err.println(attachment + " - accept failed");
      exc.printStackTrace();
      currentThread.interrupt();
    }
  }

  private class ReadCompletionHandler implements CompletionHandler<Integer, String> {
    private final ByteBuffer inputBuffer;
    private final AsynchronousSocketChannel channel;

    ReadCompletionHandler(ByteBuffer inputBuffer, AsynchronousSocketChannel channel) {
      this.inputBuffer = inputBuffer;
      this.channel = channel;
    }

    @Override
    public void completed(Integer result, String attachment) {
      byte[] buffer = new byte[result];
      inputBuffer.rewind();  // Rewinds the input buffer to read from the beginning
      inputBuffer.get(buffer);
      String message = new String(buffer);
      System.out.println("Received message from client: " + message);

      if (message.equals("Bye")) {
        // Echo the message back to client
        ByteBuffer outputBuffer = ByteBuffer.wrap(buffer);
        channel.write(outputBuffer);

        if (!threadGroup.isTerminated()) {
          System.out.println("Terminating the group...");
          threadGroup.shutdown();
          try {
            threadGroup.awaitTermination(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            System.err.println("Exception during group termination");
            e.printStackTrace();
          }
          currentThread.interrupt();
        }
      } else {
        // Echo the message back to client
        ByteBuffer outputBuffer = ByteBuffer.wrap(buffer);
        channel.write(outputBuffer);
      }

      ByteBuffer inputBuffer = ByteBuffer.allocate(2048);
      channel.read(inputBuffer, attachment, new ReadCompletionHandler(inputBuffer, channel));
    }

    @Override
    public void failed(Throwable exc, String attachment) {
      System.err.println(attachment + " - read failed");
      exc.printStackTrace();
      currentThread.interrupt();
    }
  }
}
