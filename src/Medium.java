import java.util.concurrent.Phaser;

public class Medium {        
    Channel channels[];

    public Medium(int stopTime, Phaser timeSync) {
        this.channels = new Channel[Config.TOTAL_CHANNEL_COUNT];
        for (int i = 0; i < Config.TOTAL_CHANNEL_COUNT; i++) {
            channels[i] = new Channel(i, stopTime, timeSync);
        }
    }  
    
    public Channel getChannel(int index) {
        assert(index >= 0 && index < Config.TOTAL_CHANNEL_COUNT);
        return channels[index];
    } 
}