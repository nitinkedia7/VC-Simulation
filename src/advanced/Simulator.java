package advanced;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import infrastructure.*;

public class Simulator {
    int currentTime;
    int stopTime;
    int vehiclesPerSegment;
    int totalVehicleCount;
    int averageVehicleSpeed;
    ArrayList<Vehicle> vehicles;
    ArrayList<RoadSideUnit> roadSideUnits;
    Medium medium;
    Statistics statsStore;
    FileWriter csvFileWriter;

    public Simulator(int givenVehiclePerSegment, int givenAverageVehicleSpeed, FileWriter fw) {
        currentTime = 0;
        stopTime = Config.STOP_TIME;
        int segmentCount = (int) Math.ceil(Config.ROAD_END / Config.SEGMENT_LENGTH);
        totalVehicleCount = givenVehiclePerSegment * segmentCount;
        vehiclesPerSegment = givenVehiclePerSegment;
        averageVehicleSpeed = givenAverageVehicleSpeed;
        medium = new Medium();
        statsStore = new Statistics();
        csvFileWriter = fw;

        // Spawn vehicles at random positions
        vehicles  = new ArrayList<Vehicle>();
        for (int i = 1; i <= totalVehicleCount; i++) {
            // givenAverageVehicleSpeed * 0.277 is to convert km/h to m/s
            vehicles.add(new Vehicle(i, givenAverageVehicleSpeed * 0.277f, statsStore, medium));
        }

        // Spawn RSU's in the mid of each (implicit) segment
        roadSideUnits = new ArrayList<RoadSideUnit>();
        float rsuPosition = Config.SEGMENT_LENGTH * 0.5f;
        int rsuId = 1;
        while (rsuPosition < Config.ROAD_END) {
            if (rsuId % 2 == 1) { // Position RSU's at alternate segments
                roadSideUnits.add(new RoadSideUnit(-1 * rsuId, rsuPosition, statsStore, medium));
            }
            rsuPosition += Config.SEGMENT_LENGTH;
            rsuId++;
        }
        System.out.println("Simulation Initialised");    
    }

    public void printStatistics() {
        statsStore.printStatistics(vehiclesPerSegment, averageVehicleSpeed, csvFileWriter);
    }

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
        ExecutorService taskExecutor = Executors.newFixedThreadPool(vehiclesPerSegment * 3);
        
        System.out.println("Simulation Started");
        while (currentTime <= stopTime) {
            if (currentTime % 1000 == 0) {
                System.out.println("Interval " + currentTime);
                // statsStore.printStatistics(vehiclesPerSegment, averageVehicleSpeed, csvFileWriter);
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
                    assert(tasks.size() <= 3 * vehiclesPerSegment);
                    List<Future<Integer>> results =  taskExecutor.invokeAll(tasks);
                    for (Future<Integer> result : results) {
                        result.get();
                    }
                } 
                catch (InterruptedException | ExecutionException e) {
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
            String csvHeader = String.format(
                "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",
                "Average Vehicle Density",
                "Average Vehicle Speed (km/h)",
                "Requests Generated",
                "Requests Serviced",
                "Requests Queued",
                "Average Cluster Overhead",
                "Clouds formed by RSU",
                "Average Cloud Formation Time by RSU (ms)",
                "Clouds formed Distributedly",
                "Average Cloud Formation Time Distributedly (ms)",
                "Leader Change Count",
                "Member Leave Count",
                "Average Request Service Time"
            );
            fw.write(csvHeader);
            fw.flush();

            int avgVehicleSpeedKMPH = 60;
            for (int vehiclesPerSegment = 8; vehiclesPerSegment <= 36; vehiclesPerSegment += 4) {
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