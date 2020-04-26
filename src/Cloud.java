import java.util.*;

public class Cloud {
    int appId;
    int currentLeaderId;
    int requestorId;
    int neededResources;
    int finishedWork;
    int initialRequestTime;
    int resourceQuotaMetTime;
    boolean formedByRSU;
    Simulator simulatorRef;
    Queue<Packet> pendingRequests;
    class Member {
        int id;
        int donatedResources;
        Boolean completed;

        public Member(int id, int donatedResources) {
            this.id = id;
            this.donatedResources = donatedResources;
            this.completed = false;
        }
    }    
    List<Member> workingMembers;
    List<Member> idleMembers;
    class Leader {
        int id;
        double speed;
        double LQI;

        public Leader(int id, double speed) {
            this.id = id;
            this.speed = speed;
            this.LQI = 0;
        }
    }
    List<Leader> futureLeaders;
    class SortByLQI implements Comparator<Leader> {
        public int compare(Leader x, Leader y) {
            if (x.LQI < y.LQI) return 1;
            else if (x.LQI == y.LQI) return 0;
            return -1;    
        }
    }

    public Cloud(Simulator simulatorRef, int appId, int parentId, boolean formedByRSU) {
        this.appId = appId;
        this.currentLeaderId = parentId;
        this.simulatorRef = simulatorRef;
        this.formedByRSU = formedByRSU;
        this.pendingRequests = new LinkedList<>();
        this.workingMembers = new ArrayList<Member>();
        this.idleMembers = new ArrayList<Member>();
        this.futureLeaders = new ArrayList<Leader>(); 
        return;
    }

    public boolean isMember(int id) {
        for (Member member : workingMembers) {
            if (member.id == id) return true;
        }
        for (Member member : idleMembers) {
            if (member.id == id) return true;
        } 
        return false;
    }
    
    public boolean isCloudLeader(int id) {
        return currentLeaderId == id;
    }

    public boolean isNextLeader(int id) {
        if (futureLeaders.size() < 2) {
            System.out.println("No future leader");
        }
        return futureLeaders.size() >= 2 && futureLeaders.get(1).id == id;
    }

    public void assignNextLeader() {
        if (futureLeaders.size() < 2) {
            System.out.println("No future leader can be assigned");
        }
        Leader nextLeader = futureLeaders.get(1);
        this.currentLeaderId = nextLeader.id;
    }

    public void electLeader() {
        simulatorRef.incrLeaderAlgoInvokedCount();
        for (Leader potentiaLeader1 : futureLeaders) {
            for (Leader potentialLeader2 : futureLeaders) {
                potentiaLeader1.LQI += Math.abs(potentiaLeader1.speed - potentialLeader2.speed); 
            }
        }
        Collections.sort(futureLeaders, new SortByLQI());
        currentLeaderId = futureLeaders.get(0).id;
        return;
    }

    public void addRequestor(Packet reqPacket) {
        if (appId != reqPacket.appId) {
            System.out.println("CONFLICT: cloud appId amd request appId");
        }
        this.requestorId = reqPacket.senderId;
        this.neededResources = reqPacket.reqResources;
        this.finishedWork = 0;
        this.initialRequestTime = reqPacket.genTime;
        this.resourceQuotaMetTime = Integer.MAX_VALUE;
        for (Member member : workingMembers) {
            idleMembers.add(member);
        }
        workingMembers.clear();
        addMember(reqPacket);
    }

    public void addMember(Packet packet) {
        // Add if not present to the idle member and future leader list
        for (Member member : idleMembers) {
            if (member.id == packet.senderId) return;
        }
        idleMembers.add(new Member(packet.senderId, Config.WORK_CHUNK_SIZE));    
        for (Leader potentiaLeader : futureLeaders) {
            if (potentiaLeader.id == packet.senderId) {
                return;
            }
        }
        futureLeaders.add(new Leader(packet.senderId, packet.velocity));
    }

    public Boolean justMetResourceQuota() {
        return resourceQuotaMetTime == Integer.MAX_VALUE && idleMembers.size() * Config.WORK_CHUNK_SIZE >= neededResources;
    }

