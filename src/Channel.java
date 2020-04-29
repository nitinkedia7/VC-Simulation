import java.util.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.Phaser;

public class Channel implements Runnable {
    int id;
    int currentTime;
    int stopTime;
    Phaser timeSync;
    List<Packet> packetQueue;
    ReadWriteLock packetQueueLock;

    public class Transmitter {
        int id;
        double position;

        public Transmitter(int id, double position) {
            this.id = id;
            this.position = position;
        }
    }

    List<Transmitter> transmitterPositions;
    Lock transmitterPositionsLock;
    
    public Channel(int id, int stopTime, Phaser timeSync) {
        this.id = id;
        this.currentTime = 0;
        this.stopTime = stopTime;
        this.timeSync = timeSync;
        timeSync.register();
        packetQueue = new ArrayList<Packet>();
        packetQueueLock = new ReentrantReadWriteLock(Config.useFair);
        transmitterPositions = new LinkedList<Transmitter>();
        transmitterPositionsLock = new ReentrantLock(Config.useFair);
    }

    public boolean isFree(int id, double position) {
        transmitterPositionsLock.lock();
        for (Transmitter t : transmitterPositions) {
            int segment1 = (int) (position / Config.SEGMENT_LENGTH);
            int segment2 = (int) (t.position / Config.SEGMENT_LENGTH);
            if (Math.abs(segment1 - segment2) <= 1) {
                transmitterPositionsLock.unlock();
                return false;        
            }
        }
        transmitterPositions.add(new Transmitter(id, position));
        transmitterPositionsLock.unlock();
        return true;
    }

    // public void stopTransmit(int id) {
    //     transmitterPositionsLock.lock();
    //     Iterator<Transmitter> iterator = transmitterPositions.iterator();
    //     while (iterator.hasNext()) {
    //         Transmitter transmitter = iterator.next();
    //         if (transmitter.id == id) {
    //             iterator.remove();
    //         }
    //     }
    //     transmitterPositionsLock.unlock();
    // }

    public void transmitPacket(Packet packet, int currentTime, double currentPosition) {
        packetQueueLock.writeLock().lock();
        packetQueue.add(packet);
        packet.recordTransmission(currentTime, currentPosition);
        packetQueueLock.writeLock().unlock();
    }

    public int receivePackets(int receiverId, int readTillIndex, int currentTime, double position, Queue<Packet> receiveQueue) {
        packetQueueLock.readLock().lock();
        int newPacketCount = packetQueue.size() - readTillIndex;
        while (readTillIndex < packetQueue.size()) {
            Packet packet = packetQueue.get(readTillIndex);

            int segment1 = (int) (position / Config.SEGMENT_LENGTH);
            int segment2 = (int) (packet.position / Config.SEGMENT_LENGTH);
            int diff = 0;
            if (packet.type == Config.PACKET_TYPE.RLEAVE) diff = 1;

            if (packet.senderId != receiverId && Math.abs(segment1 - segment2) <= diff) {
                receiveQueue.add(packet);
                packet.recordReception(currentTime);
            }
            readTillIndex++;
        }
        packetQueueLock.readLock().unlock();
        return newPacketCount;
    }

    private void cleanup() {
        transmitterPositionsLock.lock();
        // System.err.println("Interval " + currentTime + ": transmitter positions: "); 
        // for (Transmitter t : transmitterPositions) {
        //     System.err.println(t.id + "," + t.position + " "); 
        // }
        transmitterPositions.clear();
        transmitterPositionsLock.unlock();
    }

    public void run() {
        while (currentTime <= stopTime) {
            timeSync.arriveAndAwaitAdvance();
            cleanup();
            timeSync.arriveAndAwaitAdvance();
            currentTime++;
        }
    }
}