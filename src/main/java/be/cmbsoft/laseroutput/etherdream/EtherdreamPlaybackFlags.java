package be.cmbsoft.laseroutput.etherdream;

import static be.cmbsoft.laseroutput.etherdream.Etherdream.isFlag;

public class EtherdreamPlaybackFlags
{
    private final boolean shutterState;
    private final boolean underFlow;    //Likely bugged and not sent when an underflow occurs
    private final boolean eStop;

    public EtherdreamPlaybackFlags(short flags)
    {
        shutterState = isFlag(flags, 0);
        underFlow = isFlag(flags, 1);
        eStop = isFlag(flags, 2);
    }

    public boolean isShutterState()
    {
        return shutterState;
    }

    public boolean isUnderFlow()
    {
        return underFlow;
    }

    public boolean iseStop()
    {
        return eStop;
    }
}
