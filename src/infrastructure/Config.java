package infrastructure;

public class Config {
   public static int STOP_TIME = 30000;
   public static float ROAD_END = 15000.0f; // m
   public static float ROAD_START = 0.0f;

   public static float VEHICLE_SPEED_MIN = 0; // m/s
   public static float VEHICLE_SPEED_MAX = 27.78f; // m/s
   public static float VEHICLE_SPEED_STD_DEV = 1.00f;

   public static float SEGMENT_LENGTH = 600;
   public static float TRANSMISSION_RANGE = 300;
   public static enum PACKET_TYPE {
      RREQ, RJOIN, RREP, RACK, RTEAR, PSTART, PDONE, RLEAVE, RPROBE, RPRESENT;
   }
   public static boolean useFair = true;
   public static int APPLICATION_TYPE_COUNT = 5;
    // A vehicle stays in a segment for a minimum of 24 s,
    // as given by segment length (600 m) / max speed (25 m/s).
    // Thus requirement should be in seconds.
   public static int[] APPLICATION_REQUIREMENT = {500, 1000, 1500, 2000, 2500}; 
   public static int WORK_CHUNK_SIZE = 100;
   public static int PROCESSING_SPEED = 1;
    
   public static int INV_RREQ_PROB = 2000;
   public static int TOTAL_CHANNEL_COUNT = 1;
   public static int CONTENTION_WINDOW_BASE = 1;
   public static int CONTENTION_WINDOW_MAX = 1024;
    
   public static int MAX_RPRESENT_WAIT_TIME = 10;
   public static String LOG_PATH = "../logs/";
}