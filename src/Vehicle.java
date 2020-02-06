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
    
    class ProcessBlock {
        int completionTime;
        int appId;

        public ProcessBlock(int appId, int donatedResources) {
            if (currentTime > bookedTillTime) {
                bookedTillTime = currentTime;
            }
            bookedTillTime += donatedResources * Config.PROCESSING_SPEED;
            this.completionTime = bookedTillTime;
            this.appId = appId;  
        }
    }
    int bookedTillTime;
    Queue<ProcessBlock> processQueue;

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
        this.bookedTillTime = 0;
        processQueue = new LinkedList<ProcessBlock>();

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

    public void handleRREQ(Packet p) {
        int donatedResources = getDonatedResources();
        if (donatedResources > 0) {
            availableResources -= donatedResources;
            writePending = true;
            pendingPacket = new Packet(Config.PACKET_TYPE.RREP, id, currentTime, LQI, p.appId, donatedResources);
        }
    }

    public void handleRACK(Packet ackPacket) {
        // Save this cloud
        Cloud cloud = ackPacket.cloud;
        existingVCs.put(ackPacket.appId, cloud);
        if (isCloudLeader(cloud)) {
            cloud.workingMemberCount = cloud.members.size();
            writePending = true;
            pendingPacket = new Packet(Config.PACKET_TYPE.PSTART, id, currentTime, cloud.appId);
        }
    }
    
    public void handlePSTART(Packet startPacket) {
        // Add an alarm for contribution
        int donatedResources = startPacket.cloud.getDonatedAmount(id);
        if (donatedResources > 0) {
            processQueue.add(new ProcessBlock(startPacket.appId, donatedResources));
        }
        return;
    }

    public void handlePDONE(Packet donePacket) {
        Cloud cloud = existingVCs.get(donePacket.appId);
        if (!isCloudLeader(cloud)) return;
        cloud.markAsDone(donePacket.senderId);
        if (cloud.workingMemberCount == 0) {
            writePending = true;
            pendingPacket = new Packet(Config.PACKET_TYPE.RTEAR, id, currentTime, donePacket.appId);
        }
    }

    public void handleRJOIN(Packet newPacket) {
        // TODO
    }

    public Boolean isCloudLeader(Cloud cloud) {
        return cloud != null && cloud.requestorId == id;  
    }

    public int getDonatedResources() {
        return ThreadLocalRandom.current().nextInt(availableResources);
    }

    public void run() {
        while (currentTime <= stopTime) {
            // System.out.println("Vehicle " + id + " starting interval " + currentTime);
            if (!processQueue.isEmpty()) {
                ProcessBlock nextBlock = processQueue.peek();
                if (currentTime >= nextBlock.completionTime) {
                    writePending = true;
                    pendingPacket = new Packet(Config.PACKET_TYPE.PDONE, id, currentTime, nextBlock.appId);
                    processQueue.remove();
                }
            }
            else if (writePending) {
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
                            handleRREQ(p);
                            break;
                        case RJOIN:
                            handleRJOIN(p);
                            break;
                        case RREP:
                            // Currently only RSU handles RREP's
                            break;
                        case RACK:
                            handleRACK(p);
                            break;
                        case RTEAR:
                            existingVCs.remove(p.appId);
                            break;
                        case PSTART:
                            handlePSTART(p);
                            break;
                        case PDONE:
                            handlePDONE(p);
                            break;
                        default:
                            System.out.println("Unhandled packet type " + p.type);
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