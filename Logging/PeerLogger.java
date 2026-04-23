package Logging;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

/** Per-process file logging plus mirrored stdout lines. */
public final class PeerLogger {

    private FileHandler rotatingSink;
    private static final Logger backend = Logger.getLogger(PeerLogger.class.getName());

    public void setupLogger(String peerID) {
        try {
            rotatingSink = new FileHandler("log_peer_" + peerID + ".log");
            rotatingSink.setFormatter(new PeerLogLayout());
            backend.addHandler(rotatingSink);
            backend.setUseParentHandlers(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeLog(String logMessage) {
        backend.info(logMessage);
        System.out.print(PeerLogLayout.decorate(logMessage));
    }

    public static void tag(String tag, String msg) {
        writeLog(String.format("[%s] %s", tag, msg));
    }
}
