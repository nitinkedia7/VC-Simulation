package src;

import java.util.*;

public class Packet {
    Config.PACKET_TYPE type;
    int LQI;
    int appId;
    int senderId;
    int sentTime;          
    int cloudId;
    List<Packet> memberList;

    public Packet(Config.PACKET_TYPE type, int senderId, int sentTime, int LQI, int appId, List<Packet> memberList) {
        this.type = type;
        this.senderId = senderId;   
        this.sentTime = sentTime;
        this.appId = appId;
        this.LQI = LQI;
        this.memberList = memberList;
        System.out.println("Packet " + ": Sender " + senderId + " generated " + type + " at " + sentTime);
    }
}