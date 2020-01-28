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
1. Teardown of VC
2. Mobility of vehicles  
Instead of a hard segment approach followed currently for each interval first update the positions for all vehicles. Iterate over the vehicle list in chunks of a segment, simulate one time unit for each chunk.

# Doubts
[2] For Graph 3 will constant vehicle speed will work or the average speed should be varied.
