import java.util.*;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;

public class Vehicle implements Runnable {
    int id;
    double position;
    double speed;
    double averageSpeed;
    int direction;
    int lastUpdated;
    int currentTime;
    int stopTime;
    Phaser timeSync;
    Simulator simulatorRef;
    Medium mediumRef;
    Map<Integer, Cloud> clouds;
    int channelId;
    Queue<Packet> transmitQueue;
    Queue<Packet> receiveQueue;
    int readTillIndex;
    int backoffTime;
    int contentionWindowSize;
    
    class ProcessBlock {
        int completionTime;
        int appId;
        int workAmount;

        public ProcessBlock(int appId, int donatedResources) {
            // System.out.println(id + " has got " + donatedResources + " work");
            if (currentTime > bookedTillTime) {
                bookedTillTime = currentTime;
            }
            bookedTillTime += donatedResources * Config.PROCESSING_SPEED;
            this.completionTime = bookedTillTime;
            this.appId = appId;
            this.workAmount = donatedResources;  
        }
    }
    int bookedTillTime;
    Queue<ProcessBlock> processQueue;

    Packet pendingReqPacket;
    boolean hasPendingReq;

    public Vehicle(int id, Phaser timeSync, Simulator simulatorRef, Medium mediumRef, int stopTime, double averageSpeed) {
        this.id = id;        
        this.position = ThreadLocalRandom.current().nextDouble() * Config.ROAD_END;
        this.averageSpeed = averageSpeed;
        this.speed = averageSpeed;
        this.direction = ThreadLocalRandom.current().nextInt(2);
        if (this.direction == 0) this.direction = -1; 
        this.lastUpdated = 0;
        this.currentTime = 0;
        this.stopTime = stopTime;
        this.simulatorRef = simulatorRef;
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
        this.hasPendingReq = false;

        this.timeSync = timeSync;
        timeSync.register();
        // System.out.println("Vehicle " + id + " initialised at position " + this.position);
    } 

    public boolean hasSegmentChanged(double oldPosition, double newPosition) {
        int oldSegmentId = (int) (oldPosition / Config.SEGMENT_LENGTH);
        int newSegmentId = (int) (newPosition / Config.SEGMENT_LENGTH);
        return oldSegmentId != newSegmentId;
    }

