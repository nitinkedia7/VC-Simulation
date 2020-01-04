import java.util.*;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;

public class Vehicle implements Runnable {
    int id;
    int posX; // in m
    int speedX; // in m/s
    int LQI;
    int hasRequest;
    int currentTime;
    int stopTime;
    Phaser timeSync;
    Medium mediumRef;
    Segment segmentRef;
    Map<Integer, Integer>[] existingVCs;
    boolean writePending;
    Packet pendingPacket;
    Queue<Packet> messageQueue;

    public Vehicle(int id, int posX, int speedX, Phaser timeSync, Medium mediumRef, Segment segmentRef, int stopTime) {
        this.id = id;
        this.currentTime = 0;
        this.stopTime = stopTime;
        this.posX = posX;
        this.speedX = speedX;
        this.LQI = 0;
        this.mediumRef = mediumRef;
        this.segmentRef = segmentRef;
        // Allot size
        for (int i = 0; i < Config.APPLICATION_TYPE; i++) {
            existingVCs[i] = new HashMap<Integer, Integer>();
        }
        this.timeSync = timeSync;
        timeSync.register();
        System.out.println("Vehicle " + id + " initialised.");
    } 

    /* Returns false if segment has changed */ 
    public boolean updatePosition() {
        posX += speedX;
        int segmentStart = Config.SEGMENT_LENGTH * segmentId;
        int segmentEnd = Config.SEGMENT_LENGTH * (segmentId + 1);
        if (posX < segmentStart || posX > segmentEnd) return false;
        return true;
    }

    public void run() {
        while (currentTime <= stopTime) {
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
                // 1. Read medium queue
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
                // 2. (Randomly) Request for an application
                hasRequest = ThreadLocalRandom.current().nextInt(1);
                if (hasRequest == 1) {
                    int appType = ThreadLocalRandom.current().nextInt(Config.APPLICATION_TYPE);
                    if (!existingVCs[appType].isEmpty()) {
                        // Join VC by broadcasting ones LQI
                        writePending = true;
                        pendingPacket = Packet.generateRJOINPacket(id, segmentRef.currentTime, LQI, appType);
                    } 
                    else {
                        writePending = true;
                        pendingPacket = Packet.generateRREQPacket(id, segmentRef.currentTime, LQI, appType);    
                    }
                }
            }
            timeSync.arriveAndAwaitAdvance();
            currentTime++;
        }
    }
}