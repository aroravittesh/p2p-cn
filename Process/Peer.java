package Process;

import Configs.RuntimeConfig;
import Handler.FrameDispatcher;
import Handler.LinkSession;
import Handler.SocketAcceptor;
import Logging.PeerLogger;
import Msgs.BitField;
import Metadata.NeighborProfile;
import Tasks.OptimisticallyUnchoke;
import Tasks.PreferredNeighbors;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

import static Logging.PeerLogger.writeLog;

/**
 * Course peer process: wires config, sockets, timers, and shutdown sequencing.
 */
public class Peer {

    private static final ExecutorService fileServerExecutor = Executors.newSingleThreadExecutor();
    private static final ExecutorService messageProcessor = Executors.newSingleThreadExecutor();
    private static final ExecutorService receivingExecutor = Executors.newCachedThreadPool();
    private static ServerSocket fileServingSocket;

    public static String peerID;
    public static String peerFolder;
    private static int peerIndex;
    private static int peerPort;
    public static boolean isFirstPeer;
    private static boolean hasFile;

    public static BitField bitFieldMessage;

    public static final Map<String, NeighborProfile> remotePeerDetails = new ConcurrentHashMap<>();
    public static final Map<String, NeighborProfile> preferredNeighbours = new ConcurrentHashMap<>();
    public static final Map<String, NeighborProfile> optimisticUnchoked = new ConcurrentHashMap<>();
    public static final Map<String, Socket> peerToSocketMap = new ConcurrentHashMap<>();

    private static Timer preferredNeighboursTimer;
    private static Timer optimisticUnchokeTimer;

    public static void main(String[] args) {

        peerID = args[0];
        peerFolder = "peer_" + peerID;
        System.setProperty("peer.id", peerID);

        try {
            new PeerLogger().setupLogger(peerID);
            writeLog("Peer " + peerID + " started");

            System.out.println("\nReading configurations...");
            ingestCommonAndPeerTables();
            hydrateLocalIdentity();
            System.out.println("\nPeer " + peerID + " listening on port " + peerPort + "\n");
            primePieceBitmap();
            spinDispatcher();
            openTransport();
            armChokeTimers();
            blockUntilEveryoneDone();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            writeLog("Peer " + peerID + " shutting down.");
            System.exit(0);
        }
    }

    private static void ingestCommonAndPeerTables() throws IOException {
        Files.lines(Paths.get("Configs/Common.cfg")).forEach(line -> {
            String[] p = line.trim().split("\\s+");
            switch (p[0]) {
                case "NumberOfPreferredNeighbors":
                    RuntimeConfig.preferredNeighbourCount = Integer.parseInt(p[1]);
                    break;
                case "UnchokingInterval":
                    RuntimeConfig.unchokingInterval = Integer.parseInt(p[1]);
                    break;
                case "OptimisticUnchokingInterval":
                    RuntimeConfig.optimisticUnchokingInterval = Integer.parseInt(p[1]);
                    break;
                case "FileSize":
                    RuntimeConfig.fileSize = Integer.parseInt(p[1]);
                    break;
                case "FileName":
                    RuntimeConfig.fileName = p[1];
                    break;
                case "PieceSize":
                    RuntimeConfig.pieceSize = Integer.parseInt(p[1]);
                    break;
                default:
                    break;
            }
        });

        PeerLogger.tag("CONFIG", String.format(
                "Loaded Common.cfg → NP=%d, p=%d, m=%d, file=%s, size=%d, piece=%d",
                RuntimeConfig.preferredNeighbourCount,
                RuntimeConfig.unchokingInterval,
                RuntimeConfig.optimisticUnchokingInterval,
                RuntimeConfig.fileName,
                RuntimeConfig.fileSize,
                RuntimeConfig.pieceSize));

        List<String> peerLines = Files.readAllLines(Paths.get("Configs/PeerInfo.cfg"));
        int idx = 0;
        for (String line : peerLines) {
            String[] t = line.trim().split("\\s+");
            remotePeerDetails.put(
                    t[0],
                    new NeighborProfile(t[0], t[1], t[2], Integer.parseInt(t[3]), idx++));
        }

        PeerLogger.tag("CONFIG", "Loaded " + peerLines.size() + " entries from PeerInfo.cfg");
    }

    private static void hydrateLocalIdentity() {
        NeighborProfile me = remotePeerDetails.get(peerID);
        peerPort = Integer.parseInt(me.getPortNumber());
        peerIndex = me.getPeerIndex();
        /* PeerInfo.cfg last column: 1 = already has the full file (seeder). */
        isFirstPeer = me.hasFile();
        hasFile = isFirstPeer;
    }

    private static void primePieceBitmap() {
        bitFieldMessage = new BitField();
        bitFieldMessage.initializePieces(peerID, hasFile);
    }

    private static void spinDispatcher() {
        messageProcessor.execute(new FrameDispatcher(peerID));
    }

    private static void openTransport() throws IOException {
        fileServingSocket = new ServerSocket(peerPort);
        fileServerExecutor.execute(new SocketAcceptor(fileServingSocket, peerID));

        if (!isFirstPeer) {
            materializeSparseTarget();
            for (NeighborProfile row : remotePeerDetails.values()) {
                if (peerIndex > row.getPeerIndex()) {
                    System.out.println("Peer " + peerID + " attempting connection to " + row.getPeerId());
                    receivingExecutor.execute(new LinkSession(
                            peerID,
                            1,
                            row.getIpAddress(),
                            Integer.parseInt(row.getPortNumber())));
                }
            }
        }
    }

    private static void materializeSparseTarget() throws IOException {
        File dir = new File(peerFolder);
        if (dir.mkdirs()) {
            try (OutputStream os =
                         new FileOutputStream(new File(dir, RuntimeConfig.fileName))) {
                for (int i = 0; i < RuntimeConfig.fileSize; i++) {
                    os.write(0);
                }
            }
            System.out.println("Peer " + peerID + " created placeholder file " + RuntimeConfig.fileName);
        }
    }

    private static void armChokeTimers() {
        preferredNeighboursTimer = new Timer(true);
        preferredNeighboursTimer.schedule(
                new PreferredNeighbors(), 0, RuntimeConfig.unchokingInterval * 1000L);

        optimisticUnchokeTimer = new Timer(true);
        optimisticUnchokeTimer.schedule(
                new OptimisticallyUnchoke(), 0, RuntimeConfig.optimisticUnchokingInterval * 1000L);
    }

    private static void blockUntilEveryoneDone() throws InterruptedException {
        while (!everyRowClaimsDone()) {
            Thread.sleep(2000);
        }

        writeLog("All peers have finished downloading.");
        System.out.println("\nPeer " + peerID + ": all peers finished — shutting down workers.\n");
        preferredNeighboursTimer.cancel();
        optimisticUnchokeTimer.cancel();
        messageProcessor.shutdownNow();
        receivingExecutor.shutdownNow();
        fileServerExecutor.shutdownNow();
    }

    private static boolean everyRowClaimsDone() {
        try {
            for (String line : Files.readAllLines(Paths.get("Configs/PeerInfo.cfg"))) {
                if (line.trim().endsWith("0")) return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized static void updateOtherPeerMetadata() {
        try {
            for (String l : Files.readAllLines(Paths.get("Configs/PeerInfo.cfg"))) {
                String[] t = l.trim().split("\\s+");
                NeighborProfile pm = remotePeerDetails.get(t[0]);
                if ("1".equals(t[3])) {
                    pm.setInterested(false);
                    pm.setCompletedFile(true);
                    pm.setChoked(false);
                }
            }
        } catch (IOException ignored) {}
    }
}
