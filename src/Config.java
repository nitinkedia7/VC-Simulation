public class Config {
    static double ROAD_END = 9000.0; // m
    static double ROAD_START = 0.0;
    static double VEHICLE_SPEED_MIN = 8.33; // m/s
    static double VEHICLE_SPEED_MAX = 25.0; // m/s
    static double SEGMENT_LENGTH = 610;
    static double TRANSMISSION_RANGE = 300;
    static enum PACKET_TYPE {
        RREQ, RREP, RJOIN, RACK, RTEAR, PSTART, PDONE, RLEAVE;
    }
    static int PACKET_TYPE_COUNT = 8;
    static boolean useFair = true;
    static int APPLICATION_TYPE_COUNT = 5;
    static int TRY_LOCK_WAIT_TIME = 100;
    static int STOP_TIME = 10000;
    static int MAX_RESOURCE_QUOTA = 100;
    static int PROCESSING_SPEED = 1;
    static int INV_RREQ_PROB = 10000;
    static int TOTAL_CHANNEL_COUNT = 5;
    static int CONTENTION_WINDOW_BASE = 1;
    static int CONTENTION_WINDOW_MAX = 1024;
    static String OUTPUT_FILENAME = "output.txt"; 
}