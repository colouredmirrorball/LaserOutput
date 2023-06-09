package be.cmbsoft.laseroutput;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import be.cmbsoft.ilda.IldaPoint;
import be.cmbsoft.laseroutput.etherdream.Etherdream;
import be.cmbsoft.laseroutput.etherdream.EtherdreamDiscoverer;

public class EtherdreamOutput extends LaserOutput
{

    private final HashMap<String, Etherdream> devices = new HashMap<>();
    private final EtherdreamDiscoverer discoverer;

    public EtherdreamOutput()
    {
        discoverer = new EtherdreamDiscoverer(devices);
        Thread discovererThread = new Thread(discoverer);
        discovererThread.setName("Etherdream discoverer");
        discovererThread.start();
    }

    @Override
    public void project(List<IldaPoint> points)
    {
        synchronized (devices)
        {
            Collection<Etherdream> etherdreams = devices.values();

            // TODO support output to multiple (discoverer should already support this)
            etherdreams.stream().findFirst().ifPresent(etherdream -> etherdream.project(points, getPps()));
            devices.entrySet().removeIf(entry -> entry.getValue().connectionFailed());

        }

    }

    @Override
    public void halt()
    {
        devices.values().forEach(Etherdream::stop);
        discoverer.stop();
        super.halt();
    }

    public int getDetectedDevices()
    {
        return devices.size();
    }

}