    public void updatePosition() {
        double newPosition = position + (direction * speed * (currentTime - lastUpdated)) / 1000;
        if (newPosition > Config.ROAD_END) {
            newPosition = Config.ROAD_END;
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
                        new Packet(simulatorRef, Config.PACKET_TYPE.RLEAVE, id, currentTime, appId)
                    );
                }
                if (cloud != null && cloud.isCloudLeader(id)) {
                    simulatorRef.incrLeaderLeaveCount();
                }
            });
            clouds.clear();
            // Clear any pending requests
            hasPendingReq = false;
            pendingReqPacket = null;
        }
        position = newPosition;

        double newSpeed;
        do {
            newSpeed = ThreadLocalRandom.current().nextGaussian();
            newSpeed = newSpeed * Config.VEHICLE_SPEED_STD_DEV + averageSpeed;
        } while (newSpeed < Config.VEHICLE_SPEED_MIN || newSpeed > Config.VEHICLE_SPEED_MAX);
        speed = newSpeed;
        lastUpdated = currentTime;
        return;
    }

    public void handleRREQ(Packet p) {
        // If this vehicle is the leader then it enqueues it
        Cloud cloud = clouds.get(p.appId);
        if (cloud != null && cloud.isCloudLeader(id)) {
            cloud.queueRequestPacket(p);
        }

        // Also send a RREP, if someone else's request
        if (p.senderId != id) {
            Packet rrepPacket = new Packet(simulatorRef, Config.PACKET_TYPE.RREP, id, currentTime, direction * speed, p.appId);
            transmitQueue.add(rrepPacket);
            handleRREP(rrepPacket);
        }
    }

    public void handleRJOIN(Packet p) {
        // If this vehicle is the leader then it enqueues it
        Cloud cloud = clouds.get(p.appId);
        if (cloud != null && cloud.isCloudLeader(id)) {
            cloud.queueRequestPacket(p);
        }
    }

    public void handleRREP(Packet donorPacket) {
        Cloud cloud = clouds.get(donorPacket.appId);
        if (cloud == null || !cloud.isCloudLeader(id)) return;
        cloud.addMember(donorPacket);
        if (cloud.justMetResourceQuota()) {
            cloud.electLeader();
            Packet ackPacket = new Packet(simulatorRef, Config.PACKET_TYPE.RACK, id, currentTime, donorPacket.appId, cloud);
            transmitQueue.add(ackPacket);
            handleRACK(ackPacket);
            clouds.remove(donorPacket.appId);
        }
    }   

    public void handleRACK(Packet ackPacket) {
        // Save this cloud
        Cloud cloud = ackPacket.cloud;
        clouds.put(ackPacket.appId, cloud);
        if (cloud != null && cloud.isCloudLeader(id)) {
            cloud.assignWork();
            cloud.recordCloudFormed(currentTime, "formed");
            Packet pstartPacket = new Packet(simulatorRef, Config.PACKET_TYPE.PSTART, id, currentTime, cloud.appId, cloud.getWorkAssignment());
            transmitQueue.add(pstartPacket);
            handlePSTART(pstartPacket); // honor self-contribution
        }
    }
    
    public void handlePSTART(Packet startPacket) {
        // Add an alarm for contribution
        int assignedWork = startPacket.workAssignment.getOrDefault(id, 0);
        if (assignedWork > 0) {
            // System.out.println(id + " receives " + assignedWork + " work for appId " + startPacket.appId);
            processQueue.add(new ProcessBlock(startPacket.appId, assignedWork));
        }
        return;
    }

    public void handlePDONE(Packet donePacket) {
        Cloud cloud = clouds.get(donePacket.appId);
        if (cloud == null || !cloud.isCloudLeader(id)) return;
        cloud.markAsDone(donePacket.senderId, donePacket.workDoneAmount);
        if (cloud.workFinished()) {
            // System.out.println("Work finished for appId " + cloud.appId);
            if (cloud.processPendingRequest(currentTime)) {
                Packet pstartPacket = new Packet(simulatorRef, Config.PACKET_TYPE.PSTART, id, currentTime, cloud.appId, cloud.getWorkAssignment());
                transmitQueue.add(pstartPacket);
                handlePSTART(pstartPacket);
            }
            else {
                transmitQueue.add(new Packet(simulatorRef, Config.PACKET_TYPE.RTEAR, id, currentTime, donePacket.appId));
                cloud.printStats("deleted");
                clouds.remove(donePacket.appId);
            }
        }
    }

    public void handleRLEAVE(Packet packet) {
        Cloud cloud = clouds.get(packet.appId);
        if (cloud == null) return;

        // First, reassign leader if needed
        if (cloud.isCloudLeader(packet.senderId) && cloud.isNextLeader(id)) {
            cloud.assignNextLeader();
            simulatorRef.incrLeaderChangeCount();
        }
        // Then reassign the left work
        if (cloud.isCloudLeader(id)) {
            Map<Integer, Integer> workAssignment = cloud.reassignWork(packet.senderId);
            Packet pstartPacket = new Packet(simulatorRef, Config.PACKET_TYPE.PSTART, id, currentTime, cloud.appId, workAssignment);
            transmitQueue.add(pstartPacket);
            handlePSTART(pstartPacket);
        }
    }

    public void selfInitiateCloudFormation(Packet reqPacket) {
        // Ensure that no cloud this appId exists till now
        Cloud cloud = clouds.get(reqPacket.appId);
        if (cloud != null) {
            System.out.println("Self initiating cloud formation when one is already present.");
            transmitQueue.add(reqPacket);
            return;
        }
        clouds.put(reqPacket.appId, new Cloud(simulatorRef, reqPacket.appId, id, false));
        clouds.get(reqPacket.appId).addRequestor(reqPacket);
        transmitQueue.add(reqPacket);
    }

    public void handleRPROBE(Packet probe) {
        Cloud cloud = clouds.get(probe.appId);
        if (cloud != null && cloud.isCloudLeader(id)) {
            // send RPRESENT
            Packet presentPacket = new Packet(simulatorRef, Config.PACKET_TYPE.RPRESENT, id, currentTime, probe.appId, probe.senderId, true);
            transmitQueue.add(presentPacket);
            handleRPRESENT(presentPacket);
        }
    }

    public void handleRPRESENT(Packet packet) {
        if (!hasPendingReq) return;
        if (packet.appId == pendingReqPacket.appId && packet.requestorId == id) {
            hasPendingReq = false;
            Packet rjoinPacket = new Packet(simulatorRef, Config.PACKET_TYPE.RJOIN, id, currentTime, speed * direction, pendingReqPacket.appId, Config.MAX_RESOURCE_QUOTA);
            transmitQueue.add(rjoinPacket);
        }
    }

    public void run() {
        while (currentTime <= stopTime) {
            // System.out.println("Vehicle " + id + " starting interval " + currentTime);
            if (currentTime % 50 == 0) updatePosition();
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
            // Put processed work done (if any) to transmitQueue
            while (!processQueue.isEmpty()) {
                ProcessBlock nextBlock = processQueue.peek();
                if (currentTime >= nextBlock.completionTime) {
                    // System.out.println(id + " completed " + nextBlock.workAmount + " work at " + nextBlock.completionTime);
                    Packet p = new Packet(simulatorRef, Config.PACKET_TYPE.PDONE, id, currentTime, nextBlock.appId, nextBlock.workAmount);
                    handlePDONE(p);
                    transmitQueue.add(p);
                    processQueue.remove();
                }
                else {
                    break;
                }
            }

            if (hasPendingReq) {
                // Wait Config.MAX_WAIT_TIME for a RQUEUE message, if received OK, else self-initiate
                if (currentTime >= pendingReqPacket.genTime + Config.MAX_RQUEUE_WAIT_TIME) {
                    System.out.println("Vehicle " + id + " self-initiating cloud formation for appId " + pendingReqPacket.appId);
                    selfInitiateCloudFormation(pendingReqPacket);
                    hasPendingReq = false;
                }
            } 
            else {
                // (Randomly) Request for an application
                int hasRequest = ThreadLocalRandom.current().nextInt(Config.INV_RREQ_PROB);
                int appId = ThreadLocalRandom.current().nextInt(Config.APPLICATION_TYPE_COUNT);
                if (hasRequest == 1) {
                    if (clouds.get(appId) != null) {
                        Packet rjoinPacket = new Packet(simulatorRef, Config.PACKET_TYPE.RJOIN, id, currentTime, speed * direction, appId, Config.MAX_RESOURCE_QUOTA);
                        transmitQueue.add(rjoinPacket);
                        handleRJOIN(rjoinPacket);
                    }
                    else {
                        pendingReqPacket = new Packet(simulatorRef, Config.PACKET_TYPE.RREQ, id, currentTime, speed * direction, appId, Config.MAX_RESOURCE_QUOTA); 
                        // Save this request
                        hasPendingReq = true;
                        // send a RPROBE to see if RSU/CL is present for this appId
                        Packet probe = new Packet(simulatorRef, Config.PACKET_TYPE.RPROBE, id, currentTime, appId);
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
                        handleRACK(p);
                        break;
                    case RTEAR:
                        clouds.remove(p.appId);
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
                        System.out.println("Unhandled packet type " + p.type);
                        break;
                }
            }
            timeSync.arriveAndAwaitAdvance();
            currentTime++;
        }
        System.out.println("Vehicle " + id + " stopped after " + stopTime + " ms.");   
    }
}