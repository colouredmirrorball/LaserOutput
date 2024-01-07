package be.cmbsoft.laseroutput;

import org.junit.jupiter.api.Test;

import be.cmbsoft.ilda.IldaFrame;

class EtherdreamOutputTest extends AbstractOutputTest
{
    @Test
    void sendCircle() throws InterruptedException
    {
        IldaFrame ildaFrame = generateCircle();
        System.out.println("There be a frame with " + ildaFrame.getPointCount() + " points.");
        EtherdreamOutput output = new EtherdreamOutput();
        long             now    = System.currentTimeMillis();
        while (output.getDetectedDevicesAmount() == 0)
        {
            Thread.sleep(2000);
            if (System.currentTimeMillis() - now > 5000)
            {
                System.out.println("No devices found :(");
                break;
            }
        }
        output.project(ildaFrame.getCopyOnWritePoints());
        Thread.sleep(5000);
        output.halt();
    }

    @Test
    void listenForDevices() throws InterruptedException
    {
        EtherdreamOutput output = new EtherdreamOutput();
        Thread.sleep(10000);
        System.out.println(output.getDetectedDevicesAmount());
        System.out.println(output.getAlias());
        output.halt();
    }

}
