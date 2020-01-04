import java.util.ArrayList;

public class Simulator {
    ArrayList<Segment> segments;

    public Simulator() {
        segments = new ArrayList<Segment>();
        for (int i = 0; i < Config.SEGMENT_COUNT; i++) {
            segments.add(new Segment(i, Config.STOP_TIME));
        }
    }

    public static void main(String[] args) {
        Simulator simulator = new Simulator();
        for (int i = 0; i < Config.SEGMENT_COUNT; i++) {
            new Thread(simulator.segments.get(i)).start();
        }
    } 
}