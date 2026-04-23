package Metadata;

import Configs.RuntimeConfig;
import Msgs.BitField;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** One row from {@code PeerInfo.cfg} plus volatile protocol state for that partner. */
public final class NeighborProfile implements Comparable<NeighborProfile> {

    private final String peerId;
    private final String ipAddress;
    private final String portNumber;
    private final boolean seedAtBootstrap;
    private final int peerIndex;

    private int peerState = -1;
    private int previousState = -1;
    private boolean isPreferredNeighbor = false;
    private BitField bitField;
    private boolean isOptimisticallyUnchoked = false;
    private boolean isInterested = false;
    private boolean isHandshaked = false;
    private boolean isChoked = false;
    private boolean hasCompletedFile = false;
    private Date startTime;
    private Date endTime;
    private double dataRate = 0.0;

    public NeighborProfile(String peerId,
                           String ipAddress,
                           String portNumber,
                           int hasFile,
                           int peerIndex) {
        this.peerId = peerId;
        this.ipAddress = ipAddress;
        this.portNumber = portNumber;
        this.seedAtBootstrap = (hasFile == 1);
        this.peerIndex = peerIndex;
    }

    public synchronized void syncHasFileColumn(String targetPeerId, int newHasFile)
            throws IOException {
        Path path = Paths.get(RuntimeConfig.peerInfoFile);
        try (Stream<String> lines = Files.lines(path)) {
            List<String> updated = lines
                    .map(line -> {
                        String[] tok = line.trim().split("\\s+");
                        if (tok[0].equals(targetPeerId)) {
                            return tok[0] + " " + tok[1] + " " + tok[2] + " " + newHasFile;
                        }
                        return line;
                    })
                    .collect(Collectors.toList());
            Files.write(path, updated);
        }
    }

    public String getPeerId() {
        return peerId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getPortNumber() {
        return portNumber;
    }

    public boolean hasFile() {
        return seedAtBootstrap;
    }

    public int getPeerIndex() {
        return peerIndex;
    }

    public void setPeerState(int s) {
        this.peerState = s;
    }

    public int getPeerState() {
        return peerState;
    }

    public void setPreviousState(int ps) {
        this.previousState = ps;
    }

    public int getPreviousState() {
        return previousState;
    }

    public void setPreferredNeighbor(boolean b) {
        this.isPreferredNeighbor = b;
    }

    public boolean isPreferredNeighbor() {
        return isPreferredNeighbor;
    }

    public void setOptimisticallyUnchoked(boolean b) {
        this.isOptimisticallyUnchoked = b;
    }

    public boolean isOptimisticallyUnchoked() {
        return isOptimisticallyUnchoked;
    }

    public void setInterested(boolean b) {
        this.isInterested = b;
    }

    public boolean isInterested() {
        return isInterested;
    }

    public void setHandshaked(boolean b) {
        this.isHandshaked = b;
    }

    public boolean isHandshaked() {
        return isHandshaked;
    }

    public void setChoked(boolean b) {
        this.isChoked = b;
    }

    public boolean isChoked() {
        return isChoked;
    }

    public void setCompletedFile(boolean b) {
        this.hasCompletedFile = b;
    }

    public boolean hasCompletedFile() {
        return hasCompletedFile;
    }

    public void setStartTime(Date d) {
        this.startTime = d;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setEndTime(Date d) {
        this.endTime = d;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setDataRate(double r) {
        this.dataRate = r;
    }

    public double getDataRate() {
        return dataRate;
    }

    public void setBitField(BitField bf) {
        this.bitField = bf;
    }

    public BitField getBitField() {
        return bitField;
    }

    @Override
    public int compareTo(NeighborProfile o) {
        return Double.compare(this.dataRate, o.dataRate);
    }
}
