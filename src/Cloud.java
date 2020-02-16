import java.util.*;

public class Cloud {
    int appId;
    int requestorId;
    int neededResources;
    int workingMemberCount;
    int initialRequestTime;
    int resourceQuotaMetTime;
    Simulator simulatorRef;

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
        this.members = new ArrayList<Member>(); 
        addMember(packet);
    }

    public void addMember(Packet packet) {
        int acceptedResources = Math.min(packet.donatedResources, neededResources);
        neededResources -= acceptedResources;
        this.members.add(new Member(packet.senderId, acceptedResources));
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
            }
        }
        System.out.println(vehicleId + " is not a member of this cloud " + appId);
    }

    public Boolean metResourceQuota() {
        return (neededResources == 0); // returns True is satisfied
    }

    public void recordCloudFormed(int formedTime) {
        this.resourceQuotaMetTime = formedTime;
        this.simulatorRef.recordCloudFormed(formedTime - this.initialRequestTime);
        return;
    }

    public void printStats(Boolean isForming) {
        System.out.print("Cloud with members ");
        for (int i = 0; i < members.size(); i++) {
            System.out.print(members.get(i).vehicleId + " ");
        }        
        System.out.println("for app id " + appId + (isForming ? " formed" : " deleted")) ;
    }
}