    public void assignWork() {
        int allocatedWork = 0;
        while (allocatedWork < neededResources && idleMembers.size() > 0) {
            workingMembers.add(new Member(idleMembers.get(0).id, Config.WORK_CHUNK_SIZE));
            idleMembers.remove(0);
            allocatedWork += Config.WORK_CHUNK_SIZE;
        }
        neededResources = allocatedWork;    
        return;
    }

    public Map<Integer, Integer> getWorkAssignment() {
        Map<Integer, Integer> workAssignment = new HashMap<Integer, Integer>();
        for (Member member : workingMembers) {
            if (member.donatedResources == 0) {
                System.out.println("Member " + member.id + " has 0 donated resources.");
            }
            workAssignment.put(member.id, member.donatedResources);
        } 
        return workAssignment;
    }

    public Map<Integer, Integer> reassignWork(int id) {
        Map<Integer, Integer> workAssignment = new HashMap<Integer, Integer>();
        int i = 0;
        for (Member member : workingMembers) {
            if (member.id == id) break;
            i++;
        }   

        if (i != workingMembers.size()) {
            int reassignAmount = workingMembers.get(i).donatedResources;
            workingMembers.remove(i);
            if (reassignAmount != 0) {
                if (idleMembers.size() > 0) {
                    System.out.println("Reassigned " + idleMembers.get(0).id + " for extra " + reassignAmount);
                    workAssignment.put(idleMembers.get(0).id, reassignAmount);
                    int idleMemberId = idleMembers.get(0).id;
                    workingMembers.add(new Member(idleMemberId, reassignAmount));
                    idleMembers.remove(0);
                }   
                else if (workingMembers.size() > 0) {
                    System.out.println("Reassigned " + workingMembers.get(0).id + " for extra " + reassignAmount);
                    workAssignment.put(workingMembers.get(0).id, reassignAmount);
                    workingMembers.get(0).donatedResources += reassignAmount;
                }
                else {
                    // No member is available, forfeit the work
                    System.out.println("Reassignment forfeited");
                    finishedWork += reassignAmount;
                }
            }
        }
        for (Member member : idleMembers) {
            if (member.id == id) {
                idleMembers.remove(member);
                break;
            }
        }
        for (Leader potentiaLeader : futureLeaders) {
            if (potentiaLeader.id == id) {
                futureLeaders.remove(potentiaLeader);
                break;
            }
        }
        return workAssignment;
    }

    public Boolean workFinished() {
        return finishedWork >= neededResources;
    }
    
    public void markAsDone(int id, int workDoneAmount) {
        // System.out.println(id + " submits " + workDoneAmount + " work for appId " + appId);
        for (Member member : workingMembers) {
            if (member.id == id) {
                // System.out.println(id + " finished " + workDoneAmount + " work for appId " + appId);
                finishedWork += workDoneAmount;
                member.donatedResources -= workDoneAmount;
                if (member.donatedResources == 0) {
                    member.completed = true;
                }
                return;
            }
        }
        // System.out.println(id + " illegal " + workDoneAmount + " work for appId " + appId);
    }

    public void queueRequestPacket(Packet packet) {
        pendingRequests.add(packet);
    }

    public boolean processPendingRequest(int currentTime) {
        if (pendingRequests.isEmpty()) return false;
        Packet reqPacket = pendingRequests.poll();
        addRequestor(reqPacket);
        assignWork();
        this.resourceQuotaMetTime = currentTime;
        this.simulatorRef.incrTotalCloudsRecycled();
        this.printStats("recycled");
        return true;
    }

    public void recordCloudFormed(int formedTime, String type) {
        this.resourceQuotaMetTime = formedTime;
        this.simulatorRef.recordCloudFormed(formedTime - this.initialRequestTime, formedByRSU);
        this.printStats(type);
        return;
    }

    public void printStats(String status) {
        String message = "Cloud with members ";
        for (int i = 0; i < workingMembers.size(); i++) {
            message += workingMembers.get(i).id + " ";
        }        
        message += ("for app id " + appId + " " + status);
        System.out.println(message);
    }
}