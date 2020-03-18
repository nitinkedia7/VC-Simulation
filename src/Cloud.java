import java.util.*;

public class Cloud {
    int appId;
    int requestorId;
    int neededResources;
    int workingMemberCount;
    int initialRequestTime;
    int resourceQuotaMetTime;
    Simulator simulatorRef;
    Queue<Packet> pendingRJOINs;
    Boolean hasFormed;

    class Member {
        int vehicleId;
        int donatedResources;
        Boolean completed;

        public Member(int vehicleId, int donatedResources) {
            this.vehicleId = vehicleId;
            this.donatedResources = donatedResources;
            this.completed = false;
        }
    }    
    List<Member> members;

    public Cloud(Simulator simulatorRef, Packet packet) {
        this.appId = packet.appId;
        this.requestorId = packet.senderId;
        this.neededResources = packet.reqResources;
        this.initialRequestTime = packet.genTime;
        this.resourceQuotaMetTime = 0;
        this.simulatorRef = simulatorRef;
        this.pendingRJOINs = new LinkedList<>();
        this.members = new ArrayList<Member>(); 
        this.hasFormed = false;
        addMember(packet);
    }

    public Boolean addMember(Packet packet) {
        if (hasFormed) {
            // System.out.println("cannot Add member " + packet.senderId);
            return false;
        }
        // System.out.println("Adding member " + packet.senderId);
        int acceptedResources = Math.min(packet.donatedResources, neededResources);
        neededResources -= acceptedResources;
        for (Member member : members) {
            if (member.vehicleId == packet.senderId) {
                System.out.println("Added member " + packet.senderId);
                return true;
            }
        }
        members.add(new Member(packet.senderId, acceptedResources));
        // System.out.println("Added member " + packet.senderId);
        return true;
    }

    public int getDonatedAmount(int vehicleId) {
        for (Member member : members) {
            if (member.vehicleId == vehicleId) {
                return member.donatedResources;
            }
        }
        return 0;
    }

    public void markAsDone(int vehicleId) {
        for (Member member : members) {
            if (member.vehicleId == vehicleId) {
                member.completed = true;
                workingMemberCount -= 1;
                return;
            }
        }
        System.out.println(vehicleId + " is not a member of this cloud " + appId);
    }

    public Boolean metResourceQuota() {
        return (neededResources <= 0); // returns True is satisfied
    }

    public void recordCloudFormed(int formedTime) {
        this.hasFormed = true;
        this.resourceQuotaMetTime = formedTime;
        this.simulatorRef.recordCloudFormed(formedTime - this.initialRequestTime);
        return;
    }

    public void addRJOINPacket(Packet packet) {
        pendingRJOINs.add(packet);
    }

    public Boolean processPendingRJOIN() {
        if (pendingRJOINs.isEmpty()) return false;
        Packet packet = pendingRJOINs.poll();
        this.requestorId = packet.senderId;
        this.neededResources = packet.reqResources;
        this.initialRequestTime = packet.genTime;
        this.resourceQuotaMetTime = 0;
        this.hasFormed = false;
        addMember(packet);
        return true;
    }

    public void printStats(Boolean isForming) {
        String message = "Cloud with members ";
        for (int i = 0; i < members.size(); i++) {
            message += members.get(i).vehicleId + " ";
        }        
        message += ("for app id " + appId + (isForming ? " formed" : " deleted"));
        System.out.println(message);
    }

    public void test() {
        System.out.println("SOS");
    }
}