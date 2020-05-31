/*
    classical/Vehicle.java: Clone of advanced/Vehicle.java
    with modifications for the classical algorithm:
    1. Vehicles always spawn their clouds for themselves
    2. In proposed algorithm, the key which identifies a clous is
    app_id (each category has its id) but such a concept is not present
    here. The key is vehicle_id since every vehicle makes its own cloud. 
*/

package classic;

import java.util.*;
import java.util.concurrent.*;
import infrastructure.*;

public class Vehicle implements Callable<Integer> {
    int id;
    float position;
    float speed;
    float averageSpeed;
    int direction;
    int lastUpdated;
    int currentTime;
    Statistics statsStore;
    Medium mediumRef;
    Map<Integer, Cloud> clouds;
    int channelId;
    Queue<Packet> transmitQueue;
    Queue<Packet> receiveQueue;
    int readTillIndex;
    int backoffTime;
    int contentionWindowSize;
    
    class ProcessBlock {
        int requestorId;
        int requestId;
        int workAmount;
        int completionTime;

        public ProcessBlock(int requestorId, int requestId, int donatedResources) {
            // System.out.println(id + " has got " + donatedResources + " work");
            if (currentTime > bookedTillTime) {
                bookedTillTime = currentTime;
            }
            bookedTillTime += donatedResources * Config.PROCESSING_SPEED;
            this.requestorId = requestorId;
            this.requestId = requestId;
            this.workAmount = donatedResources;  
            this.completionTime = bookedTillTime;
        }
    }
    int bookedTillTime;
    Queue<ProcessBlock> processQueue;

    int pendingAppId;
    int pendingRequestGenTime;
    boolean hasPendingRequest;

    public Vehicle(int id, float averageSpeed, Statistics statsStore, Medium mediumRef) {
        this.id = id;        
        this.position = ThreadLocalRandom.current().nextFloat() * Config.ROAD_END;
        this.averageSpeed = averageSpeed;
        this.speed = averageSpeed;
        this.direction = ThreadLocalRandom.current().nextInt(2);
        if (this.direction == 0) this.direction = -1; 
        this.lastUpdated = 0;
        this.currentTime = 0;
        this.statsStore = statsStore;
        this.mediumRef = mediumRef;
        this.clouds = new HashMap<Integer, Cloud>();
        this.channelId = 0;
        this.transmitQueue = new LinkedList<Packet>();
        this.receiveQueue = new LinkedList<Packet>();
        this.readTillIndex = 0;
        this.bookedTillTime = 0;
        this.processQueue = new LinkedList<ProcessBlock>();
        this.backoffTime = 0;
        this.contentionWindowSize = Config.CONTENTION_WINDOW_BASE;
        this.hasPendingRequest = false;
        // System.out.println("Vehicle Classic " + id + " initialised at position " + this.position);
    }

    public boolean hasSegmentChanged(float oldPosition, float newPosition) {
        int oldSegmentId = (int) (oldPosition / Config.SEGMENT_LENGTH);
        int newSegmentId = (int) (newPosition / Config.SEGMENT_LENGTH);
        return oldSegmentId != newSegmentId;
    }

    public void updatePosition() {
        float newPosition = position + (direction * speed * (currentTime - lastUpdated)) / 1000;
        if (newPosition > Config.ROAD_END) {
            newPosition = Config.ROAD_END - 1;
            direction = -1;
        }
        else if (newPosition < Config.ROAD_START) {
            newPosition = Config.ROAD_START;
            direction = 1;
        }
        
        // Send out RLEAVE's if vehicle is inside a new segment
        if (hasSegmentChanged(position, newPosition)) {
            clouds.forEach((requestorId, cloud) -> {
                if (cloud != null) {
                    if (cloud.isMember(id) && !cloud.isCloudLeader(id)) {
                        transmitQueue.add(
                            new PacketCustom(statsStore, Config.PACKET_TYPE.RLEAVE, id, currentTime, requestorId)
                        );
                    }
                    if (cloud.isCloudLeader(id)) {
                        statsStore.incrLeaderLeaveCount();
                    }
                }
            });
            Cloud cloud = clouds.get(id);
            clouds.clear();
            clouds.put(id, cloud);
            // Clear any pending requests
            hasPendingRequest = false;
        }
        position = newPosition;

        float newSpeed;
        do {
            newSpeed = (float) ThreadLocalRandom.current().nextGaussian();
            newSpeed = newSpeed * Config.VEHICLE_SPEED_STD_DEV + averageSpeed;
        } while (newSpeed < Config.VEHICLE_SPEED_MIN || newSpeed > Config.VEHICLE_SPEED_MAX);
        speed = newSpeed;
        lastUpdated = currentTime;
        return;
    }

