import java.util.*;
import java.util.concurrent.Phaser;

public class RoadSideUnit implements Runnable {
    int id;
    Phaser timeSync;
    boolean writePending;
    Packet pendingPacket;
    Queue<Packet> messageQueue;
    Map<Integer, Integer>[] existingVCs;
    Medium mediumRef;
    Segment segmentRef;

    public void run() {
        while (true) {
            if (writePending) {
                timeSync.arriveAndAwaitAdvance();
                boolean written = mediumRef.write(pendingPacket);
                if (written) {
                    writePending = false;
                    pendingPacket = null;
                }
            }
            timeSync.arriveAndAwaitAdvance();
            if (!writePending) {
                // 1. Read pending requests
                messageQueue = mediumRef.read(id);
                while (messageQueue != null && !messageQueue.isEmpty()) {
                    Packet p = messageQueue.poll();
                    switch (p.type) {
                        case RREQ:
                            writePending = true;
                            pendingPacket = Packet.generateRREPPacket(id, segmentRef.currentTime, LQI, p.appId);
                            break;
                        case RJOIN:
                            existingVCs[p.appId].put(p.senderId, p.LQI);
                            break;
                        case RREP:
                            break;
                    }
                }
            }
            timeSync.arriveAndAwaitAdvance();
        }
    }
}