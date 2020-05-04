package infrastructure;

public class Medium {        
    Channel channels[];

    public Medium() {
        this.channels = new Channel[Config.TOTAL_CHANNEL_COUNT];
        for (int i = 0; i < Config.TOTAL_CHANNEL_COUNT; i++) {
            channels[i] = new Channel(i);
        }
    }  
    
    public Channel getChannel(int index) {
        assert(index >= 0 && index < Config.TOTAL_CHANNEL_COUNT);
        return channels[index];
    } 
}