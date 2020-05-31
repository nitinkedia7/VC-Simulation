/**
 * classical/Simulator.java: Clone of the advanced alrorithm
 * simulator. RSU's are not required in the classical algorithm.
 */

package classic;

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
        System.out.println("Simulation Initialised");    
    }

    public void printStatistics() {
        statsStore.printStatistics(vehiclesPerSegment, averageVehicleSpeed, csvFileWriter);
    }

    public void run() { // Optimised run implementation
        ExecutorService taskExecutor = Executors.newFixedThreadPool(totalVehicleCount + 10);
        List<Callable<Integer>> tasks = new ArrayList<Callable<Integer>>();
        for (Callable<Integer> vehicle : vehicles) {
            tasks.add(vehicle);
        }
        
        System.out.println("Simulation Started");
        while (currentTime <= stopTime) {
            if (currentTime % 1000 == 0) {
                System.out.println("Interval " + currentTime);
                // statsStore.printStatistics(vehiclesPerSegment, averageVehicleSpeed, csvFileWriter);
            }
            Collections.shuffle(tasks);
            try {
                List<Future<Integer>> results = taskExecutor.invokeAll(tasks);
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