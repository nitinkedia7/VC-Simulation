import java.util.*;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;

public class Vehicle implements Runnable {
    int id;
    int posX; // in m
    int speedX; // in m/s
    int segmentId;
    int LQI;
    int hasRequest;
    Phaser timeSync;
    Medium mediumRef;
    Segment segmentRef;
    Set<Integer> existingVCs;
    boolean writePending;
    Packet pendingPacket;

    public Vehicle(int id, int posX, int speedX, Phaser timeSync, Medium mediumRef) {
        this.id = id;
        this.posX = posX;
        this.speedX = speedX;
        this.timeSync = timeSync;
        this.mediumRef = mediumRef;
        this.existingVCs = new TreeSet<Integer>();
        timeSync.register();
        new Thread(this).start();
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
        while (true) {
            if (writePending) {
                boolean written = mediumRef.write(pendingPacket);
                if (written) {
                    writePending = false;
                    pendingPacket = null;
                }
            }   
            else {
                // 1. Read medium queue
                
                // 2. (Randomly) Request for an application
                hasRequest = ThreadLocalRandom.current().nextInt(1);
                if (hasRequest == 1) {
                    int appType = ThreadLocalRandom.current().nextInt(Config.APPLICATION_TYPE);
                    if (existingVCs.contains(appType)) {
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
        }
    }
}