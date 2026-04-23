package Msgs;

import Configs.RuntimeConfig;
import Logging.PeerLogger;
import Metadata.NeighborProfile;
import Process.Peer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class BitField {
    private final FilePiece[] filePieces;
    private final int totalPieces;

    public BitField() {
        int fileSize  = RuntimeConfig.fileSize;
        int pieceSize = RuntimeConfig.pieceSize;
        this.totalPieces = (int) Math.ceil((double) fileSize / pieceSize);
        this.filePieces  = new FilePiece[this.totalPieces];
        Arrays.setAll(this.filePieces, i -> new FilePiece());
    }

    public void initializePieces(String peerId, boolean hasFile) {
        for (FilePiece piece : filePieces) {
            piece.setPieceAvailable(hasFile);
            piece.setFromPeer(peerId);
        }
    }

    public byte[] encodeBitField() {
        int numBytes = (totalPieces + 7) / 8;
        byte[] bits = new byte[numBytes];

        for (int i = 0; i < totalPieces; i++) {
            if (filePieces[i].isPieceAvailable()) {
                bits[i / 8] |= (1 << (7 - (i % 8)));
            }
        }
        return bits;
    }


    public static BitField decodeBitField(byte[] data) {
        BitField bf = new BitField();
    
        // 1) nothing to decode → return an “all-zero” bit-field
        if (data == null || data.length == 0) {
            return bf;
        }
    
        // 2) decode only as many bits as we really have input for
        int bitsToDecode = Math.min(bf.totalPieces, data.length * 8);
    
        for (int i = 0; i < bitsToDecode; i++) {
            boolean available = ((data[i / 8] & 0xFF) & (1 << (7 - (i % 8)))) != 0;
            bf.filePieces[i].setPieceAvailable(available);
        }
        return bf;
    }

    public int countAvailablePieces() {
        int count = 0;
        for (FilePiece piece : filePieces) {
            if (piece.isPieceAvailable()) count++;
        }
        return count;
    }

    public boolean isDownloadComplete() {
        for (FilePiece piece : filePieces) {
            if (!piece.isPieceAvailable()) return false;
        }
        return true;
    }

    /**
     * Find the first piece this peer is missing that the other BitField has.
     * @return piece index or –1 if none
     */
    public int findFirstMissingPiece(BitField other) {
        for (int i = 0; i < totalPieces; i++) {
            if (!filePieces[i].isPieceAvailable() && other.filePieces[i].isPieceAvailable()) {
                return i;
            }
        }
        return -1;
    }

    public synchronized void updateBitField(String fromPeer, FilePiece fp) {
        int idx = fp.getPieceIndex();
    
        // 1) ignore duplicates
        if (filePieces[idx].isPieceAvailable()) return;
    
        // 2) write the payload to our local file
        try (RandomAccessFile raf = new RandomAccessFile(
                new File(Peer.peerFolder, RuntimeConfig.fileName), "rw")) {
            raf.seek((long) idx * RuntimeConfig.pieceSize);
            raf.write(fp.getContent());  // writes exactly the bytes you received
        } catch (IOException ioe) {
            PeerLogger.writeLog("Error writing piece " + idx + ": " + ioe.getMessage());
            return;
        }
    
        // 3) mark it present in our bitfield
        filePieces[idx].setPieceAvailable(true);
        filePieces[idx].setFromPeer(fromPeer);
    
        // 4) log the piece download
        PeerLogger.writeLog(String.format(
            "Peer %s has downloaded the piece %d from %s. Now the number of pieces it has is %d",
            Peer.peerID, idx, fromPeer, countAvailablePieces()));
    
        // 5) if that was the final piece, log completion and update PeerInfo.cfg
        if (isDownloadComplete()) {
            PeerLogger.writeLog("Peer " + Peer.peerID + " has downloaded the complete file.");
    
            NeighborProfile me = Peer.remotePeerDetails.get(Peer.peerID);
            if (me != null && !me.hasCompletedFile()) {
                try {
                    me.syncHasFileColumn(Peer.peerID, 1);  // may throw IOException
                    me.setCompletedFile(true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public FilePiece[] getFilePieces() {
        return filePieces;
    }

    public int getTotalPieces() {
        return totalPieces;
    }
}
