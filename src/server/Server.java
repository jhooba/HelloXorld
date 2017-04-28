package server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by jhooba on 2017-04-24.
 */
public class Server implements Runnable {
  private static final int THREAD_COUNT = 2;

  private final AsynchronousChannelGroup channelGroup;
  private final AsynchronousServerSocketChannel serverChannel;
  private Thread currentThread;

  public static void main(String[] args) throws IOException {
    new Server().go();
  }

  private Server() throws IOException {
    channelGroup = AsynchronousChannelGroup.withFixedThreadPool(THREAD_COUNT, Executors.defaultThreadFactory());
    serverChannel = AsynchronousServerSocketChannel.open(channelGroup);
  }

  private void go() throws IOException {
    currentThread = Thread.currentThread();

    InetSocketAddress hostAddress = new InetSocketAddress(3883);
    serverChannel.bind(hostAddress);

    System.out.println("Server channel bound to port: " + hostAddress.getPort());
    System.out.println("Waiting for client to connect...");

    serverChannel.accept(null, new AcceptCompletionHandler());
    ((Executor)channelGroup).execute(this);
    try {
      currentThread.join();
    } catch (InterruptedException ignored) {
    }
    System.out.println("Exiting the server");
  }

  @Override
  public void run() {
    System.out.println("...");
    if (!channelGroup.isShutdown()) {
      ((Executor)channelGroup).execute(this);
    }
  }

  private class AcceptCompletionHandler implements CompletionHandler<AsynchronousSocketChannel, Object> {
    @Override
    public void completed(AsynchronousSocketChannel result, Object o) {
      String remoteIp = null;
      try {
        remoteIp = result.getRemoteAddress().toString();
      } catch (IOException ignored) {
      }
      System.out.println("Accepted: " + remoteIp);
      // accept the next connection
      System.out.println("Waiting for Next Connection");
      serverChannel.accept(o, this);

      // handle this connection
      ByteBuffer inputBuffer = ByteBuffer.allocate(2048);
      result.read(inputBuffer, inputBuffer, new ReadCompletionHandler(result));
    }

    @Override
    public void failed(Throwable exc, Object o) {
      System.err.println("Accept failed");
//      exc.printStackTrace();
      currentThread.interrupt();
    }
  }

  private class ReadCompletionHandler implements CompletionHandler<Integer, ByteBuffer> {
    private final AsynchronousSocketChannel channel;

    ReadCompletionHandler(AsynchronousSocketChannel channel) {
      this.channel = channel;
    }

    @Override
    public void completed(Integer result, ByteBuffer inputBuffer) {
      if (result < 0) {
        return;
      }
      byte[] buffer = new byte[result];
      inputBuffer.rewind();  // Rewinds the input buffer to read from the beginning
      inputBuffer.get(buffer);
      String message = new String(buffer);
      System.out.println("Received message from client: " + message);

      if (message.equals("Bye")) {
        // Echo the message back to client
        ByteBuffer outputBuffer = ByteBuffer.wrap(buffer);
        channel.write(outputBuffer);

        if (!channelGroup.isTerminated()) {
          System.out.println("Terminating the group...");
          try {
            channelGroup.shutdownNow();
            channelGroup.awaitTermination(10, TimeUnit.SECONDS);
          } catch (IOException | InterruptedException e) {
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
      inputBuffer.clear();
      channel.read(inputBuffer, inputBuffer, new ReadCompletionHandler(channel));
    }

    @Override
    public void failed(Throwable exc, ByteBuffer inputBuffer) {
      System.err.println("Read failed");
//      exc.printStackTrace();
      currentThread.interrupt();
    }
  }
}
