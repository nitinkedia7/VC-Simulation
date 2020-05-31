/*
    Channel.java: Channel implementation supporting CSMA-CA.
    This class is thread-safe.
*/

package infrastructure; 

import java.util.*;
import java.util.concurrent.locks.*;

public class Channel {
    int id;
    // List of all packets transmission in this channel
    List<Packet> packetQueue;
    ReadWriteLock packetQueueLock;

    // A transmitter is a vehicle/RSU
    // Only position is required since we need to implement range
    public class Transmitter {
        int id;
        float position;

        public Transmitter(int id, float position) {
            this.id = id;
            this.position = position;
        }
    }
    // List of transmitter currently transmitting packets
    // Cleared every ms by the simulator
    List<Transmitter> transmitterPositions;
    Lock transmitterPositionsLock;
    
    public Channel(int id) {
        this.id = id;
        packetQueue = new ArrayList<Packet>();
        packetQueueLock = new ReentrantReadWriteLock(Config.useFair);
        transmitterPositions = new LinkedList<Transmitter>();
        transmitterPositionsLock = new ReentrantLock(Config.useFair);
    }

    // This function is used to sense if a channel is idle
    // with an intent to transmit immediately if found so.
    // If idle, put the position of the caller in current transmissions list
    // to book the channel
    public boolean isFree(int id, float position) {
        transmitterPositionsLock.lock();
        // A channel is sensed free from a position if no other
        // transmission is going on in that segment (full-segment broadcast)
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

    // To sense channel without an intent to transmit
    // eg. while decrementing backoff counter
    public boolean senseFree(int id, float position) {
        transmitterPositionsLock.lock();
        for (Transmitter t : transmitterPositions) {
            int segment1 = (int) (position / Config.SEGMENT_LENGTH);
            int segment2 = (int) (t.position / Config.SEGMENT_LENGTH);
            if (Math.abs(segment1 - segment2) <= 1) {
                transmitterPositionsLock.unlock();
                return false;        
            }
        }
        transmitterPositionsLock.unlock();
        return true;
    }

    // Put the packet in the the channel queue to be distributed to other receivers
    // Must have received true from isFree() first
    public void transmitPacket(Packet packet, int currentTime, float currentPosition) {
        packetQueueLock.writeLock().lock();
        packetQueue.add(packet);
        packet.recordTransmission(currentTime, currentPosition);
        packetQueueLock.writeLock().unlock();
    }

    /*
        This function is to receive packets. In real life transmission and reception happen simultaneously.
        But this is not possible in a simulation which is concurrent. Implementation:
        1. The channel has a list of packets with earliest transmitted packets at the beginning.
        2. Each receiver has a pointer indicating upto what index it has read this list previously.
        3. Every call of this function moves that pointer forward to current end of the packet list
        Thus, it is ensured that receiver doesn't read packet twice. Also, range check between transmitter
        and receiver is done (must be in same segment)
    */
    public int receivePackets(int receiverId, int readTillIndex, int currentTime, float position, Queue<Packet> receiveQueue) {
        packetQueueLock.readLock().lock();
        int newPacketCount = packetQueue.size() - readTillIndex;
        while (readTillIndex < packetQueue.size()) {
            Packet packet = packetQueue.get(readTillIndex);

            int segment1 = (int) (position / Config.SEGMENT_LENGTH);
            int segment2 = (int) (packet.getPosition() / Config.SEGMENT_LENGTH);
            int diff = 0;
            if (packet.getType() == Config.PACKET_TYPE.RLEAVE) diff = 1;

            if (packet.getSenderId() != receiverId && Math.abs(segment1 - segment2) <= diff) {
                receiveQueue.add(packet);
                packet.recordReception(currentTime);
            }
            readTillIndex++;
        }
        packetQueueLock.readLock().unlock();
        return newPacketCount;
    }

    // See declaration of transmitter poistions
    public void clearTransmitterPositions() {
        transmitterPositionsLock.lock();
        transmitterPositions.clear();
        transmitterPositionsLock.unlock();
        return;
    }
}