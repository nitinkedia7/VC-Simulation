package infrastructure;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.text.DecimalFormat;

public class Statistics {
    AtomicInteger totalCloudsFormedSelf;
    AtomicInteger totalCloudsFormationTimeSelf;
    AtomicInteger totalCloudsFormedRSU;
    AtomicInteger totalCloudsFormationTimeRSU;
    AtomicInteger totalRequestsServiced;
    AtomicInteger totalRequestsQueued;
    AtomicInteger leaderChangeCount;
    AtomicInteger leaderLeaveCount;
    AtomicInteger rrepReceivecCount;
    
    DecimalFormat decimalFormat;

    public class PacketStat {
        Config.PACKET_TYPE type;
        AtomicInteger generatedCount;
        AtomicInteger transmittedCount;
        AtomicInteger receivedCount;
        AtomicInteger totalTransmitTime;
        AtomicInteger totalReceiveTime;

        public PacketStat(Config.PACKET_TYPE type) {
            this.type = type;
            this.generatedCount = new AtomicInteger();
            this.transmittedCount = new AtomicInteger();
            this.receivedCount = new AtomicInteger();
            this.totalTransmitTime = new AtomicInteger();
            this.totalReceiveTime = new AtomicInteger();
        }

        public void printStatistics() {
            System.out.println("-----------------------------------------");
            System.out.println("Packet type " + type);
            System.out.println("Total packets generated = " + generatedCount);
            System.out.println("Total packets transmitted = " + transmittedCount);
            System.out.println("Total packets received = " + receivedCount);
            System.out.println("Average transmit time in ms = " + decimalFormat.format(totalTransmitTime.floatValue() / transmittedCount.intValue()));
            System.out.println("Average receive time in ms = " + decimalFormat.format(totalReceiveTime.floatValue() / receivedCount.intValue()));
        }
    }
    Map<Config.PACKET_TYPE, PacketStat> packetStats;

    public Statistics() {
        decimalFormat = new DecimalFormat();
        decimalFormat.setMaximumFractionDigits(4);

        packetStats = new HashMap<Config.PACKET_TYPE, PacketStat>();
        for (Config.PACKET_TYPE type : Config.PACKET_TYPE.values()) {
            packetStats.put(type, new PacketStat(type));

        }

        totalCloudsFormedSelf = new AtomicInteger();
        totalCloudsFormationTimeSelf = new AtomicInteger();
        totalCloudsFormedRSU = new AtomicInteger();
        totalCloudsFormationTimeRSU = new AtomicInteger();
        totalRequestsServiced = new AtomicInteger();
        totalRequestsQueued = new AtomicInteger();
        
        leaderChangeCount = new AtomicInteger();
        leaderLeaveCount = new AtomicInteger();
        rrepReceivecCount = new AtomicInteger();        
    }

    public void incrGenCount(Config.PACKET_TYPE type) {
        packetStats.get(type).generatedCount.incrementAndGet();
    }

    public void recordTransmission(Config.PACKET_TYPE type, int transmitTime) {
        assert (transmitTime >= 0) : "Negative transmission time encountered for a packet.";
        packetStats.get(type).transmittedCount.incrementAndGet();
        packetStats.get(type).totalTransmitTime.addAndGet(transmitTime);
    }

    public void recordReception(Config.PACKET_TYPE type, int receiveTime) {
        assert (receiveTime >= 0) : "Negative receive time encountered for a packet.";
        packetStats.get(type).receivedCount.incrementAndGet();
        packetStats.get(type).totalReceiveTime.addAndGet(receiveTime);
    }

    public void recordCloudFormed(int formationTime, boolean formedByRSU) {
        if (formedByRSU) {
            totalCloudsFormedRSU.incrementAndGet();
            totalCloudsFormationTimeRSU.addAndGet(formationTime);
        }
        else {
            totalCloudsFormedSelf.incrementAndGet();
            totalCloudsFormationTimeSelf.addAndGet(formationTime);
        }
    }

    public void changeTotalRequestsQueued(boolean incr) {
        if (incr) {
            totalRequestsQueued.incrementAndGet();
        }
        else {
            totalRequestsQueued.decrementAndGet();
        }
    }

    public void incrTotalRequestsServiced() {
        totalRequestsServiced.incrementAndGet();
    }

