package infrastructure;

import java.util.*;

public interface Packet {
    public Config.PACKET_TYPE getType();
    public int getSenderId();
    public int getAppId();
    public int getGenTime();
    public int getOfferedResources();
    public float getPosition();
    public float getVelocity();
    public Cloud getCloud();
    public Map<Integer, Map<Integer, Integer>> getWorkAssignment();
    public int getRequestorId();
    public int getRequestId();
    public int getWorkDoneAmount();
    public boolean didRsuReply();

    public void recordTransmission(int currentTime, float currentPosition);
    public void recordReception(int currentTime);
}