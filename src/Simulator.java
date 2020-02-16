import java.util.*;
import java.util.concurrent.Phaser;

public class Simulator implements Runnable {
    int currentTime;
    int stopTime;
    ArrayList<Vehicle> vehicles;
    ArrayList<RoadSideUnit> roadSideUnits;
    Medium medium;
    Phaser timeSync;

    public class PacketStat {
        Config.PACKET_TYPE type;
        int generatedCount;
        int transmittedCount;
        int receivedCount;
        int totalTransmitTime;
        int totalReceiveTime;

        public PacketStat(Config.PACKET_TYPE type) {
            this.type = type;
            this.generatedCount = 0;
            this.transmittedCount = 0;
            this.receivedCount = 0;
            this.totalTransmitTime = 0;
            this.totalReceiveTime = 0;
        }

        public void printStatistics() {
            System.out.println("-----------------------------------------");
            System.out.println("Total packets generated = " + generatedCount);
            System.out.println("Total packets transmitted = " + transmittedCount);
            System.out.println("Total packets received = " + receivedCount);
            System.out.println("Average trasmit time = " + ((double) totalTransmitTime)/transmittedCount);
            System.out.println("Average receive time = " + ((double) totalReceiveTime)/receivedCount);
        }
    }
    Map<Config.PACKET_TYPE, PacketStat> packetStats;

    public Simulator() {
        currentTime = 0;
        stopTime = Config.STOP_TIME;
        timeSync = new Phaser();
        timeSync.register();
        medium = new Medium();

        packetStats = new HashMap<Config.PACKET_TYPE, PacketStat>();
        for (Config.PACKET_TYPE type : Config.PACKET_TYPE.values()) {
            packetStats.put(type, new PacketStat(type));

        }

        // Spawn vehicles at random positions
        vehicles  = new ArrayList<Vehicle>();
        for (int i = 1; i <= Config.VEHICLE_COUNT; i++) {
            vehicles.add(new Vehicle(i, timeSync, this, medium, stopTime));
        }

        // Spawn RSU's in the mid of each (implicit) segment
        roadSideUnits = new ArrayList<RoadSideUnit>();
        double rsuPosition = Config.SEGMENT_LENGTH * 0.5;
        int rsuId = 1;
        while (rsuPosition < Config.ROAD_END) {
            roadSideUnits.add(new RoadSideUnit(-1 * rsuId, rsuPosition, timeSync, this, medium, stopTime));
            rsuPosition += Config.SEGMENT_LENGTH;
            rsuId++;
        }
        System.out.println("Simulation Initialised");    
    }

    public synchronized void incrGenCount(Config.PACKET_TYPE type) {
        packetStats.get(type).generatedCount++;
    }

    public synchronized void recordTransmission(Config.PACKET_TYPE type, int transmitTime) {
        assert (transmitTime >= 0) : "Negative transmission time encountered for a packet.";
        packetStats.get(type).transmittedCount++;
        packetStats.get(type).totalTransmitTime += transmitTime;
    }

    public synchronized void recordReception(Config.PACKET_TYPE type, int receiveTime) {
        assert (receiveTime >= 0) : "Negative receive time encountered for a packet.";
        packetStats.get(type).receivedCount++;
        packetStats.get(type).totalReceiveTime += receiveTime;
    }

    public void printStatistics() {
        int totalGeneratedCount = 0;
        int totalTransmittedCount = 0;
        int totalReceivedCount = 0;
        int totalTransmitTime = 0;
        int totalReceiveTime = 0;

        for (Config.PACKET_TYPE type : Config.PACKET_TYPE.values()) {
            packetStats.get(type).printStatistics();
            totalGeneratedCount += packetStats.get(type).generatedCount;
            totalTransmittedCount += packetStats.get(type).transmittedCount;
            totalReceivedCount += packetStats.get(type).receivedCount;
            totalTransmitTime += packetStats.get(type).totalTransmitTime;
            totalReceiveTime += packetStats.get(type).totalReceiveTime;
        }
        System.out.println("-----------------------------------------");
        System.out.println("Total packets generated = " + totalGeneratedCount);
        System.out.println("Total packets transmitted = " + totalTransmittedCount);
        System.out.println("Total packets received = " + totalReceivedCount);
        System.out.println("Average trasmit time = " + ((double) totalTransmitTime)/totalTransmittedCount);
        System.out.println("Average receive time = " + ((double) totalReceiveTime)/totalReceivedCount);
    }

    public void run() {
        for (Vehicle v : vehicles) {
            new Thread(v).start();
        }
        for (RoadSideUnit rsu : roadSideUnits) {
            new Thread(rsu).start();
        }
        while (currentTime <= stopTime) {
            System.out.println("Interval " + currentTime);
            timeSync.arriveAndAwaitAdvance();
            currentTime++;
        }
        System.out.println("Simulation stopped after " + stopTime + " ms");   
    }

    public static void main(String[] args) {
        Simulator simulator = new Simulator();
        simulator.run();
    }
}