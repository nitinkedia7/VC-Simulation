# Interim Statistics
RREQ/RREP/RACK message count.

# End Result
Three graphs have to be generated:  
1. _Clustering overhead vs Number of vehicles_  
Should be derived from average number of brokerage packets transmitted per VC request.
2. _Service time vs Number of vehicles_  
Average time from initial VC request to cloud formation vs number of vehicles.
3. _Leader Changes vs Vehicle Speed_
Number of leader changes in a particular VC vs mobility of the vehicles.

# TODO
Graphs 1 and 2 are similar. Current approach is to run simulation and print event logs with sufficient information so that the metrics can be computed. Pending items:  

0. Ensure completeness of logs.
Need to fix bugs.
1. Distribution of task and Teardown of VC.
RREQ's additionally have a quota to complete. Currently, each RREP indicates 1 unit of service. After the VC is elected it needs to send message to start processing. After that time every member responds that its work is done. VC then sends teardown message.
2. Mobility of vehicles  
Instead of a hard segment approach followed currently for each interval first update the positions for all vehicles. Iterate over the vehicle list in chunks of a segment, simulate one time unit for each chunk.

# Doubts
[2] For Graph 3 will constant vehicle speed will work or the average speed should be varied.
Discuss the implementation of how the vehicles will move, when LEAVE message will be generated.

Gouping by application category. Suppose two RREQ's come simultaneously for an appiId without a VC. Then RSU will remove one of the requests or join them?

# Bugs
1. Sender itself is reading its packet in the next cycle. DONE
```
Packet : Sender 2 generated RREQ at 3
Segment 0 starting interval 4
Packet : Sender 3 generated RREQ at 4
Packet : Sender 2 wrote RREQ at 3
Packet : Vehicle 1 read RREQ from 2 at 3
Packet : RSU    0 read RREQ from 2 at 3
Packet : Sender 1 generated RREP at 4
Segment 0 starting interval 5
Packet : Vehicle 2 read RREQ from 2 at 3
Packet : Sender 3 wrote RREQ at 4
Packet : Sender 1 wrote RREP at 4
Packet : Sender 2 generated RREP at 5
```
2. Two writes in same interval, need CSMA specs like 50ms control phase etc.