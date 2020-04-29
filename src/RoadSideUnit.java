import java.util.*;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;

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
    Simulator simulatorRef;
    Medium mediumRef;
    int backoffTime;
    int contentionWindowSize;

    public RoadSideUnit(int id, double position, Phaser timeSync, Simulator simulatorRef, Medium mediumRef, int stopTime) {
        this.id = id;
        this.position = position;
        this.currentTime = 0;
        this.stopTime = stopTime;
        this.simulatorRef = simulatorRef;
        this.mediumRef = mediumRef;
        this.channelId = 0;
        this.transmitQueue = new LinkedList<Packet>();
        this.receiveQueue = new LinkedList<Packet>();
        this.readTillIndex = 0;
        this.clouds = new HashMap<Integer, Cloud>();
        this.timeSync = timeSync;
        timeSync.register();
        this.backoffTime = 0;
        this.contentionWindowSize = Config.CONTENTION_WINDOW_BASE;
        // System.out.println("RSU     " + id + " initialised at position " + this.position);
    }

    public void handleRREQ(Packet reqPacket) {
        Cloud cloud = clouds.get(reqPacket.appId);
        if (cloud == null) { // RSU starts making a cloud
            clouds.put(reqPacket.appId, new Cloud(simulatorRef, reqPacket.appId, id, true, reqPacket.genTime));
            clouds.get(reqPacket.appId).addNewRequest(reqPacket);
        }
        else if (cloud.isCloudLeader(id)) {
            cloud.addNewRequest(reqPacket);
        }
    }

    public void handleRJOIN(Packet p) {
        // If this vehicle is the leader then it enqueues it
        Cloud cloud = clouds.get(p.appId);
        if (cloud != null && cloud.isCloudLeader(id)) {
            cloud.addNewRequest(p);
        }
    }

    public void handleRREP(Packet donorPacket) {
        Cloud cloud = clouds.get(donorPacket.appId);
        if (cloud == null) {
            System.out.println("No cloud present for app " + donorPacket.appId);
            return;
        }
        if (!cloud.isCloudLeader(id)) {
            return;
        }
        cloud.addMember(donorPacket.senderId, donorPacket.offeredResources, donorPacket.velocity);
        if (cloud.justMetResourceQuota()) {
            cloud.electLeader();
            transmitQueue.add(new Packet(simulatorRef, Config.PACKET_TYPE.RACK, id, currentTime, donorPacket.appId, cloud));
        }
    }

    public void handleRPROBE(Packet probe) {
        Cloud cloud = clouds.get(probe.appId);
        if (cloud == null) {
            Packet presentPacket = new Packet(simulatorRef, Config.PACKET_TYPE.RPRESENT, id, currentTime, probe.appId, probe.senderId, true);
            transmitQueue.add(presentPacket);
        }
        else if (cloud.isCloudLeader(id)) {
            // RSU replies as a leader since cloud formation has RREP's
            Packet presentPacket = new Packet(simulatorRef, Config.PACKET_TYPE.RPRESENT, id, currentTime, probe.appId, probe.senderId, false);
            transmitQueue.add(presentPacket);
        }
    }

    public void run() {
        while (currentTime <= stopTime) {
            // System.out.println("RSU     " + id + " starting interval " + currentTime);
            Channel targetChannel = mediumRef.channels[channelId];
            
            // Attempt to transmit packets in transmitQueue only if there are any pending packets
            if (!transmitQueue.isEmpty()) {
                if (backoffTime == 0) {
                    if (targetChannel.isFree(id, position)) {
                        while (!transmitQueue.isEmpty()) {
                            Packet packet = transmitQueue.poll();
                            targetChannel.transmitPacket(packet, currentTime, position);
                        }        
                        targetChannel.stopTransmit(id);
                        // Reset contention window
                        contentionWindowSize = Config.CONTENTION_WINDOW_BASE;
                    }
                    else {
                        contentionWindowSize *= 2;
                        if (contentionWindowSize > Config.CONTENTION_WINDOW_MAX) {
                            System.out.println("Vehicle could not transmit in backoff, retrying again");
                            backoffTime = 0;
                            contentionWindowSize = Config.CONTENTION_WINDOW_BASE;
                        }
                        else {
                            backoffTime = ThreadLocalRandom.current().nextInt(contentionWindowSize) + 1;
                        }
                    }
                }
                else {
                    if (targetChannel.isFree(id, position)) {
                        backoffTime--;
                        targetChannel.stopTransmit(id);
                    }
                }
            }

            // Also get and process receivedPackets
            int newPacketCount = targetChannel.receivePackets(id, readTillIndex, currentTime, position, receiveQueue); 
            readTillIndex += newPacketCount;
            while (!receiveQueue.isEmpty()) {
                Packet p = receiveQueue.poll();
                assert p != null : "Read packet is NULL";      
                p.printRead(id);              
                
                switch (p.type) {
                    case RREQ:
                        handleRREQ(p);
                        break;
                    case RJOIN:
                        handleRJOIN(p);
                        break;
                    case RREP:
                        handleRREP(p);
                        break;
                    case RACK:
                        // RSU sends RACK, not process it
                        break;
                    case RTEAR:
                        clouds.remove(p.appId);
                        break;
                    case RPROBE:
                        handleRPROBE(p);
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