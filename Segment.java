import java.util.ArrayList;
import java.util.concurrent.Phaser;

/**
 * This class is the smallest unit in the simulation.
 * As an MVP, let's run one segment.
 */
public class Segment implements Runnable {
    int id;
    int currentTime;
    int stopTime;
    ArrayList<Vehicle> vehicles;
    RoadSideUnit rsu;
    Medium medium;
    Phaser timeSync;

    public Segment(int segmentId, int givenStopTime) {
        id = segmentId;
        currentTime = 0;
        stopTime = givenStopTime;
        timeSync = new Phaser();
        timeSync.register();
        medium = new Medium();
        vehicles = new ArrayList<Vehicle>();
        for (int i = 1; i <= Config.VEHICLE_COUNT; i++) {
            vehicles.add(new Vehicle(i, 0, 0, timeSync, medium, this, givenStopTime));
        }
        rsu = new RoadSideUnit(id, 0, timeSync, medium, this, givenStopTime);
        System.out.println("Segment " + id + " initialised.");
    }

    public void run() {
        new Thread(rsu).start();
        for (int i = 1; i <= Config.VEHICLE_COUNT; i++) {
            new Thread(vehicles.get(i)).start();
        }
        while (currentTime <= stopTime) {
            timeSync.arriveAndAwaitAdvance();
            currentTime++;
        }
        System.out.println("Segment " + id + " stopped after " + stopTime + " timeUnits");   
    }
}
