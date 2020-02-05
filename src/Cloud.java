package src;

import java.util.*;

public class Cloud {
    int appId;
    int requestorId;
    int neededResources;

    class Member {
        int vehicleId;
        int donatedResources;

        public Member(int vehicleId, int donatedResources) {
            this.vehicleId = vehicleId;
            this.donatedResources = donatedResources;
        }
    }    
    List<Member> members;

    public Cloud(Packet packet) {
        this.appId = packet.appId;
        this.requestorId = packet.senderId;
        this.neededResources = packet.reqResources;
        this.members = new ArrayList<Member>(); 
        addMember(packet);
    }

    public void addMember(Packet packet) {
        int acceptedResources = Math.min(packet.donatedResources, neededResources);
        neededResources -= acceptedResources;
        this.members.add(new Member(packet.senderId, acceptedResources));
    }

    public Boolean metResourceQuota() {
        return (neededResources == 0); // returns True is satisfied
    }

    public void printStats() {
        System.out.print("Cloud generated with members ");
        for (int i = 0; i < members.size(); i++) {
            System.out.print(members.get(i).vehicleId + " ");
        }        
        System.out.println("for app id " + appId);
    }
}