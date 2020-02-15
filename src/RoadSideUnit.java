import java.util.*;
import java.util.concurrent.Phaser;

public class RoadSideUnit implements Runnable {
    int id;
    double position;
    Phaser timeSync;
    int currentTime;
    int stopTime;
    int channelId;
    Queue<Packet> transmitQueue;
    Queue<Packet> receiveQueue;
    int readTillIndex;
    Map<Integer, Cloud> clouds;
    Medium mediumRef;

    public RoadSideUnit(int id, double position, Phaser timeSync, Medium mediumRef, int stopTime) {
        this.id = id;
        this.position = position;
        this.currentTime = 0;
        this.stopTime = stopTime;
        this.mediumRef = mediumRef;
        this.channelId = 0;
        this.transmitQueue = new LinkedList<Packet>();
        this.receiveQueue = new LinkedList<Packet>();
        this.readTillIndex = 0;
        this.clouds = new HashMap<Integer, Cloud>();
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
            transmitQueue.add(new Packet(Config.PACKET_TYPE.RACK, id, currentTime, appId, clouds.get(appId)));
            clouds.get(appId).printStats(true);
        }
    }

    public void run() {
        while (currentTime <= stopTime) {
            // System.out.println("RSU     " + id + " starting interval " + currentTime);
            
            // Attempt to transmit packets in transmitQueue
            Channel targetChannel = mediumRef.channels[channelId];
            if (targetChannel.isFree(id, position)) {
                while (!transmitQueue.isEmpty()) {
                    Packet packet = transmitQueue.poll();
                    targetChannel.transmitPacket(packet);
                }        
                targetChannel.stopTransmit(id);
            }

            // Also get and process receivedPackets
            int newPacketCount = targetChannel.receivePackets(readTillIndex, position, receiveQueue); 
            readTillIndex += newPacketCount;
            while (!receiveQueue.isEmpty()) {
                Packet p = receiveQueue.poll();
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
            timeSync.arriveAndAwaitAdvance();
            currentTime++;
        }
        System.out.println("RSU     " + id + " stopped after " + stopTime + " ms.");   
    }
}