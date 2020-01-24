import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

public class Medium {
    Map<Integer,Queue<Packet>> messages;
    ReadWriteLock mediumLock;

    public Medium() {
        messages = new HashMap<Integer, Queue<Packet>>();
        for (int i = 0; i <= Config.VEHICLE_COUNT; i++) {
            messages.put(i, new LinkedList<Packet>());
        }
        mediumLock = new ReentrantReadWriteLock(Config.useFair);
    }

    private boolean tryWriteLock() {
        try {
            return mediumLock.writeLock().tryLock(Config.TRY_LOCK_WAIT_TIME, TimeUnit.MILLISECONDS);
        } catch(InterruptedException err) {
            System.out.println(err);
            return false;
        }
    }

    private boolean tryReadLock() {
        try {
            return mediumLock.readLock().tryLock(Config.TRY_LOCK_WAIT_TIME, TimeUnit.MILLISECONDS);
        } catch(InterruptedException err) {
            System.out.println(err);
            return false;
        }
    }

    // Add Packet to each vehicle's message queue
    public boolean write(Packet newPacket) {
        if (tryWriteLock()) {
            for (Integer vid : messages.keySet()) {
                messages.get(vid).add(newPacket);
            }
            mediumLock.writeLock().unlock();
            System.out.println("Packet " + newPacket.id + ": Sender " + newPacket.senderId + " wrote " + newPacket.type + " at " + newPacket.sentTime);
            return true;
        }
        else return false;
    }

    // Returns a reference to the private queue of a vehicle
    public Queue<Packet> read(int id) {
        if (tryReadLock()) {
            Queue<Packet> vehicleQueue = messages.getOrDefault(id, null);
            mediumLock.readLock().unlock();
            return vehicleQueue;
        }   
        else return null;
    }

    // public static void main(String[] args) {
    //     Medium medium = new Medium();
    //     System.out.println(medium.read(0));
    // }
}