import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.text.DecimalFormat;

public class Simulator {
    int currentTime;
    int stopTime;
    int vehiclesPerSegment;
    int totalVehicleCount;
    int averageVehicleSpeed;
    ArrayList<Vehicle> vehicles;
    ArrayList<RoadSideUnit> roadSideUnits;
    Medium medium;
    DecimalFormat decimalFormat;
    FileWriter csvFileWriter;

    public class PacketStat {
        Config.PACKET_TYPE type;
        AtomicInteger generatedCount;
        AtomicInteger transmittedCount;
        AtomicInteger receivedCount;
        AtomicInteger totalTransmitTime;
        AtomicInteger totalReceiveTime;

        public PacketStat(Config.PACKET_TYPE type) {
            this.type = type;
            this.generatedCount = new AtomicInteger();
            this.transmittedCount = new AtomicInteger();
            this.receivedCount = new AtomicInteger();
            this.totalTransmitTime = new AtomicInteger();
            this.totalReceiveTime = new AtomicInteger();
        }

        public void printStatistics() {
            System.out.println("-----------------------------------------");
            System.out.println("Packet type " + type);
            System.out.println("Total packets generated = " + generatedCount);
            System.out.println("Total packets transmitted = " + transmittedCount);
            System.out.println("Total packets received = " + receivedCount);
            System.out.println("Average transmit time in ms = " + decimalFormat.format(totalTransmitTime.floatValue() / transmittedCount.intValue()));
            System.out.println("Average receive time in ms = " + decimalFormat.format(totalReceiveTime.floatValue() / receivedCount.intValue()));
        }
    }
    Map<Config.PACKET_TYPE, PacketStat> packetStats;
    AtomicInteger totalCloudsFormedSelf;
    AtomicInteger totalCloudsFormationTimeSelf;
    AtomicInteger totalCloudsFormedRSU;
    AtomicInteger totalCloudsFormationTimeRSU;
    AtomicInteger totalRequestsServiced;
    AtomicInteger totalRequestsQueued;

    AtomicInteger leaderChangeCount;
    AtomicInteger leaderLeaveCount;
    AtomicInteger rrepReceivecCount;

    public Simulator(int givenVehiclePerSegment, int givenAverageVehicleSpeed, FileWriter fw) {
        currentTime = 0;
        stopTime = Config.STOP_TIME;
        int segmentCount = (int) Math.ceil(Config.ROAD_END / Config.SEGMENT_LENGTH);
        totalVehicleCount = givenVehiclePerSegment * segmentCount;
        vehiclesPerSegment = givenVehiclePerSegment;
        averageVehicleSpeed = givenAverageVehicleSpeed;
        medium = new Medium();

        decimalFormat = new DecimalFormat();
        decimalFormat.setMaximumFractionDigits(4);
        csvFileWriter = fw;

        packetStats = new HashMap<Config.PACKET_TYPE, PacketStat>();
        for (Config.PACKET_TYPE type : Config.PACKET_TYPE.values()) {
            packetStats.put(type, new PacketStat(type));

        }
        totalCloudsFormedSelf = new AtomicInteger();
        totalCloudsFormationTimeSelf = new AtomicInteger();
        totalCloudsFormedRSU = new AtomicInteger();
        totalCloudsFormationTimeRSU = new AtomicInteger();
        totalRequestsServiced = new AtomicInteger();
        totalRequestsQueued = new AtomicInteger();
        
        leaderChangeCount = new AtomicInteger();
        leaderLeaveCount = new AtomicInteger();
        rrepReceivecCount = new AtomicInteger();

        // Spawn vehicles at random positions
        vehicles  = new ArrayList<Vehicle>();
        for (int i = 1; i <= totalVehicleCount; i++) {
            // givenAverageVehicleSpeed * 0.277 is to convert km/h to m/s
            vehicles.add(new Vehicle(i, givenAverageVehicleSpeed * 0.277f, this, medium));
        }

        // Spawn RSU's in the mid of each (implicit) segment
        roadSideUnits = new ArrayList<RoadSideUnit>();
        float rsuPosition = Config.SEGMENT_LENGTH * 0.5f;
        int rsuId = 1;
        while (rsuPosition < Config.ROAD_END) {
            if (rsuId % 2 == 1) { // Position RSU's at alternate segments
                roadSideUnits.add(new RoadSideUnit(-1 * rsuId, rsuPosition, this, medium));
            }
            rsuPosition += Config.SEGMENT_LENGTH;
            rsuId++;
        }
        System.out.println("Simulation Initialised");    
    }

