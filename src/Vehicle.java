import java.util.*;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;

public class Vehicle implements Runnable {
    int id;
    double position;
    double speed;
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
    int availableResources;
    int backoffTime;
    int contentionWindowSize;
    
    class ProcessBlock {
        int completionTime;
        int appId;

        public ProcessBlock(int appId, int donatedResources) {
            if (currentTime > bookedTillTime) {
                bookedTillTime = currentTime;
            }
            bookedTillTime += donatedResources * Config.PROCESSING_SPEED;
            this.completionTime = bookedTillTime;
            this.appId = appId;  
        }
    }
    int bookedTillTime;
    Queue<ProcessBlock> processQueue;

    public Vehicle(int id, Phaser timeSync, Simulator simulatorRef, Medium mediumRef, int stopTime) {
        this.id = id;
        Random random = new Random();
        this.position = random.nextDouble() * Config.ROAD_END;
        this.speed = Config.VEHICLE_SPEED_MIN + random.nextDouble() * (Config.VEHICLE_SPEED_MAX - Config.VEHICLE_SPEED_MIN);
        this.direction = random.nextInt(2);
        this.lastUpdated = 0;
        this.currentTime = 0;
        this.stopTime = stopTime;
        this.simulatorRef = simulatorRef;
        this.mediumRef = mediumRef;
        clouds = new HashMap<Integer, Cloud>();
        this.channelId = 0;
        this.transmitQueue = new LinkedList<Packet>();
        this.receiveQueue = new LinkedList<Packet>();
        this.readTillIndex = 0;
        this.availableResources = Config.MAX_RESOURCE_QUOTA;
        this.bookedTillTime = 0;
        processQueue = new LinkedList<ProcessBlock>();
        this.backoffTime = 0;
        this.contentionWindowSize = Config.CONTENTION_WINDOW_BASE;

        this.timeSync = timeSync;
        timeSync.register();
        System.out.println("Vehicle " + id + " initialised at position " + this.position);
    } 

    public boolean hasSegmentChanged(double oldPosition, double newPosition) {
        int oldSegmentId = (int) (oldPosition / Config.SEGMENT_LENGTH);
        int newSegmentId = (int) (newPosition / Config.SEGMENT_LENGTH);
        return oldSegmentId != newSegmentId;
    }                   

    public void updatePosition() {
        double newPosition = position + direction * speed * (currentTime- lastUpdated);
        if (newPosition > Config.ROAD_END) newPosition = Config.ROAD_START;
        else if (newPosition < Config.ROAD_START) newPosition = Config.ROAD_END;
        if (hasSegmentChanged(position, newPosition)) {
            clouds.forEach((appId, cloud) -> {
                if (cloud.isMember(id)) {
                    transmitQueue.add(
                        new Packet(simulatorRef, Config.PACKET_TYPE.RLEAVE, id, currentTime, appId)
                    );
                }
            });
            clouds.clear();
        }
        position = newPosition;

        Random random = new Random();
        double speedChange = random.nextDouble() * 10;
        Boolean accelerate = random.nextBoolean();
        if (accelerate && (speed + speedChange) < Config.VEHICLE_SPEED_MAX) {
            speed += speedChange;
        }
        if (!accelerate && (speed - speedChange) > Config.VEHICLE_SPEED_MIN) {
            speed -= speedChange;
        }
        this.lastUpdated = currentTime;  
        return;
    }

    public void handleRREQ(Packet p) {
        int donatedResources = getDonatedResources();
        if (donatedResources > 0) {
            // availableResources -= donatedResources;
            transmitQueue.add(new Packet(simulatorRef, Config.PACKET_TYPE.RREP, id, currentTime, direction * speed, p.appId, donatedResources));
        }
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

    public void handleRACK(Packet ackPacket) {
        // Save this cloud
        Cloud cloud = ackPacket.cloud;
        clouds.put(ackPacket.appId, cloud);
        if (isCloudLeader(cloud)) {
            cloud.workingMemberCount = cloud.members.size();
            Packet pstartPacket = new Packet(simulatorRef, Config.PACKET_TYPE.PSTART, id, currentTime, cloud.appId, cloud);
            transmitQueue.add(pstartPacket);
            handlePSTART(pstartPacket); // honor self-contribution
        }
    }
    
    public void handlePSTART(Packet startPacket) {
        // Add an alarm for contribution
        int donatedResources = startPacket.cloud.getDonatedAmount(id);
        if (donatedResources > 0) {
            processQueue.add(new ProcessBlock(startPacket.appId, donatedResources));
        }
        return;
    }

    public void handlePDONE(Packet donePacket) {
        Cloud cloud = clouds.get(donePacket.appId);
        if (!isCloudLeader(cloud)) return;
        cloud.markAsDone(donePacket.senderId);
        if (cloud.workingMemberCount == 0) {
            if (cloud.processPendingRJOIN()) {
                cloud.workingMemberCount = cloud.members.size();
                Packet pstartPacket = new Packet(simulatorRef, Config.PACKET_TYPE.PSTART, id, currentTime, cloud.appId, cloud);
                transmitQueue.add(pstartPacket);
                handlePSTART(pstartPacket);
            }
            else {
                transmitQueue.add(new Packet(simulatorRef, Config.PACKET_TYPE.RTEAR, id, currentTime, donePacket.appId));
                cloud.printStats(false);
                clouds.remove(donePacket.appId);
            }
        }
    }

    public void handleRLEAVE(Packet packet) {
        /*
        // Case I: Leader leaves
        if (this.id == nextLeader) {
            cloud.currentLeader = this.id;        
        }
        
        // Case II: Member leaves
        if (this.id == currentLeader) {

        }    
        */
    }

    public Boolean isCloudLeader(Cloud cloud) {
        return cloud != null && cloud.currentLeaderId == id;  
    }

    public int getDonatedResources() {
        if (availableResources < 10) return availableResources; 
        return availableResources/2 + ThreadLocalRandom.current().nextInt(availableResources / 2);
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
            // Put processed work done (if any) to receiveQueue
            while (!processQueue.isEmpty()) {
                ProcessBlock nextBlock = processQueue.peek();
                if (currentTime >= nextBlock.completionTime) {
                    transmitQueue.add(new Packet(simulatorRef, Config.PACKET_TYPE.PDONE, id, currentTime, nextBlock.appId));
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
                        new Packet(simulatorRef, Config.PACKET_TYPE.RJOIN, id, currentTime, speed * direction, appType, getDonatedResources());
                    transmitQueue.add(pendingPacket);
                    handleRJOIN(pendingPacket);
                } 
                else { // RREQ
                    transmitQueue.add(
                        new Packet(simulatorRef, Config.PACKET_TYPE.RREQ, id, currentTime, speed * direction, appType, Config.MAX_RESOURCE_QUOTA, getDonatedResources())
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