import java.util.*;

public class Packet {
    static int id = 0;
    Config.PACKET_TYPE type;
    int LQI;
    int appId;
    int senderId;
    int sentTime;          
    int cloudId;
    List<Integer> memberList;       

    public Packet(Config.PACKET_TYPE type, int senderId, int sentTime, int LQI, int appId, List<Integer> memberList) {
        this.id += 1;
        this.type = type;
        this.senderId = senderId;   
        this.sentTime = sentTime;
        this.appId = appId;
        this.LQI = LQI;
        this.memberList = memberList;
        System.out.println("Packet " + id + ": Sender " + senderId + " generated " + type + " at " + sentTime);
    }
}