    public void incrGenCount(Config.PACKET_TYPE type) {
        packetStats.get(type).generatedCount.incrementAndGet();
    }

    public void recordTransmission(Config.PACKET_TYPE type, int transmitTime) {
        assert (transmitTime >= 0) : "Negative transmission time encountered for a packet.";
        packetStats.get(type).transmittedCount.incrementAndGet();
        packetStats.get(type).totalTransmitTime.addAndGet(transmitTime);
    }

    public void recordReception(Config.PACKET_TYPE type, int receiveTime) {
        assert (receiveTime >= 0) : "Negative receive time encountered for a packet.";
        packetStats.get(type).receivedCount.incrementAndGet();
        packetStats.get(type).totalReceiveTime.addAndGet(receiveTime);
    }

    public void recordCloudFormed(int formationTime, boolean formedByRSU) {
        if (formedByRSU) {
            totalCloudsFormedRSU.incrementAndGet();
            totalCloudsFormationTimeRSU.addAndGet(formationTime);
        }
        else {
            totalCloudsFormedSelf.incrementAndGet();
            totalCloudsFormationTimeSelf.addAndGet(formationTime);
        }
    }

    public void changeTotalRequestsQueued(boolean incr) {
        if (incr) {
            totalRequestsQueued.incrementAndGet();
        }
        else {
            totalRequestsQueued.decrementAndGet();
        }
    }

    public void incrTotalRequestsServiced() {
        totalRequestsServiced.incrementAndGet();
    }

    public void incrLeaderChangeCount() {
        leaderChangeCount.incrementAndGet();
    }

    public void incrLeaderLeaveCount() {
        leaderLeaveCount.incrementAndGet();
    }

    public void incrRrepReceiveCount() {
        rrepReceivecCount.incrementAndGet();
    }

