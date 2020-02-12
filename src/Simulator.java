import java.util.Random;
import java.util.ArrayList;
import java.util.concurrent.Phaser;

public class Simulator implements Runnable {
    int currentTime;
    int stopTime;
    ArrayList<Vehicle> vehicles;
    ArrayList<RoadSideUnit> roadSideUnits;
    Medium medium;
    Phaser timeSync;

    public Simulator() {
        currentTime = 0;
        stopTime = Config.STOP_TIME;
        timeSync = new Phaser();
        timeSync.register();
        medium = new Medium();

        // Spawn vehicles at random positions
        vehicles  = new ArrayList<Vehicle>();
        for (int i = 1; i <= Config.VEHICLE_COUNT; i++) {
            vehicles.add(new Vehicle(i, timeSync, medium, stopTime));
        }

        // Spawn RSU's in the mid of each (implicit) segment
        roadSideUnits = new ArrayList<RoadSideUnit>();
        double rsuPosition = Config.SEGMENT_LENGTH * 0.5;
        int rsuId = 1;
        while (rsuPosition < Config.ROAD_END) {
            roadSideUnits.add(new RoadSideUnit(-1 * rsuId, rsuPosition, timeSync, medium, stopTime));
            rsuPosition += Config.SEGMENT_LENGTH;
            rsuId++;
        }
        System.out.println("Simulation Initialised");    
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