public class Config {
    static double ROAD_END = 40000.0; // m
    static double ROAD_START = 0.0;
    static double VEHICLE_SPEED_MIN = 8.33; // m/s
    static double VEHICLE_SPEED_MAX = 25.0; // m/s
    static double SEGMENT_LENGTH = 600;
    static double TRANSMISSION_RANGE = 300;
    static int VEHICLE_COUNT = 3;
    static enum PACKET_TYPE {
        RREQ, RREP, RJOIN, RACK, RTEAR, PSTART, PDONE;
    }
    static int PACKET_TYPE_COUNT = 7;
    static boolean useFair = true;
    static int APPLICATION_TYPE_COUNT = 5;
    static int TRY_LOCK_WAIT_TIME = 100;
    static int STOP_TIME = 18000;
    static int MAX_RESOURCE_QUOTA = 100;
    static int PROCESSING_SPEED = 1;
    static int INV_RREQ_PROB = 10;
    static int TOTAL_CHANNEL_COUNT = 5;
}