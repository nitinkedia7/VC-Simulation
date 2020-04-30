import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Vehicle implements Runnable {
    int id;
    float position;
    float speed;
    float averageSpeed;
    int direction;
    int lastUpdated;
    int currentTime;
    int stopTime;
    PhaserCustom timeSync;
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

    public Vehicle(int id, PhaserCustom timeSync, Simulator simulatorRef, Medium mediumRef, int stopTime, float averageSpeed) {
        this.id = id;        
        this.position = ThreadLocalRandom.current().nextFloat() * Config.ROAD_END;
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
        this.hasPendingRequest = false;

        this.timeSync = timeSync;
        timeSync.register();
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

    public void handleRREQ(Packet p) {
        // If this vehicle is the leader then it enqueues it
        Cloud cloud = clouds.get(p.appId);
        if (cloud != null && cloud.isCloudLeader(id)) {
            cloud.addNewRequest(p);
        }

        // Also send a RREP, if someone else's request
        if (p.senderId != id) {
            Packet rrepPacket = new Packet(simulatorRef, Config.PACKET_TYPE.RREP, id, currentTime, direction * speed, p.appId, getRandomChunkSize());
            transmitQueue.add(rrepPacket);
            handleRREP(rrepPacket);
        }
    }

    public void handleRJOIN(Packet p) {
        // If this vehicle is the leader then it enqueues it
        Cloud cloud = clouds.get(p.appId);
        if (cloud != null && cloud.isCloudLeader(id)) {
            cloud.addNewRequest(p);
            Map<Integer, Map<Integer,Integer>> newWorkStore = cloud.processPendingRequests();
            if (!newWorkStore.isEmpty()) {
                Packet pstartPacket = new Packet(simulatorRef, Config.PACKET_TYPE.PSTART, id, currentTime, cloud.appId, newWorkStore);
                transmitQueue.add(pstartPacket);
                handlePSTART(pstartPacket);
            }
        }
    }

    public void handleRREP(Packet donorPacket) {
        Cloud cloud = clouds.get(donorPacket.appId);
        if (cloud == null || !cloud.isCloudLeader(id)) return;
        simulatorRef.incrRrepReceiveCount();
        cloud.addMember(donorPacket.senderId, donorPacket.offeredResources, donorPacket.velocity);
        if (cloud.justMetResourceQuota()) {
            cloud.electLeader();
            Packet ackPacket = new Packet(simulatorRef, Config.PACKET_TYPE.RACK, id, currentTime, donorPacket.appId, cloud);
            transmitQueue.add(ackPacket);
            clouds.remove(donorPacket.appId);
            handleRACK(ackPacket);
        }
    }   

    public void handleRACK(Packet ackPacket) {
        // Save this cloud
        Cloud cloud = ackPacket.cloud;
        clouds.put(ackPacket.appId, cloud);
        if (cloud != null && cloud.isCloudLeader(id)) {
            cloud.recordCloudFormed(currentTime);
            // cloud.printStats();
            Map<Integer, Map<Integer,Integer>> workAssignment = cloud.processPendingRequests();
            Packet pstartPacket = new Packet(simulatorRef, Config.PACKET_TYPE.PSTART, id, currentTime, cloud.appId, workAssignment);
            transmitQueue.add(pstartPacket);
            handlePSTART(pstartPacket); // honor self-contribution
        }
    }
    
    public void handlePSTART(Packet startPacket) {
        startPacket.workAssignment.forEach((reqId, appWorkAssignment) -> {
            int assignedWork = appWorkAssignment.getOrDefault(id, 0);
            if (assignedWork > 0) {
                // Add an alarm for contribution
                processQueue.add(new ProcessBlock(startPacket.appId, reqId, assignedWork));
            }
        });
        return;
    }

    public void handlePDONE(Packet donePacket) {
        Cloud cloud = clouds.get(donePacket.appId);
        if (cloud == null || !cloud.isCloudLeader(id)) return;
        cloud.markAsDone(donePacket.reqId, donePacket.senderId, donePacket.workDoneAmount);
        
        Map<Integer, Map<Integer,Integer>> newWorkStore = cloud.processPendingRequests();
        if (!newWorkStore.isEmpty()) {
            Packet pstartPacket = new Packet(simulatorRef, Config.PACKET_TYPE.PSTART, id, currentTime, cloud.appId, newWorkStore);
            transmitQueue.add(pstartPacket);
            handlePSTART(pstartPacket);
        }
        else if (cloud.globalWorkStore.isEmpty()) {
            Packet tearPacket = new Packet(simulatorRef, Config.PACKET_TYPE.RTEAR, id, currentTime, donePacket.appId);
            transmitQueue.add(tearPacket);
            clouds.remove(donePacket.appId);
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
            Map<Integer, Map<Integer,Integer>> workAssignment = cloud.reassignWork(packet.senderId);
            Packet pstartPacket = new Packet(simulatorRef, Config.PACKET_TYPE.PSTART, id, currentTime, cloud.appId, workAssignment);
            transmitQueue.add(pstartPacket);
            handlePSTART(pstartPacket);
        }
    }

    public void selfInitiateCloudFormation() {
        // System.out.println("Vehicle " + id + " self-initiating cloud formation for appId " + pendingAppId);
        // Ensure that no cloud this appId exists till now
        Cloud cloud = clouds.get(pendingAppId);
        if (cloud != null) {
            Packet rjoinPacket = new Packet(
                simulatorRef,
                Config.PACKET_TYPE.RJOIN,
                id,
                currentTime,
                speed * direction,
                pendingAppId,
                Config.APPLICATION_REQUIREMENT[pendingAppId],
                getRandomChunkSize()
            );
            transmitQueue.add(rjoinPacket);
            handleRJOIN(rjoinPacket);
        }
        else {
            Packet reqPacket = new Packet(
                simulatorRef,
                Config.PACKET_TYPE.RREQ,
                id,
                currentTime,
                speed * direction,
                pendingAppId,
                Config.APPLICATION_REQUIREMENT[pendingAppId],
                getRandomChunkSize()
            );
            clouds.put(reqPacket.appId, new Cloud(simulatorRef, reqPacket.appId, id, false, pendingRequestGenTime));
            clouds.get(reqPacket.appId).addNewRequest(reqPacket);
            transmitQueue.add(reqPacket);
        }
    }

    public void handleRPROBE(Packet probe) {
        Cloud cloud = clouds.get(probe.appId);
        if (cloud != null && cloud.isCloudLeader(id)) {
            // send RPRESENT
            Packet presentPacket = new Packet(simulatorRef, Config.PACKET_TYPE.RPRESENT, id, currentTime, probe.appId, probe.senderId, false);
            transmitQueue.add(presentPacket);
            handleRPRESENT(presentPacket);
        }
    }

    public void handleRPRESENT(Packet packet) {
        if (!hasPendingRequest) return;
        if (!(packet.appId == pendingAppId && packet.requestorId == id)) return;

        hasPendingRequest = false;
        if (packet.rsuReplied) {
            Packet rreqPacket = new Packet(
                simulatorRef,
                Config.PACKET_TYPE.RREQ,
                id,
                currentTime,
                speed * direction,
                pendingAppId,
                Config.APPLICATION_REQUIREMENT[pendingAppId],
                getRandomChunkSize()
            );
            transmitQueue.add(rreqPacket);
            handleRREQ(rreqPacket);
        }
        else {
            Packet rjoinPacket = new Packet(
                simulatorRef,
                Config.PACKET_TYPE.RJOIN,
                id, currentTime,
                speed * direction,
                pendingAppId,
                Config.APPLICATION_REQUIREMENT[pendingAppId],
                getRandomChunkSize()
            );
            transmitQueue.add(rjoinPacket);
            handleRJOIN(rjoinPacket);
        }
    }

    public void handleRTEAR(Packet tearPacket) {
        Cloud cloud = clouds.get(tearPacket.appId);
        if (cloud == null || !cloud.isCloudLeader(tearPacket.senderId)) {
            return;
        }
        else {
            clouds.remove(tearPacket.appId);
        }
    }

    public void run() {
        while (currentTime <= stopTime) {
            // System.out.println("Vehicle " + id + " starting interval " + currentTime);
            Channel targetChannel = mediumRef.channels[channelId];

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
                    Packet p = new Packet(simulatorRef, Config.PACKET_TYPE.PDONE, id, currentTime, nextBlock.appId, nextBlock.workAmount, nextBlock.reqId);
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
                        Packet rjoinPacket = new Packet(
                            simulatorRef,
                            Config.PACKET_TYPE.RJOIN,
                            id,
                            currentTime,
                            speed * direction,
                            appId,
                            Config.APPLICATION_REQUIREMENT[pendingAppId],
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
                        System.out.println("Unhandled packet type " + p.type);
                        break;
                }
            }
            if (currentTime % 50 == 0) updatePosition();
            timeSync.arriveAndAwaitAdvance();
            currentTime++;
        }
        // System.out.println("Vehicle " + id + " stopped after " + stopTime + " ms.");   
    }
}