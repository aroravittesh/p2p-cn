package Msgs;

import Configs.RuntimeConfig;
import java.nio.ByteBuffer;

public class FilePiece {
    private int pieceIndex;
    private byte[] content;
    private String fromPeer;
    private boolean isPieceAvailable;

    public FilePiece() {
        this.pieceIndex = -1;
        this.content = new byte[RuntimeConfig.pieceSize];
        this.fromPeer = null;
        this.isPieceAvailable = false;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }

    public void setPieceIndex(int pieceIndex) {
        this.pieceIndex = pieceIndex;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public String getFromPeer() {
        return fromPeer;
    }

    public void setFromPeer(String fromPeer) {
        this.fromPeer = fromPeer;
    }

    public boolean isPieceAvailable() {
        return isPieceAvailable;
    }

    public void setPieceAvailable(boolean isPieceAvailable) {
        this.isPieceAvailable = isPieceAvailable;
    }

    public static FilePiece fromPayload(byte[] payload) {
        byte[] indexBytes = new byte[Constants.PIECE_INDEX_LENGTH];
        System.arraycopy(payload, 0, indexBytes, 0, Constants.PIECE_INDEX_LENGTH);

        FilePiece piece = new FilePiece();
        piece.setPieceIndex(ByteBuffer.wrap(indexBytes).getInt());

        byte[] data = new byte[payload.length - Constants.PIECE_INDEX_LENGTH];
        System.arraycopy(payload, Constants.PIECE_INDEX_LENGTH, data, 0, data.length);
        piece.setContent(data);
        
        return piece;
    }
}