    public void incrLeaderChangeCount() {
        leaderChangeCount.incrementAndGet();
    }

    public void incrLeaderLeaveCount() {
        leaderLeaveCount.incrementAndGet();
    }

    public void incrRrepReceiveCount() {
        rrepReceivecCount.incrementAndGet();
    }

    public void printStatistics(int vehiclesPerSegment, int averageVehicleSpeed, FileWriter csvFileWriter) {
        int totalGeneratedCount = 0;
        int totalTransmittedCount = 0;
        int totalReceivedCount = 0;
        int totalTransmitTime = 0;
        int totalReceiveTime = 0;

        for (Config.PACKET_TYPE type : Config.PACKET_TYPE.values()) {
            packetStats.get(type).printStatistics();
            totalGeneratedCount += packetStats.get(type).generatedCount.intValue();
            totalTransmittedCount += packetStats.get(type).transmittedCount.intValue();
            totalReceivedCount += packetStats.get(type).receivedCount.intValue();
            totalTransmitTime += packetStats.get(type).totalTransmitTime.intValue();
            totalReceiveTime += packetStats.get(type).totalReceiveTime.intValue();
        }
        System.out.println("-----------------------------------------");
        System.out.println("All packet types");
        System.out.println("Total packets generated = " + totalGeneratedCount);
        System.out.println("Total packets transmitted = " + totalTransmittedCount);
        System.out.println("Total packets received = " + totalReceivedCount);
        System.out.println("Average transmit time in ms = " + decimalFormat.format(((float) totalTransmitTime) / totalTransmittedCount));
        System.out.println("Average receive time in ms = " + decimalFormat.format(((float) totalReceiveTime) / totalReceivedCount));
    
        float averageClusterOverhead = 
            totalTransmittedCount
            - packetStats.get(Config.PACKET_TYPE.PSTART).transmittedCount.intValue()
            - packetStats.get(Config.PACKET_TYPE.PDONE).transmittedCount.intValue();
        averageClusterOverhead /= totalTransmittedCount;
        float averageCloudFormationTimeSelf = totalCloudsFormationTimeSelf.floatValue() / totalCloudsFormedSelf.intValue();
        float averageCloudFormationTimeRSU = totalCloudsFormationTimeRSU.floatValue() / totalCloudsFormedRSU.intValue();
        System.out.println("-----------------------------------------");
        System.out.println("Average cluster overhead = " + decimalFormat.format(averageClusterOverhead));
        System.out.println("Total clouds formed by RSU = " + totalCloudsFormedRSU);
        System.out.println("Average cloud formation time (ms) by RSU = " + decimalFormat.format(averageCloudFormationTimeRSU));
        System.out.println("Total clouds formed distributedly = " + totalCloudsFormedSelf);
        System.out.println("Average cloud formation time (ms) distributedly = " + decimalFormat.format(averageCloudFormationTimeSelf));
        System.out.println("Total requests serviced = " + totalRequestsServiced);
        System.out.println("Total requests still queued = " + totalRequestsQueued);
        System.out.println("Leader change count = " + leaderChangeCount);
        System.out.println("Leader leave count = " + leaderLeaveCount);
        System.out.println("RREP received by leader/RSU = " + rrepReceivecCount);

        String csvRow = String.format(
            "%d\t%d\t%d\t%d\t%d\t%s\t%d\t%s\t%d\t%s\t%d\n",
            vehiclesPerSegment,
            averageVehicleSpeed,
            packetStats.get(Config.PACKET_TYPE.RREQ).generatedCount.intValue() + packetStats.get(Config.PACKET_TYPE.RJOIN).generatedCount.intValue(),
            totalRequestsServiced.intValue(),
            totalRequestsQueued.intValue(),
            decimalFormat.format(averageClusterOverhead),
            totalCloudsFormedRSU.intValue(),
            decimalFormat.format(averageCloudFormationTimeRSU),
            totalCloudsFormedSelf.intValue(),
            decimalFormat.format(averageCloudFormationTimeSelf),
            leaderChangeCount.intValue()
        );
        try {
            csvFileWriter.write(csvRow);
            csvFileWriter.flush();
        } catch (IOException ioe) {
            System.err.println("IOException: " + ioe.getMessage());
        }
    }
}