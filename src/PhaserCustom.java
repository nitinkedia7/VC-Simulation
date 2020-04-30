import java.util.concurrent.Phaser;

public class PhaserCustom extends Phaser {
    Channel channelRef;

    public PhaserCustom(Channel channel) {
        super();
        channelRef = channel;
    }

    protected boolean onAdvance(int phase, int parties) {
        channelRef.clearTransmitterPositions();
        return false;
    }
}