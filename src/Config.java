package src;

public class Config {
    static int ROAD_LENGTH = 40000;
    static int SEGMENT_LENGTH = 600;
    static int SEGMENT_COUNT = 1;
    static int VEHICLE_COUNT = 3;
    static int RSU_RANGE = 300;
    static int VEHICLE_RANGE = 300;
    static enum PACKET_TYPE {
        RREQ,
        RREP,
        RJOIN,
        RACK,
        RTEAR,
        PSTART,
        PDONE;
    }
    static boolean useFair = true;
    static int APPLICATION_TYPE_COUNT = 5;
    static int TRY_LOCK_WAIT_TIME = 100;
    static int STOP_TIME = 18000;
    static int MAX_RESOURCE_QUOTA = 100;
    static int PROCESSING_SPEED = 1;
}