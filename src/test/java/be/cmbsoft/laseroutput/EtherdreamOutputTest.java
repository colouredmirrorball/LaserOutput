package be.cmbsoft.laseroutput;


import be.cmbsoft.ilda.IldaFrame;
import org.junit.jupiter.api.Test;

public class EtherdreamOutputTest extends AbstractOutputTest
{
    @Test
    void sendCircle() throws InterruptedException
    {
        IldaFrame        ildaFrame = generateCircle();
        EtherdreamOutput output    = new EtherdreamOutput();
        long             now       = System.currentTimeMillis();
        while (output.getDetectedDevices() == 0)
        {
            Thread.sleep(6000);
            if (System.currentTimeMillis() - now > 5000)
            {
                System.out.println("No devices found :(");
                break;
            }
        }
        output.project(ildaFrame.getCopyOnWritePoints());
        Thread.sleep(50000);
        output.halt();
    }

    @Test
    void listenForDevices() throws InterruptedException
    {
        EtherdreamOutput output = new EtherdreamOutput();
        Thread.sleep(10000);
        System.out.println(output.getDetectedDevices());
        output.halt();
    }

}
