package be.cmbsoft.laseroutput;

import be.cmbsoft.ilda.IldaFrame;
import org.junit.jupiter.api.Test;

class EtherdreamOutputTest extends AbstractOutputTest
{
    @Test
    void sendCircle() throws InterruptedException
    {
        IldaFrame        ildaFrame = generateCircle();
        EtherdreamOutput output    = new EtherdreamOutput();
        long             now       = System.currentTimeMillis();
        while (output.getDetectedDevices() == 0)
        {
            Thread.sleep(100);
            if (System.currentTimeMillis() - now > 5000)
            {
                System.out.println("No devices found :(");
                break;
            }
        }
        output.project(ildaFrame.getCopyOnWritePoints());
        Thread.sleep(500);
        output.halt();
    }

}
