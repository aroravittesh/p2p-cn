package Process;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import Metadata.NeighborProfile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

/**
 * Launches each peer on its remote host via SSH.
 */
public class StartRemotePeers {
    private static final String PEER_INFO_PATH = "Configs/PeerInfo.cfg";
    private static final String WORKING_DIR    = System.getProperty("user.dir");
    private static final String REMOTE_DIR = "~/p2p-cn";

    private final Vector<NeighborProfile> peerInfo = new Vector<>();

    /** Reads PeerInfo.cfg into peerInfo vector */
    private void loadPeerInfo() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(PEER_INFO_PATH));
            for (int i = 0; i < lines.size(); i++) {
                String[] t = lines.get(i).trim().split("\\s+");
                peerInfo.add(new NeighborProfile(
                    t[0],       // peerId
                    t[1],       // hostAddress
                    t[2],       // portString (ignored here)
                    Integer.parseInt(t[3]),
                    i
                ));
            }
        } catch (Exception e) {
            System.err.println("Error loading peer info: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        StartRemotePeers starter = new StartRemotePeers();
        starter.loadPeerInfo();

        Scanner sc = new Scanner(System.in);
        System.out.print("SSH username: ");
        String user = sc.nextLine();
        System.out.print("SSH password: ");
        String pass = sc.nextLine();

        for (NeighborProfile pm : starter.peerInfo) {
            try {
                // open SSH session
                Session session = new JSch()
                    .getSession(user, pm.getIpAddress(), 22);
                session.setPassword(pass);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();

                // remote command: cd into working dir, then run our Peer
                String cmd = String.format(
                    // cd into project dir, launch peer detached, redirect output
                    "cd %s; nohup java -cp bin Process.Peer %s > peer_%s.out 2>&1 &",
                    REMOTE_DIR,
                    pm.getPeerId(),
                    pm.getPeerId()
                );
                ChannelExec channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand(cmd);
                channel.setOutputStream(new ByteArrayOutputStream());
                channel.connect();

                System.out.printf(
                    "Started peer %s at %s%n",
                    pm.getPeerId(), pm.getIpAddress()
                );

                // give it a moment
                TimeUnit.SECONDS.sleep(2);

                channel.disconnect();
                session.disconnect();

            } catch (Exception e) {
                System.err.printf(
                    "Failed to start peer %s: %s%n",
                    pm.getPeerId(), e.getMessage()
                );
            }
        }

        System.out.println("All remote peers have been launched.");
        System.exit(0);
    }
}
