package be.cmbsoft.laseroutput.etherdream;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import static be.cmbsoft.laseroutput.etherdream.Etherdream.log;
import static be.cmbsoft.laseroutput.etherdream.Etherdream.logException;

/**
 * This class will listen to incoming broadcasts sent every second by the device. It will discover new devices and
 * update the status of existing devices.
 */

public enum EtherdreamDiscoverer implements Runnable
{
    INSTANCE;

    private static final Map<String, Etherdream> devices = new HashMap<>();

    private boolean interrupted         = true;
    private int     attemptIntervalTime = 1;

    public static void startIfYouWerent()
    {
        if (INSTANCE.interrupted)
        {
            new Thread(INSTANCE).start();
        }
    }

    public static Map<String, Etherdream> getDevices()
    {
        return devices;
    }

    public static void stop()
    {
        INSTANCE.interrupted = true;
    }

    @Override
    public void run()
    {
        interrupted = false;
        Thread.currentThread().setName("Etherdream discoverer");
        while (!interrupted)
        {
            try (DatagramSocket socket = new DatagramSocket(7654))
            {
                byte[]         buffer   = new byte[512];
                DatagramPacket response = new DatagramPacket(buffer, 36);
                socket.receive(response);
                InetAddress         address   = response.getAddress();
                EtherdreamBroadcast broadcast = new EtherdreamBroadcast(buffer);
                String              mac       = broadcast.getMac();
                synchronized (devices) {
                    Etherdream etherdream = devices.get(mac);
                    if (etherdream == null) {
                        log("Found an Etherdream: " + mac + " at " + address);
                        devices.put(mac, new Etherdream(address, broadcast));
                        log("Etherdream details:\n  " +
                            "Buffer capacity: \t\t\t" + broadcast.getBufferCapacity() +
                            "\n  Max point rate: \t\t\t" + broadcast.getMaxPointRate() +
                            "\n  Hardware revision number: " + broadcast.getHardwareRevision() +
                            "\n  Software revision number: " + broadcast.getSoftwareRevision());

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
}
