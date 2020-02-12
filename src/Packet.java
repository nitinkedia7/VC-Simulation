public class Packet {
    Config.PACKET_TYPE type;
    int senderId;
    int sentTime;   
    int appId;
    double velocity;
    Cloud cloud;       
    int reqResources;
    int donatedResources;

    public Packet(Config.PACKET_TYPE type, int senderId, int sentTime, int appId) {
        this.type = type;
        this.senderId = senderId;   
        this.sentTime = sentTime;
        this.appId = appId;
        System.out.println("Packet " + ": Sender " + senderId + " generated " + type + " at " + sentTime);
    }

    // Constructor for RREQ
    public Packet(Config.PACKET_TYPE type, int senderId, int sentTime, double velocity, int appId, int reqResources, int donatedResources) {
        this(type, senderId, sentTime, appId);
        assert (type == Config.PACKET_TYPE.RREQ) : "Packet constructor type mismatch";
        this.reqResources = reqResources;
        this.donatedResources = donatedResources;
        this.velocity = velocity;
    }    


    // Constructor for RREP, RJOIN
    public Packet(Config.PACKET_TYPE type, int senderId, int sentTime, double velocity, int appId, int donatedResources) {
        this(type, senderId, sentTime, appId);
        assert (type == Config.PACKET_TYPE.RREP || type == Config.PACKET_TYPE.RJOIN) : "Packet constructor type mismatch";
        this.donatedResources = donatedResources;
        this.velocity = velocity;
    }

    // Constructor for RACK, PSTART
    public Packet(Config.PACKET_TYPE type, int senderId, int sentTime, int appId, Cloud cloud) {
        this(type, senderId, sentTime, appId);
        assert (type == Config.PACKET_TYPE.RACK || type == Config.PACKET_TYPE.PSTART) : "Packet constructor type mismatch";
        this.cloud = cloud;
    }

    public void printRead(int readerId) {
        System.out.println("Packet : " + readerId + " read " + type + " from " + senderId + " at " + sentTime);
        return;
    }
}