    private int getRandomChunkSize() {
        int multiplier = ThreadLocalRandom.current().nextInt(Config.APPLICATION_TYPE_COUNT) + 1;
        return Config.WORK_CHUNK_SIZE * multiplier;
    }

    private void handleRREQ(Packet packet) {
        assert(packet.getRequestorId() != id);
        Packet rrepPacket = new PacketCustom (statsStore, Config.PACKET_TYPE.RREP, id, currentTime, packet.getRequestorId(), getRandomChunkSize());
        transmitQueue.add(rrepPacket);
    }

    private void handleRREP(Packet packet) {
        assert(packet.getSenderId() != id);

        Cloud cloud = clouds.get(packet.getRequestorId());
        if (cloud == null || !cloud.isCloudLeader(id)) return;

        statsStore.incrRrepReceiveCount();
        cloud.addMember(packet.getSenderId(), packet.getOfferedResources(), 0);
        if (cloud.justMetResourceQuota()) {
            Packet ackPacket = new PacketCustom(statsStore, Config.PACKET_TYPE.RACK, id, currentTime, packet.getRequestorId(), cloud);
            transmitQueue.add(ackPacket);
            clouds.remove(id);
            handleRACK(ackPacket);
        }
    }

    private void handleRACK(Packet packet)  {
        assert(packet.getType() == Config.PACKET_TYPE.RACK);
        
        Cloud cloud = packet.getCloud();
        clouds.put(packet.getRequestorId(), cloud);
        if (cloud != null && cloud.isCloudLeader(id)) {
            cloud.recordCloudFormed(currentTime);
            // cloud.printStats();
            Map<Integer, Map<Integer,Integer>> workAssignment = cloud.processPendingRequests();
            Packet pstartPacket = new PacketCustom (statsStore, Config.PACKET_TYPE.PSTART, id, currentTime, packet.getRequestorId(), workAssignment);
            transmitQueue.add(pstartPacket);
            handlePSTART(pstartPacket);
        }
    }

    private void handlePSTART(Packet packet)  {
        assert(packet.getType() == Config.PACKET_TYPE.PSTART);
        packet.getWorkAssignment().forEach((requestId, appWorkAssignment) -> {
            int assignedWork = appWorkAssignment.getOrDefault(id, 0);
            if (assignedWork > 0) {
                // Add an alarm for contribution
                processQueue.add(new ProcessBlock(packet.getRequestorId(), requestId, assignedWork));
            }
        });
        return;
    }

    private void handlePDONE(Packet donePacket)  {
        assert(donePacket.getType() == Config.PACKET_TYPE.PDONE);
        
        Cloud cloud = clouds.get(donePacket.getRequestorId());
        if (cloud == null || !cloud.isCloudLeader(id)) return;

        cloud.markAsDone(donePacket.getRequestId(), donePacket.getSenderId(), donePacket.getWorkDoneAmount(), currentTime);
        Map<Integer, Map<Integer,Integer>> newWorkStore = cloud.processPendingRequests();
        if (!newWorkStore.isEmpty()) {
            Packet pstartPacket = new PacketCustom(statsStore, Config.PACKET_TYPE.PSTART, id, currentTime, id, newWorkStore);
            transmitQueue.add(pstartPacket);
            handlePSTART(pstartPacket);
        }
        else if (cloud.allRequestsServiced()) {
            Packet tearPacket = new PacketCustom(statsStore, Config.PACKET_TYPE.RTEAR, id, currentTime, id);
            transmitQueue.add(tearPacket);
            clouds.remove(id);
        }
    }

