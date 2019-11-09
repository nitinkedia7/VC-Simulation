public class Config {
    static int ROAD_LENGTH = 40000;
    static int SEGMENT_LENGTH = 600;
    static int SEGMENT_COUNT = 60;
    static int RSU_RANGE = 300;
    static int VEHICLE_RANGE = 300;
    static enum PACKET_TYPE {
        RREQ, RRES, RJOIN;
    }
    static boolean useFair = true;
    static int APPLICATION_TYPE = 5;
    static int TRY_LOCK_WAIT_TIME = 100;
}