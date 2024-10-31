package be.cmbsoft.laseroutput.etherdream;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;

import be.cmbsoft.ilda.IldaPoint;

public class Etherdream
{
    private final EtherdreamCommunicationThread thread;
    private       EtherdreamBroadcast           broadcast;
    private       boolean                       connectionFailed = false;
    private LocalDateTime lastSeen;


    public Etherdream(InetAddress address, EtherdreamBroadcast broadcast)
    {
        this.broadcast = broadcast;
        thread = new EtherdreamCommunicationThread(address, this);
        thread.start();
    }

    public static boolean isFlag(short flags, int position)
    {
        return ((flags >> position) & 0x01) == 1;
    }

    public void update(EtherdreamBroadcast broadcast)
    {
        this.broadcast = broadcast;
        this.lastSeen = LocalDateTime.now();
    }

    public void project(List<IldaPoint> points, int pps)
    {
        /*
        if (thread.isHalted())
        {
            thread.start();
        }
         */
        thread.project(points, pps);
    }

    public void stop()
    {
        thread.halt();
    }

    public boolean connectionFailed()
    {
        return connectionFailed;
    }

    void setConnectionFailed()
    {
        this.connectionFailed = true;
    }

    public EtherdreamBroadcast getBroadcast()
    {
        return broadcast;
    }

    public static void log(String message)
    {
        //TODO improve logging somehow
        System.out.println(message);
    }

    public static void logException(Exception exception)
    {
        exception.printStackTrace();
    }

    public String getMac()
    {
        return broadcast.getMac();
    }

    /**
     * A device is considered "stale" if a status update broadcast was not received within ten seconds.
     *
     * @return whether device is stale
     */
    public boolean stale()
    {
        return lastSeen == null || lastSeen.plusSeconds(10).isBefore(LocalDateTime.now());
    }

}