    public void printStatistics() {
        int totalGeneratedCount = 0;
        int totalTransmittedCount = 0;
        int totalReceivedCount = 0;
        int totalTransmitTime = 0;
        int totalReceiveTime = 0;

        for (Config.PACKET_TYPE type : Config.PACKET_TYPE.values()) {
            packetStats.get(type).printStatistics();
            totalGeneratedCount += packetStats.get(type).generatedCount.intValue();
            totalTransmittedCount += packetStats.get(type).transmittedCount.intValue();
            totalReceivedCount += packetStats.get(type).receivedCount.intValue();
            totalTransmitTime += packetStats.get(type).totalTransmitTime.intValue();
            totalReceiveTime += packetStats.get(type).totalReceiveTime.intValue();
        }
        System.out.println("-----------------------------------------");
        System.out.println("All packet types");
        System.out.println("Total packets generated = " + totalGeneratedCount);
        System.out.println("Total packets transmitted = " + totalTransmittedCount);
        System.out.println("Total packets received = " + totalReceivedCount);
        System.out.println("Average transmit time in ms = " + decimalFormat.format(((float) totalTransmitTime) / totalTransmittedCount));
        System.out.println("Average receive time in ms = " + decimalFormat.format(((float) totalReceiveTime) / totalReceivedCount));
    
        float averageClusterOverhead = 
            totalTransmittedCount
            - packetStats.get(Config.PACKET_TYPE.PSTART).transmittedCount.intValue()
            - packetStats.get(Config.PACKET_TYPE.PDONE).transmittedCount.intValue();
        averageClusterOverhead /= totalTransmittedCount;
        float averageCloudFormationTimeSelf = totalCloudsFormationTimeSelf.floatValue() / totalCloudsFormedSelf.intValue();
        float averageCloudFormationTimeRSU = totalCloudsFormationTimeRSU.floatValue() / totalCloudsFormedRSU.intValue();
        System.out.println("-----------------------------------------");
        System.out.println("Average cluster overhead = " + decimalFormat.format(averageClusterOverhead));
        System.out.println("Total clouds formed by RSU = " + totalCloudsFormedRSU);
        System.out.println("Average cloud formation time (ms) by RSU = " + decimalFormat.format(averageCloudFormationTimeRSU));
        System.out.println("Total clouds formed distributedly = " + totalCloudsFormedSelf);
        System.out.println("Average cloud formation time (ms) distributedly = " + decimalFormat.format(averageCloudFormationTimeSelf));
        System.out.println("Total requests serviced = " + totalRequestsServiced);
        System.out.println("Total requests still queued = " + totalRequestsQueued);
        System.out.println("Leader change count = " + leaderChangeCount);
        System.out.println("Leader leave count = " + leaderLeaveCount);
        System.out.println("RREP received by leader/RSU = " + rrepReceivecCount);

        String csvRow = String.format(
            "%d\t%d\t%d\t%d\t%d\t%s\t%d\t%s\t%d\t%s\t%d\n",
            vehiclesPerSegment,
            averageVehicleSpeed,
            packetStats.get(Config.PACKET_TYPE.RREQ).generatedCount.intValue() + packetStats.get(Config.PACKET_TYPE.RJOIN).generatedCount.intValue(),
            totalRequestsServiced.intValue(),
            totalRequestsQueued.intValue(),
            decimalFormat.format(averageClusterOverhead),
            totalCloudsFormedRSU.intValue(),
            decimalFormat.format(averageCloudFormationTimeRSU),
            totalCloudsFormedSelf.intValue(),
            decimalFormat.format(averageCloudFormationTimeSelf),
            leaderChangeCount.intValue()
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
            leaderChangeCount.intValue(),
            leaderLeaveCount.intValue()
        );
    }

    // public void runOld() {
    //     for (RoadSideUnit rsu : roadSideUnits) {
    //         new Thread(rsu).start();
    //     }
    //     for (Vehicle v : vehicles) {
    //         new Thread(v).start();
    //     }
    //     System.out.println("Simulation Started");    
    //     while (currentTime <= stopTime) {
    //         if (currentTime % 1000 == 0) {
    //             System.out.println("Interval " + currentTime);
    //         }
    //         timeSync.arriveAndAwaitAdvance();
    //         currentTime++;
    //     }
    //     try {
    //         Thread.sleep(1000);
    //     } catch (Exception e) {
    //         System.out.println(e);
    //     }
    //     System.out.println("Simulation Finished after " + stopTime + " ms");
    // }

    private class Entity {
        int id;
        int index;
        float position;

        public Entity(RoadSideUnit rsu, int index) {
            this.id = rsu.id;
            this.index = index;
            this.position = rsu.position;
        }
        
        public Entity(Vehicle vehicle, int index) {
            this.id = vehicle.id;
            this.index = index;
            this.position = vehicle.position;
        }

        public int getSegmentId() {
            return (int) (position / Config.SEGMENT_LENGTH);
        }
    }

