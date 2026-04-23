package Tasks;

import Msgs.Constants;
import Msgs.Msg;
import Metadata.NeighborProfile;
import Process.Peer;

import static Logging.PeerLogger.writeLog;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.*;

/**
 * TimerTask that, every {@code RuntimeConfig.optimisticUnchokingInterval},
 * selects one random choked & interested neighbour to un-choke.
 */
public class OptimisticallyUnchoke extends TimerTask {

    @Override
    public void run() {
        /* refresh PeerInfo.cfg flags on every tick — keeps memory in sync */
        Peer.updateOtherPeerMetadata();

        /* clear previous optimistic choice */
        Peer.optimisticUnchoked.clear();

        /* gather all neighbours that are: choked, interested, and incomplete */
        List<NeighborProfile> candidates = new ArrayList<>();
        for (Map.Entry<String, NeighborProfile> e : Peer.remotePeerDetails.entrySet()) {
            if (!e.getKey().equals(Peer.peerID) && isPeerInterested(e.getValue())) {
                candidates.add(e.getValue());
            }
        }

        if (candidates.isEmpty()) return;

        /* pick one peer at random */
        Collections.shuffle(candidates);
        NeighborProfile chosen = candidates.get(0);
        chosen.setOptimisticallyUnchoked(true);
        Peer.optimisticUnchoked.put(chosen.getPeerId(), chosen);

        writeLog(Peer.peerID + " selected optimistic neighbour " + chosen.getPeerId());

        /* if it is currently choked, un-choke and send HAVE */
        if (chosen.isChoked()) {
            chosen.setChoked(false);
            sendUnchoke(Peer.peerToSocketMap.get(chosen.getPeerId()), chosen.getPeerId());
            sendHave  (Peer.peerToSocketMap.get(chosen.getPeerId()), chosen.getPeerId());
            chosen.setPeerState(3);           // “awaiting interest” state
        }
    }

    /* ---------- helper predicates ---------- */

    private boolean isPeerInterested(NeighborProfile pm) {
        return !pm.hasCompletedFile() && pm.isChoked() && pm.isInterested();
    }

    /* ---------- message send helpers ---------- */

    private void sendUnchoke(Socket socket, String remoteId) {
        writeLog(Peer.peerID + " → UNCHOKE → " + remoteId);
        try {
            byte[] out = Msg.serializeMessage(new Msg(Constants.UNCHOKE));
            writeToSocket(socket, out);
        } catch (Exception ignored) {}
    }

    private void sendHave(Socket socket, String remoteId) {
        writeLog(Peer.peerID + " → HAVE → " + remoteId);
        try {
            byte[] payload = Peer.bitFieldMessage.encodeBitField();
            byte[] out = Msg.serializeMessage(new Msg(Constants.HAVE, payload));
            writeToSocket(socket, out);
        } catch (Exception ignored) {}
    }

    private void writeToSocket(Socket socket, byte[] data) {
        if (socket == null) return;
        try (OutputStream out = socket.getOutputStream()) {
            out.write(data);
        } catch (IOException ignored) {}
    }
}
