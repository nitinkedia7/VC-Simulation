import java.util.*;
import java.util.concurrent.Phaser;
import java.text.DecimalFormat;
import java.io.*;

public class Simulator implements Runnable {
    int currentTime;
    int stopTime;
    int vehiclesPerSegment;
    int totalVehicleCount;
    double averageVehicleSpeed;
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
    int totalCloudsFormed;
    int totalCloudsFormationTime;
    int leaderChangeCount;
    int leaderLeaveCount;

    public Simulator(int givenVehiclePerSegment, double givenAverageVehicleSpeed, FileWriter fw) {
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
        totalCloudsFormed = 0;
        totalCloudsFormationTime = 0;
        leaderChangeCount = 0;
        leaderLeaveCount = 0;
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

    public synchronized void recordCloudFormed(int formationTime) {
        totalCloudsFormed++;
        totalCloudsFormationTime += formationTime;
    }

    public synchronized void incrLeaderChangeCount() {
        leaderChangeCount++;
    }

    public synchronized void incrLeaderLeaveCount() {
        leaderLeaveCount++;
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
    
        int totalAppRequests = packetStats.get(Config.PACKET_TYPE.RREQ).transmittedCount + packetStats.get(Config.PACKET_TYPE.RJOIN).transmittedCount;
        double averageClusterOverhead = ((double) totalTransmittedCount) / totalAppRequests;
        double averageCloudFormationTime = ((double) totalCloudsFormationTime) / totalCloudsFormed;
        System.out.println();
        System.out.println("Average cluster overhead = " + decimalFormat.format(averageClusterOverhead));
        System.out.println("Average cloud formation time in ms = " + decimalFormat.format(averageCloudFormationTime));
        System.out.println("Leader change count = " +leaderChangeCount);
        System.out.println("Leader leave count = " +leaderLeaveCount);

        String csvRow = String.format(
            "%d,%d,%d,%s,%s,%d\n",
            vehiclesPerSegment,
            totalVehicleCount,
            averageVehicleSpeed,
            decimalFormat.format(averageClusterOverhead),
            decimalFormat.format(averageCloudFormationTime),
            leaderChangeCount
        );
        csvFileWriter.write(csvRow);
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

    // public static void main(String[] args) {
    //     try {
    //         FileWriter fw = new FileWriter(Config.OUTPUT_FILENAME, true);
    //         fw.write("Number of Vehicles,Average Cluster Overhead,Average Cloud Formation Time,Leader Change Count\n");
    //         fw.close();
    //     } catch (IOException ioe) {
    //         System.err.println("IOException: " + ioe.getMessage());
    //     }

    //     int segmentCount = (int) Math.ceil(Config.ROAD_END / Config.SEGMENT_LENGTH);
    //     for (int vehiclesPerSegment = 40; vehiclesPerSegment <= 40; vehiclesPerSegment += 4) {
    //         int totalVehicleCount = vehiclesPerSegment * segmentCount;
    //         Simulator simulator = new Simulator(totalVehicleCount);
    //         simulator.run();
    //         simulator.printStatistics();
    //     }
    // }

        public static void main(String[] args) {
            String logDirectoryPath = Config.LOG_PATH + System.currentTimeMillis();
            new File(logDirectoryPath).mkdirs();    

            PrintStream console = System.out;
            FileWriter fw = new FileWriter(logDirectoryPath + "/plot.csv", true);
            fw.write("Average Vehicle Density,Number of Vehicles,Average Vehicle Speed,Average Cluster Overhead,Average Cloud Formation Time,Leader Change Count\n");

            int avgVehicleSpeedKMPH = 60;
            for (int vehiclesPerSegment = 4; vehiclesPerSegment <= 40; vehiclesPerSegment += 4) {
                try {
                    String logFilePath = String.format("%s/%d_%d.log", logDirectoryPath, vehiclesPerSegment, avgVehicleSpeedKMPH);
                    PrintStream logFile = new PrintStream(new File(logFilePath));
                    System.setOut(logFile);
                    
                    Simulator simulator = new Simulator(vehiclesPerSegment, avgVehicleSpeedKMPH * 0.2777, fw);
                    simulator.run();
                    simulator.printStatistics();
                } catch (Exception e) {
                    System.err.printf(
                        "Simulation with density %d and average speed %d failed.\n", vehiclesPerSegment, avgVehicleSpeedKMPH
                    );
                    e.printStackTrace(System.err);
                }
            }
            fw.close();

            fw = new FileWriter(logDirectoryPath + "/plot_speed.csv", true);
            int averageVehiclePerSegment = 24;
            for (int vehicleSpeedKMPH = 30; vehicleSpeedKMPH <= 80; vehicleSpeedKMPH += 10) {
                try {
                    String logFilePath = String.format("%s/%d_%d_speed.log", logDirectoryPath, averageVehiclePerSegment, avgVehicleSpeedKMPH);
                    PrintStream logFile = new PrintStream(new File(logFilePath));
                    System.setOut(logFile);
                    
                    Simulator simulator = new Simulator(averageVehiclePerSegment * segmentCount, avgVehicleSpeedKMPH * 0.2777, fw);
                    simulator.run();
                    simulator.printStatistics();
                } catch (Exception e) {
                    System.err.printf(
                        "Simulation with density %d and average speed %d failed.\n", averageVehiclePerSegment, avgVehicleSpeedKMPH
                    );
                    e.printStackTrace(System.err);
                }    
            }
            fw.close();
        }
}