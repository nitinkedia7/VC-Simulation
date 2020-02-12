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
    double position;
    Phaser timeSync;
    int currentTime;
    int stopTime;
    boolean writePending;
    Packet pendingPacket;
    Queue<Packet> messageQueue;
    Map<Integer, Cloud> clouds;
    Medium mediumRef;

    public RoadSideUnit(int id, double position, Phaser timeSync, Medium mediumRef, int stopTime) {
        this.id = id;
        this.position = position;
        this.currentTime = 0;
        this.stopTime = stopTime;
        this.mediumRef = mediumRef;
        this.writePending = false;
        clouds = new HashMap<Integer, Cloud>();
        this.timeSync = timeSync;
        timeSync.register();
        System.out.println("RSU     " + id + " initialised.");
    }

    public void handleRJOIN(Packet newPacket) {
       // TODO
    }

    public void handleRREQ(Packet reqPacket) {
        int appId = reqPacket.appId;
        if (clouds.containsKey(appId)) {
            return;
        }
        clouds.put(appId, new Cloud(reqPacket));
        // Also add the requestor as a donor
        handleRREP(reqPacket);
    }

    public void handleRREP(Packet donorPacket) {
        int appId = donorPacket.appId;
        assert clouds.containsKey(appId) : "No cloud present for app " + appId;
        clouds.get(appId).addMember(donorPacket);
        if (clouds.get(appId).metResourceQuota()) {
            writePending = true;
            pendingPacket = new Packet(Config.PACKET_TYPE.RACK, id, currentTime, appId, clouds.get(appId));
            clouds.get(appId).printStats(true);
        }
    }

    public void run() {
        while (currentTime <= stopTime) {
            // System.out.println("RSU     " + id + " starting interval " + currentTime);
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
                    assert p != null : "Read packet is NULL";                                       
                    p.printRead(id);
                    switch (p.type) {
                        case RREQ:
                            handleRREQ(p);
                            break;
                        case RREP:
                            handleRREP(p);
                            break;
                        case RJOIN:
                            handleRJOIN(p);
                            break;
                        case RACK:
                            // RSU sends RACK, not process it
                            break;
                        case RTEAR:
                            clouds.remove(p.appId);
                            break;
                        default:
                            // PSTART, PDONE are between VC members
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