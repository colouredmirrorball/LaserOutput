package be.cmbsoft.laseroutput;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        setName("Etherdream output thread");
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
        return alias == null ? !devices.isEmpty() :
            Optional.ofNullable(devices.get(alias)).map(device -> !device.stale()).orElse(false);
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
        setName("Etherdream output thread for " + alias);
        return this;
    }

    public Collection<Etherdream> getDetectedDevices()
    {
        return devices.values();
    }

}
