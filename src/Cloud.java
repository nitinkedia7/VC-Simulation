import java.util.*;

public class Cloud {
    int appId;
    int currentLeaderId;
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
    List<Member> idleMembers;

    class Leader {
        int vehicleId;

        public Leader(int vehicleId) {
            this.vehicleId = vehicleId;
        }
    }
    List<Leader> futureLeaders;

    public Cloud(Simulator simulatorRef, Packet packet) {
        this.appId = packet.appId;
        this.requestorId = packet.senderId;
        this.neededResources = packet.reqResources;
        this.initialRequestTime = packet.genTime;
        this.resourceQuotaMetTime = 0;
        this.simulatorRef = simulatorRef;
        this.pendingRJOINs = new LinkedList<>();
        this.members = new ArrayList<Member>();
        this.idleMembers = new ArrayList<Member>();
        this.futureLeaders = new ArrayList<Leader>(); 
        this.hasFormed = false;
        addMember(packet);
    }

    public boolean isMember(int id) {
        for (Member member : members) {
            if (member.vehicleId == id) return true;
        } 
        for (Member member : idleMembers) {
            if (member.vehicleId == id) return true;
        }
        return false;
    }

    public int findIndex(List<Member> list, int id) {
        int i = 0;
        for (Member member : list) {
            if (member.vehicleId == id) return i;
            i++;
        } 
        return -1;
    }

    public boolean remove(List<Member> list, int id) {
        for (Member member : list) {
            if (member.vehicleId == id) {
                list.remove(member);
                return true;
            }
        }
        return false;
    }

    public boolean remove1(List<Leader> list, int id) {
        for (Leader potentiaLeader : list) {
            if (potentiaLeader.vehicleId == id) {
                list.remove(potentiaLeader);
                return true;
            }
        }
        return false;
    }

    private synchronized void addIfNotPresent(List<Member> list, Member newMember) {
        for (Member member : list) {
            if (member.vehicleId == newMember.vehicleId) {
                return;
            }
        }
        list.add(newMember);
    }

    public void addMember(Packet packet) {     
        int acceptedResources = Math.min(Config.WORK_CHUNK_SIZE, neededResources);
        if (!hasFormed && acceptedResources > 0) {
            neededResources -= acceptedResources;
            addIfNotPresent(members, new Member(packet.senderId, acceptedResources));
        }
        else {
            addIfNotPresent(idleMembers, new Member(packet.senderId, Config.WORK_CHUNK_SIZE));
        }
        for (Leader potentiaLeader : futureLeaders) {
            if (potentiaLeader.vehicleId == packet.senderId) {
                return;
            }
        }
        futureLeaders.add(new Leader(packet.senderId));
    }

    public Map<Integer, Integer> reassignWork(int id) {
        Map<Integer, Integer> workAssignment = new HashMap<Integer, Integer>();
        
        int i = findIndex(members, id);
        if (i != -1) { // working member
            int reassignAmount = members.get(i).donatedResources;
            members.remove(i); 
            if (idleMembers.size() > 0) {
                workAssignment.put(idleMembers.get(0).vehicleId, reassignAmount);
                members.add(idleMembers.get(0));
                idleMembers.remove(0);
            }   
            else if (members.size() > 0) {
                workAssignment.put(members.get(0).vehicleId, reassignAmount);
                members.get(0).donatedResources += reassignAmount;
            }
            else {
                // No member is available, forfeit the work
            }
        }
        remove(idleMembers, id);
        remove1(futureLeaders, id);
        return workAssignment;
    }

    public boolean isCloudLeader(int id) {
        return currentLeaderId == id;
    }

    public boolean isNextLeader(int id) {
        Leader nextLeader = futureLeaders.get(0);
        return nextLeader != null && nextLeader.vehicleId == id; 
    }

    public void assignNextLeader() {
        Leader nextLeader = futureLeaders.get(0);
        this.currentLeaderId = nextLeader.vehicleId;
        futureLeaders.remove(0);
    }

    public void markAsDone(int vehicleId, int workDoneAmount) {
        for (Member member : members) {
            if (member.vehicleId == vehicleId) {
                member.donatedResources -= workDoneAmount;
                if (member.donatedResources == 0) {
                    member.completed = true;
                    workingMemberCount -= 1;
                    return;
                } 
            }
        }
        System.out.println(vehicleId + " is not a working member of this cloud " + appId);
    }

    public Boolean metResourceQuota() {
        return (neededResources <= 0); // returns True is satisfied
    }

    public Map<Integer, Integer> getWorkAssignment() {
        Map<Integer, Integer> workAssignment = new HashMap<Integer, Integer>();
        for (Member member : members) {
            workAssignment.put(member.vehicleId, member.donatedResources);
        }
        return workAssignment;
    }

    public void electLeader() {
        // TODO: LQI, currently the requestor is the leader
        currentLeaderId = futureLeaders.get(0).vehicleId;
        futureLeaders.remove(0);
        return;
    }

    public void addRJOINPacket(Packet packet) {
        pendingRJOINs.add(packet);
    }

    public Boolean processPendingRJOIN(int currentTime) {
        if (pendingRJOINs.isEmpty()) return false;
        Packet packet = pendingRJOINs.poll();
        this.requestorId = packet.senderId;
        this.neededResources = packet.reqResources;
        this.initialRequestTime = packet.genTime;
        addMember(packet);
        this.resourceQuotaMetTime = currentTime;
        this.workingMemberCount = members.size();
        return true;
    }

    public void recordCloudFormed(int formedTime) {
        this.hasFormed = true;
        this.resourceQuotaMetTime = formedTime;
        this.workingMemberCount = members.size();
        this.simulatorRef.recordCloudFormed(formedTime - this.initialRequestTime);
        this.printStats(true);
        return;
    }

    public void printStats(Boolean isForming) {
        String message = "Cloud with members ";
        for (int i = 0; i < members.size(); i++) {
            message += members.get(i).vehicleId + " ";
        }        
        message += ("for app id " + appId + (isForming ? " formed" : " deleted"));
        System.out.println(message);
    }
}