# How to Compile
1. Navigate to src/ folder by `cd src`
2. Delete class files using `find . -name "*.class" -type f -delete`
3. Compile by `javac advanced/Simulation.java` or `javac classic/Simulation.java`

# How to Run
1. Regular run using `java advanced.Simulation`
2. Run with assertions using `java -ea advanced.Simulation`
3. Run with [async-profiler](https://github.com/jvm-profiling-tools/async-profiler) using `java -agentpath:/home/nitin/async-profiler-1.7-linux-x64/build/libasyncProfiler.so=start,file=profile.svg Simulator`

# Output
1. All logs are stored at `logs/` which is in the same level as `src/`
2. Each run will generate a unique folder using _System.currentTimeMillis()_ eg. `1588266314130/`
3. Each invocation of run() of Simulation class will have a log file eg. `24_60/`
4. A csv file (to generate plots) will be generated having results from all the Simulation.run() invocations.

# Result Metrics
Definitions of plotted metrics:  
1. _Clustering overhead vs Vehicle density_  
Defined as the ratio of total cloud formation packets transmitted vs total packets transmitted
2. _Cloud formation time vs Vehicle density_ 
Formation time for a (fresh) cloud is the difference between leader recognising itself as leader and the request generation time
3. _Leader changes vs Vehicle Speed_
4. _Requests Generated/Serviced/Queued vs Vehicle density_
Use a stacked column graph to verify thar requests are being full-filled by the leader.

Graphs are available at this [Google Sheets link](https://docs.google.com/spreadsheets/d/174WfTeKtr4LEfkfxB45uXd_G1JdsvFf7tRWB-gQNcro/edit?usp=sharing).

# Benchmark
Following results were obtained using `time java Simulator`

| Vehicle density | real | user | sys | Remarks | commit |
| --------------- | ---- | ---- | --- | ------- | -------- |
| (8,36,4) | 36m7.589s | 100m27.850s | 29m24.983s | Full simulation first half | dfc6715 |
| 24 | 32m9.841s |  82m49.220s | 25m19.116s | Full simulation second half | dfc6715 |
| 24 |  5m15.805s | 13m58.626s | 4m5.868s | Single run | dfc6715 |
| 24 | 2m44.564s | 7m15.348s | 1m59.026s | Single run | perf-optimisation |

# Reference
1. CSMA pseudocode:
```
if (backoff == 0) {
    if channel is free {
        transmit
        backoff = 0
        cw = cw_base
    }
    else {
        cw *= 2
        if (cw < cw_max) {
            backoff = rand(cw) + 1
        }
        else {
            // could not transfer start all over again
        }
    }
}
else {
    if channel is free {
        decrement backoff
    }
}
```
