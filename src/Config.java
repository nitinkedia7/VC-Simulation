public class Config {
    static int STOP_TIME = 30000;
    static float ROAD_END = 15000.0f; // m
    static float ROAD_START = 0.0f;

    static float VEHICLE_SPEED_MIN = 0; // m/s
    static float VEHICLE_SPEED_MAX = 27.78f; // m/s
    static float VEHICLE_SPEED_STD_DEV = 1.00f;

    static float SEGMENT_LENGTH = 600;
    static float TRANSMISSION_RANGE = 300;
    static enum PACKET_TYPE {
        RREQ, RJOIN, RREP, RACK, RTEAR, PSTART, PDONE, RLEAVE, RPROBE, RPRESENT;
    }
    static boolean useFair = true;
    static int APPLICATION_TYPE_COUNT = 5;
    // A vehicle stays in a segment for a minimum of 24 s,
    // as given by segment length (600 m) / max speed (25 m/s).
    // Thus requirement should be in seconds.
    static int[] APPLICATION_REQUIREMENT = {500, 1000, 1500, 2000, 2500}; 
    static int WORK_CHUNK_SIZE = 100;
    static int PROCESSING_SPEED = 1;
    
    static int INV_RREQ_PROB = 2000;
    static int TOTAL_CHANNEL_COUNT = 1;
    static int CONTENTION_WINDOW_BASE = 1;
    static int CONTENTION_WINDOW_MAX = 1024;
    
    static int MAX_RPRESENT_WAIT_TIME = 10;
    static String LOG_PATH = "../logs/";
}