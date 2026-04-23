package Configs;

/**
 * Mutable knobs loaded from {@code Common.cfg} plus fixed paths into {@code Configs/}.
 */
public final class RuntimeConfig {

    public static int preferredNeighbourCount;
    public static int unchokingInterval;
    public static int optimisticUnchokingInterval;
    public static String fileName;
    public static int fileSize;
    public static int pieceSize;

    public static final String peerInfoFile =
            System.getProperty("user.dir") + "/Configs/PeerInfo.cfg";
    public static final String systemConfigurationFile =
            System.getProperty("user.dir") + "/Configs/Common.cfg";

    private RuntimeConfig() {}
}
