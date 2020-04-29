import java.util.*;

public class Cloud {
    int appId;
    int currentLeaderId;
    boolean formedByRSU;
    Simulator simulatorRef;
    int requestIdCounter;
    int initialRequestTime;
    int resourceQuotaMetTime;
    Map<Integer, Map<Integer,Integer>> globalWorkStore;
    
    private class Request {
        int id;
        int requestorId;
        int resourcesNeeded;

        public Request(int id, int requestorId, int resourcesNeeded) {
            this.id = id;
            this.requestorId = requestorId;
            this.resourcesNeeded = resourcesNeeded;
        }
    }   
    Queue<Request> pendingRequests;
    
    private class Pair {
        int first;
        int second;

        public Pair(int a, int b) {
            first = a;
            second = b;
        }
    }
    TreeSet<Pair> freeResourcesSet;
    Map<Integer,Integer> freeResourcesMap; // Acts as member list
    int totalFreeResource;

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
        this.requestIdCounter = 0;
        this.resourceQuotaMetTime = Integer.MAX_VALUE;
        this.globalWorkStore = new HashMap<Integer, Map<Integer,Integer>>();
        this.pendingRequests = new LinkedList<>();
        this.freeResourcesSet = new TreeSet<Pair>(new Comparator<Pair>() {
            @Override
            public int compare(Pair x, Pair y) {
                if (x.first == y.first) {
                    if (x.second == y.second) {
                        return 1;
                    }
                    return y.second - x.second; 
                }
                else {
                    return y.first - x.first;
                }
            }
        });
        this.freeResourcesMap = new HashMap<Integer,Integer>();
        this.totalFreeResource = 0;
        this.futureLeaders = new LinkedList<>();
    }

    public boolean isMember(int id) {
        return freeResourcesMap.containsKey(id);
    }

    public void addMember(int id, int resourceLimit, double velocity) {
        assert(resourceLimit > 0);
        if (freeResourcesMap.containsKey(id)) {
            // already present
            return;
        }
        else {
            freeResourcesMap.put(id, resourceLimit);
            freeResourcesSet.add(new Pair(resourceLimit, id));
            futureLeaders.add(new Leader(id, velocity));
            totalFreeResource += resourceLimit;
        }
    }

    private Map<Integer,Integer> allocateResource(int resourcesNeeded) {
        assert(resourcesNeeded >= 0);
        int allocatedResources = 0;
        Map<Integer,Integer> workAssignment = new HashMap<Integer, Integer>();
        while (allocatedResources < resourcesNeeded) {
            Pair p = freeResourcesSet.pollFirst();
            if (allocatedResources + p.first > resourcesNeeded) {
                int acceptedResources = resourcesNeeded - allocatedResources;
                freeResourcesMap.replace(p.second, p.first - acceptedResources);
                freeResourcesSet.add(new Pair(p.first - acceptedResources, p.second));
                workAssignment.put(p.second, acceptedResources);
                allocatedResources += acceptedResources;
                totalFreeResource -= acceptedResources;
            }
            else {
                freeResourcesMap.replace(p.second, 0);
                workAssignment.put(p.second, p.first);
                allocatedResources += p.first;
                totalFreeResource -= p.first;
            }
        }
        return workAssignment;
    } 

    public void addNewRequest(Packet reqPacket) {
        assert(reqPacket.appId == appId);
        int id = reqPacket.senderId;
        int resourcesNeeded = reqPacket.reqResources;
        int reqId = requestIdCounter++;
        // Add to pending request queue
        pendingRequests.add(new Request(reqId, id, resourcesNeeded));
        // Also add as an member
        addMember(id, reqPacket.offeredResources, reqPacket.velocity);
        return;
    }

    public Map<Integer, Map<Integer,Integer>> getWorkAssignment(int reqId) {
        Map<Integer, Integer> workAssignment = new HashMap<Integer,Integer>();
        globalWorkStore.get(reqId).forEach((workerId, work) -> {
            workAssignment.put(workerId, work);
        });
        Map<Integer, Map<Integer,Integer>> globalWorkStoreCopy = new HashMap<Integer, Map<Integer,Integer>>();
        globalWorkStoreCopy.put(reqId, workAssignment);
        return globalWorkStoreCopy;
    }

    public void markAsDone(int reqId, int workerId, int workDoneAmount) {
        if (!globalWorkStore.containsKey(reqId)) return;
        Map<Integer,Integer> workAssignment = globalWorkStore.get(reqId);
        if (!workAssignment.containsKey(workerId)) return;

        int workAllocated = workAssignment.get(workerId);
        workAllocated = Math.max(0, workAllocated - workDoneAmount);
        assert(workAllocated >= workDoneAmount);
        if (workAllocated == 0) {
            workAssignment.remove(workerId);
        }
        else {
            workAssignment.replace(workerId, workAllocated);
        }

        if (globalWorkStore.get(reqId).isEmpty()) {
            this.simulatorRef.incrTotalRequestsServiced();
        }
        return;
    }

    public Map<Integer, Map<Integer,Integer>> reassignWork(int id) {
        // Delete the member
        if (!isMember(id)) return null;
        int resourceProvided = freeResourcesMap.get(id);
        boolean removed = freeResourcesSet.remove(new Pair(resourceProvided, id));
        assert(removed == true);
        freeResourcesMap.remove(id);
        totalFreeResource -= resourceProvided;
        for (Leader potentiaLeader : futureLeaders) {
            if (potentiaLeader.id == id) {
                futureLeaders.remove(potentiaLeader);
                break;
            }
        }
        
        // Iterate through globalWorkStore and find where this member is currently working
        Map<Integer, Map<Integer,Integer>> complementWorkStore = new HashMap<Integer, Map<Integer,Integer>>();
        globalWorkStore.forEach((reqId, workAssignment) -> {
            if (!workAssignment.containsKey(id)) return;
            int resourcesNeeded = workAssignment.get(id);
            workAssignment.remove(id);
            Map<Integer,Integer> complementworkAssignment = allocateResource(resourcesNeeded);
            // join workAssignment and workAssignment;
            complementworkAssignment.forEach((workerId, work) -> {
                if (workAssignment.containsKey(workerId)) {
                    workAssignment.replace(workerId, workAssignment.get(workerId) + work);
                } 
                else {
                    workAssignment.put(workerId, work);
                }
            });
            // Build complement work store
            complementWorkStore.put(reqId, complementworkAssignment);
        });
        
        return complementWorkStore;
    }

    public Map<Integer, Map<Integer,Integer>> processPendingRequests() {
        Map<Integer, Map<Integer,Integer>> complementGlobalWorkStore = new HashMap<Integer, Map<Integer,Integer>>();
        while (!pendingRequests.isEmpty()) {
            Request currentRequest = pendingRequests.peek();
            if (currentRequest.resourcesNeeded < totalFreeResource) {
                break;
            }
            else {
                Map<Integer,Integer> workAssignment = allocateResource(currentRequest.resourcesNeeded);
                globalWorkStore.put(currentRequest.id, workAssignment);
                Map<Integer,Integer> workAssignmentCopy = new HashMap<Integer,Integer>();
                workAssignment.forEach((workerId, work) -> { 
                    workAssignmentCopy.put(workerId, work);
                });
                complementGlobalWorkStore.put(currentRequest.id, workAssignmentCopy);
            }
        }
        return complementGlobalWorkStore;
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

    public boolean justMetResourceQuota() {
        return resourceQuotaMetTime == Integer.MAX_VALUE && totalFreeResource >= Config.MAX_RESOURCE_QUOTA;
    }

    public void recordCloudFormed(int formedTime) {
        this.resourceQuotaMetTime = formedTime;
        this.simulatorRef.recordCloudFormed(formedTime - this.initialRequestTime, formedByRSU);
        return;
    }
}