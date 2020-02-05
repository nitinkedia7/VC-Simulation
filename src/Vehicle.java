package src;

import java.util.*;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;

public class Vehicle implements Runnable {
    int id;
    int posX; // in m
    int speedX; // in m/s
    int LQI;
    int currentTime;
    int stopTime;
    Phaser timeSync;
    Medium mediumRef;
    Segment segmentRef;
    Map<Integer, Cloud> existingVCs;
    boolean writePending;
    Packet pendingPacket;
    Queue<Packet> messageQueue;
    int availableResources;

    public Vehicle(int id, int posX, int speedX, Phaser timeSync, Medium mediumRef, Segment segmentRef, int stopTime) {
        this.id = id;
        this.currentTime = 0;
        this.stopTime = stopTime;
        this.posX = posX;
        this.speedX = speedX;
        this.LQI = 0;
        this.mediumRef = mediumRef;
        this.segmentRef = segmentRef;
        this.writePending = false;
        existingVCs = new HashMap<Integer, Cloud>();
        this.availableResources = Config.MAX_RESOURCE_QUOTA;
        this.timeSync = timeSync;
        timeSync.register();
        System.out.println("Vehicle " + id + " initialised.");
    } 

    /* Returns false if segment has changed */ 
    public boolean updatePosition() {
        posX += speedX;
        int segmentStart = Config.SEGMENT_LENGTH * segmentRef.id;
        int segmentEnd = Config.SEGMENT_LENGTH * (segmentRef.id + 1);
        if (posX < segmentStart || posX > segmentEnd) return false;
        return true;
    }

    public void handleRJOIN(Packet newPacket) {
        // VC for requested app id must be present
        int appId = newPacket.appId;
        if (existingVCs.containsKey(appId)) {
            existingVCs.get(appId).addMember(newPacket);
        } 
        else {
            // ERROR
        }
    }

    public int getDonatedResources() {
        return ThreadLocalRandom.current().nextInt(availableResources);
    }

    public void run() {
        while (currentTime <= stopTime) {
            // System.out.println("Vehicle " + id + " starting interval " + currentTime);
            if (writePending) {
                boolean written = mediumRef.write(pendingPacket);
                if (written) {
                    writePending = false;
                    pendingPacket = null;
                }
            }   
            else {
                // 1. (Randomly) Request for an application
                int hasRequest = ThreadLocalRandom.current().nextInt(5);
                if (hasRequest == 1) {
                    int appType = ThreadLocalRandom.current().nextInt(Config.APPLICATION_TYPE_COUNT);
                    if (existingVCs.getOrDefault(appType, null) != null) { // RJOIN
                        writePending = true;
                        pendingPacket = new Packet(Config.PACKET_TYPE.RJOIN, id, currentTime, LQI, appType, getDonatedResources());
                        handleRJOIN(pendingPacket);
                    } 
                    else { // RREQ
                        writePending = true;
                        pendingPacket = new Packet(Config.PACKET_TYPE.RREQ, id, currentTime, LQI, appType, Config.MAX_RESOURCE_QUOTA , getDonatedResources());
                    }
                }
                // 2. Read medium queue
                messageQueue = mediumRef.read(id);
                while (!writePending && messageQueue != null && !messageQueue.isEmpty()) {
                    Packet p = messageQueue.poll();
                    assert p != null : "Read packet is NULL";                    
                    System.out.println("Packet " + ": Vehicle " + id + " read " + p.type + " from " + p.senderId + " at " + p.sentTime);
                    
                    switch (p.type) {
                        case RREQ:
                            int donatedResources = getDonatedResources();
                            if (donatedResources > 0) {
                                availableResources -= donatedResources;
                                writePending = true;
                                pendingPacket = new Packet(Config.PACKET_TYPE.RREP, id, currentTime, LQI, p.appId, donatedResources);
                            }
                            break;
                        case RJOIN:
                            handleRJOIN(p);
                            break;
                        case RREP:
                            // Currently RSU handles RREP's
                            break;
                        case RACK:
                            existingVCs.put(p.appId, p.cloud);
                            break;
                    }
                }
            }
            timeSync.arriveAndAwaitAdvance();
            currentTime++;
        }
        System.out.println("Vehicle " + id + " stopped after " + stopTime + " ms.");   
    }
}