    public void run() { // Optimised run implementation
        
        Map<Integer, List<Entity>> segmentMap = new HashMap<Integer, List<Entity>>();
        List<Callable<Integer>> tasks = new ArrayList<Callable<Integer>>();
        ExecutorService taskExecutor = Executors.newFixedThreadPool(vehiclesPerSegment * 2);
        
        System.out.println("Simulation Started");
        while (currentTime <= stopTime) {
            if (currentTime % 1000 == 0) {
                System.out.println("Interval " + currentTime);
            }
            if (currentTime == 0 || currentTime % 50 == 1) {
                segmentMap.clear();
                for (int i = 0; i < roadSideUnits.size(); i++) {
                    Entity entity = new Entity(roadSideUnits.get(i), i);
                    int segmentId = entity.getSegmentId();
                    if (!segmentMap.containsKey(segmentId)) {
                        segmentMap.put(segmentId, new LinkedList<Entity>());
                    }
                    segmentMap.get(segmentId).add(entity);
                }
                for (int i = 0; i < totalVehicleCount; i++) {
                    Entity entity = new Entity(vehicles.get(i), i);
                    int segmentId = entity.getSegmentId();
                    if (!segmentMap.containsKey(segmentId)) {
                        segmentMap.put(segmentId, new LinkedList<Entity>());
                    }
                    segmentMap.get(segmentId).add(entity);
                }
            }
            
            for (List<Entity> elist : segmentMap.values()) {
                tasks.clear();
                Collections.shuffle(elist);
                for (Entity entity : elist) {
                    if (entity.id > 0) {
                        tasks.add(vehicles.get(entity.index));
                    }
                    else {
                        tasks.add(roadSideUnits.get(entity.index));
                    }
                }
                try {
                    assert(tasks.size() <= 2 * vehiclesPerSegment);
                    taskExecutor.invokeAll(tasks);
                } 
                catch (InterruptedException e) {
                    System.err.printf(
                        "Simulation with density %d and average speed %d failed at time %d ms.\n",
                        vehiclesPerSegment,
                        averageVehicleSpeed,
                        currentTime
                    );
                    e.printStackTrace(System.err);
                }
            }
            medium.getChannel(0).clearTransmitterPositions();
            currentTime++;
        }
        taskExecutor.shutdown();
        System.out.println("Simulation Finished after " + stopTime + " ms");
    }

    public static void main(String[] args) {
        try {
            String logDirectoryPath = Config.LOG_PATH + System.currentTimeMillis();
            new File(logDirectoryPath).mkdirs();    

            // PrintStream console = System.out;
            FileWriter fw = new FileWriter(logDirectoryPath + "/plot.csv", true);
            fw.write("Average Vehicle Density\tAverage Vehicle Speed (km/h)\tRequests Generated\tRequests Serviced\tRequests Queued\tAverage Cluster Overhead (Ratio)\tClouds formed by RSU\tAverage Cloud Formation Time by RSU (ms)\tClouds formed Distributedly\tAverage Cloud Formation Time Distributedly (ms)\tLeader Change Count\n");
            fw.flush();

            int avgVehicleSpeedKMPH = 60;
            for (int vehiclesPerSegment = 24; vehiclesPerSegment <= 24; vehiclesPerSegment += 4) {
                String logFilePath = String.format("%s/%d_%d.log", logDirectoryPath, vehiclesPerSegment, avgVehicleSpeedKMPH);
                PrintStream logFile = new PrintStream(new File(logFilePath));
                System.setOut(logFile);
                
                Simulator simulator = new Simulator(vehiclesPerSegment, avgVehicleSpeedKMPH, fw);
                simulator.run();
                simulator.printStatistics();
            }

            // int averageVehiclePerSegment = 24;
            // for (int vehicleSpeedKMPH = 30; vehicleSpeedKMPH <= 90; vehicleSpeedKMPH += 10) {
            //     String logFilePath = String.format("%s/%d_%d.log", logDirectoryPath, averageVehiclePerSegment, vehicleSpeedKMPH);
            //     PrintStream logFile = new PrintStream(new File(logFilePath));
            //     System.setOut(logFile);
                
            //     Simulator simulator = new Simulator(averageVehiclePerSegment, vehicleSpeedKMPH, fw);
            //     simulator.run();
            //     simulator.printStatistics(); 
            // }
            fw.close();
        } catch (IOException ioe) {
            System.err.println("IOException: " + ioe.getMessage());
        }
    }
}