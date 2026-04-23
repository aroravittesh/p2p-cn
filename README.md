# CNT5106C - P2P FILE SHARING SYSTEM

## Group Members

- Vittesh Arora
- Raghav Gupta
- Ansh Jain

## Compilation Steps

```bash
make clean # needed if you have already compiled the code
make
make Peer
```

Other peers (each in its own terminal, from this directory, after `make`):

```bash
java -cp "lib/jsch-0.1.55.jar:bin" Process.Peer 1002
```

Replace `1002` with the peer ids listed in `Configs/PeerInfo.cfg`. Start the seeder (line with `1` in the last column) first, then the rest.

## File Descriptions

```
├── Configs
│   └── `RuntimeConfig.java` - Loads Common.cfg and exposes paths into Configs/.
│
├── Handler
│   ├── `LinkSession.java` - Handles peer-to-peer message exchange and handshake processing.
│   ├── `SocketAcceptor.java` - Listens for incoming TCP connections and hands off sockets.
│   └── `FrameDispatcher.java` - Processes queued messages and drives the protocol state machine.
│
├── Logging
│   ├── `PeerLogger.java` - Provides utility methods for logging messages to files.
│   └── `PeerLogLayout.java` - Formats log messages with timestamps for readability.
│
├── Metadata
│   ├── `TaggedFrame.java` - Stores metadata related to messages exchanged between peers.
│   └── `NeighborProfile.java` - Manages information about peers, including their states and file availability.
│
├── Msgs
│   ├── `BitField.java` - Manages bitfield representation of file piece availability.
│   ├── `Constants.java` - Defines constants used throughout the system.
│   ├── `Details.java` - Represents message details exchanged between peers.
│   ├── `FilePiece.java` - Handles individual file pieces for file sharing.
│   ├── `Handshake.java` - Manages handshake messages between peers.
│   └── `Msg.java` - Defines the structure of peer-to-peer messages.
│
├── Process
│   ├── `Peer.java` - Main process managing peer initialization, configuration, and execution.
│   └── `StartRemotePeers.java` - Launches peers on remote hosts over SSH (optional).
│
├── Queue
│   └── `FrameMailbox.java` - Implements a queue for storing and processing incoming messages.
│
└── Tasks
    ├── `PreferredNeighbors.java` - Timer-driven preferred-neighbour selection.
    └── `OptimisticallyUnchoke.java` - Timer-driven optimistic unchoking.
```

## Java Version Used

```
openjdk 21.0.6 2025-01-21
OpenJDK Runtime Environment (build 21.0.6+7-Ubuntu-124.04.1)
OpenJDK 64-Bit Server VM (build 21.0.6+7-Ubuntu-124.04.1, mixed mode, sharing)
javac 21.0.6
```