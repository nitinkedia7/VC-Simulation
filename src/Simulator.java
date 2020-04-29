import java.util.*;
import java.util.concurrent.Phaser;
import java.text.DecimalFormat;
import java.io.*;

public class Simulator implements Runnable {
    int currentTime;
    int stopTime;
    int vehiclesPerSegment;
    int totalVehicleCount;
    int averageVehicleSpeed;
    ArrayList<Vehicle> vehicles;
    ArrayList<RoadSideUnit> roadSideUnits;
    Medium medium;
    Phaser timeSync;
    DecimalFormat decimalFormat;
    FileWriter csvFileWriter;

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
            System.out.println("Packet type " + type);
            System.out.println("Total packets generated = " + generatedCount);
            System.out.println("Total packets transmitted = " + transmittedCount);
            System.out.println("Total packets received = " + receivedCount);
            System.out.println("Average transmit time in ms = " + decimalFormat.format(((double) totalTransmitTime)/transmittedCount));
            System.out.println("Average receive time in ms = " + decimalFormat.format(((double) totalReceiveTime)/receivedCount));
        }
    }
    Map<Config.PACKET_TYPE, PacketStat> packetStats;
    int totalCloudsFormedSelf;
    long totalCloudsFormationTimeSelf;
    int totalCloudsFormedRSU;
    long totalCloudsFormationTimeRSU;
    int totalRequestsServiced;
    int totalCloudsQueued;

    int leaderAlgoInvoked;
    int leaderChangeCount;
    int leaderLeaveCount;

    public Simulator(int givenVehiclePerSegment, int givenAverageVehicleSpeed, FileWriter fw) {
        currentTime = 0;
        stopTime = Config.STOP_TIME;
        int segmentCount = (int) Math.ceil(Config.ROAD_END / Config.SEGMENT_LENGTH);
        totalVehicleCount = givenVehiclePerSegment * segmentCount;
        vehiclesPerSegment = givenVehiclePerSegment;
        averageVehicleSpeed = givenAverageVehicleSpeed;
        timeSync = new Phaser();
        timeSync.register();
        medium = new Medium();
        decimalFormat = new DecimalFormat();
        decimalFormat.setMaximumFractionDigits(3);
        csvFileWriter = fw;

        packetStats = new HashMap<Config.PACKET_TYPE, PacketStat>();
        for (Config.PACKET_TYPE type : Config.PACKET_TYPE.values()) {
            packetStats.put(type, new PacketStat(type));

        }
        totalCloudsFormedSelf = 0;
        totalCloudsFormationTimeSelf = 0;
        totalCloudsFormedRSU = 0;
        totalCloudsFormationTimeRSU = 0;
        totalRequestsServiced = 0;
        totalCloudsQueued = 0;
        
        leaderChangeCount = 0;
        leaderLeaveCount = 0;
        leaderAlgoInvoked = 0;
        // Spawn vehicles at random positions
        vehicles  = new ArrayList<Vehicle>();
        for (int i = 1; i <= totalVehicleCount; i++) {
            // givenAverageVehicleSpeed * 0.277 is to convert km/h to m/s
            vehicles.add(new Vehicle(i, timeSync, this, medium, stopTime, givenAverageVehicleSpeed * 0.277));
        }

        // Spawn RSU's in the mid of each (implicit) segment
        roadSideUnits = new ArrayList<RoadSideUnit>();
        double rsuPosition = Config.SEGMENT_LENGTH * 0.5;
        int rsuId = 1;
        while (rsuPosition < Config.ROAD_END) {
            if (rsuId % 2 == 1) { // Position RSU's at alternate segments
                roadSideUnits.add(new RoadSideUnit(-1 * rsuId, rsuPosition, timeSync, this, medium, stopTime));
            }
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

    public synchronized void recordCloudFormed(int formationTime, boolean formedByRSU) {
        if (formedByRSU) {
            totalCloudsFormedRSU++;
            totalCloudsFormationTimeRSU += formationTime;
        }
        else {
            totalCloudsFormedSelf++;
            totalCloudsFormationTimeSelf += formationTime;
        }
    }

    public synchronized void changeRequestQueuedCount(boolean incr) {
        if (incr) {
            totalCloudsQueued++;
        }
        else {
            totalCloudsQueued--;
        }
    }

    public synchronized void incrTotalRequestsServiced() {
        totalRequestsServiced++;
    }

    public synchronized void incrLeaderChangeCount() {
        leaderChangeCount++;
    }

    public synchronized void incrLeaderLeaveCount() {
        leaderLeaveCount++;
    }

    public synchronized void incrLeaderAlgoInvokedCount() {
        leaderAlgoInvoked++;
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
        System.out.println("All packet types");
        System.out.println("Total packets generated = " + totalGeneratedCount);
        System.out.println("Total packets transmitted = " + totalTransmittedCount);
        System.out.println("Total packets received = " + totalReceivedCount);
        System.out.println("Average transmit time in ms = " + decimalFormat.format(((double) totalTransmitTime) / totalTransmittedCount));
        System.out.println("Average receive time in ms = " + decimalFormat.format(((double) totalReceiveTime) / totalReceivedCount));
    
        double averageClusterOverhead = ((double) totalTransmittedCount) / totalRequestsServiced;
        double averageCloudFormationTimeSelf = ((double) totalCloudsFormationTimeSelf) / totalCloudsFormedSelf;
        double averageCloudFormationTimeRSU = ((double) totalCloudsFormationTimeRSU) / totalCloudsFormedRSU;
        System.out.println();
        System.out.println("Average cluster overhead = " + decimalFormat.format(averageClusterOverhead));
        System.out.println("Total clouds formed by RSU = " + totalCloudsFormedRSU);
        System.out.println("Average cloud formation time (ms) by RSU = " + decimalFormat.format(averageCloudFormationTimeRSU));
        System.out.println("Total clouds formed distributedly = " + totalCloudsFormedSelf);
        System.out.println("Average cloud formation time (ms) distributedly = " + decimalFormat.format(averageCloudFormationTimeSelf));
        System.out.println("Total requests serviced = " + totalRequestsServiced);
        System.out.println("Leader change count = " + leaderChangeCount);
        System.out.println("Leader leave count = " + leaderLeaveCount);

        String csvRow = String.format(
            "%d,%d,%d,%d,%d,%s,%d,%s,%d,%s,%d\n",
            vehiclesPerSegment,
            averageVehicleSpeed,
            packetStats.get(Config.PACKET_TYPE.RREQ).generatedCount + packetStats.get(Config.PACKET_TYPE.RJOIN).generatedCount,
            totalRequestsServiced,
            totalCloudsQueued,
            decimalFormat.format(averageClusterOverhead),
            totalCloudsFormedRSU,
            decimalFormat.format(averageCloudFormationTimeRSU),
            totalCloudsFormedSelf,
            decimalFormat.format(averageCloudFormationTimeSelf),
            leaderChangeCount
        );
        try {
            csvFileWriter.write(csvRow);
            csvFileWriter.flush();
        } catch (IOException ioe) {
            System.err.println("IOException: " + ioe.getMessage());
        } 

        System.err.printf(
            "Simulation with density %d and average speed %d km/h finished. Leader changed %d times out of %d times leader left.\n",
            vehiclesPerSegment,
            averageVehicleSpeed,
            leaderChangeCount,
            leaderLeaveCount
        );
    }

    public void run() {
        for (RoadSideUnit rsu : roadSideUnits) {
            new Thread(rsu).start();
        }
        for (Vehicle v : vehicles) {
            new Thread(v).start();
        }
        while (currentTime <= stopTime) {
            System.out.println("Interval " + currentTime);
            timeSync.arriveAndAwaitAdvance();
            currentTime++;
        }
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println(e);
        }
        System.out.println("Simulation stopped after " + stopTime + " ms");
    }

    public static void main(String[] args) {
        try {
            String logDirectoryPath = Config.LOG_PATH + System.currentTimeMillis();
            new File(logDirectoryPath).mkdirs();    

            PrintStream console = System.out;
            FileWriter fw = new FileWriter(logDirectoryPath + "/plot.csv", true);
            fw.write("Average Vehicle Density,Average Vehicle Speed (km/h),Total Requests Generated,Total Requests Serviced,Total Requests Queued,Average Cluster Overhead (Ratio),Clouds formed by RSU,Average Cloud Formation Time by RSU (ms),Clouds formed Distributedly,Average Cloud Formation Time Distributedly (ms),Leader Change Count\n");
            fw.flush();

            int avgVehicleSpeedKMPH = 60;
            for (int vehiclesPerSegment = 8; vehiclesPerSegment <= 32; vehiclesPerSegment += 8) {
                try {
                    String logFilePath = String.format("%s/%d_%d.log", logDirectoryPath, vehiclesPerSegment, avgVehicleSpeedKMPH);
                    PrintStream logFile = new PrintStream(new File(logFilePath));
                    System.setOut(logFile);
                    
                    Simulator simulator = new Simulator(vehiclesPerSegment, avgVehicleSpeedKMPH, fw);
                    simulator.run();
                    simulator.printStatistics();
                } catch (Exception e) {
                    System.err.printf(
                        "Simulation with density %d and average speed %d failed.\n", vehiclesPerSegment, avgVehicleSpeedKMPH
                    );
                    e.printStackTrace(System.err);
                }
            }

            // int averageVehiclePerSegment = 24;
            // for (int vehicleSpeedKMPH = 30; vehicleSpeedKMPH <= 90; vehicleSpeedKMPH += 10) {
            //     try {
            //         String logFilePath = String.format("%s/%d_%d.log", logDirectoryPath, averageVehiclePerSegment, vehicleSpeedKMPH);
            //         PrintStream logFile = new PrintStream(new File(logFilePath));
            //         System.setOut(logFile);
                    
            //         Simulator simulator = new Simulator(averageVehiclePerSegment, vehicleSpeedKMPH, fw);
            //         simulator.run();
            //         simulator.printStatistics();
            //     } catch (Exception e) {
            //         System.err.printf(
            //             "Simulation with density %d and average speed %d failed.\n", averageVehiclePerSegment, vehicleSpeedKMPH
            //         );
            //         e.printStackTrace(System.err);
            //     }    
            // }
            fw.close();
        } catch (IOException ioe) {
            System.err.println("IOException: " + ioe.getMessage());
        }
    }
}