public class Config {
    static double ROAD_END = 15000.0; // m
    static double ROAD_START = 0.0;

    static double VEHICLE_SPEED_MIN = 0; // m/s
    static double VEHICLE_SPEED_MAX = 27.78; // m/s
    static double VEHICLE_SPEED_STD_DEV = 1.00;

    static double SEGMENT_LENGTH = 600;
    static double TRANSMISSION_RANGE = 300;
    static enum PACKET_TYPE {
        RREQ, RJOIN, RREP, RACK, RTEAR, PSTART, PDONE, RLEAVE, RPROBE, RPRESENT;
    }
    static int PACKET_TYPE_COUNT = 8;
    static boolean useFair = true;
    static int APPLICATION_TYPE_COUNT = 5;
    static int TRY_LOCK_WAIT_TIME = 100;
    static int STOP_TIME = 10000;
    // A vehicle stays in a segment for a minimum of 24 s,
    // as given by segment length (600 m) / max speed (25 m/s).
    static int MAX_RESOURCE_QUOTA = 1000; // should in order of seconds
    static int WORK_CHUNK_SIZE = 100;
    static int PROCESSING_SPEED = 1;
    
    static int INV_RREQ_PROB = 2000;
    static int TOTAL_CHANNEL_COUNT = 5;
    static int CONTENTION_WINDOW_BASE = 1;
    static int CONTENTION_WINDOW_MAX = 1024;
    
    static int MAX_RQUEUE_WAIT_TIME = 50;
    static String LOG_PATH = "../logs/";
}