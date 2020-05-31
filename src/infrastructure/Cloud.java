/*
    Cloud.java: Provides an API for the internal processing of cloud.
    For a cloud instance, ensure that only one vehicle/RSU is controlling it.
    This is usually the leader.
*/

package infrastructure;

import java.util.*;

public class Cloud {
    int appId;
    int currentLeaderId;
    boolean formedByRSU;
    Statistics statsStore;
    int requestIdCounter;
    int initialRequestTime;
    int resourceQuotaMetTime;
    // globalWorkStore tracks all ongoing requests
    Map<Integer, Map<Integer,Integer>> globalWorkStore;
    
    // Class for storing a request
    private class Request {
        int id;
        int resourcesNeeded;

        public Request(int id, int resourcesNeeded) {
            this.id = id;
            this.resourcesNeeded = resourcesNeeded;
        }
    }   
    Queue<Request> pendingRequests;
    List<Integer> requestGenerationTime;
    
    // Pair class because Java doesn't have one
    private class Pair {
        int first;
        int second;

        public Pair(int a, int b) {
            first = a;
            second = b;
        }

        @Override
        public boolean equals(Object p) {
            System.out.println("In equals");
            if (p instanceof Pair) {
                Pair pp = (Pair) p;
                return pp.first == first && pp.second == second;
            }
            else {
                return false;
            }
        }
    }
    TreeSet<Pair> freeResourcesSet; // Contains (donation, member) pairs
    Map<Integer,Integer> freeResourcesMap; // Map member to its donation, acts as member list
    int totalFreeResource; // Amount of pooled resources currently free

    // (Potential) leader class stores members sorted by their LQI
    class Leader {
        int id;
        float speed;
        float LQI;

        public Leader(int id, float speed) {
            this.id = id;
            this.speed = speed;
            this.LQI = 0;
        }
    }
    List<Leader> futureLeaders;
    class SortByLQI implements Comparator<Leader> {
        public int compare(Leader x, Leader y) {
            if (x.LQI < y.LQI) return -1;
            else if (x.LQI == y.LQI) return 0;
            return 1;    
        }
    }

    public Cloud(Statistics statsStore, int appId, int parentId, boolean formedByRSU, int initialRequestTime) {
        this.appId = appId;
        this.currentLeaderId = parentId;
        this.statsStore = statsStore;
        this.formedByRSU = formedByRSU;
        this.requestIdCounter = 0;
        this.initialRequestTime = initialRequestTime; 
        this.resourceQuotaMetTime = Integer.MAX_VALUE;
        this.globalWorkStore = new HashMap<Integer, Map<Integer,Integer>>();
        this.pendingRequests = new LinkedList<>();
        this.requestGenerationTime = new ArrayList<>();
        // Memeber with highest amount of free resource is at set beginning
        this.freeResourcesSet = new TreeSet<Pair>(new Comparator<Pair>() {
            @Override
            public int compare(Pair x, Pair y) {
                if (x.first == y.first) {
                    return x.second - y.second;
                }
                else if (x.first < y.first) {
                    return 1;
                }
                else return -1;
            }
        });
        this.freeResourcesMap = new HashMap<Integer,Integer>();
        this.totalFreeResource = 0;
        this.futureLeaders = new LinkedList<>();
    }

    private void add(int resourceAmount) {
        totalFreeResource += resourceAmount;
        // System.err.println(totalFreeResource);
    }

    private void subtract(int resourceAmount) {
        totalFreeResource -= resourceAmount;
        assert(totalFreeResource >= 0);
        // System.err.println(totalFreeResource);
    }

    public boolean isMember(int id) {
        return freeResourcesMap.containsKey(id);
    }

    // Add member to cloud (in resource pool and leader list)
    public void addMember(int id, int resourceLimit, float velocity) {
        assert(resourceLimit > 0);
        if (freeResourcesMap.containsKey(id)) {
            // already present
            return;
        }
        else {
            freeResourcesMap.put(id, resourceLimit);
            freeResourcesSet.add(new Pair(resourceLimit, id));
            futureLeaders.add(new Leader(id, velocity));
            // totalFreeResource += resourceLimit;
            add(resourceLimit);
        }
    }