    private void handleRLEAVE(Packet packet) {
        assert(packet.getType() == Config.PACKET_TYPE.RLEAVE);

        Cloud cloud = clouds.get(packet.getRequestorId());
        if (cloud == null) return;
        if (cloud.isCloudLeader(packet.getSenderId())) {
            clouds.remove(packet.getRequestorId());
        }
        else if (cloud.isCloudLeader(id)) {
            // Reassign the left work
            Map<Integer, Map<Integer,Integer>> workAssignment = cloud.reassignWork(packet.getSenderId());
            Packet pstartPacket = new PacketCustom (statsStore, Config.PACKET_TYPE.PSTART, id, currentTime, id, workAssignment);
            transmitQueue.add(pstartPacket);
            handlePSTART(pstartPacket);
        }
    }

    public void handleRTEAR(Packet tearPacket) {
        assert(tearPacket.getType() == Config.PACKET_TYPE.RTEAR);

        Cloud cloud = clouds.get(tearPacket.getRequestorId());
        if (cloud == null || !cloud.isCloudLeader(tearPacket.getRequestorId())) {
            return;
        }
        else {
            clouds.remove(tearPacket.getRequestorId());
        }
    }

    private void selfInitiateCloudFormation() {
        // System.out.println("Vehicle " + id + " self-initiating cloud formation for appId " + pendingAppId);
        Cloud cloud = clouds.get(id);
        if (cloud == null) {
            Packet reqPacket = new PacketCustom (
                statsStore,
                Config.PACKET_TYPE.RREQ,
                id,
                currentTime,
                id,
                getRandomChunkSize()
            );
            clouds.put(id, new Cloud(statsStore, pendingAppId, id, false, pendingRequestGenTime));
            clouds.get(id).addNewRequest(id, pendingAppId, reqPacket.getOfferedResources(), 0, reqPacket.getGenTime());
            transmitQueue.add(reqPacket);
        }
        else {
            Packet reqPacket = new PacketCustom (
                statsStore,
                Config.PACKET_TYPE.RREQ,
                id,
                currentTime,
                id,
                getRandomChunkSize()
            );
            cloud.addNewRequest(id, pendingAppId, getRandomChunkSize(), 0, reqPacket.getGenTime());
            Map<Integer, Map<Integer,Integer>> newWorkStore = cloud.processPendingRequests();
            if (!newWorkStore.isEmpty()) {
                Packet pstartPacket = new PacketCustom(statsStore, Config.PACKET_TYPE.PSTART, id, currentTime, id, newWorkStore);
                transmitQueue.add(pstartPacket);
                handlePSTART(pstartPacket);
            }
        }
        hasPendingRequest = false;
    }

    public Integer call() {
        // System.out.println("Vehicle " + id + " starting interval " + currentTime);
        Channel targetChannel = mediumRef.getChannel(channelId);

        // Attempt to transmit if any packet is queued
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
                        System.out.println("Vehicle " + id + " could not transmit in backoff, retrying again");
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

        // Put processed work done (if any) to transmitQueue
        while (!processQueue.isEmpty()) {
            ProcessBlock nextBlock = processQueue.peek();
            if (currentTime >= nextBlock.completionTime) {
                // System.out.println(id + " completed " + nextBlock.workAmount + " work at " + nextBlock.completionTime);
                Packet p = new PacketCustom (
                    statsStore,
                    Config.PACKET_TYPE.PDONE,
                    id,
                    currentTime,
                    nextBlock.requestorId,
                    nextBlock.requestId,
                    nextBlock.workAmount
                );
                handlePDONE(p);
                transmitQueue.add(p);
                processQueue.remove();
            }
            else {
                break;
            }
        }

        // (Randomly) Request for an application
        int hasRequest = ThreadLocalRandom.current().nextInt(Config.INV_RREQ_PROB);
        if (hasRequest == 1) {
            hasPendingRequest = true;
            pendingRequestGenTime = currentTime;
            pendingAppId = ThreadLocalRandom.current().nextInt(Config.APPLICATION_TYPE_COUNT);
            selfInitiateCloudFormation();
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
                case RREP:
                    handleRREP(p);
                    break;
                case RACK:
                    handleRACK(p);
                    break;
                case PSTART:
                    handlePSTART(p);
                    break;
                case PDONE:
                    handlePDONE(p);
                    break;
                case RLEAVE:
                    handleRLEAVE(p);
                    break;
                case RTEAR:
                    handleRTEAR(p);
                    break;
                default:
                    System.out.println("Unhandled packet type " + p.getType());
                    break;
            }
        }
        if (currentTime % 50 == 0) updatePosition();
        return (++currentTime);
    }

}