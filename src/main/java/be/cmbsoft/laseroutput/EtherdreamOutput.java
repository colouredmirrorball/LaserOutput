package be.cmbsoft.laseroutput;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import be.cmbsoft.ilda.IldaPoint;
import be.cmbsoft.laseroutput.etherdream.Etherdream;
import be.cmbsoft.laseroutput.etherdream.EtherdreamDiscoverer;

public class EtherdreamOutput extends LaserOutput
{

    private final Map<String, Etherdream> devices;
    private       String                  alias;

    public EtherdreamOutput()
    {
        EtherdreamDiscoverer.startIfYouWerent();
        devices = EtherdreamDiscoverer.getDevices();
    }

    @Override
    public void project(List<IldaPoint> points)
    {
        List<IldaPoint> transformedPoints = transform(points);
        synchronized (devices)
        {
            Collection<Etherdream> dreams = devices.values();

            dreams.stream()
                  .filter(dream -> alias == null || dream.getBroadcast().getMac().endsWith(alias))
                  .findFirst()
                  .ifPresent(etherdream -> etherdream.project(transformedPoints, getPps()));
            devices.entrySet().removeIf(entry -> entry.getValue().connectionFailed());
        }

    }

    @Override
    public void halt()
    {
        devices.values().forEach(Etherdream::stop);
        EtherdreamDiscoverer.stop();
        super.halt();
    }

    @Override
    public boolean isConnected()
    {
        return alias == null ? !devices.isEmpty() : devices.containsKey(alias);
    }

    public int getDetectedDevicesAmount()
    {
        return devices.size();
    }

    public String getAlias()
    {
        return alias;
    }

    public EtherdreamOutput setAlias(String alias)
    {
        this.alias = alias;
        return this;
    }

}
