import java.util.*;
import java.util.concurrent.locks.*;

public class Channel {
    int id;
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
    
    public Channel(int id) {
        this.id = id;
        packetQueue = new ArrayList<Packet>();
        packetQueueLock = new ReentrantReadWriteLock(Config.useFair);
        transmitterPositions = new LinkedList<Transmitter>();
        transmitterPositionsLock = new ReentrantLock();
    }

    public boolean isFree(int id, double position) {
        transmitterPositionsLock.lock();
        for (Transmitter t : transmitterPositions) {
            if (Math.abs(position - t.position) <= Config.TRANSMISSION_RANGE) {
                transmitterPositionsLock.unlock();
                return false;        
            }
        }
        transmitterPositions.add(new Transmitter(id, position));
        transmitterPositionsLock.unlock();
        return true;
    }

    public void stopTransmit(int id) {
        transmitterPositionsLock.lock();
        Iterator<Transmitter> iterator = transmitterPositions.iterator();
        while (iterator.hasNext()) {
            Transmitter transmitter = iterator.next();
            if (transmitter.id == id) {
                iterator.remove();
            }
        }
        transmitterPositionsLock.unlock();
    }

    public void transmitPacket(Packet packet, int currentTime, double currentPosition) {
        packetQueueLock.writeLock().lock();
        packetQueue.add(packet);
        packet.recordTransmission(currentTime, currentPosition);
        packetQueueLock.writeLock().unlock();
    }

    public int receivePackets(int readTillIndex, int currentTime, double position, Queue<Packet> receiveQueue) {
        packetQueueLock.readLock().lock();
        int newPacketCount = packetQueue.size() - readTillIndex;
        while (readTillIndex < packetQueue.size()) {
            Packet packet = packetQueue.get(readTillIndex);
            if (Math.abs(position - packet.position) <= Config.TRANSMISSION_RANGE) {
                receiveQueue.add(packet);
                packet.recordReception(currentTime);
            }
            readTillIndex++;
        }
        packetQueueLock.readLock().unlock();
        return newPacketCount;
    }
}