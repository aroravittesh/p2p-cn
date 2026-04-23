package Handler;

import Configs.RuntimeConfig;
import Msgs.BitField;
import Msgs.Constants;
import Msgs.FilePiece;
import Msgs.Msg;
import Metadata.NeighborProfile;
import Metadata.TaggedFrame;
import Queue.FrameMailbox;
import Process.Peer;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Date;

import static Logging.PeerLogger.writeLog;

/**
 * Drains {@link FrameMailbox} and executes the peer’s finite-state reactions.
 */
public final class FrameDispatcher implements Runnable {

    private final String selfId;

    public FrameDispatcher(String peerId) {
        this.selfId = peerId;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            while (FrameMailbox.hasPending()) {
                TaggedFrame meta = FrameMailbox.dequeue();
                Msg msg = meta.getMessage();
                String remoteId = meta.getSenderId();
                String type = msg.getType();

                NeighborProfile remote = Peer.remotePeerDetails.get(remoteId);
                int state = remote.getPeerState();

                if (Constants.HAVE.equals(type) && state != 14) {
                    onHaveWhileNotIdle(msg, remoteId);
                    continue;
                }

                switch (state) {
                    case 2:
                        onInboundBitfieldDuringHandshake(msg, remoteId);
                        break;
                    case 3:
                        onInterestSignal(type, remoteId);
                        break;
                    case 4:
                        onMaybeRequest(msg, type, remoteId);
                        break;
                    case 8:
                        onTheirBitfieldWhileAwaiting(msg, type, remoteId);
                        break;
                    case 9:
                        onChokeToggle(type, remoteId);
                        break;
                    case 11:
                        onPiecePayload(msg, type, remoteId);
                        break;
                    case 14:
                        onHaveOrFreshUnchoke(msg, type, remoteId);
                        break;
                    case 15:
                        onNeighborCompletionNotice(remoteId);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private void onInboundBitfieldDuringHandshake(Msg msg, String remoteId) {
        BitField theirs = BitField.decodeBitField(msg.getPayload());
        Peer.remotePeerDetails.get(remoteId).setBitField(theirs);

        writeLog(selfId + " received BITFIELD from " + remoteId);
        emitBitfield(Peer.peerToSocketMap.get(remoteId), remoteId);
        Peer.remotePeerDetails.get(remoteId).setPeerState(3);

        if (Peer.bitFieldMessage.findFirstMissingPiece(theirs) >= 0) {
            writeLog(selfId + " sent INTERESTED to " + remoteId);
            emitInterested(Peer.peerToSocketMap.get(remoteId), remoteId);
            Peer.remotePeerDetails.get(remoteId).setPeerState(9);
        } else {
            writeLog(selfId + " sent NOT_INTERESTED to " + remoteId);
            emitNotInterested(Peer.peerToSocketMap.get(remoteId), remoteId);
            Peer.remotePeerDetails.get(remoteId).setPeerState(13);
        }
    }

    private void onInterestSignal(String type, String remoteId) {
        if (Constants.INTERESTED.equals(type)) {
            reactInterested(remoteId);
        } else if (Constants.NOT_INTERESTED.equals(type)) {
            reactNotInterested(remoteId);
        }
    }

    private void onMaybeRequest(Msg msg, String type, String remoteId) {
        if (!Constants.REQUEST.equals(type)) return;
        emitPiece(Peer.peerToSocketMap.get(remoteId), msg, remoteId);
        maybeAnnounceFullCopy();
        if (neitherPreferredNorOptimistic(remoteId)) {
            applyChoke(remoteId);
        }
    }

    private void onTheirBitfieldWhileAwaiting(Msg msg, String type, String remoteId) {
        if (Constants.BITFIELD.equals(type)) {
            if (stillInteresting(msg, remoteId)) reactInterested(remoteId);
            else reactNotInterested(remoteId);
        }
    }

    private void onChokeToggle(String type, String remoteId) {
        if (Constants.CHOKE.equals(type)) {
            writeLog(selfId + " is CHOKED by " + remoteId);
            Peer.remotePeerDetails.get(remoteId).setChoked(true);
            Peer.remotePeerDetails.get(remoteId).setPeerState(14);
        } else if (Constants.UNCHOKE.equals(type)) {
            writeLog(selfId + " is UNCHOKED by " + remoteId);
            queueNextInterestingPiece(remoteId);
        }
    }

    private void onPiecePayload(Msg msg, String type, String remoteId) {

        if (!Constants.PIECE.equals(type)) return;

        byte[] payload = msg.getPayload();
        bumpThroughput(payload.length, remoteId);

        FilePiece piece = FilePiece.fromPayload(payload);
        Peer.bitFieldMessage.updateBitField(remoteId, piece);

        int haveNow = Peer.bitFieldMessage.countAvailablePieces();
        writeLog(String.format("Peer %s has downloaded the piece %d from %s. "
                + "Now the number of pieces it has is %d",
                selfId, piece.getPieceIndex(), remoteId, haveNow));

        queueNextInterestingPiece(remoteId);

        Peer.updateOtherPeerMetadata();

        for (String id : Peer.remotePeerDetails.keySet()) {
            if (!id.equals(Peer.peerID) && peerStillWantsPieces(id)) {
                emitHave(Peer.peerToSocketMap.get(id));
                Peer.remotePeerDetails.get(id).setPeerState(3);
            }
        }

        if (Peer.bitFieldMessage.isDownloadComplete()) {
            writeLog(String.format("Peer %s has downloaded the complete file",
                    selfId));
        }

        maybeAnnounceFullCopy();
    }

    private void onHaveWhileNotIdle(Msg msg, String remoteId) {
        writeLog(selfId + " got HAVE from " + remoteId);
        if (stillInteresting(msg, remoteId)) reactInterested(remoteId);
        else reactNotInterested(remoteId);
    }

    private void onHaveOrFreshUnchoke(Msg msg, String type, String remoteId) {
        if (Constants.HAVE.equals(type)) {
            onHaveWhileNotIdle(msg, remoteId);
        } else if (Constants.UNCHOKE.equals(type)) {
            queueNextInterestingPiece(remoteId);
        }
    }

    private void onNeighborCompletionNotice(String remoteId) {
        writeLog(remoteId + " finished downloading.");
        int prev = Peer.remotePeerDetails.get(remoteId).getPreviousState();
        Peer.remotePeerDetails.get(remoteId).setPeerState(prev);
    }

    private void reactInterested(String remoteId) {
        writeLog(selfId + " got INTERESTED from " + remoteId);
        NeighborProfile pm = Peer.remotePeerDetails.get(remoteId);
        pm.setInterested(true);
        pm.setHandshaked(true);
        if (neitherPreferredNorOptimistic(remoteId)) {
            applyChoke(remoteId);
        } else {
            applyUnchoke(remoteId);
        }
    }

    private void reactNotInterested(String remoteId) {
        writeLog(selfId + " got NOT_INTERESTED from " + remoteId);
        NeighborProfile pm = Peer.remotePeerDetails.get(remoteId);
        pm.setInterested(false);
        pm.setHandshaked(true);
        pm.setPeerState(5);
    }

    private boolean neitherPreferredNorOptimistic(String id) {
        return !Peer.preferredNeighbours.containsKey(id)
                && !Peer.optimisticUnchoked.containsKey(id);
    }

    private void queueNextInterestingPiece(String remoteId) {
        int idx = Peer.bitFieldMessage.findFirstMissingPiece(
                Peer.remotePeerDetails.get(remoteId).getBitField());
        if (idx == -1) {
            Peer.remotePeerDetails.get(remoteId).setPeerState(13);
            return;
        }
        emitRequest(Peer.peerToSocketMap.get(remoteId), idx, remoteId);
        Peer.remotePeerDetails.get(remoteId).setPeerState(11);
        Peer.remotePeerDetails.get(remoteId).setStartTime(new Date());
    }

    private void applyChoke(String remoteId) {
        emitChoke(Peer.peerToSocketMap.get(remoteId), remoteId);
        Peer.remotePeerDetails.get(remoteId).setChoked(true);
        Peer.remotePeerDetails.get(remoteId).setPeerState(6);
    }

    private void applyUnchoke(String remoteId) {
        emitUnchoke(Peer.peerToSocketMap.get(remoteId), remoteId);
        Peer.remotePeerDetails.get(remoteId).setChoked(false);
        Peer.remotePeerDetails.get(remoteId).setPeerState(4);
    }

    private boolean peerStillWantsPieces(String id) {
        NeighborProfile pm = Peer.remotePeerDetails.get(id);
        return !pm.hasCompletedFile() && !pm.isChoked() && pm.isInterested();
    }

    private boolean stillInteresting(Msg msg, String remoteId) {
        BitField bf = BitField.decodeBitField(msg.getPayload());
        Peer.remotePeerDetails.get(remoteId).setBitField(bf);
        int idx = Peer.bitFieldMessage.findFirstMissingPiece(bf);
        if (idx != -1 && Constants.HAVE.equals(msg.getType())) {
            writeLog(selfId + " remote " + remoteId + " has piece " + idx);
        }
        return idx != -1;
    }

    private void bumpThroughput(long payloadBytes, String remoteId) {
        NeighborProfile pm = Peer.remotePeerDetails.get(remoteId);
        pm.setEndTime(new Date());
        long elapsed = pm.getEndTime().getTime() - pm.getStartTime().getTime();
        if (elapsed == 0) elapsed = 1;
        double rate = ((double) (payloadBytes + Constants.MESSAGE_LENGTH + Constants.MESSAGE_TYPE) / elapsed) * 1000;
        pm.setDataRate(rate);
    }

    private void emitBitfield(Socket sock, String remoteId) {
        writeLog(selfId + " → BITFIELD → " + remoteId);
        push(sock, new Msg(Constants.BITFIELD,
                Peer.bitFieldMessage.encodeBitField()));
    }

    private void emitHave(Socket sock) {
        push(sock, new Msg(Constants.HAVE,
                Peer.bitFieldMessage.encodeBitField()));
    }

    private void emitChoke(Socket sock, String remoteId) {
        writeLog(selfId + " → CHOKE → " + remoteId);
        push(sock, new Msg(Constants.CHOKE));
    }

    private void emitUnchoke(Socket sock, String remoteId) {
        writeLog(selfId + " → UNCHOKE → " + remoteId);
        push(sock, new Msg(Constants.UNCHOKE));
    }

    private void emitInterested(Socket sock, String remoteId) {
        writeLog(selfId + " → INTERESTED → " + remoteId);
        push(sock, new Msg(Constants.INTERESTED));
    }

    private void emitNotInterested(Socket sock, String remoteId) {
        writeLog(selfId + " → NOT_INTERESTED → " + remoteId);
        push(sock, new Msg(Constants.NOT_INTERESTED));
    }

    private void emitRequest(Socket sock, int pieceIdx, String remoteId) {
        writeLog(selfId + " REQUEST piece " + pieceIdx + " → " + remoteId);
        push(sock, new Msg(Constants.REQUEST,
                ByteBuffer.allocate(4).putInt(pieceIdx).array()));
    }

    private void emitPiece(Socket sock, Msg req, String remoteId) {
        int idx = ByteBuffer.wrap(req.getPayload()).getInt();
        writeLog(selfId + " sending PIECE " + idx + " → " + remoteId);
        byte[] buf = new byte[RuntimeConfig.pieceSize];
        int n;
        try (RandomAccessFile raf = new RandomAccessFile(
                new File(Peer.peerFolder, RuntimeConfig.fileName), "r")) {
            raf.seek((long) idx * RuntimeConfig.pieceSize);
            n = raf.read(buf, 0, RuntimeConfig.pieceSize);
        } catch (IOException e) {
            return;
        }
        byte[] payload = new byte[n + Constants.PIECE_INDEX_LENGTH];
        System.arraycopy(req.getPayload(), 0, payload, 0, Constants.PIECE_INDEX_LENGTH);
        System.arraycopy(buf, 0, payload, Constants.PIECE_INDEX_LENGTH, n);
        push(sock, new Msg(Constants.PIECE, payload));
    }

    private void maybeAnnounceFullCopy() {
        if (Peer.isFirstPeer || !Peer.bitFieldMessage.isDownloadComplete()) return;
        for (String id : Peer.remotePeerDetails.keySet()) {
            if (id.equals(Peer.peerID)) continue;
            Socket s = Peer.peerToSocketMap.get(id);
            if (s != null) push(s, new Msg(Constants.MESSAGE_DOWNLOADED));
        }
    }

    private void push(Socket socket, Msg msg) {
        if (socket == null) return;
        try (OutputStream out = socket.getOutputStream()) {
            out.write(Msg.serializeMessage(msg));
        } catch (Exception ignored) {}
    }
}
