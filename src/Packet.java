import java.util.*;

public class Packet {
    Config.PACKET_TYPE type;
    int senderId;
    int genTime;   
    int appId;
    double velocity;
    double position;
    Cloud cloud;       
    int reqResources;
    Simulator simulatorRef;
    Map<Integer, Integer> workAssignment;
    int workDoneAmount;

    // Default constructor, suffices for RLEAVE, RTEAR
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
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, double velocity, int appId, int reqResources) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.RREQ) : "Packet constructor type mismatch";
        this.reqResources = reqResources;
        this.velocity = velocity;
    }

    // Constructor for RREP
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, double velocity, int appId) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.RREP) : "Packet constructor type mismatch";
        this.velocity = velocity;
    }

    // Constructor for RACK
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, int appId, Cloud cloud) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.RACK) : "Packet constructor type mismatch";
        this.cloud = cloud;
    }

    // Constructor for PSTART
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, int appId, Map<Integer, Integer> workAssignment) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.PSTART) : "Packet constructor type mismatch";
        this.workAssignment = workAssignment;
    }

    // Constructor for PDONE
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, int appId, int workDoneAmount) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.PDONE) : "Packet constructor type mismatch";
        this.workDoneAmount = workDoneAmount;
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