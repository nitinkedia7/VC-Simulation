/**
 * RoadSideUnit.java: Implements static RSU's which follow
 * our vehicular cloud life-cycle algorithm. Analogous to
 * Vehicle class implementation.
 * The main task of RSU is to form a cloud. For this
 * when it gets a RREQ, it initialises a cloud and waits till sufficient members
 * have been recruited. During this time RSU is effectively the cloud leader
 * and hence most of the functions handlePacket() class can be reused.
 * (Both RSU's and vehicles have the logic of building clouds in them)
 */
package advanced;

import java.util.*;
import java.util.concurrent.*;
import infrastructure.*;

public class RoadSideUnit implements Callable<Integer> {
    int id; // Negative integers -1, -2, ...
    float position; // static
    int currentTime;
    // Communication parameters
    int channelId;
    Queue<Packet> transmitQueue;
    Queue<Packet> receiveQueue;
    int readTillIndex;
    Map<Integer, Cloud> clouds; // clouds in the segment indexed by app id
    Statistics statsStore;
    Medium mediumRef;
    int backoffTime;
    int contentionWindowSize;

    // Constructor allocate space for the data structures
    public RoadSideUnit(int id, float position, Statistics statsStore, Medium mediumRef) {
        this.id = id;
        this.position = position;
        this.currentTime = 0;
        this.statsStore = statsStore;
        this.mediumRef = mediumRef;
        this.channelId = 0;
        this.transmitQueue = new LinkedList<Packet>();
        this.receiveQueue = new LinkedList<Packet>();
        this.readTillIndex = 0;
        this.clouds = new HashMap<Integer, Cloud>();
        this.backoffTime = 0;
        this.contentionWindowSize = Config.CONTENTION_WINDOW_BASE;
        // System.out.println("RSU     " + id + " initialised at position " + this.position);
    }

    public void handleRREQ(Packet reqPacket) {
        Cloud cloud = clouds.get(reqPacket.getAppId());
        // System.out.println("RSU got request from id " + reqPacket.getSenderId());
        if (cloud == null) {
            clouds.put(reqPacket.getAppId(), new Cloud(statsStore, reqPacket.getAppId(), id, true, reqPacket.getGenTime()));
            clouds.get(reqPacket.getAppId()).addNewRequest(
                reqPacket.getSenderId(),
                reqPacket.getAppId(),
                reqPacket.getOfferedResources(),
                reqPacket.getVelocity(),
                reqPacket.getGenTime()
            );
        }
        else if (cloud.isCloudLeader(id)) {
            cloud.addNewRequest(
                reqPacket.getSenderId(),
                reqPacket.getAppId(),
                reqPacket.getOfferedResources(),
                reqPacket.getVelocity(),
                reqPacket.getGenTime()  
            );
        }
    }

    public void handleRJOIN(Packet p) {
        // If this vehicle is the leader then it enqueues it
        Cloud cloud = clouds.get(p.getAppId());
        if (cloud != null && cloud.isCloudLeader(id)) {
            cloud.addNewRequest(
                p.getSenderId(),
                p.getAppId(),
                p.getOfferedResources(),
                p.getVelocity(),
                p.getGenTime()
            );
        }
    }

    public void handleRREP(Packet donorPacket) {
        Cloud cloud = clouds.get(donorPacket.getAppId());
        if (cloud == null) {
            System.out.println("No cloud present for app " + donorPacket.getAppId());
            return;
        }
        if (!cloud.isCloudLeader(id)) {
            return;
        }
        statsStore.incrRrepReceiveCount();
        cloud.addMember(donorPacket.getSenderId(), donorPacket.getOfferedResources(), donorPacket.getVelocity());
        if (cloud.justMetResourceQuota()) {
            cloud.electLeader();
            transmitQueue.add(new PacketCustom(statsStore, Config.PACKET_TYPE.RACK, id, currentTime, donorPacket.getAppId(), cloud));
        }
    }

    public void handleRPROBE(Packet probe) {
        Cloud cloud = clouds.get(probe.getAppId());
        if (cloud == null) {
            // System.out.println("RSU got probe from id " + probe.getSenderId());
            Packet presentPacket = new PacketCustom(statsStore, Config.PACKET_TYPE.RPRESENT, id, currentTime, probe.getAppId(), probe.getSenderId(), true);
            transmitQueue.add(presentPacket);
        }
        else if (cloud.isCloudLeader(id)) {
            // RSU replies as a leader since cloud formation has RREP's
            Packet presentPacket = new PacketCustom(statsStore, Config.PACKET_TYPE.RPRESENT, id, currentTime, probe.getAppId(), probe.getSenderId(), false);
            transmitQueue.add(presentPacket);
        }
    }

    public void handleRTEAR(Packet tearPacket) {
        Cloud cloud = clouds.get(tearPacket.getAppId());
        if (cloud == null || !cloud.isCloudLeader(tearPacket.getSenderId())) {
            return;
        }
        else {
            clouds.remove(tearPacket.getAppId());
        }
    }

    public Integer call() {
        // System.out.println("RSU     " + id + " starting interval " + currentTime);
        Channel targetChannel = mediumRef.getChannel(channelId);
        
        // Attempt to transmit packets in transmitQueue only if there are any pending packets
        if (!transmitQueue.isEmpty()) {
            if (backoffTime == 0) {
                if (targetChannel.isFree(id, position)) {
                    Packet packet = transmitQueue.poll();
                    targetChannel.transmitPacket(packet, currentTime, position);
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
                if (targetChannel.senseFree(id, position)) {
                    backoffTime--;
                }
            }
        }

        // Also get and process receivedPackets
        int newPacketCount = targetChannel.receivePackets(id, readTillIndex, currentTime, position, receiveQueue); 
        readTillIndex += newPacketCount;
        while (!receiveQueue.isEmpty()) {
            Packet p = receiveQueue.poll();
            assert p != null : "Read packet is NULL";                      
            switch (p.getType()) {
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
                    handleRTEAR(p);
                    break;
                case RPROBE:
                    handleRPROBE(p);
                    break;
                default:
                    // PSTART, PDONE are between VC members
                    break;
            }
        }
        currentTime++;
        return currentTime;
    }
}