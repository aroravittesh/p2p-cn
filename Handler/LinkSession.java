package Handler;

import Logging.PeerLogger;
import Msgs.Constants;
import Msgs.Handshake;
import Metadata.TaggedFrame;
import Msgs.Msg;
import Queue.FrameMailbox;
import Process.Peer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

/**
 * One TCP session toward a neighbour: handshake, optional bitfield burst,
 * then a framing loop that forwards payloads into {@link FrameMailbox}.
 */
public final class LinkSession implements Runnable {

    private final Socket socket;
    private final InputStream inbound;
    private final OutputStream outbound;
    private Handshake peerHello;
    private final String selfPeer;
    private String counterpartId;
    private final int connectionMode;

    public LinkSession(String peerId, int connectionMode, String address, int port)
            throws IOException {
        this.selfPeer = peerId;
        this.connectionMode = connectionMode;
        this.socket = new Socket(address, port);
        this.inbound = socket.getInputStream();
        this.outbound = socket.getOutputStream();
    }

    public LinkSession(String peerId, int connectionMode, Socket socket) throws IOException {
        this.selfPeer = peerId;
        this.connectionMode = connectionMode;
        this.socket = socket;
        this.inbound = socket.getInputStream();
        this.outbound = socket.getOutputStream();
    }

    private boolean dispatchHandshake() throws IOException {
        Handshake hs = new Handshake(Constants.HANDSHAKE_HEADER, selfPeer);
        outbound.write(hs.serialize());
        PeerLogger.tag("HANDSHAKE", selfPeer + " sent handshake");
        System.out.println(selfPeer + " handshake sent.");
        return true;
    }

    private void absorbHandshake() throws Exception {
        byte[] buf = new byte[Constants.HANDSHAKE_MESSAGE_LENGTH];
        while (inbound.read(buf) > 0) {
            peerHello = Handshake.deserialize(buf);
            if (Constants.HANDSHAKE_HEADER.equals(peerHello.getHeader())) {
                counterpartId = peerHello.getPeerID();
                PeerLogger.tag("HANDSHAKE", selfPeer + " received handshake from " + counterpartId);
                Peer.peerToSocketMap.put(counterpartId, socket);
                System.out.println(selfPeer + " connected to " + counterpartId);
                System.out.println(selfPeer + " received handshake from " + counterpartId);
                break;
            }
        }
    }

    public void exchangeBitfield() throws Exception {
        byte[] bitfieldPayload = Peer.bitFieldMessage.encodeBitField();
        Msg bitfieldMsg = new Msg(Constants.BITFIELD, bitfieldPayload);
        outbound.write(Msg.serializeMessage(bitfieldMsg));
        PeerLogger.tag("BITFIELD", selfPeer + " sent BITFIELD to " + counterpartId);
        PeerLogger.tag("INTERESTED", selfPeer + " sent INTERESTED to " + counterpartId);
        PeerLogger.tag("COMPLETE", selfPeer + " COMPLETED DOWNLOAD");

        Peer.remotePeerDetails.get(counterpartId).setPeerState(8);
    }

    private void passiveOpening() throws Exception {
        absorbHandshake();

        if (dispatchHandshake()) {
            PeerLogger.tag("HANDSHAKE", selfPeer + " handshake reply sent to " + counterpartId);
        } else {
            PeerLogger.tag("HANDSHAKE", selfPeer + " handshake reply failed");
            System.exit(-1);
        }

        byte[] bitfieldPayload = Peer.bitFieldMessage.encodeBitField();
        Msg bitfieldMsg = new Msg(Constants.BITFIELD, bitfieldPayload);
        outbound.write(Msg.serializeMessage(bitfieldMsg));
        PeerLogger.tag("BITFIELD", selfPeer + " sent BITFIELD to " + counterpartId);

        Peer.remotePeerDetails.get(counterpartId).setPeerState(3);
    }

    private void pumpFramedMessages() throws IOException {
        byte[] lenBuf = new byte[Constants.MESSAGE_LENGTH];
        byte[] typeBuf = new byte[Constants.MESSAGE_TYPE];
        byte[] headerBuf = new byte[Constants.MESSAGE_LENGTH + Constants.MESSAGE_TYPE];

        while (!Thread.currentThread().isInterrupted()) {
            int read = inbound.read(headerBuf);
            if (read == -1) break;

            System.arraycopy(headerBuf, 0, lenBuf, 0, Constants.MESSAGE_LENGTH);
            System.arraycopy(headerBuf, Constants.MESSAGE_LENGTH, typeBuf, 0, Constants.MESSAGE_TYPE);

            Msg m = new Msg();
            m.setMessageLength(lenBuf);
            m.setMessageType(typeBuf);

            TaggedFrame meta = new TaggedFrame();
            meta.setMessage(m);
            meta.setSenderId(counterpartId);
            FrameMailbox.enqueue(meta);

            int payloadLen = m.getDataLength() - Constants.MESSAGE_TYPE;
            if (payloadLen > 0) {
                byte[] payload = new byte[payloadLen];
                int readCount = 0;
                while (readCount < payloadLen) {
                    int r = inbound.read(payload, readCount, payloadLen - readCount);
                    if (r < 0) return;
                    readCount += r;
                }

                byte[] fullMsg = new byte[Constants.MESSAGE_LENGTH + Constants.MESSAGE_TYPE + payloadLen];
                System.arraycopy(headerBuf, 0, fullMsg, 0, headerBuf.length);
                System.arraycopy(payload, 0, fullMsg, headerBuf.length, payloadLen);

                Msg withPayload = Msg.deserializeMessage(fullMsg);
                meta.setMessage(withPayload);
                FrameMailbox.enqueue(meta);
            }
        }
    }

    @Override
    public void run() {
        try {
            if (connectionMode == Constants.ACTIVE_CONNECTION) {
                if (dispatchHandshake()) {
                    absorbHandshake();
                    exchangeBitfield();
                }
            } else {
                passiveOpening();
            }
            pumpFramedMessages();
        } catch (Exception e) {
            System.err.println(Arrays.toString(e.getStackTrace()));
        }
    }
}
