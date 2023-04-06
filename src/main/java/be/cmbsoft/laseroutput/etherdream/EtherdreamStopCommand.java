package be.cmbsoft.laseroutput.etherdream;

public class EtherdreamStopCommand implements EtherdreamCommand
{
    @Override
    public byte[] getBytes()
    {
        return new byte[]{(byte) 's'};
    }

    @Override
    public char getCommandChar()
    {
        return 's';
    }
}
