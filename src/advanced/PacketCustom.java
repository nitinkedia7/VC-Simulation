package advanced;

import java.util.*;
import infrastructure.*;

public class PacketCustom implements Packet {
    Config.PACKET_TYPE type;
    int senderId;
    int genTime;   
    int appId;
    float position;    
    Statistics statsStore;

    float velocity;
    Cloud cloud;       
    int offeredResources;
    Map<Integer, Map<Integer, Integer>> workAssignment;
    int workDoneAmount;
    boolean rsuReplied;
    int requestorId;
    int requestId;

    public PacketCustom(Statistics statsStore, Config.PACKET_TYPE type, int senderId, int genTime, int appId) {
        this.type = type;
        this.senderId = senderId;   
        this.genTime = genTime;
        this.appId = appId;
        this.statsStore = statsStore;
        statsStore.incrGenCount(type);
        // System.out.println("Packet " + ": Sender " + senderId + " generated " + type + " at " + genTime);
    }

    // Constructor for RREQ / RJOIN / RREP
    public PacketCustom(Statistics statsStore, Config.PACKET_TYPE type, int senderId, int genTime, float velocity, int appId, int offeredResources) {
        this(statsStore, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.RREQ || type == Config.PACKET_TYPE.RJOIN || type == Config.PACKET_TYPE.RREP) : "Packet constructor type mismatch";
        this.velocity = velocity;
        this.offeredResources = offeredResources;
    }

    // Constructor for RACK
    public PacketCustom(Statistics statsStore, Config.PACKET_TYPE type, int senderId, int genTime, int appId, Cloud cloud) {
        this(statsStore, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.RACK) : "Packet constructor type mismatch";
        this.cloud = cloud;
    }

    // Constructor for PSTART
    public PacketCustom(Statistics statsStore, Config.PACKET_TYPE type, int senderId, int genTime, int appId, Map<Integer, Map<Integer, Integer>> workAssignment) {
        this(statsStore, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.PSTART) : "Packet constructor type mismatch";
        this.workAssignment = workAssignment;
    }

    // Constructor for PDONE
    public PacketCustom(Statistics statsStore, Config.PACKET_TYPE type, int senderId, int genTime, int appId, int workDoneAmount, int requestId) {
        this(statsStore, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.PDONE) : "Packet constructor type mismatch";
        this.workDoneAmount = workDoneAmount;
        this.requestId = requestId;
    }

    // Constructor for RPRESENT
    public PacketCustom(Statistics statsStore, Config.PACKET_TYPE type, int senderId, int genTime, int appId, int requestorId, boolean rsuReplied) {
        this(statsStore, type, senderId, genTime, appId);
        assert (type == Config.PACKET_TYPE.RPRESENT) : "Packet constructor type mismatch";
        this.requestorId = requestorId;
        this.rsuReplied = rsuReplied;
    }

    public Config.PACKET_TYPE getType() {
        return type;
    }

    public int getSenderId() {
        return senderId;
    }

    public int getAppId() {
        return appId;
    }

    public int getGenTime() {
        return genTime;
    }

    public int getOfferedResources() {
        return offeredResources;
    }

    public float getPosition() {
        return position;
    }

    public float getVelocity() {
        return velocity;
    }

    public Cloud getCloud() {
        return cloud;
    }

    public Map<Integer, Map<Integer, Integer>> getWorkAssignment() {
        return workAssignment;
    }

    public int getRequestId() {
        return requestId;
    }

    public int getWorkDoneAmount() {
        return workDoneAmount;
    }

    public int getRequestorId() {
        return requestorId;
    }

    public boolean didRsuReply() {
        return rsuReplied;
    }

    public void recordTransmission(int currentTime, float currentPosition) {
        this.position = currentPosition;
        statsStore.recordTransmission(type, currentTime - genTime);
    }

    public void recordReception(int currentTime) {
        statsStore.recordReception(type, currentTime - genTime);
    }
}