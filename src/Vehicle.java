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

        this.timeSync = timeSync;
        timeSync.register();
        // System.out.println("Vehicle " + id + " initialised at position " + this.position);
    } 

    public boolean hasSegmentChanged(double oldPosition, double newPosition) {
        int oldSegmentId = (int) (oldPosition / Config.SEGMENT_LENGTH);
        int newSegmentId = (int) (newPosition / Config.SEGMENT_LENGTH);
        return oldSegmentId != newSegmentId;
    }
    
    // public boolean almostInNewSegment(double position) {
    //     int segmentId = (int) (position / Config.SEGMENT_LENGTH);
    //     double segmentEnd = (segmentId + 1) * Config.SEGMENT_LENGTH;
    //     if ((segmentEnd - position) / Config.SEGMENT_LENGTH > 0.9) {
    //         return true;
    //     }
    // }

    public void updatePosition() {
        double newPosition = position + direction * speed * (currentTime- lastUpdated);
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
        // Send a RREP in reply
        transmitQueue.add(new Packet(simulatorRef, Config.PACKET_TYPE.RREP, id, currentTime, direction * speed, p.appId));
    }

    public void handleRJOIN(Packet joinPacket) {
        Cloud cloud = clouds.get(joinPacket.appId);
        if (cloud != null && cloud.isCloudLeader(id)) {
            cloud.addRJOINPacket(joinPacket);
        }
        else {
            // do nothing, cloud leader will redistribute the work
        }
    }

    public void handleRACK(Packet ackPacket) {
        // Save this cloud
        Cloud cloud = ackPacket.cloud;
        clouds.put(ackPacket.appId, cloud);
        if (cloud != null && cloud.isCloudLeader(id)) {
            Packet pstartPacket = new Packet(simulatorRef, Config.PACKET_TYPE.PSTART, id, currentTime, cloud.appId, cloud.getWorkAssignment());
            transmitQueue.add(pstartPacket);
            handlePSTART(pstartPacket); // honor self-contribution
        }
    }
    
    public void handlePSTART(Packet startPacket) {
        // Add an alarm for contribution
        int assignedWork = startPacket.workAssignment.getOrDefault(id, 0);
        if (assignedWork > 0) {
            processQueue.add(new ProcessBlock(startPacket.appId, assignedWork));
        }
        return;
    }

    public void handlePDONE(Packet donePacket) {
        Cloud cloud = clouds.get(donePacket.appId);
        if (cloud == null || !cloud.isCloudLeader(id)) return;

        cloud.markAsDone(donePacket.senderId, donePacket.workDoneAmount);
        if (cloud.workingMemberCount == 0) {
            if (cloud.processPendingRJOIN(currentTime)) {
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

    public void run() {
        while (currentTime <= stopTime) {
            // System.out.println("Vehicle " + id + " starting interval " + currentTime);
            updatePosition();
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
                    // System.out.println(id + " has completed " + nextBlock.workAmount + " work at " + nextBlock.completionTime);
                    Packet p = new Packet(simulatorRef, Config.PACKET_TYPE.PDONE, id, currentTime, nextBlock.appId, nextBlock.workAmount);
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
                int appType = ThreadLocalRandom.current().nextInt(Config.APPLICATION_TYPE_COUNT);
                if (clouds.getOrDefault(appType, null) != null) { // RJOIN
                    Packet pendingPacket = 
                        new Packet(simulatorRef, Config.PACKET_TYPE.RJOIN, id, currentTime, speed * direction, appType, Config.MAX_RESOURCE_QUOTA);
                    transmitQueue.add(pendingPacket);
                    handleRJOIN(pendingPacket);
                } 
                else { // RREQ
                    transmitQueue.add(
                        new Packet(simulatorRef, Config.PACKET_TYPE.RREQ, id, currentTime, speed * direction, appType, Config.MAX_RESOURCE_QUOTA)
                    );
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
                        // Currently only RSU handles RREP's
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