package be.cmbsoft.laseroutput.etherdream;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;

import static be.cmbsoft.laseroutput.etherdream.Etherdream.log;
import static be.cmbsoft.laseroutput.etherdream.Etherdream.logException;

/**
 * This class will listen to incoming broadcasts sent every second by the device. It will discover new devices and
 * update the status of existing devices.
 */

public class EtherdreamDiscoverer implements Runnable
{

    private final Map<String, Etherdream> devices;

    private boolean interrupted = false;
    private int attemptIntervalTime = 1;

    public EtherdreamDiscoverer(Map<String, Etherdream> devices)
    {
        this.devices = devices;
    }

    @Override
    public void run()
    {
        while (!interrupted)
        {
            try (DatagramSocket socket = new DatagramSocket(7654))
            {
                byte[] buffer = new byte[512];
                DatagramPacket response = new DatagramPacket(buffer, 36);
                socket.receive(response);
                InetAddress address = response.getAddress();
                EtherdreamBroadcast broadcast = new EtherdreamBroadcast(buffer);
                String mac = broadcast.getMac();
                synchronized (devices) {
                    log("found device in discoverer: " + mac);
                    Etherdream etherdream = devices.get(mac);
                    if (etherdream == null) {
                        log("Found an Etherdream: " + mac);
                        devices.put(mac, new Etherdream(address, broadcast));
                    } else {
                        etherdream.update(broadcast);
                    }
                }
                attemptIntervalTime = 1;
            } catch (BindException exception) {
                attemptIntervalTime *= 2;
            } catch (IOException exception) {
                logException(exception);
            }
            try {
                Thread.sleep(attemptIntervalTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void stop()
    {
        interrupted = true;
    }
}
