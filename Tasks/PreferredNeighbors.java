package Tasks;

import Configs.RuntimeConfig;
import Msgs.Constants;
import Msgs.Msg;
import Metadata.NeighborProfile;
import Process.Peer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.*;

import static Logging.PeerLogger.writeLog;

/**
 * Runs every {@code RuntimeConfig.unchokingInterval} seconds to refresh the
 * current peer’s list of preferred neighbours.
 */
public class PreferredNeighbors extends TimerTask {

    @Override
    public void run() {
        /* sync in-memory status with PeerInfo.cfg */
        Peer.updateOtherPeerMetadata();

        /* gather all remote peers that are interested & incomplete */
        List<NeighborProfile> interested = new ArrayList<>();
        for (NeighborProfile pm : Peer.remotePeerDetails.values()) {
            if (!pm.getPeerId().equals(Peer.peerID)
                && pm.isInterested()
                && !pm.hasCompletedFile()) {
                interested.add(pm);
            }
        }

        /* choose up to N preferred neighbours */
        int n = Math.min(RuntimeConfig.preferredNeighbourCount, interested.size());
        if (n == 0) {
            Peer.preferredNeighbours.clear();
            return;
        }

        // if we already have the full file, shuffle; otherwise pick by highest rate
        if (Peer.bitFieldMessage.isDownloadComplete()) {
            Collections.shuffle(interested);
        } else {
            interested.sort(Comparator.comparingDouble(NeighborProfile::getDataRate).reversed());
        }

        /* build the new preferred set */
        Set<String> newPreferred = new HashSet<>();
        for (int i = 0; i < n; i++) {
            newPreferred.add(interested.get(i).getPeerId());
        }

        /* apply changes: un-choke newly preferred, choke those dropped */
        Set<String> previouslyPreferred = new HashSet<>(Peer.preferredNeighbours.keySet());

        // Un-choke new
        for (String pid : newPreferred) {
            NeighborProfile pm = Peer.remotePeerDetails.get(pid);
            Peer.preferredNeighbours.put(pid, pm);
            pm.setPreferredNeighbor(true);
            if (pm.isChoked()) {
                pm.setChoked(false);
                sendUnchoke(Peer.peerToSocketMap.get(pid), pid);
                sendHave   (Peer.peerToSocketMap.get(pid));
                pm.setPeerState(3); // waiting for INTERESTED / NOT_INTERESTED
            }
        }

        // Choke ones that fell out of the list (and aren’t optimistic)
        for (String pid : previouslyPreferred) {
            if (!newPreferred.contains(pid) && !Peer.optimisticUnchoked.containsKey(pid)) {
                NeighborProfile pm = Peer.remotePeerDetails.get(pid);
                pm.setPreferredNeighbor(false);
                pm.setChoked(true);
                sendChoke(Peer.peerToSocketMap.get(pid), pid);
                Peer.preferredNeighbours.remove(pid);
            }
        }

        if (!newPreferred.isEmpty()) {
            writeLog(Peer.peerID + " preferred neighbours: " + String.join(",", newPreferred));
        }
    }

    /* ------------ message helpers ------------ */

    private void sendUnchoke(Socket socket, String remoteId) {
        writeLog(Peer.peerID + " → UNCHOKE → " + remoteId);
        sendControlMsg(socket, new Msg(Constants.UNCHOKE));
    }

    private void sendChoke(Socket socket, String remoteId) {
        writeLog(Peer.peerID + " → CHOKE → " + remoteId);
        sendControlMsg(socket, new Msg(Constants.CHOKE));
    }

    private void sendHave(Socket socket) {
        byte[] payload = Peer.bitFieldMessage.encodeBitField();
        sendControlMsg(socket, new Msg(Constants.HAVE, payload));
    }

    private void sendControlMsg(Socket socket, Msg msg) {
        if (socket == null) return;
        try {
            byte[] bytes = Msg.serializeMessage(msg);
            try (OutputStream out = socket.getOutputStream()) {
                out.write(bytes);
            }
        } catch (Exception ignored) {}
    }
}
