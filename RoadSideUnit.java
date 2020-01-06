import java.util.*;
import java.util.concurrent.Phaser;

/*
*   Tasks at RSU:
    1. Listen for RREQ, RREP messages for each app id, if say N vehicles ping same
    appId, make a vehicular cloud and broadcast member LQI's.
    We make this simpler model first.
    2. Need to handle cloud disintegration.
 */

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
    // for each appId need a list of rreps
    Map<Integer, List<Packet>> existingVCs;
    Map<Integer, List<Packet>> pendingRequests;
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
        this.writePending = false;
        existingVCs = new HashMap<Integer, List<Packet>>();
        pendingRequests = new HashMap<Integer, List<Packet>>();
        this.timeSync = timeSync;
        timeSync.register();
        System.out.println("RSU     " + id + " initialised.");
    }

    public void handleNewRequest(Packet newPacket) {
        int appId = newPacket.appId;
        // VC for that appId already exists
        if (existingVCs.containsKey(appId)) {
            writePending = false;
            pendingPacket = null;
            return;
        }
        // No VC exists for that appID
        if (!pendingRequests.containsKey(appId)) {
            pendingRequests.put(appId, new ArrayList<Packet>());
        }
        pendingRequests.get(appId).add(newPacket);
        if (pendingRequests.get(appId).size() >= Config.MIN_CLOUD_SIZE) {
            existingVCs.put(appId, pendingRequests.get(appId));
            pendingRequests.remove(appId);
        }
        writePending = true;
        pendingPacket = new Packet(Config.PACKET_TYPE.RACK, id, currentTime, LQI, appId, null);        
        return;
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
                            handleNewRequest(p);
                            break;
                        case RREP:
                            handleNewRequest(p);
                            break;
                        case RJOIN:
                            break;
                        case RACK:
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