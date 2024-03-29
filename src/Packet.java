import java.util.*;

public class Packet {
    Config.PACKET_TYPE type;
    int senderId;
    int genTime;   
    int appId;
    float velocity;
    float position;
    Cloud cloud;       
    int reqResources;
    int offeredResources;
    Simulator simulatorRef;
    Map<Integer, Map<Integer, Integer>> workAssignment;
    int workDoneAmount;
    int requestorId;
    boolean rsuReplied;
    int reqId;

    // Default constructor, suffices for RLEAVE, RTEAR, RPROBE
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, int appId) {
        this.type = type;
        this.senderId = senderId;   
        this.genTime = genTime;
        this.appId = appId;
        this.simulatorRef = simulatorRef;
        simulatorRef.incrGenCount(type);
        // System.out.println("Packet " + ": Sender " + senderId + " generated " + type + " at " + genTime);
    }

    // Constructor for RREQ / RJOIN
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, float velocity, int appId, int reqResources, int offeredResources) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.RREQ || type == Config.PACKET_TYPE.RJOIN) : "Packet constructor type mismatch";
        this.velocity = velocity;
        this.reqResources = reqResources;
        this.offeredResources = offeredResources;
    }

    // Constructor for RREP
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, float velocity, int appId, int offeredResources) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.RREP) : "Packet constructor type mismatch";
        this.velocity = velocity;
        this.offeredResources = offeredResources;
    }

    // Constructor for RACK
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, int appId, Cloud cloud) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.RACK) : "Packet constructor type mismatch";
        this.cloud = cloud;
    }

    // Constructor for PSTART
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, int appId, Map<Integer, Map<Integer, Integer>> workAssignment) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.PSTART) : "Packet constructor type mismatch";
        this.workAssignment = workAssignment;
    }

    // Constructor for PDONE
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, int appId, int workDoneAmount, int reqId) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.PDONE) : "Packet constructor type mismatch";
        this.workDoneAmount = workDoneAmount;
        this.reqId = reqId;
    }

    // Constructor for RPRESENT
    public Packet(Simulator simulatorRef, Config.PACKET_TYPE type, int senderId, int genTime, int appId, int requestorId, boolean rsuReplied) {
        this(simulatorRef, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.RPRESENT) : "Packet constructor type mismatch";
        this.requestorId = requestorId;
        this.rsuReplied = rsuReplied;
    }

    public void recordTransmission(int currentTime, float currentPosition) {
        this.position = currentPosition;
        simulatorRef.recordTransmission(type, currentTime - genTime);
    }

    public void recordReception(int currentTime) {
        simulatorRef.recordReception(type, currentTime - genTime);
    }
}