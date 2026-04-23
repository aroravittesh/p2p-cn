package Msgs;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Handshake {
    private byte[] headerBytes;
    private byte[] peerIDBytes;
    private byte[] zeroBits;
    private String header;
    private String peerID;

    public Handshake() {
        this.zeroBits = new byte[Constants.HANDSHAKE_ZEROBITS_LENGTH];
        Arrays.fill(this.zeroBits, (byte) 0);
    }

    public Handshake(String header, String peerID) {
        this();
        this.header = header;
        this.headerBytes = header.getBytes(StandardCharsets.UTF_8);
        this.peerID = peerID;
        this.peerIDBytes = peerID.getBytes(StandardCharsets.UTF_8);
    }

    public void setHeaderFromBytes(byte[] headerBytes) {
        this.header = new String(headerBytes, StandardCharsets.UTF_8);
        this.headerBytes = headerBytes;
    }

    public void setPeerIdFromBytes(byte[] peerIDBytes) {
        this.peerID = new String(peerIDBytes, StandardCharsets.UTF_8);
        this.peerIDBytes = peerIDBytes;
    }

    public byte[] serialize() {
        byte[] handshakeBytes = new byte[Constants.HANDSHAKE_MESSAGE_LENGTH];
        System.arraycopy(this.headerBytes, 0, handshakeBytes, 0, this.headerBytes.length);
        System.arraycopy(this.zeroBits, 0, handshakeBytes, Constants.HANDSHAKE_HEADER_LENGTH, this.zeroBits.length);
        System.arraycopy(this.peerIDBytes, 0, handshakeBytes, Constants.HANDSHAKE_HEADER_LENGTH + this.zeroBits.length, this.peerIDBytes.length);
        return handshakeBytes;
    }

    public static Handshake deserialize(byte[] handshakeBytes) {
        Handshake message = new Handshake();
        byte[] header = Arrays.copyOfRange(handshakeBytes, 0, Constants.HANDSHAKE_HEADER_LENGTH);
        byte[] peerId = Arrays.copyOfRange(handshakeBytes, Constants.HANDSHAKE_HEADER_LENGTH + Constants.HANDSHAKE_ZEROBITS_LENGTH, Constants.HANDSHAKE_MESSAGE_LENGTH);
        message.setHeaderFromBytes(header);
        message.setPeerIdFromBytes(peerId);
        return message;
    }

    public String getHeader() {
        return header;
    }

    public String getPeerID() {
        return peerID;
    }

    public void setPeerID(String peerID) {
        this.peerID = peerID;
        this.peerIDBytes = peerID.getBytes(StandardCharsets.UTF_8);
    }
}