    /*
        This function is used to split the resources required for a request
        among members. Implements a greedy approach of starting from the
        member with highest free resources first to reduce number of splits
    */
    private Map<Integer,Integer> allocateResource(int resourcesNeeded) {
        assert(resourcesNeeded >= 0);
        assert(resourcesNeeded <= totalFreeResource);
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
                // totalFreeResource -= acceptedResources;
                subtract(acceptedResources);
            }
            else {
                freeResourcesMap.replace(p.second, 0);
                workAssignment.put(p.second, p.first);
                allocatedResources += p.first;
                // totalFreeResource -= p.first;
                subtract(p.first);
            }
        }
        assert(allocatedResources == resourcesNeeded);
        return workAssignment;
    } 

    // This function is used to enqueue a new request
    public void addNewRequest(int requestorId, int appId, int offeredResources, float velocity, int generationTime) {
        int requestId = requestIdCounter;
        requestGenerationTime.add(generationTime);
        requestIdCounter++;
        // Add to pending request queue
        pendingRequests.add(new Request(requestId, Config.APPLICATION_REQUIREMENT[appId]));
        statsStore.changeTotalRequestsQueued(true);
        // Also add as an member
        addMember(requestorId, offeredResources, velocity);
        return;
    }

    // When a working member reverts with a PDONE, the resource
    // is put back in the resource pool
    private void replenishResource(int id, int replenishAmount) {
        assert(isMember(id));
        int freeResource = freeResourcesMap.get(id);
        if (freeResource > 0) {
            boolean removed = freeResourcesSet.remove(new Pair(freeResource, id));
            assert(removed == true);
        }
        freeResourcesSet.add(new Pair(freeResource + replenishAmount, id));
        freeResourcesMap.replace(id, freeResource + replenishAmount);
        // totalFreeResource += replenishAmount;
        add(replenishAmount);
        return;
    }

    /*
        This function is used to track that one working member has finished its subtask.
        Has several checks in place, request id should be valid and the member should have
        been alloted a subtask in the firist place.
        Also if a worker was alloted more than one subtask (in reassignment), then reduce
        the remaining work as necessary.
    */
    public void markAsDone(int reqId, int workerId, int workDoneAmount, int currentTime) {
        // System.out.println("Worker " + workerId + " submits " + workDoneAmount + " work for request id " + reqId);
        if (!globalWorkStore.containsKey(reqId)) {
            // System.out.println("Worker " + workerId + " illegal reqId " + workDoneAmount + " work for request id " + reqId);
            return;
        }
        Map<Integer,Integer> workAssignment = globalWorkStore.get(reqId);
        if (!workAssignment.containsKey(workerId)) {
            // printFreeResourceMap(workAssignment);
            // System.out.println("Worker " + workerId + " illegal worker " + workDoneAmount + " work for request id " + reqId);
            return;
        }
        int workAllocated = workAssignment.get(workerId);
        if (workAllocated < workDoneAmount) {
            System.out.printf("App id %d, request id %d, worker id %d:\n", appId, reqId, workerId);
            printFreeResourceMap(globalWorkStore.get(reqId));
            System.out.println("Work allocated " + workAllocated + " but work done " + workDoneAmount);
        }
        // assert workAllocated >= workDoneAmount : "Work allocated " + workAllocated + " but work done " + workDoneAmount;  
        workDoneAmount = Math.min(workDoneAmount, workAllocated);
        if (workAllocated - workDoneAmount == 0) {
            workAssignment.remove(workerId);
            // System.out.println("Worker " + workerId + " has done " + workDoneAmount + " work for request id " + reqId + " and deleted, remaining " + workAssignment.size());
        }
        else {
            workAssignment.replace(workerId, workAllocated - workDoneAmount);
            // System.out.println("Worker " + workerId + " has done " + workDoneAmount + " work for request id " + reqId + " out of " + workAllocated + ", remaining " + workAssignment.size());
        }
        replenishResource(workerId, workDoneAmount);
        if (globalWorkStore.get(reqId).isEmpty()) {
            // System.out.printf("Request %d serviced\n", reqId);
            globalWorkStore.remove(reqId);
            this.statsStore.recordRequestServiced(currentTime - requestGenerationTime.get(reqId));
        }
        return;
    }

    private void printFreeResourceMap(Map<Integer,Integer> map) {
        String message = "Map of size " + map.size() + ": ";
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            message += (entry.getKey() + "(" + entry.getValue() + ") "); 
        }
        System.out.println(message);
    }

    private void printFreeResourceSet() {
        String message = "Set of size " + freeResourcesSet.size() + ": ";
        for (Pair p : freeResourcesSet) {
            message += (p.second + "(" + p.first + ") "); 
        }
        System.out.println(message);
    }

    // This function is to delete a leaving member from the cloud
    // Also finds if a leaving member had some alloted task.
    // If so, this work is reallocated and the allocation communicated back to leader
    public Map<Integer, Map<Integer,Integer>> reassignWork(int id) {
        Map<Integer, Map<Integer,Integer>> complementWorkStore = new HashMap<Integer, Map<Integer,Integer>>();
        if (!isMember(id)) {
            // System.out.println(id + " is not a member of cloud " + appId);
            return complementWorkStore;
        }
        // Delete the member
        int resourceProvided = freeResourcesMap.get(id);
        if (resourceProvided > 0) {
            boolean removed = freeResourcesSet.remove(new Pair(resourceProvided, id));
            if (!removed) {
                System.out.println(id + "," + resourceProvided);
                printFreeResourceMap(freeResourcesMap);
                printFreeResourceSet();
            }
            assert(removed == true);
        }
        freeResourcesMap.remove(id);
        // totalFreeResource -= resourceProvided;
        subtract(resourceProvided);
        for (Leader potentiaLeader : futureLeaders) {
            if (potentiaLeader.id == id) {
                futureLeaders.remove(potentiaLeader);
                break;
            }
        }
        // Iterate through globalWorkStore and find where this member is currently working
        globalWorkStore.forEach((reqId, workAssignment) -> {
            if (workAssignment.containsKey(id)) {
                int resourcesNeeded = workAssignment.get(id);
                workAssignment.remove(id);
                if (resourcesNeeded > totalFreeResource) {
                    System.out.println("Forfeit work " + resourcesNeeded + " by " + id + ", total resources " + totalFreeResource);
                }
                else {
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
                }
            }
        });
        return complementWorkStore;
    }

    /*
        This function is used to process pending pending order in FIFO manner.
        Schedules as many requests from head of queue as possible in the resource pool.
        Returns a map of request_id -> member_id -> work_allocated
    */
    public Map<Integer, Map<Integer,Integer>> processPendingRequests() {
        Map<Integer, Map<Integer,Integer>> complementGlobalWorkStore = new HashMap<Integer, Map<Integer,Integer>>();
        while (!pendingRequests.isEmpty()) {
            Request currentRequest = pendingRequests.peek();
            if (currentRequest.resourcesNeeded > totalFreeResource) {
                break;
            }
            else {
                statsStore.changeTotalRequestsQueued(false);
                Map<Integer,Integer> workAssignment = allocateResource(currentRequest.resourcesNeeded);
                assert(!globalWorkStore.containsKey(currentRequest.id));
                globalWorkStore.put(currentRequest.id, workAssignment);
                Map<Integer,Integer> workAssignmentCopy = new HashMap<Integer,Integer>();
                workAssignment.forEach((workerId, work) -> { 
                    workAssignmentCopy.put(workerId, work);
                });
                complementGlobalWorkStore.put(currentRequest.id, workAssignmentCopy);
                pendingRequests.remove();
            }
        }
        return complementGlobalWorkStore;
    }

    /*
        Following functions are used for assigning cloud leader.
    */
    public boolean isCloudLeader(int id) {
        return currentLeaderId == id;
    }

    // Check if the given vehicle id is the next leader
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

    // This function is used calculate members by LQI and sort them
    // Here, LQI = sum |speed difference| hence lower is better
    public void electLeader() {
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
        return (resourceQuotaMetTime == Integer.MAX_VALUE &&
            totalFreeResource >= Config.APPLICATION_REQUIREMENT[appId]);
    }

    public boolean allRequestsServiced() {
        return (globalWorkStore.isEmpty() && pendingRequests.isEmpty());
    }

    public void recordCloudFormed(int formedTime) {
        this.resourceQuotaMetTime = formedTime;
        this.statsStore.recordCloudFormed(formedTime - this.initialRequestTime, formedByRSU);
        return;
    }

    public void printStats() {
        String message = "Cloud with leader " + currentLeaderId + ", members ";
        for (Map.Entry<Integer, Integer> entry : freeResourcesMap.entrySet()) {
            message += (entry.getKey() + "(" + entry.getValue() + ") "); 
        }
        message += "formed.";
        System.out.println(message);
    }
}