/**
 * PacketCustom.java: Implementation of the Packet interface
 * with members variables as required by the classical algorithm.
*/

package classic;

import java.util.*;
import infrastructure.*;

public class PacketCustom implements Packet {
    Statistics statsStore;
    Config.PACKET_TYPE type;
    int senderId;
    int genTime;   
    int requestorId;
    float position;    

    float velocity;
    Cloud cloud;       
    int offeredResources;
    Map<Integer, Map<Integer, Integer>> workAssignment;
    int workDoneAmount;
    boolean rsuReplied;
    int requestId;

    // Default constructor suffices for RLEAVE, RTEAR
    public PacketCustom(Statistics statsStore, Config.PACKET_TYPE type, int senderId, int genTime, int requestorId) {
        this.type = type;
        this.senderId = senderId;   
        this.genTime = genTime;
        this.requestorId = requestorId;
        this.statsStore = statsStore;
        statsStore.incrGenCount(type);
    }

    // Constructor for RREQ, RREP
    public PacketCustom(Statistics statsStore, Config.PACKET_TYPE type, int senderId, int genTime, int requestorId, int offeredResources) {
        this(statsStore, type, senderId, genTime, requestorId);
        assert (type == Config.PACKET_TYPE.RREQ || type == Config.PACKET_TYPE.RREP) : "Packet constructor type mismatch";
        this.offeredResources = offeredResources;
    }

    // Constructor for RACK
    public PacketCustom(Statistics statsStore, Config.PACKET_TYPE type, int senderId, int genTime, int requestorId, Cloud cloud) {
        this(statsStore, type, senderId, genTime, requestorId);
        assert (type == Config.PACKET_TYPE.RACK) : "Packet constructor type mismatch";
        this.cloud = cloud;
    }

    // Constructor for PSTART
    public PacketCustom(Statistics statsStore, Config.PACKET_TYPE type, int senderId, int genTime, int requestorId, Map<Integer, Map<Integer, Integer>> workAssignment) {
        this(statsStore, type, senderId, genTime, requestorId);
        assert (type == Config.PACKET_TYPE.PSTART) : "Packet constructor type mismatch";
        this.workAssignment = workAssignment;
    }

    // Constructor for PDONE
    public PacketCustom(Statistics statsStore, Config.PACKET_TYPE type, int senderId, int genTime, int requestorId, int requestId, int workDoneAmount) {
        this(statsStore, type, senderId, genTime, requestorId);
        assert (type == Config.PACKET_TYPE.PDONE) : "Packet constructor type mismatch";
        this.workDoneAmount = workDoneAmount;
        this.requestId = requestId;
    }

    public Config.PACKET_TYPE getType() {
        return type;
    }

    public int getSenderId() {
        return senderId;
    }

    public int getAppId() {
        return -1;
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