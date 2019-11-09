import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

public class Medium {
    Map<Integer,Queue<Packet>> messages;
    ReadWriteLock mediumLock;

    public Medium() {
        messages = new HashMap<Integer, Queue<Packet>>();
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

    public boolean write(Packet newPacket) {
        if (tryWriteLock()) {
            messageQueue.add(newPacket);
            return true;
        }
        else return false;
    }

    public boolean read() {
        if (tryReadLock()) {
            if (messageQueue.isEmpty()) return false;
            
            return true;
        }   
        else return true;
    }
}