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
        System.out.println("RSU     " + id + " initialised.");
    }

    public void handleRJOIN(Packet joinPacket) {
        Cloud cloud = clouds.get(joinPacket.appId);
        if (isCloudLeader(cloud)) {
            cloud.addRJOINPacket(joinPacket);
        }
        else {
            // do nothing, cloud leader will redistribute the work
        }
    }

    public Boolean isCloudLeader(Cloud cloud) {
        return cloud != null && cloud.requestorId == id;  
    }

    public void handleRREQ(Packet reqPacket) {
        int appId = reqPacket.appId;
        if (clouds.containsKey(appId)) {
            // TODO: handle simultaneous RREQ requests
            return;
        }
        clouds.put(appId, new Cloud(simulatorRef, reqPacket));
        // Also add the requestor as a donor
        handleRREP(reqPacket);
    }

    public void handleRREP(Packet donorPacket) {
        int appId = donorPacket.appId;
        assert clouds.containsKey(appId) && clouds.get(appId) != null && donorPacket != null : "No cloud present for app " + appId;
        clouds.get(appId).addMember(donorPacket);
        if (clouds.get(appId).metResourceQuota()) {            
            clouds.get(appId).recordCloudFormed(currentTime);
            clouds.get(appId).printStats(true);
            transmitQueue.add(new Packet(simulatorRef, Config.PACKET_TYPE.RACK, id, currentTime, appId, clouds.get(appId)));
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
                    targetChannel.transmitPacket(packet, currentTime, position);
                }        
                targetChannel.stopTransmit(id);
                // Reset contention window
                contentionWindowSize = Config.CONTENTION_WINDOW_BASE;
            }
            // if (backoffTime == 0) {
            //     if (targetChannel.isFree(id, position)) {
            //         while (!transmitQueue.isEmpty()) {
            //             Packet packet = transmitQueue.poll();
            //             targetChannel.transmitPacket(packet, currentTime, position);
            //         }        
            //         targetChannel.stopTransmit(id);
            //         // Reset contention window
            //         contentionWindowSize = Config.CONTENTION_WINDOW_BASE;
            //     }
            //     else {
            //         contentionWindowSize *= 2;
            //         if (contentionWindowSize > Config.CONTENTION_WINDOW_MAX) {
            //             System.out.println("Vehicle could not transmit in backoff, retrying again");
            //             backoffTime = 0;
            //             contentionWindowSize = Config.CONTENTION_WINDOW_BASE;
            //         }
            //         else {
            //             backoffTime = ThreadLocalRandom.current().nextInt(contentionWindowSize) + 1;
            //         }
            //     }
            // }
            // else {
            //     if (targetChannel.isFree(id, position)) {
            //         backoffTime--;
            //     }
            // }

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