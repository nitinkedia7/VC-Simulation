package advanced;

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
        int appId;
        int reqId;
        int workAmount;
        int completionTime;

        public ProcessBlock(int appId, int reqId, int donatedResources) {
            // System.out.println(id + " has got " + donatedResources + " work");
            if (currentTime > bookedTillTime) {
                bookedTillTime = currentTime;
            }
            bookedTillTime += donatedResources * Config.PROCESSING_SPEED;
            this.appId = appId;
            this.reqId = reqId;
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
        // System.out.println("Vehicle " + id + " initialised at position " + this.position);
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
            clouds.forEach((appId, cloud) -> {
                if (cloud != null && cloud.isMember(id)) {
                    transmitQueue.add(
                        new PacketCustom(statsStore, Config.PACKET_TYPE.RLEAVE, id, currentTime, appId)
                    );
                }
                if (cloud != null && cloud.isCloudLeader(id)) {
                    statsStore.incrLeaderLeaveCount();
                }
            });
            clouds.clear();
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

    public void handleRREQ(Packet reqPacket) {
        // If this vehicle is the leader then it enqueues it
        Cloud cloud = clouds.get(reqPacket.getAppId());
        if (cloud != null && cloud.isCloudLeader(id)) {
            cloud.addNewRequest(
                reqPacket.getSenderId(),
                reqPacket.getAppId(),
                reqPacket.getOfferedResources(),
                reqPacket.getVelocity()
            );
        }

        // Also send a RREP, if someone else's request
        if (reqPacket.getSenderId() != id) {
            Packet rrepPacket = new PacketCustom(statsStore, Config.PACKET_TYPE.RREP, id, currentTime, direction * speed, reqPacket.getAppId(), getRandomChunkSize());
            transmitQueue.add(rrepPacket);
            handleRREP(rrepPacket);
        }
    }

    public void handleRJOIN(Packet reqPacket) {
        // If this vehicle is the leader then it enqueues it
        Cloud cloud = clouds.get(reqPacket.getAppId());
        if (cloud != null && cloud.isCloudLeader(id)) {
            cloud.addNewRequest(
                reqPacket.getSenderId(),
                reqPacket.getAppId(),
                reqPacket.getOfferedResources(),
                reqPacket.getVelocity()
            );
            Map<Integer, Map<Integer,Integer>> newWorkStore = cloud.processPendingRequests();
            if (!newWorkStore.isEmpty()) {
                Packet pstartPacket = new PacketCustom(statsStore, Config.PACKET_TYPE.PSTART, id, currentTime, reqPacket.getAppId(), newWorkStore);
                transmitQueue.add(pstartPacket);
                handlePSTART(pstartPacket);
            }
        }
    }

    public void handleRREP(Packet donorPacket) {
        Cloud cloud = clouds.get(donorPacket.getAppId());
        if (cloud == null || !cloud.isCloudLeader(id)) return;
        statsStore.incrRrepReceiveCount();
        cloud.addMember(donorPacket.getSenderId(), donorPacket.getOfferedResources(), donorPacket.getVelocity());
        if (cloud.justMetResourceQuota()) {
            cloud.electLeader();
            Packet ackPacket = new PacketCustom(statsStore, Config.PACKET_TYPE.RACK, id, currentTime, donorPacket.getAppId(), cloud);
            transmitQueue.add(ackPacket);
            clouds.remove(donorPacket.getAppId());
            handleRACK(ackPacket);
        }
    }   

    public void handleRACK(Packet ackPacket) {
        // Save this cloud
        Cloud cloud = ackPacket.getCloud();
        clouds.put(ackPacket.getAppId(), cloud);
        if (cloud != null && cloud.isCloudLeader(id)) {
            cloud.recordCloudFormed(currentTime);
            // cloud.printStats();
            Map<Integer, Map<Integer,Integer>> workAssignment = cloud.processPendingRequests();
            Packet pstartPacket = new PacketCustom(statsStore, Config.PACKET_TYPE.PSTART, id, currentTime, ackPacket.getAppId(), workAssignment);
            transmitQueue.add(pstartPacket);
            handlePSTART(pstartPacket); // honor self-contribution
        }
    }
    
    public void handlePSTART(Packet startPacket) {
        startPacket.getWorkAssignment().forEach((reqId, appWorkAssignment) -> {
            int assignedWork = appWorkAssignment.getOrDefault(id, 0);
            if (assignedWork > 0) {
                // Add an alarm for contribution
                processQueue.add(new ProcessBlock(startPacket.getAppId(), reqId, assignedWork));
            }
        });
        return;
    }

    public void handlePDONE(Packet donePacket) {
        Cloud cloud = clouds.get(donePacket.getAppId());
        if (cloud == null || !cloud.isCloudLeader(id)) return;
        cloud.markAsDone(donePacket.getRequestId(), donePacket.getSenderId(), donePacket.getWorkDoneAmount());
        
        Map<Integer, Map<Integer,Integer>> newWorkStore = cloud.processPendingRequests();
        if (!newWorkStore.isEmpty()) {
            Packet pstartPacket = new PacketCustom(statsStore, Config.PACKET_TYPE.PSTART, id, currentTime, donePacket.getAppId(), newWorkStore);
            transmitQueue.add(pstartPacket);
            handlePSTART(pstartPacket);
        }
        else if (cloud.globalWorkStore.isEmpty()) {
            Packet tearPacket = new PacketCustom(statsStore, Config.PACKET_TYPE.RTEAR, id, currentTime, donePacket.getAppId());
            transmitQueue.add(tearPacket);
            clouds.remove(donePacket.getAppId());
        }
    }

    public void handleRLEAVE(Packet packet) {
        Cloud cloud = clouds.get(packet.getAppId());
        if (cloud == null) return;

        // First, reassign leader if needed
        if (cloud.isCloudLeader(packet.getSenderId()) && cloud.isNextLeader(id)) {
            cloud.assignNextLeader();
            statsStore.incrLeaderChangeCount();
        }
        // Then reassign the left work
        if (cloud.isCloudLeader(id)) {
            Map<Integer, Map<Integer,Integer>> workAssignment = cloud.reassignWork(packet.getSenderId());
            Packet pstartPacket = new PacketCustom(statsStore, Config.PACKET_TYPE.PSTART, id, currentTime, packet.getAppId(), workAssignment);
            transmitQueue.add(pstartPacket);
            handlePSTART(pstartPacket);
        }
    }

    public void selfInitiateCloudFormation() {
        // System.out.println("Vehicle " + id + " self-initiating cloud formation for appId " + pendingAppId);
        // Ensure that no cloud this appId exists till now
        Cloud cloud = clouds.get(pendingAppId);
        if (cloud != null) {
            Packet rjoinPacket = new PacketCustom (
                statsStore,
                Config.PACKET_TYPE.RJOIN,
                id,
                currentTime,
                speed * direction,
                pendingAppId,
                getRandomChunkSize()
            );
            transmitQueue.add(rjoinPacket);
            handleRJOIN(rjoinPacket);
        }
        else {
            Packet reqPacket = new PacketCustom (
                statsStore,
                Config.PACKET_TYPE.RREQ,
                id,
                currentTime,
                speed * direction,
                pendingAppId,
                getRandomChunkSize()
            );
            clouds.put(reqPacket.getAppId(), new Cloud(statsStore, reqPacket.getAppId(), id, false, pendingRequestGenTime));
            clouds.get(reqPacket.getAppId()).addNewRequest(
                reqPacket.getSenderId(),
                reqPacket.getAppId(),
                reqPacket.getOfferedResources(),
                reqPacket.getVelocity() 
            );
            transmitQueue.add(reqPacket);
        }
    }

    public void handleRPROBE(Packet probe) {
        Cloud cloud = clouds.get(probe.getAppId());
        if (cloud != null && cloud.isCloudLeader(id)) {
            // send RPRESENT
            Packet presentPacket = new PacketCustom (
                statsStore,
                Config.PACKET_TYPE.RPRESENT,
                id,
                currentTime,
                probe.getAppId(),
                probe.getSenderId(),
                false
            );
            transmitQueue.add(presentPacket);
            handleRPRESENT(presentPacket);
        }
    }

    public void handleRPRESENT(Packet packet) {
        if (!hasPendingRequest) return;
        if (!(packet.getAppId() == pendingAppId && packet.getRequestorId() == id)) return;

        hasPendingRequest = false;
        if (packet.didRsuReply()) {
            Packet rreqPacket = new PacketCustom (
                statsStore,
                Config.PACKET_TYPE.RREQ,
                id,
                currentTime,
                speed * direction,
                pendingAppId,
                getRandomChunkSize()
            );
            transmitQueue.add(rreqPacket);
            handleRREQ(rreqPacket);
        }
        else {
            Packet rjoinPacket = new PacketCustom (
                statsStore,
                Config.PACKET_TYPE.RJOIN,
                id, currentTime,
                speed * direction,
                pendingAppId,
                getRandomChunkSize()
            );
            transmitQueue.add(rjoinPacket);
            handleRJOIN(rjoinPacket);
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
        // System.out.println("Vehicle " + id + " starting interval " + currentTime);
        Channel targetChannel = mediumRef.getChannel(channelId);

        if (!transmitQueue.isEmpty()) {
            if (targetChannel.isFree(id, position)) {
                Packet packet = transmitQueue.poll();
                targetChannel.transmitPacket(packet, currentTime, position);    
            }
        }
        // Attempt to transmit packets in transmitQueue only if there are any pending packets
        // if (!transmitQueue.isEmpty()) {
        //     if (backoffTime == 0) {
        //         if (targetChannel.isFree(id, position)) {
        //             Packet packet = transmitQueue.poll();
        //             targetChannel.transmitPacket(packet, currentTime, position);    
        //             targetChannel.stopTransmit(id);
        //             // Reset contention window
        //             contentionWindowSize = Config.CONTENTION_WINDOW_BASE;
        //         }
        //         else {
        //             contentionWindowSize *= 2;
        //             if (contentionWindowSize > Config.CONTENTION_WINDOW_MAX) {
        //                 System.out.println("Vehicle could not transmit in backoff, retrying again");
        //                 backoffTime = 0;
        //                 contentionWindowSize = Config.CONTENTION_WINDOW_BASE;
        //             }
        //             else {
        //                 backoffTime = ThreadLocalRandom.current().nextInt(contentionWindowSize) + 1;
        //             }
        //         }
        //     }
        //     else {
        //         if (targetChannel.isFree(id, position)) {
        //             backoffTime--;
        //             targetChannel.stopTransmit(id);
        //         }
        //     }
        // }  

        // Put processed work done (if any) to transmitQueue
        while (!processQueue.isEmpty()) {
            ProcessBlock nextBlock = processQueue.peek();
            if (currentTime >= nextBlock.completionTime) {
                // System.out.println(id + " completed " + nextBlock.workAmount + " work at " + nextBlock.completionTime);
                Packet p = new PacketCustom(statsStore, Config.PACKET_TYPE.PDONE, id, currentTime, nextBlock.appId, nextBlock.workAmount, nextBlock.reqId);
                handlePDONE(p);
                transmitQueue.add(p);
                processQueue.remove();
            }
            else {
                break;
            }
        }

        if (hasPendingRequest) {
            // Wait Config.MAX_WAIT_TIME for a RQUEUE message, if received OK, else self-initiate
            if (currentTime >= pendingRequestGenTime + Config.MAX_RPRESENT_WAIT_TIME) {
                selfInitiateCloudFormation();
                hasPendingRequest = false;
            }
        } 
        else {
            // (Randomly) Request for an application
            int hasRequest = ThreadLocalRandom.current().nextInt(Config.INV_RREQ_PROB);
            int appId = ThreadLocalRandom.current().nextInt(Config.APPLICATION_TYPE_COUNT);
            if (hasRequest == 1) {
                if (clouds.get(appId) != null) {
                    Packet rjoinPacket = new PacketCustom (
                        statsStore,
                        Config.PACKET_TYPE.RJOIN,
                        id,
                        currentTime,
                        speed * direction,
                        appId,
                        getRandomChunkSize()
                    );
                    transmitQueue.add(rjoinPacket);
                    handleRJOIN(rjoinPacket);
                }
                else {
                    // Save this request
                    pendingRequestGenTime = currentTime;
                    pendingAppId = appId;
                    hasPendingRequest = true;
                    // send a RPROBE to see if RSU/CL is present for this appId
                    Packet probe = new PacketCustom(statsStore, Config.PACKET_TYPE.RPROBE, id, currentTime, appId);
                    transmitQueue.add(probe);
                    handleRPROBE(probe);
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
                    handleRACK(p);
                    break;
                case RTEAR:
                    handleRTEAR(p);
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
                case RPROBE:
                    handleRPROBE(p);
                    break;
                case RPRESENT:
                    handleRPRESENT(p);
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