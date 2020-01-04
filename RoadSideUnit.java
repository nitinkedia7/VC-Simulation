import java.util.*;
import java.util.concurrent.Phaser;

public class RoadSideUnit implements Runnable {
    int id;
    int posX;
    int LQI;
    Phaser timeSync;
    int currentTime;
    int stopTime;
    boolean writePending;
    Packet pendingPacket;
    Queue<Packet> messageQueue;
    Map<Integer, Integer>[] existingVCs;
    Medium mediumRef;
    Segment segmentRef;

    public RoadSideUnit(int id, int posX, Phaser timeSync, Medium mediumRef, Segment segmentRef, int stopTime) {
        this.id = id;
        this.posX = posX;
        this.LQI = 0;
        this.currentTime = 0;
        this.stopTime = stopTime;
        this.mediumRef = mediumRef;
        this.segmentRef = segmentRef;
        this.timeSync = timeSync;
        timeSync.register();
        System.out.println("RSU     " + id + " initialised.");
    }
    
    public void run() {
        while (currentTime <= stopTime) {
            System.out.println("RSU     " + id + " starting interval " + currentTime);
            if (writePending) {
                boolean written = mediumRef.write(pendingPacket);
                if (written) {
                    writePending = false;
                    pendingPacket = null;
                }
            }
            else {
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
            currentTime++;
        }
        System.out.println("RSU     " + id + " stopped after " + stopTime + " ms.");   
    }
}