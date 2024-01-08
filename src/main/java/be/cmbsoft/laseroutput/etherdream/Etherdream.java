package be.cmbsoft.laseroutput.etherdream;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

import be.cmbsoft.ilda.IldaPoint;

public class Etherdream
{
    private final EtherdreamCommunicationThread3 thread;
    private       EtherdreamBroadcast            broadcast;
    private       boolean                        connectionFailed = false;


    public Etherdream(InetAddress address, EtherdreamBroadcast broadcast)
    {
        this.broadcast = broadcast;
        thread = new EtherdreamCommunicationThread3(address, this);
        thread.start();
    }

    public static boolean isFlag(short flags, int position)
    {
        return ((flags >> position) & 0x01) == 1;
    }

    public void update(EtherdreamBroadcast broadcast)
    {
        this.broadcast = broadcast;
    }

    public void project(List<IldaPoint> points, int pps)
    {
        if (thread.isHalted())
        {
            thread.start();
        }
        thread.project(points, pps);
    }

    public void stop()
    {
        try {
            thread.halt();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

}
