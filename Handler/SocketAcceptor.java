package Handler;

import Process.Peer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Blocks on {@link ServerSocket#accept()} and forks a passive {@link LinkSession} per client.
 */
public final class SocketAcceptor implements Runnable {

    private final ServerSocket listener;
    private final String ownerId;

    public SocketAcceptor(ServerSocket serverSocket, String peerId) {
        this.listener = serverSocket;
        this.ownerId = peerId;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Socket client = listener.accept();
                System.out.println("Peer " + ownerId + " accepted connection from " + client.getRemoteSocketAddress());
                Thread t = new Thread(new LinkSession(ownerId, 0, client));
                t.start();
                Peer.peerToSocketMap.putIfAbsent(client.getInetAddress().getHostAddress(), client);
            } catch (IOException e) {
                break;
            }
        }
    }
}
