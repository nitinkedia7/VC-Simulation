public class Packet {
    Config.PACKET_TYPE type;
    int LQI;
    int appId;
    int senderId;
    int sentTime;          
    int cloudId;       

    public Packet(Config.PACKET_TYPE type, int senderId, int sentTime) {
        this.type = type;
        this.senderId = senderId;   
        this.sentTime = sentTime;
    } 

    public static Packet generateRJOINPacket(int senderId, int sentTime, int LQI, int appId) {
        Packet rjoinPacket = new Packet(Config.PACKET_TYPE.RJOIN, senderId, sentTime);
        rjoinPacket.LQI = LQI;
        rjoinPacket.appId = appId; 
        return rjoinPacket;
    }

    public static Packet generateRREQPacket(int senderId, int sentTime, int LQI, int appId) {
        Packet rreqPacket = new Packet(Config.PACKET_TYPE.RREQ, senderId, sentTime);
        rreqPacket.LQI = LQI;
        rreqPacket.appId = appId; 
        return rreqPacket;
    }

}