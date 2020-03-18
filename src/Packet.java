public class Packet {
    Config.PACKET_TYPE type;
    int senderId;
    int genTime;   
    int appId;
    double velocity;
    double position;
    Cloud cloud;       
    int reqResources;
    int donatedResources;
    Simulator simulatorRef;

    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, int appId) {
        this.type = type;
        this.senderId = senderId;   
        this.genTime = genTime;
        this.appId = appId;
        this.simulatorRef = simulatorRef;
        simulatorRef.incrGenCount(type);
        // System.out.println("Packet " + ": Sender " + senderId + " generated " + type + " at " + genTime);
    }

    // Constructor for RREQ
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, double velocity, int appId, int reqResources, int donatedResources) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.RREQ) : "Packet constructor type mismatch";
        this.reqResources = reqResources;
        this.donatedResources = donatedResources;
        this.velocity = velocity;
    }    


    // Constructor for RREP, RJOIN
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, double velocity, int appId, int donatedResources) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.RREP || type == Config.PACKET_TYPE.RJOIN) : "Packet constructor type mismatch";
        this.donatedResources = donatedResources;
        this.velocity = velocity;
    }

    // Constructor for RACK, PSTART
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, int appId, Cloud cloud) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.RACK || type == Config.PACKET_TYPE.PSTART) : "Packet constructor type mismatch";
        this.cloud = cloud;
    }


    public void recordTransmission(int currentTime, double currentPosition) {
        this.position = currentPosition;
        simulatorRef.recordTransmission(type, currentTime - genTime);
    }

    public void recordReception(int currentTime) {
        simulatorRef.recordReception(type, currentTime - genTime);
    }

    public void printRead(int readerId) {
        // System.out.println("Packet : " + readerId + " read " + type + " from " + senderId + " at " + genTime);
        return;
    }
}